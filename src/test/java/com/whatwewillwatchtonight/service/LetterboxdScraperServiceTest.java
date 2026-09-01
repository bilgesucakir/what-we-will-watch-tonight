package com.whatwewillwatchtonight.service;

import com.sun.net.httpserver.HttpServer;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.model.FilmDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LetterboxdScraperServiceTest {

    private static final String ALICE_AVATAR_URL =
            "https://a.ltrbxd.com/resized/avatar/upload/1/2/3/shard/avtr-0-48-0-48-crop.jpg?v=abc123";
    private static final String ALICE_AVATAR_URL_UPGRADED =
            "https://a.ltrbxd.com/resized/avatar/upload/1/2/3/shard/avtr-0-220-0-220-crop.jpg?v=abc123";

    private static final String PAGE_1_WITH_PAGINATION = """
            <html><body>
              <section class="profile-header js-profile-header -is-mini-nav" data-person="Alice">
                <div class="profile-mini-person -has-badge -longbadge">
                  <a class="avatar -a24" href="/alice/"><img src="%s" alt="Alice" width="24" height="24" /></a>
                </div>
              </section>
              <div data-item-slug="dune-part-two" data-item-name="Dune: Part Two (2024)"
                   data-item-full-display-name="Dune: Part Two (2024)" class="poster">
                <img alt="Dune: Part Two" src="empty-poster.jpg"/>
              </div>
              <div data-item-slug="the-substance" data-item-name="The Substance (2024)"
                   data-item-full-display-name="The Substance (2024)" class="poster">
                <img alt="The Substance" src="empty-poster.jpg"/>
              </div>
              <div class="pagination">
                <div class="paginate-pages">
                  <ul>
                    <li class="paginate-page"><a href="/alice/watchlist/page/1/">1</a></li>
                    <li class="paginate-page"><a href="/alice/watchlist/page/2/">2</a></li>
                  </ul>
                </div>
              </div>
            </body></html>
            """.formatted(ALICE_AVATAR_URL);

    private static final String PAGE_2 = """
            <html><body>
              <div data-item-slug="anora" data-item-name="Anora (2024)"
                   data-item-full-display-name="Anora (2024)" class="poster">
                <img alt="Anora" src="empty-poster.jpg"/>
              </div>
            </body></html>
            """;

    private static final String NO_TILES = """
            <html><body>
              <p>This watchlist is private.</p>
            </body></html>
            """;

    private HttpServer server;
    private LetterboxdScraperService scraperService;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

        server.createContext("/alice/watchlist/", exchange -> respond(exchange, 200, PAGE_1_WITH_PAGINATION));
        server.createContext("/alice/watchlist/page/2/", exchange -> respond(exchange, 200, PAGE_2));
        server.createContext("/bob/watchlist/", exchange -> respond(exchange, 200, NO_TILES));
        server.createContext("/ghost/watchlist/", exchange -> respond(exchange, 404, "not found"));

        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        scraperService = new LetterboxdScraperService(baseUrl, 0);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void collectsFilmsAcrossPaginatedPages() {
        WatchlistResult result = scraperService.fetchWatchlist("alice");

        assertThat(result.accessible()).isTrue();
        assertThat(result.films())
                .extracting(film -> film.slug())
                .containsExactlyInAnyOrder("dune-part-two", "the-substance", "anora");
    }

    @Test
    void extractsTitleFromDataAttributes() {
        WatchlistResult result = scraperService.fetchWatchlist("alice");

        assertThat(result.films())
                .extracting(film -> film.title())
                .contains("Dune: Part Two (2024)", "The Substance (2024)", "Anora (2024)");
    }

    @Test
    void extractsYearFromTitle() {
        WatchlistResult result = scraperService.fetchWatchlist("alice");

        assertThat(result.films())
                .extracting(film -> film.year())
                .containsOnly(2024);
    }

    @Test
    void fallsBackToPosterAltTextWhenDataAttributesAreMissing() {
        server.createContext("/noattrs/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <div data-item-slug="dune-part-two" class="poster">
                    <img alt="Dune: Part Two" src="empty-poster.jpg"/>
                  </div>
                </body></html>
                """));

        WatchlistResult result = scraperService.fetchWatchlist("noattrs");

        assertThat(result.films())
                .extracting(film -> film.title())
                .containsExactly("Dune: Part Two");
    }

    @Test
    void yearIsNullWhenTitleHasNoYear() {
        server.createContext("/noyear/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <div data-item-slug="dune-part-two" class="poster">
                    <img alt="Dune: Part Two" src="empty-poster.jpg"/>
                  </div>
                </body></html>
                """));

        WatchlistResult result = scraperService.fetchWatchlist("noyear");

        assertThat(result.films())
                .extracting(film -> film.year())
                .containsExactly((Integer) null);
    }

    @Test
    void treatsPageWithNoFilmTilesAsAPrivateOrEmptyWatchlist() {
        WatchlistResult result = scraperService.fetchWatchlist("bob");

        assertThat(result.accessible()).isFalse();
        assertThat(result.films()).isEmpty();
        assertThat(result.reason()).isEqualTo(WatchlistResult.Reason.PRIVATE_OR_EMPTY);
    }

    @Test
    void treatsA404OnTheWatchlistPageAsANonexistentUser() {
        WatchlistResult result = scraperService.fetchWatchlist("ghost");

        assertThat(result.accessible()).isFalse();
        assertThat(result.films()).isEmpty();
        assertThat(result.reason()).isEqualTo(WatchlistResult.Reason.NONEXISTENT);
    }

    @Test
    void checkUsernameReportsUserExistsAndWatchlistPublicWhenPageHasFilmTiles() {
        UsernameCheck check = scraperService.checkUsername("alice");

        assertThat(check.userExists()).isTrue();
        assertThat(check.watchlistPublic()).isTrue();
    }

    @Test
    void checkUsernameExtractsAvatarUrlFromProfileHeaderAndUpgradesItsResolution() {
        UsernameCheck check = scraperService.checkUsername("alice");

        assertThat(check.avatarUrl()).isEqualTo(ALICE_AVATAR_URL_UPGRADED);
    }

    @Test
    void checkUsernameLeavesAnUnrecognisedAvatarUrlUntouched() {
        String plainUrl = "https://example.com/avatar.png";
        server.createContext("/plainavatar/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <section class="profile-header js-profile-header">
                    <a class="avatar" href="/plainavatar/"><img src="%s" alt="x" /></a>
                  </section>
                  <div data-item-slug="dune-part-two" class="poster">
                    <img alt="Dune: Part Two" src="empty-poster.jpg"/>
                  </div>
                </body></html>
                """.formatted(plainUrl)));

        UsernameCheck check = scraperService.checkUsername("plainavatar");

        assertThat(check.avatarUrl()).isEqualTo(plainUrl);
    }

    @Test
    void checkUsernameReportsUserExistsButWatchlistNotPublicWhenPageHasNoFilmTiles() {
        UsernameCheck check = scraperService.checkUsername("bob");

        assertThat(check.userExists()).isTrue();
        assertThat(check.watchlistPublic()).isFalse();
    }

    @Test
    void checkUsernameAvatarUrlIsNullWhenProfileHeaderIsMissing() {
        UsernameCheck check = scraperService.checkUsername("bob");

        assertThat(check.avatarUrl()).isNull();
    }

    @Test
    void checkUsernameReportsUserDoesNotExistOn404() {
        UsernameCheck check = scraperService.checkUsername("ghost");

        assertThat(check.userExists()).isFalse();
        assertThat(check.watchlistPublic()).isFalse();
        assertThat(check.avatarUrl()).isNull();
    }

    @Test
    void fetchWatchlistTreatsANon404HttpErrorAsPrivateOrEmpty() {
        server.createContext("/broken/watchlist/", exchange -> respond(exchange, 500, "boom"));

        WatchlistResult result = scraperService.fetchWatchlist("broken");

        assertThat(result.accessible()).isFalse();
        assertThat(result.reason()).isEqualTo(WatchlistResult.Reason.PRIVATE_OR_EMPTY);
    }

    @Test
    void checkUsernameTreatsANon404HttpErrorAsNotFound() {
        server.createContext("/broken/watchlist/", exchange -> respond(exchange, 500, "boom"));

        UsernameCheck check = scraperService.checkUsername("broken");

        assertThat(check.userExists()).isFalse();
    }

    @Test
    void keepsThePage1FilmsWhenALaterPageFailsToLoad() {
        server.createContext("/flaky/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <div data-item-slug="dune-part-two" data-item-name="Dune: Part Two (2024)"
                       data-item-full-display-name="Dune: Part Two (2024)"></div>
                  <div class="pagination"><a href="/flaky/watchlist/page/2/">2</a></div>
                </body></html>
                """));
        server.createContext("/flaky/watchlist/page/2/", exchange -> respond(exchange, 500, "boom"));

        WatchlistResult result = scraperService.fetchWatchlist("flaky");

        assertThat(result.accessible()).isTrue();
        assertThat(result.films()).extracting(Film::slug).containsExactly("dune-part-two");
    }

    @Test
    void skipsTilesWhoseSlugIsBlank() {
        server.createContext("/blanks/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <div data-item-slug="" data-item-name="Ghost Tile"></div>
                  <div data-item-slug="anora" data-item-name="Anora (2024)"></div>
                </body></html>
                """));

        WatchlistResult result = scraperService.fetchWatchlist("blanks");

        assertThat(result.films()).extracting(Film::slug).containsExactly("anora");
    }

    @Test
    void titleFallsBackThroughDataItemNameThenTheSlug() {
        server.createContext("/titles/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <div data-item-slug="only-name" data-item-name="Only The Name (1999)"></div>
                  <div data-item-slug="nothing-at-all"></div>
                </body></html>
                """));

        WatchlistResult result = scraperService.fetchWatchlist("titles");

        assertThat(result.films())
                .extracting(Film::slug, Film::title)
                .containsExactlyInAnyOrder(
                        tuple("only-name", "Only The Name (1999)"),
                        tuple("nothing-at-all", "nothing-at-all"));
    }

    @Test
    void readsTheLastPageNumberFromLinkTextWhenTheHrefHasNone() {
        server.createContext("/textpages/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <div data-item-slug="a" data-item-name="A (2000)"></div>
                  <div class="pagination">
                    <a href="/textpages/">Next</a>
                    <a href="/somewhere-else">2</a>
                  </div>
                </body></html>
                """));
        server.createContext("/textpages/watchlist/page/2/", exchange -> respond(exchange, 200, """
                <html><body><div data-item-slug="b" data-item-name="B (2001)"></div></body></html>
                """));

        WatchlistResult result = scraperService.fetchWatchlist("textpages");

        assertThat(result.films()).extracting(Film::slug).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void avatarUrlIsNullWhenTheImgHasABlankSrc() {
        server.createContext("/noavatarsrc/watchlist/", exchange -> respond(exchange, 200, """
                <html><body>
                  <section class="profile-header">
                    <a class="avatar" href="/noavatarsrc/"><img src="" alt="x" /></a>
                  </section>
                  <div data-item-slug="anora" data-item-name="Anora (2024)"></div>
                </body></html>
                """));

        UsernameCheck check = scraperService.checkUsername("noavatarsrc");

        assertThat(check.userExists()).isTrue();
        assertThat(check.avatarUrl()).isNull();
    }

    @Test
    void stillCollectsEveryFilmWhenAPageDelayIsConfigured() {
        LetterboxdScraperService slowScraper =
                new LetterboxdScraperService("http://localhost:" + server.getAddress().getPort(), 1);

        WatchlistResult result = slowScraper.fetchWatchlist("alice");

        assertThat(result.films())
                .extracting(Film::slug)
                .containsExactlyInAnyOrder("dune-part-two", "the-substance", "anora");
    }

    @Test
    void fetchFilmDetailsReadsTheRatingRuntimeAndTmdbRef() {
        server.createContext("/film/dune-part-two/", exchange -> respond(exchange, 200, """
                <html><head>
                  <meta name="twitter:label2" content="Average rating" />
                  <meta name="twitter:data2" content="4.38 out of 5" />
                </head><body class="film" data-type="film" data-tmdb-type="movie" data-tmdb-id="693134">
                  <p class="text-link text-footer"> 167&nbsp;mins &nbsp; More at <a href="#">IMDb</a> </p>
                </body></html>
                """));

        FilmDetails details = scraperService.fetchFilmDetails("dune-part-two");

        assertThat(details.rating()).isEqualTo(4.38);
        assertThat(details.length()).isEqualTo(167);
        assertThat(details.tmdbRef()).isEqualTo(new com.whatwewillwatchtonight.model.TmdbRef(693134, "movie"));
    }

    @Test
    void fetchFilmDetailsReturnsNullsWhenThePageHasNothingUseful() {
        server.createContext("/film/blank/", exchange -> respond(exchange, 200, "<html><body>nothing here</body></html>"));

        FilmDetails details = scraperService.fetchFilmDetails("blank");

        assertThat(details).isEqualTo(FilmDetails.empty());
    }

    @Test
    void fetchFilmDetailsCarriesTheTvRefForASeries() {
        server.createContext("/film/some-show/", exchange -> respond(exchange, 200, """
                <html><body data-tmdb-type="tv" data-tmdb-id="555">
                  <p class="text-link text-footer"> 45&nbsp;mins </p>
                </body></html>
                """));

        assertThat(scraperService.fetchFilmDetails("some-show").tmdbRef())
                .isEqualTo(new com.whatwewillwatchtonight.model.TmdbRef(555, "tv"));
    }

    @Test
    void fetchTmdbRefReadsJustTheIdAndType() {
        server.createContext("/film/ghosts-2020-2/", exchange -> respond(exchange, 200,
                "<html><body data-tmdb-type=\"movie\" data-tmdb-id=\"726413\">x</body></html>"));

        assertThat(scraperService.fetchTmdbRef("ghosts-2020-2"))
                .isEqualTo(new com.whatwewillwatchtonight.model.TmdbRef(726413, "movie"));
    }

    @Test
    void fetchTmdbRefIsNullWhenThePageCannotBeFetched() {
        assertThat(scraperService.fetchTmdbRef("missing-ref")).isNull();
    }

    @Test
    void fetchFilmDetailsIgnoresATwitterCardThatIsNotTheAverageRating() {
        server.createContext("/film/unrated/", exchange -> respond(exchange, 200, """
                <html><head>
                  <meta name="twitter:label2" content="Directed by" />
                  <meta name="twitter:data2" content="Some Director" />
                </head><body>
                  <p class="text-link text-footer"> 90&nbsp;mins </p>
                </body></html>
                """));

        FilmDetails details = scraperService.fetchFilmDetails("unrated");

        assertThat(details.rating()).isNull();
        assertThat(details.length()).isEqualTo(90);
    }

    @Test
    void fetchFilmDetailsReturnsEmptyWhenTheFilmPageCannotBeFetched() {
        server.createContext("/film/missing/", exchange -> respond(exchange, 404, "not found"));

        FilmDetails details = scraperService.fetchFilmDetails("missing");

        assertThat(details).isEqualTo(FilmDetails.empty());
    }
}
