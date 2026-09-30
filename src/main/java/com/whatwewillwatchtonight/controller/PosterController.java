package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.controller.dto.PosterDto;
import com.whatwewillwatchtonight.controller.dto.PosterRequestDto;
import com.whatwewillwatchtonight.controller.error.InvalidFilmUrlException;
import com.whatwewillwatchtonight.controller.error.PosterBatchSizeException;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.service.FilmResponseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Resolves posters for one page of a full list. The list itself comes back
 * bare from {@code /api/intersect} and {@code /api/watchlist}; the browser holds
 * it and asks for posters page by page, so nothing is kept on the server.
 */
@RestController
public class PosterController {

    /** Twice the frontend's page size, so a page never has to be split. */
    static final int MAX_FILMS = 48;

    private final FilmResponseService filmResponseService;

    public PosterController(FilmResponseService filmResponseService) {
        this.filmResponseService = filmResponseService;
    }

    @Operation(
            summary = "Find posters for a page of films",
            description = "Takes films exactly as the full list returned them (url, title, year) and returns "
                    + "each one's TMDB poster, in the same order."
    )
    @ApiResponse(responseCode = "200", description = "One entry per film; posterUrl is null when none was found")
    @ApiResponse(responseCode = "400", description = "Empty or more than 48 films, or a film without a "
            + "Letterboxd film URL or title")
    @PostMapping("/api/posters")
    public ResponseEntity<List<PosterDto>> posters(@RequestBody List<PosterRequestDto> films) {
        if (films == null || films.isEmpty() || films.size() > MAX_FILMS) {
            throw new PosterBatchSizeException(MAX_FILMS);
        }

        List<Film> parsed = films.stream().map(PosterController::toFilm).toList();
        List<String> posterUrls = filmResponseService.posterUrls(parsed);

        return ResponseEntity.ok(IntStream.range(0, films.size())
                .mapToObj(i -> new PosterDto(films.get(i).url(), posterUrls.get(i)))
                .toList());
    }

    private static Film toFilm(PosterRequestDto film) {
        if (film == null) {
            throw new InvalidFilmUrlException(null);
        }
        String slug = FilmResponseService.slugFromUrl(film.url());
        if (slug == null || film.title() == null || film.title().isBlank()) {
            throw new InvalidFilmUrlException(film.url());
        }
        return new Film(slug, film.title(), film.year());
    }
}
