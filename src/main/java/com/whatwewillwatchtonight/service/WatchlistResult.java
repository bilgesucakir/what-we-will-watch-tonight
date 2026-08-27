package com.watchlistintersector.service;

import com.watchlistintersector.model.Film;

import java.util.Set;

/**
 * Outcome of scraping one user's watchlist.
 *
 * @param username   the Letterboxd username that was scraped
 * @param accessible {@code false} when the watchlist page returned no film tiles at all
 *                   (private, nonexistent, or an empty public watchlist)
 * @param films      the films found; empty when {@code accessible} is false
 */
public record WatchlistResult(String username, boolean accessible, Set<Film> films) {

    /**
     * @param username the Letterboxd username that couldn't be scraped
     * @return a result with no films, marked as inaccessible
     */
    public static WatchlistResult inaccessible(String username) {
        return new WatchlistResult(username, false, Set.of());
    }

    /**
     * @param username the Letterboxd username that was scraped
     * @param films    the films found on their watchlist
     * @return an accessible result carrying the given films
     */
    public static WatchlistResult of(String username, Set<Film> films) {
        return new WatchlistResult(username, true, films);
    }
}
