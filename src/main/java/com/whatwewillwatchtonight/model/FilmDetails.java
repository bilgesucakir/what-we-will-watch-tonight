package com.whatwewillwatchtonight.model;

/**
 * Extra per-film info scraped from a Letterboxd film page.
 *
 * @param rating  the weighted average Letterboxd rating out of 5, or {@code null}
 *                if the film has no ratings yet
 * @param length  the runtime in minutes, or {@code null} if it isn't listed
 * @param tmdbRef the exact TMDB entry the film links to, or {@code null}; used to
 *                fetch the right poster instead of guessing from the title
 */
public record FilmDetails(Double rating, Integer length, TmdbRef tmdbRef) {

    public static FilmDetails empty() {
        return new FilmDetails(null, null, null);
    }
}
