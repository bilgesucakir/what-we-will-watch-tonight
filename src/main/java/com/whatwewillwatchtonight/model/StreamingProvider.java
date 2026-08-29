package com.whatwewillwatchtonight.model;

/**
 * A streaming service, as named by TMDB.
 *
 * @param id      TMDB's provider id -- how the frontend tells the backend which
 *                services the group subscribes to
 * @param name    display name, e.g. "Netflix"
 * @param logoUrl a small square logo image, or {@code null}
 */
public record StreamingProvider(int id, String name, String logoUrl) {
}
