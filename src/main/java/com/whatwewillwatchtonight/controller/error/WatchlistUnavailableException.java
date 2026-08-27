package com.whatwewillwatchtonight.controller.error;

import java.util.List;

/**
 * Thrown when a user exists but their watchlist can't be read -- it's private,
 * or it has no films on it.
 */
public class WatchlistUnavailableException extends ApiException {

    public WatchlistUnavailableException(List<String> usernames) {
        super(usernames.size() == 1
                ? "The watchlist for '" + usernames.get(0) + "' is private or empty."
                : "These watchlists are private or empty: " + String.join(", ", usernames) + ".");
    }
}
