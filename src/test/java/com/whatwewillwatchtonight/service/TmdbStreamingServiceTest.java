package com.whatwewillwatchtonight.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.whatwewillwatchtonight.model.StreamingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbStreamingServiceTest {

    private static final String WATCH_PROVIDERS_RESPONSE = """
            {
              "id": 693134,
              "results": {
                "TR": {
                  "flatrate": [
                    { "provider_id": 8, "provider_name": "Netflix", "logo_path": "/netflix.jpg", "display_priority": 1 }
                  ],
                  "ads": [
                    { "provider_id": 8, "provider_name": "Netflix", "logo_path": "/netflix.jpg", "display_priority": 1 },
                    { "provider_id": 613, "provider_name": "MUBI Amazon Channel", "logo_path": "/mubi.jpg", "display_priority": 5 }
                  ],
                  "rent": [
                    { "provider_id": 3, "provider_name": "Google Play", "logo_path": "/gp.jpg", "display_priority": 2 }
                  ]
                },
                "US": {
                  "flatrate": [
                    { "provider_id": 337, "provider_name": "Disney Plus", "logo_path": "/disney.jpg", "display_priority": 3 }
                  ]
                }
              }
            }
            """;

    private static final String PROVIDER_LIST_RESPONSE = """
            {
              "results": [
                { "provider_id": 8, "provider_name": "Netflix", "logo_path": "/netflix.jpg", "display_priority": 2 },
                { "provider_id": 337, "provider_name": "Disney Plus", "logo_path": null, "display_priority": 1 }
              ]
            }
            """;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/movie/693134/watch/providers", exchange ->
                respond(exchange, 200, WATCH_PROVIDERS_RESPONSE));
        server.createContext("/movie/500/watch/providers", exchange -> respond(exchange, 500, "boom"));
        server.createContext("/watch/providers/movie", exchange ->
                respond(exchange, 200, PROVIDER_LIST_RESPONSE));
        server.createContext("/broken/watch/providers/movie", exchange -> respond(exchange, 500, "boom"));
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private TmdbStreamingService serviceWith(String apiKey, String baseUrl) {
        return new TmdbStreamingService(RestClient.builder(), apiKey, baseUrl);
    }

    @Test
    void returnsFlatrateFreeAndAdProvidersForTheRegionDedupedById() {
        TmdbStreamingService service = serviceWith("test-key", baseUrl);

        List<StreamingProvider> providers = service.streamingOptions(693134, "TR");

        assertThat(providers).extracting(StreamingProvider::name)
                .containsExactly("Netflix", "MUBI Amazon Channel");
        assertThat(providers).extracting(StreamingProvider::logoUrl)
                .containsExactly("https://image.tmdb.org/t/p/w45/netflix.jpg",
                        "https://image.tmdb.org/t/p/w45/mubi.jpg");
    }

    @Test
    void excludesRentAndBuyOnlyOptions() {
        TmdbStreamingService service = serviceWith("test-key", baseUrl);

        assertThat(service.streamingOptions(693134, "TR"))
                .extracting(StreamingProvider::name)
                .doesNotContain("Google Play");
    }

    @Test
    void isCaseInsensitiveAboutTheRegionCode() {
        TmdbStreamingService service = serviceWith("test-key", baseUrl);

        assertThat(service.streamingOptions(693134, "tr")).isNotEmpty();
    }

    @Test
    void returnsEmptyWhenTheRegionHasNoData() {
        TmdbStreamingService service = serviceWith("test-key", baseUrl);

        assertThat(service.streamingOptions(693134, "DE")).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheApiKeyIsBlank() {
        TmdbStreamingService service = serviceWith("", baseUrl);

        assertThat(service.streamingOptions(693134, "TR")).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheRequestFails() {
        TmdbStreamingService service = serviceWith("test-key", baseUrl);

        assertThat(service.streamingOptions(500, "TR")).isEmpty();
    }

    @Test
    void listsRegionProvidersMostMainstreamFirst() {
        TmdbStreamingService service = serviceWith("test-key", baseUrl);

        List<StreamingProvider> providers = service.providersInRegion("TR");

        assertThat(providers).extracting(StreamingProvider::name)
                .containsExactly("Disney Plus", "Netflix");
        assertThat(providers.get(0).logoUrl()).isNull();
        assertThat(providers.get(1).logoUrl()).isEqualTo("https://image.tmdb.org/t/p/w45/netflix.jpg");
    }

    @Test
    void returnsEmptyProviderListWhenTheApiKeyIsBlank() {
        TmdbStreamingService service = serviceWith("", baseUrl);

        assertThat(service.providersInRegion("TR")).isEmpty();
    }

    @Test
    void returnsEmptyProviderListWhenTheRequestFails() {
        TmdbStreamingService service = serviceWith("test-key", baseUrl + "/broken");

        assertThat(service.providersInRegion("TR")).isEmpty();
    }
}
