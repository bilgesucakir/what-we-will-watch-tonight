package com.watchlistintersector.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Looks up film poster images from the TMDB (themoviedb.org) API.
 */
@Service
public class TmdbPosterService {

    private static final Logger log = LoggerFactory.getLogger(TmdbPosterService.class);
    private static final String POSTER_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w342";
    private static final Pattern TRAILING_YEAR_PATTERN = Pattern.compile("\\s*\\(\\d{4}\\)\\s*$");
    private static final int TIMEOUT_MS = 5_000;

    private final String apiKey;
    private final RestClient restClient;

    public TmdbPosterService(
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
     * @param title the film's title; a trailing "(YYYY)" is stripped before searching
     * @param year  the film's release year, used to narrow the search; may be {@code null}
     * @return the poster image URL, or {@code null} if no API key is configured, TMDB
     *         has no match, or the request fails
     */
    public String findPosterUrl(String title, Integer year) {
        if (apiKey.isBlank()) {
            return null;
        }

        String query = TRAILING_YEAR_PATTERN.matcher(title).replaceAll("");

        try {
            TmdbSearchResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/search/movie")
                                .queryParam("api_key", apiKey)
                                .queryParam("query", query);
                        if (year != null) {
                            uriBuilder.queryParam("primary_release_year", year);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(TmdbSearchResponse.class);

            if (response == null || response.results().isEmpty()) {
                return null;
            }

            String posterPath = response.results().get(0).posterPath();
            return posterPath == null ? null : POSTER_IMAGE_BASE_URL + posterPath;
        } catch (RestClientException e) {
            // Not logging e.getMessage(): some exception types include the
            // full request URI, which would leak the API key into logs.
            log.warn("Failed to look up poster for '{}' ({}): {}", title, year, e.getClass().getSimpleName());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbSearchResponse(List<TmdbMovie> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbMovie(@JsonProperty("poster_path") String posterPath) {
    }
}
