package com.whatwewillwatchtonight.controller.dto;

/**
 * One film to find a poster for: the {@code url}, {@code title} and
 * {@code year} exactly as a full-list {@link FilmMatchDto} returned them.
 */
public record PosterRequestDto(String url, String title, Integer year) {
}
