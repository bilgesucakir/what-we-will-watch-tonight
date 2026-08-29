package com.whatwewillwatchtonight.controller.dto;

import com.whatwewillwatchtonight.model.StreamingProvider;

import java.util.List;

/**
 * @param providers streaming/free/ad services carrying the film in the request's
 *                  region; only populated for a single random pick, empty otherwise
 */
public record FilmMatchDto(
        String title,
        String url,
        Integer year,
        Double rating,
        Integer length,
        String posterUrl,
        List<StreamingProvider> providers) {
}
