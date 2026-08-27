package com.watchlistintersector.model;

/**
 * A film as it appears on a Letterboxd watchlist.
 *
 * @param slug  the stable identifier used for matching between two users' watchlists
 * @param title the film's display title, as shown on Letterboxd
 * @param year  the release year, or {@code null} when it couldn't be parsed out of the
 *              title (e.g. a fallback title with no year in it)
 */
public record Film(String slug, String title, Integer year) {
}
