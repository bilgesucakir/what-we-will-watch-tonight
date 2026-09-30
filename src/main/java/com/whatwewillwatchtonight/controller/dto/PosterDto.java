package com.whatwewillwatchtonight.controller.dto;

/**
 * @param url       the film's Letterboxd URL, echoed from the request
 * @param posterUrl its TMDB poster, or {@code null} if none was found
 */
public record PosterDto(String url, String posterUrl) {
}
