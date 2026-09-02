package com.whatwewillwatchtonight.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Comparator;
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
    private static final Comparator<TmdbResult> BY_POPULARITY = Comparator.comparingDouble(TmdbResult::popularity);

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
     * A title search's outcome: a best-guess poster {@code url} (may be
     * {@code null}), and {@code confident} only when exactly one result matches
     * the title (English or original) <em>and</em> the year. When not confident,
     * confirm with {@link #findPosterUrlByTmdbId} using the exact id.
     */
    public record PosterMatch(String url, boolean confident) {
        static final PosterMatch NONE = new PosterMatch(null, false);
    }

    /**
     * Looks up a poster by title via TMDB multi search (movies <em>and</em> TV,
     * since Letterboxd lists some limited series), scored: exact title beats
     * fuzzy, matching year beats wrong, popularity breaks ties.
     *
     * @param title the film's title; a trailing "(YYYY)" is stripped before searching
     * @param year  the film's release year, for disambiguation; may be {@code null}
     */
    public PosterMatch findPoster(String title, Integer year) {
        if (apiKey.isBlank()) {
            return PosterMatch.NONE;
        }

        String query = TRAILING_YEAR_PATTERN.matcher(title).replaceAll("");

        try {
            TmdbSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search/multi")
                            .queryParam("api_key", apiKey)
                            .queryParam("query", query)
                            .queryParam("include_adult", "false")
                            .build())
                    .retrieve()
                    .body(TmdbSearchResponse.class);

            if (response == null || response.results() == null) {
                return PosterMatch.NONE;
            }

            List<TmdbResult> candidates = response.results().stream()
                    .filter(r -> "movie".equals(r.mediaType()) || "tv".equals(r.mediaType()))
                    .filter(r -> r.posterPath() != null)
                    .toList();

            long exactHits = candidates.stream()
                    .filter(r -> r.titleMatches(query))
                    .filter(r -> year == null || r.matchesYear(year))
                    .count();

            String url = candidates.stream()
                    .max(Comparator
                            .comparingInt((TmdbResult r) -> match(r, query, year))
                            .thenComparing(BY_POPULARITY))
                    .map(r -> POSTER_IMAGE_BASE_URL + r.posterPath())
                    .orElse(null);

            return new PosterMatch(url, url != null && exactHits == 1);
        } catch (RestClientException e) {
            // Not logging e.getMessage(): some exception types include the
            // full request URI, which would leak the API key into logs.
            log.warn("Failed to look up poster for '{}' ({}): {}", title, year, e.getClass().getSimpleName());
            return PosterMatch.NONE;
        }
    }

    /**
     * @param id   the exact TMDB id (from the Letterboxd film page)
     * @param type {@code "tv"} for a series, anything else treated as a movie
     * @return that title's poster URL, or {@code null} if there's no API key,
     *         it has no poster, or the request fails
     */
    public String findPosterUrlByTmdbId(int id, String type) {
        if (apiKey.isBlank()) {
            return null;
        }
        String path = "tv".equals(type) ? "/tv/" + id : "/movie/" + id;
        try {
            TmdbMovie result = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path)
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(TmdbMovie.class);

            if (result == null || result.posterPath() == null) {
                return null;
            }
            return POSTER_IMAGE_BASE_URL + result.posterPath();
        } catch (RestClientException e) {
            log.warn("Failed to fetch TMDB {} {}: {}", type, id, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Ranking score for a search result: +2 if the title matches exactly, +1 if the
     * release year matches. Popularity is the tie-breaker, applied by the caller.
     */
    private static int match(TmdbResult r, String query, Integer year) {
        int score = r.titleMatches(query) ? 2 : 0;
        if (year != null && r.matchesYear(year)) {
            score += 1;
        }
        return score;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbSearchResponse(List<TmdbResult> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbResult(
            @JsonProperty("media_type") String mediaType,
            @JsonProperty("poster_path") String posterPath,
            // movie fields
            @JsonProperty("title") String title,
            @JsonProperty("original_title") String originalTitle,
            @JsonProperty("release_date") String releaseDate,
            // tv fields
            @JsonProperty("name") String name,
            @JsonProperty("original_name") String originalName,
            @JsonProperty("first_air_date") String firstAirDate,
            double popularity) {

        /** True if the query equals this result's title or name, English or original. */
        boolean titleMatches(String query) {
            String q = query.trim();
            return q.equalsIgnoreCase(title) || q.equalsIgnoreCase(originalTitle)
                    || q.equalsIgnoreCase(name) || q.equalsIgnoreCase(originalName);
        }

        /** True if TMDB's release date (movie) or first-air date (TV) is in {@code year}. */
        boolean matchesYear(int year) {
            String date = releaseDate != null && !releaseDate.isBlank() ? releaseDate : firstAirDate;
            return date != null && date.startsWith(Integer.toString(year));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbMovie(@JsonProperty("poster_path") String posterPath) {
    }
}
