package com.whatwewillwatchtonight.service;

import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.model.FilmDetails;
import com.whatwewillwatchtonight.model.TmdbRef;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LetterboxdScraperService {

    private static final Logger log = LoggerFactory.getLogger(LetterboxdScraperService.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

    private static final int REQUEST_TIMEOUT_MS = 10_000;
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("/page/(\\d+)/?$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\((\\d{4})\\)\\s*$");
    private static final Pattern AVATAR_CROP_SIZE_PATTERN = Pattern.compile("avtr-0-\\d+-0-\\d+-crop");
    private static final String AVATAR_CROP_SIZE_REPLACEMENT = "avtr-0-220-0-220-crop";
    private static final Pattern LEADING_NUMBER_PATTERN = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern RUNTIME_PATTERN = Pattern.compile("(\\d+)\\s*min");

    private final String baseUrl;
    private final long delayBetweenPagesMs;

    public LetterboxdScraperService(
            @Value("${letterboxd.base-url:https://letterboxd.com}") String baseUrl,
            @Value("${letterboxd.page-delay-ms:250}") long delayBetweenPagesMs) {
        this.baseUrl = baseUrl;
        this.delayBetweenPagesMs = delayBetweenPagesMs;
    }

    /**
     * @param username the Letterboxd username to check
     * @return whether the username exists, whether its watchlist is public, and its
     *         avatar URL if available
     */
    public UsernameCheck checkUsername(String username) {
        try {
            // The plain profile page is blocked by Letterboxd's bot protection;
            // the watchlist page isn't, and a 404 there still means the username doesn't exist.
            Document firstPage = get(watchlistUrl(username, 1));
            boolean watchlistPublic = !firstPage.select("[data-item-slug]").isEmpty();
            return UsernameCheck.existsWithWatchlist(watchlistPublic, extractAvatarUrl(firstPage));
        } catch (HttpStatusException e) {
            if (e.getStatusCode() != 404) {
                log.warn("Unexpected status checking user '{}': {}", username, e.getMessage());
            }
            return UsernameCheck.notFound();
        } catch (IOException e) {
            log.warn("Failed to check username '{}': {}", username, e.getMessage());
            return UsernameCheck.notFound();
        }
    }

    /**
     * @param page the fetched watchlist page
     * @return the avatar image URL from the page header, upgraded to a larger
     *         crop, or {@code null} if the markup doesn't have one
     */
    private String extractAvatarUrl(Document page) {
        Element avatar = page.selectFirst(".profile-header a.avatar img");
        if (avatar == null || avatar.attr("src").isBlank()) {
            return null;
        }
        return upgradeAvatarResolution(avatar.attr("src"));
    }

    /**
     * @param src the avatar URL as it appears in the page markup
     * @return the same URL asking for a 220px crop, or {@code src} unchanged if
     *         it isn't a Letterboxd resized-avatar URL
     */
    private String upgradeAvatarResolution(String src) {
        return AVATAR_CROP_SIZE_PATTERN.matcher(src).replaceFirst(AVATAR_CROP_SIZE_REPLACEMENT);
    }

    /**
     * Scrapes a user's full watchlist, walking every page.
     *
     * @param username the Letterboxd username to scrape
     * @return the films found, or an inaccessible result if the watchlist is
     *         private, nonexistent, or the first page couldn't be fetched
     */
    public WatchlistResult fetchWatchlist(String username) {
        String firstPageUrl = watchlistUrl(username, 1);

        Document firstPage;
        try {
            firstPage = get(firstPageUrl);
        } catch (HttpStatusException e) {
            // A 404 on the watchlist page means the username itself doesn't exist.
            if (e.getStatusCode() == 404) {
                return WatchlistResult.inaccessible(username, WatchlistResult.Reason.NONEXISTENT);
            }
            log.warn("Failed to fetch watchlist page 1 for user '{}': {}", username, e.getMessage());
            return WatchlistResult.inaccessible(username, WatchlistResult.Reason.PRIVATE_OR_EMPTY);
        } catch (IOException e) {
            log.warn("Failed to fetch watchlist page 1 for user '{}': {}", username, e.getMessage());
            return WatchlistResult.inaccessible(username, WatchlistResult.Reason.PRIVATE_OR_EMPTY);
        }

        Set<Film> films = new HashSet<>();
        Elements firstPageTiles = firstPage.select("[data-item-slug]");
        if (firstPageTiles.isEmpty()) {
            return WatchlistResult.inaccessible(username, WatchlistResult.Reason.PRIVATE_OR_EMPTY);
        }
        films.addAll(extractFilms(firstPageTiles));

        int lastPage = readLastPageNumber(firstPage);
        for (int page = 2; page <= lastPage; page++) {
            sleepPolitely();
            try {
                Document doc = get(watchlistUrl(username, page));
                films.addAll(extractFilms(doc.select("[data-item-slug]")));
            } catch (IOException e) {
                log.warn("Failed to fetch watchlist page {} for user '{}': {}", page, username, e.getMessage());
            }
        }

        return WatchlistResult.of(username, films);
    }

    /**
     * Fetches the average Letterboxd rating, runtime and TMDB id for one film
     * from its film page. Every part is best-effort -- any can come back
     * {@code null}, as can the whole thing if the page can't be read.
     *
     * @param slug the film's Letterboxd slug
     */
    public FilmDetails fetchFilmDetails(String slug) {
        Document page;
        try {
            page = get(baseUrl + "/film/" + slug + "/");
        } catch (IOException e) {
            log.warn("Failed to fetch film page for '{}': {}", slug, e.getMessage());
            return FilmDetails.empty();
        }
        return new FilmDetails(extractRating(page), extractRuntimeMinutes(page), extractTmdbRef(page));
    }

    /**
     * Fetches just the exact TMDB reference for one film -- a lighter call than
     * {@link #fetchFilmDetails} for when only the poster needs confirming.
     *
     * @param slug the film's Letterboxd slug
     * @return the {@link TmdbRef}, or {@code null} if the page can't be read or
     *         carries no id
     */
    public TmdbRef fetchTmdbRef(String slug) {
        try {
            return extractTmdbRef(get(baseUrl + "/film/" + slug + "/"));
        } catch (IOException e) {
            log.warn("Failed to fetch film page for '{}': {}", slug, e.getMessage());
            return null;
        }
    }

    /**
     * @param page the fetched film page
     * @return the exact TMDB entry (movie or TV) the film links to, or {@code null}
     *         if the page doesn't carry one
     */
    private TmdbRef extractTmdbRef(Document page) {
        Element body = page.selectFirst("body[data-tmdb-type][data-tmdb-id]");
        if (body == null) {
            return null;
        }
        String type = body.attr("data-tmdb-type");
        if (!"movie".equals(type) && !"tv".equals(type)) {
            return null;
        }
        try {
            return new TmdbRef(Integer.parseInt(body.attr("data-tmdb-id")), type);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param page the fetched film page
     * @return the average rating out of 5 from the "Average rating" Twitter card,
     *         or {@code null} if the film has none yet
     */
    private Double extractRating(Document page) {
        Element label = page.selectFirst("meta[name=twitter:label2]");
        Element data = page.selectFirst("meta[name=twitter:data2]");
        if (label == null || data == null || !label.attr("content").equalsIgnoreCase("Average rating")) {
            return null;
        }
        Matcher matcher = LEADING_NUMBER_PATTERN.matcher(data.attr("content"));
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }

    /**
     * @param page the fetched film page
     * @return the runtime in minutes from the page footer, or {@code null} if
     *         it isn't listed
     */
    private Integer extractRuntimeMinutes(Document page) {
        Element footer = page.selectFirst("p.text-link.text-footer");
        if (footer == null) {
            return null;
        }
        Matcher matcher = RUNTIME_PATTERN.matcher(footer.text());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private Document get(String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(REQUEST_TIMEOUT_MS);
        return connection.get();
    }

    private Set<Film> extractFilms(Elements tiles) {
        Set<Film> films = new HashSet<>();
        for (Element tile : tiles) {
            String slug = tile.attr("data-item-slug");
            if (slug.isBlank()) {
                continue;
            }
            String title = extractTitle(tile, slug);
            films.add(new Film(slug, title, extractYear(title)));
        }
        return films;
    }

    /**
     * @param title the film's title, as extracted from the page
     * @return the release year parsed from a trailing "(YYYY)" in the title, or
     *         {@code null} if the title has none
     */
    private Integer extractYear(String title) {
        Matcher matcher = YEAR_PATTERN.matcher(title);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    /**
     * @param tile the film tile element
     * @param slug the film's slug, used as a last-resort fallback title
     * @return the tile's title, preferring {@code data-item-full-display-name},
     *         then {@code data-item-name}, then the poster image's {@code alt}
     *         text, then falling back to {@code slug}
     */
    private String extractTitle(Element tile, String slug) {
        String fullDisplayName = tile.attr("data-item-full-display-name");
        if (!fullDisplayName.isBlank()) {
            return fullDisplayName;
        }
        String itemName = tile.attr("data-item-name");
        if (!itemName.isBlank()) {
            return itemName;
        }
        Element poster = tile.selectFirst("img[alt]");
        if (poster != null && !poster.attr("alt").isBlank()) {
            return poster.attr("alt");
        }
        return slug;
    }

    /**
     * Reads the highest page number linked from the pagination controls on
     * page 1.
     *
     * @param firstPage the fetched first page of the watchlist
     * @return the last page number, or 1 (i.e. no further pages) if there's no
     *         pagination, which is the case for watchlists that fit on a single page
     */
    private int readLastPageNumber(Document firstPage) {
        Elements pageLinks = firstPage.select(".paginate-pages a, .pagination a");
        int lastPage = 1;
        for (Element link : pageLinks) {
            int pageNumber = parsePageNumber(link);
            if (pageNumber > lastPage) {
                lastPage = pageNumber;
            }
        }
        return lastPage;
    }

    private int parsePageNumber(Element link) {
        Matcher hrefMatcher = PAGE_NUMBER_PATTERN.matcher(link.attr("href"));
        if (hrefMatcher.find()) {
            return Integer.parseInt(hrefMatcher.group(1));
        }
        try {
            return Integer.parseInt(link.text().trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String watchlistUrl(String username, int page) {
        return page <= 1
                ? baseUrl + "/" + username + "/watchlist/"
                : baseUrl + "/" + username + "/watchlist/page/" + page + "/";
    }

    private void sleepPolitely() {
        if (delayBetweenPagesMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayBetweenPagesMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
