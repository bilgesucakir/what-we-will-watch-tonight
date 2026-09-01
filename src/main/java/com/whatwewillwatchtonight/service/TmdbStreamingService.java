package com.whatwewillwatchtonight.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.whatwewillwatchtonight.model.StreamingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks up where films can be streamed, via TMDB's watch-provider data
 * (which TMDB sources from JustWatch). "Streamed" here means a subscription
 * ({@code flatrate}), free, or ad-supported service -- not rent or buy.
 */
@Service
public class TmdbStreamingService {

    private static final Logger log = LoggerFactory.getLogger(TmdbStreamingService.class);
    private static final String LOGO_BASE_URL = "https://image.tmdb.org/t/p/w45";
    private static final int TIMEOUT_MS = 5_000;

    private final String apiKey;
    private final RestClient restClient;

    public TmdbStreamingService(
            RestClient.Builder restClientBuilder,
            @Value("${tmdb.api-key:}") String apiKey,
            @Value("${tmdb.base-url:https://api.themoviedb.org/3}") String baseUrl) {
        this.apiKey = apiKey;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);

        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * @param id     a TMDB id
     * @param type   {@code "tv"} for a series, anything else treated as a movie
     * @param region an ISO-3166-1 country code, e.g. {@code "US"}
     * @return the streaming/free/ad services carrying that title in that region,
     *         or empty if there's no API key, none, or the request fails
     */
    public List<StreamingProvider> streamingOptions(int id, String type, String region) {
        if (apiKey.isBlank()) {
            return List.of();
        }
        String path = ("tv".equals(type) ? "/tv/" : "/movie/") + id + "/watch/providers";
        try {
            WatchProvidersResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path)
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(WatchProvidersResponse.class);

            if (response == null || response.results() == null) {
                return List.of();
            }
            RegionProviders forRegion = response.results().get(region.toUpperCase());
            return forRegion == null ? List.of() : forRegion.streamable();
        } catch (RestClientException e) {
            log.warn("Failed to fetch watch providers for {} {} in {}: {}",
                    type, id, region, e.getClass().getSimpleName());
            return List.of();
        }
    }

    /**
     * @param region an ISO-3166-1 country code
     * @return every provider TMDB lists for movies in that region, most
     *         mainstream first -- used to build the filter chips
     */
    public List<StreamingProvider> providersInRegion(String region) {
        if (apiKey.isBlank()) {
            return List.of();
        }
        try {
            ProviderListResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/watch/providers/movie")
                            .queryParam("api_key", apiKey)
                            .queryParam("watch_region", region)
                            .build())
                    .retrieve()
                    .body(ProviderListResponse.class);

            if (response == null || response.results() == null) {
                return List.of();
            }
            return response.results().stream()
                    .sorted(Comparator.comparingInt(TmdbProvider::displayPriority))
                    .map(TmdbProvider::toModel)
                    .toList();
        } catch (RestClientException e) {
            log.warn("Failed to fetch the provider list for {}: {}", region, e.getClass().getSimpleName());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WatchProvidersResponse(Map<String, RegionProviders> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RegionProviders(
            List<TmdbProvider> flatrate,
            List<TmdbProvider> free,
            List<TmdbProvider> ads) {

        /** flatrate + free + ads, de-duplicated by provider id, priority order. */
        List<StreamingProvider> streamable() {
            Map<Integer, StreamingProvider> byId = new LinkedHashMap<>();
            for (List<TmdbProvider> bucket : List.of(
                    flatrate == null ? List.<TmdbProvider>of() : flatrate,
                    free == null ? List.<TmdbProvider>of() : free,
                    ads == null ? List.<TmdbProvider>of() : ads)) {
                bucket.stream()
                        .sorted(Comparator.comparingInt(TmdbProvider::displayPriority))
                        .forEach(provider -> byId.putIfAbsent(provider.id(), provider.toModel()));
            }
            return List.copyOf(byId.values());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbProvider(
            @JsonProperty("provider_id") int id,
            @JsonProperty("provider_name") String name,
            @JsonProperty("logo_path") String logoPath,
            @JsonProperty("display_priority") int displayPriority) {

        StreamingProvider toModel() {
            return new StreamingProvider(id, name, logoPath == null ? null : LOGO_BASE_URL + logoPath);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProviderListResponse(List<TmdbProvider> results) {
    }
}
