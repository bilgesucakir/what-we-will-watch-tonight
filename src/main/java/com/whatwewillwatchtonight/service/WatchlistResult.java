package com.whatwewillwatchtonight.service;

import com.whatwewillwatchtonight.model.Film;

import java.util.Set;

/**
 * Outcome of scraping one user's watchlist.
 *
 * @param username   the Letterboxd username that was scraped
 * @param accessible {@code false} when the watchlist couldn't be read
 * @param reason     why it couldn't be read; {@code null} when {@code accessible}
 * @param films      the films found; empty when {@code accessible} is false
 */
public record WatchlistResult(String username, boolean accessible, Reason reason, Set<Film> films) {

    /**
     * Why a watchlist couldn't be read.
     */
    public enum Reason {
        /** The username doesn't exist on Letterboxd. */
        NONEXISTENT,
        /** The user exists but their watchlist is private, or has no films. */
        PRIVATE_OR_EMPTY
    }

    /**
     * @param username the Letterboxd username that couldn't be scraped
     * @param reason   why it couldn't be scraped
     * @return a result with no films, marked as inaccessible
     */
    public static WatchlistResult inaccessible(String username, Reason reason) {
        return new WatchlistResult(username, false, reason, Set.of());
    }

    /**
     * @param username the Letterboxd username that was scraped
     * @param films    the films found on their watchlist
     * @return an accessible result carrying the given films
     */
    public static WatchlistResult of(String username, Set<Film> films) {
        return new WatchlistResult(username, true, null, films);
    }
}
