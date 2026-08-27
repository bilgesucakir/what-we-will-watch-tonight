package com.watchlistintersector.service;

import com.watchlistintersector.controller.dto.FilmMatchDto;
import com.watchlistintersector.model.Film;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds the API response for a list of films, attaching a poster lookup to
 * each one returned.
 */
@Service
public class FilmResponseService {

    private static final String FILM_URL_TEMPLATE = "https://letterboxd.com/film/%s/";

    private final TmdbPosterService posterService;
    private final Executor ioExecutor;

    public FilmResponseService(TmdbPosterService posterService, @Qualifier("ioExecutor") Executor ioExecutor) {
        this.posterService = posterService;
        this.ioExecutor = ioExecutor;
    }

    /**
     * @param films  the films to build a response for
     * @param random if {@code true}, selects one random film from {@code films} and
     *               returns a single-element list; if {@code false}, returns all of
     *               {@code films} sorted alphabetically by title
     * @return the response DTOs for the selected film(s), each with its poster looked up
     */
    public List<FilmMatchDto> toDtos(List<Film> films, boolean random) {
        List<Film> filmsToReturn = random
                ? pickOneRandomFilm(films).map(List::of).orElseGet(List::of)
                : films.stream().sorted(Comparator.comparing(Film::title, String.CASE_INSENSITIVE_ORDER)).toList();

        return filmsToReturn.stream()
                .map(film -> CompletableFuture.supplyAsync(() -> toDto(film), ioExecutor))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private Optional<Film> pickOneRandomFilm(List<Film> films) {
        if (films.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(films.get(ThreadLocalRandom.current().nextInt(films.size())));
    }

    private FilmMatchDto toDto(Film film) {
        String posterUrl = posterService.findPosterUrl(film.title(), film.year());
        return new FilmMatchDto(film.title(), FILM_URL_TEMPLATE.formatted(film.slug()), film.year(), posterUrl);
    }
}
