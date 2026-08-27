package com.watchlistintersector.service;

import com.watchlistintersector.model.Film;
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
     * @return the avatar image URL from the page header, or {@code null} if the
     *         markup doesn't have one
     */
    private String extractAvatarUrl(Document page) {
        Element avatar = page.selectFirst(".profile-header a.avatar img");
        if (avatar == null || avatar.attr("src").isBlank()) {
            return null;
        }
        return avatar.attr("src");
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
        } catch (IOException e) {
            log.warn("Failed to fetch watchlist page 1 for user '{}': {}", username, e.getMessage());
            return WatchlistResult.inaccessible(username);
        }

        Set<Film> films = new HashSet<>();
        Elements firstPageTiles = firstPage.select("[data-item-slug]");
        if (firstPageTiles.isEmpty()) {
            return WatchlistResult.inaccessible(username);
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
