package com.whatwewillwatchtonight.model;

/**
 * The exact TMDB entry a Letterboxd film page points at, from its
 * {@code <body data-tmdb-type="movie|tv" data-tmdb-id="...">} attributes.
 *
 * @param id   the TMDB id
 * @param type {@code "movie"} or {@code "tv"}
 */
public record TmdbRef(int id, String type) {
}
