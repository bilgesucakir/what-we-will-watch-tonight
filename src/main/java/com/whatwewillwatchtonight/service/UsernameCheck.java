package com.watchlistintersector.service;

/**
 * Result of checking a Letterboxd username without doing a full watchlist
 * scrape.
 *
 * @param userExists      whether the username exists on Letterboxd
 * @param watchlistPublic whether their watchlist is public; only meaningful when
 *                        {@code userExists} is true -- a nonexistent user has none
 * @param avatarUrl       their profile avatar image URL, or {@code null}; only
 *                        meaningful when {@code userExists} is true
 */
public record UsernameCheck(boolean userExists, boolean watchlistPublic, String avatarUrl) {

    /**
     * @return a result for a username that doesn't exist on Letterboxd
     */
    public static UsernameCheck notFound() {
        return new UsernameCheck(false, false, null);
    }

    /**
     * @param watchlistPublic whether the existing user's watchlist is public
     * @param avatarUrl       the existing user's profile avatar image URL, or {@code null}
     * @return a result for a username that exists on Letterboxd
     */
    public static UsernameCheck existsWithWatchlist(boolean watchlistPublic, String avatarUrl) {
        return new UsernameCheck(true, watchlistPublic, avatarUrl);
    }
}
