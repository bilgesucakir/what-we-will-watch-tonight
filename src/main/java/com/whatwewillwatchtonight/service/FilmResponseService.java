package com.whatwewillwatchtonight.service;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.model.FilmDetails;
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
 * each one returned, plus the Letterboxd rating and runtime for a single
 * random pick.
 */
@Service
public class FilmResponseService {

    private static final String FILM_URL_TEMPLATE = "https://letterboxd.com/film/%s/";

    private final TmdbPosterService posterService;
    private final LetterboxdScraperService scraperService;
    private final Executor ioExecutor;

    public FilmResponseService(
            TmdbPosterService posterService,
            LetterboxdScraperService scraperService,
            @Qualifier("ioExecutor") Executor ioExecutor) {
        this.posterService = posterService;
        this.scraperService = scraperService;
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

        // Only the single random pick is worth a second Letterboxd page fetch
        // for its rating and runtime; the full grid stays lean.
        boolean withDetails = random;

        return filmsToReturn.stream()
                .map(film -> CompletableFuture.supplyAsync(() -> toDto(film, withDetails), ioExecutor))
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

    private FilmMatchDto toDto(Film film, boolean withDetails) {
        FilmDetails details = withDetails ? scraperService.fetchFilmDetails(film.slug()) : FilmDetails.empty();

        // The film page gives us the exact TMDB id -> exact poster. Only fall
        // back to a title+year search when we don't have it (the full grid).
        String posterUrl = details.tmdbId() != null
                ? posterService.findPosterUrlByTmdbId(details.tmdbId())
                : posterService.findPosterUrl(film.title(), film.year());

        return new FilmMatchDto(
                film.title(),
                FILM_URL_TEMPLATE.formatted(film.slug()),
                film.year(),
                details.rating(),
                details.length(),
                posterUrl);
    }
}
