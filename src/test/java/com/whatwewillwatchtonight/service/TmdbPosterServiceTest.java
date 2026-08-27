package com.watchlistintersector.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbPosterServiceTest {

    private static final String MATCH_RESPONSE = """
            {
              "results": [
                { "title": "Dune: Part Two", "poster_path": "/poster123.jpg" }
              ]
            }
            """;

    private static final String NO_POSTER_PATH_RESPONSE = """
            {
              "results": [
                { "title": "Some Obscure Film", "poster_path": null }
              ]
            }
            """;

    private static final String EMPTY_RESPONSE = """
            { "results": [] }
            """;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        server.createContext("/search/movie", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body;
            if (query != null && query.contains("query=No")) {
                body = NO_POSTER_PATH_RESPONSE;
            } else if (query != null && query.contains("query=Unknown")) {
                body = EMPTY_RESPONSE;
            } else {
                body = MATCH_RESPONSE;
            }
            respond(exchange, 200, body);
        });
        server.createContext("/broken/search/movie", exchange -> respond(exchange, 500, "error"));

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

    private TmdbPosterService serviceWith(String apiKey, String baseUrl) {
        return new TmdbPosterService(RestClient.builder(), apiKey, baseUrl);
    }

    @Test
    void returnsPosterUrlWhenTmdbHasAMatch() {
        TmdbPosterService service = serviceWith("test-key", baseUrl);

        String url = service.findPosterUrl("Dune: Part Two (2024)", 2024);

        assertThat(url).isEqualTo("https://image.tmdb.org/t/p/w342/poster123.jpg");
    }

    @Test
    void returnsNullWhenTmdbHasNoResults() {
        TmdbPosterService service = serviceWith("test-key", baseUrl);

        assertThat(service.findPosterUrl("Unknown Film (2024)", 2024)).isNull();
    }

    @Test
    void returnsNullWhenTheMatchedResultHasNoPosterPath() {
        TmdbPosterService service = serviceWith("test-key", baseUrl);

        assertThat(service.findPosterUrl("No Poster Film (2024)", 2024)).isNull();
    }

    @Test
    void returnsNullWhenApiKeyIsBlank() {
        TmdbPosterService service = serviceWith("", baseUrl);

        assertThat(service.findPosterUrl("Dune: Part Two (2024)", 2024)).isNull();
    }

    @Test
    void returnsNullWhenTheRequestFails() {
        TmdbPosterService service = serviceWith("test-key", baseUrl + "/broken");

        assertThat(service.findPosterUrl("Dune: Part Two (2024)", 2024)).isNull();
    }
}
