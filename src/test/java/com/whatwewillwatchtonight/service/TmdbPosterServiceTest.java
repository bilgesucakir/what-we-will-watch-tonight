package com.whatwewillwatchtonight.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.whatwewillwatchtonight.service.TmdbPosterService.PosterMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbPosterServiceTest {

    private static final String MATCH_RESPONSE = """
            { "results": [
                { "media_type": "movie", "title": "Dune: Part Two",
                  "release_date": "2024-02-27", "poster_path": "/poster123.jpg" }
            ] }
            """;

    private static final String NO_POSTER_PATH_RESPONSE = """
            { "results": [
                { "media_type": "movie", "title": "Some Obscure Film",
                  "release_date": "2024-01-01", "poster_path": null }
            ] }
            """;

    private static final String EMPTY_RESPONSE = """
            { "results": [] }
            """;

    // "beef" -> a person, then the obscure 2023 movie, then the popular 2023 series.
    private static final String BEEF_RESPONSE = """
            { "results": [
                { "media_type": "person", "name": "Beefy McBeef", "poster_path": "/person.jpg", "popularity": 3.0 },
                { "media_type": "movie", "title": "Super Beef",
                  "release_date": "2023-05-01", "poster_path": "/superbeef.jpg", "popularity": 1.2 },
                { "media_type": "tv", "name": "BEEF", "original_name": "BEEF",
                  "first_air_date": "2023-04-06", "poster_path": "/beefseries.jpg", "popularity": 240.0 }
            ] }
            """;

    // "dune" -> the 1984 film, then the 2021 film.
    private static final String DUNE_RESPONSE = """
            { "results": [
                { "media_type": "movie", "title": "Dune",
                  "release_date": "1984-12-14", "poster_path": "/dune1984.jpg", "popularity": 20.0 },
                { "media_type": "movie", "title": "Dune",
                  "release_date": "2021-09-15", "poster_path": "/dune2021.jpg", "popularity": 90.0 }
            ] }
            """;

    // "ghosts" -> two same-titled series from the wrong years; the 2020 Turkish
    // film we actually want isn't in the (first page of) results at all.
    private static final String GHOSTS_RESPONSE = """
            { "results": [
                { "media_type": "tv", "name": "Ghosts", "original_name": "Ghosts",
                  "first_air_date": "2019-04-15", "poster_path": "/ghostsBBC.jpg", "popularity": 40.0 },
                { "media_type": "tv", "name": "Ghosts", "original_name": "Ghosts",
                  "first_air_date": "2021-10-07", "poster_path": "/ghostsCBS.jpg", "popularity": 120.0 }
            ] }
            """;

    // "hayaletler" -> the film, carried by TMDB under its original_title.
    private static final String HAYALETLER_RESPONSE = """
            { "results": [
                { "media_type": "movie", "title": "Ghosts", "original_title": "Hayaletler",
                  "release_date": "2020-10-30", "poster_path": "/hayaletler.jpg", "popularity": 2.0 }
            ] }
            """;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        server.createContext("/search/multi", exchange -> {
            String q = queryParam(exchange.getRequestURI().getQuery(), "query");
            String body = switch (q) {
                case "No Poster Film" -> NO_POSTER_PATH_RESPONSE;
                case "Unknown Film" -> EMPTY_RESPONSE;
                case "BEEF" -> BEEF_RESPONSE;
                case "Dune" -> DUNE_RESPONSE;
                case "Ghosts" -> GHOSTS_RESPONSE;
                case "Hayaletler" -> HAYALETLER_RESPONSE;
                default -> MATCH_RESPONSE;
            };
            respond(exchange, 200, body);
        });
        server.createContext("/broken/search/multi", exchange -> respond(exchange, 500, "error"));
        server.createContext("/movie/693134", exchange ->
                respond(exchange, 200, "{ \"poster_path\": \"/exactMovie.jpg\" }"));
        server.createContext("/tv/17174", exchange ->
                respond(exchange, 200, "{ \"poster_path\": \"/exactTv.jpg\" }"));
        server.createContext("/movie/404", exchange ->
                respond(exchange, 200, "{ \"poster_path\": null }"));

        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return "";
        }
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return "";
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

    // --- findPoster ---------------------------------------------------------

    @Test
    void returnsAConfidentMatchWhenOneResultFitsTitleAndYear() {
        PosterMatch match = serviceWith("test-key", baseUrl).findPoster("Dune: Part Two (2024)", 2024);

        assertThat(match.url()).isEqualTo("https://image.tmdb.org/t/p/w342/poster123.jpg");
        assertThat(match.confident()).isTrue();
    }

    @Test
    void stillReturnsAUrlWhenTheYearIsUnknown() {
        PosterMatch match = serviceWith("test-key", baseUrl).findPoster("Dune: Part Two", null);

        assertThat(match.url()).isEqualTo("https://image.tmdb.org/t/p/w342/poster123.jpg");
    }

    @Test
    void prefersTheResultWhoseYearMatches() {
        TmdbPosterService service = serviceWith("test-key", baseUrl);

        assertThat(service.findPoster("Dune (2021)", 2021).url())
                .isEqualTo("https://image.tmdb.org/t/p/w342/dune2021.jpg");
        assertThat(service.findPoster("Dune (1984)", 1984).url())
                .isEqualTo("https://image.tmdb.org/t/p/w342/dune1984.jpg");
    }

    @Test
    void findsPostersForTvSeriesNotJustMovies() {
        assertThat(serviceWith("test-key", baseUrl).findPoster("BEEF (2023)", 2023).url())
                .isEqualTo("https://image.tmdb.org/t/p/w342/beefseries.jpg");
    }

    @Test
    void matchesTheOriginalLanguageTitle() {
        assertThat(serviceWith("test-key", baseUrl).findPoster("Hayaletler (2020)", 2020).url())
                .isEqualTo("https://image.tmdb.org/t/p/w342/hayaletler.jpg");
    }

    @Test
    void ignoresPersonResults() {
        assertThat(serviceWith("test-key", baseUrl).findPoster("BEEF (2023)", 2023).url())
                .doesNotContain("person.jpg");
    }

    @Test
    void isNotConfidentWhenNoResultMatchesBothTitleAndYear() {
        // two same-titled "Ghosts" series, neither from 2020 -> best guess, but flagged
        PosterMatch match = serviceWith("test-key", baseUrl).findPoster("Ghosts (2020)", 2020);

        assertThat(match.url()).isNotNull();
        assertThat(match.confident()).isFalse();
    }

    @Test
    void returnsNoneWhenTmdbHasNoResults() {
        assertThat(serviceWith("test-key", baseUrl).findPoster("Unknown Film (2024)", 2024))
                .isEqualTo(PosterMatch.NONE);
    }

    @Test
    void returnsNoUrlWhenTheMatchedResultHasNoPosterPath() {
        assertThat(serviceWith("test-key", baseUrl).findPoster("No Poster Film (2024)", 2024).url()).isNull();
    }

    @Test
    void returnsNoneWhenApiKeyIsBlank() {
        assertThat(serviceWith("", baseUrl).findPoster("Dune: Part Two (2024)", 2024))
                .isEqualTo(PosterMatch.NONE);
    }

    @Test
    void returnsNoneWhenTheRequestFails() {
        assertThat(serviceWith("test-key", baseUrl + "/broken").findPoster("Dune: Part Two (2024)", 2024))
                .isEqualTo(PosterMatch.NONE);
    }

    // --- findPosterUrlByTmdbId ---------------------------------------------

    @Test
    void findsThePosterForAnExactMovieId() {
        assertThat(serviceWith("test-key", baseUrl).findPosterUrlByTmdbId(693134, "movie"))
                .isEqualTo("https://image.tmdb.org/t/p/w342/exactMovie.jpg");
    }

    @Test
    void findsThePosterForAnExactTvId() {
        assertThat(serviceWith("test-key", baseUrl).findPosterUrlByTmdbId(17174, "tv"))
                .isEqualTo("https://image.tmdb.org/t/p/w342/exactTv.jpg");
    }

    @Test
    void returnsNullForAnIdWithNoPoster() {
        assertThat(serviceWith("test-key", baseUrl).findPosterUrlByTmdbId(404, "movie")).isNull();
    }

    @Test
    void returnsNullForAnIdLookupWhenApiKeyIsBlank() {
        assertThat(serviceWith("", baseUrl).findPosterUrlByTmdbId(693134, "movie")).isNull();
    }
}
