package com.whatwewillwatchtonight.service;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.model.FilmDetails;
import com.whatwewillwatchtonight.model.StreamingProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Builds the API response for a list of films. The full list just gets a poster
 * per film; a single random pick also gets its Letterboxd rating, runtime and
 * -- when a {@link StreamingFilter} is in play -- its streaming options, and is
 * re-rolled until it lands on one of the group's services.
 */
@Service
public class FilmResponseService {

    private static final String FILM_URL_TEMPLATE = "https://letterboxd.com/film/%s/";

    // Film pages a filtered pick will look at before giving up and handing back
    // one of the group's films anyway.
    private static final int MAX_STREAMING_TRIES = 12;

    private final TmdbPosterService posterService;
    private final TmdbStreamingService streamingService;
    private final LetterboxdScraperService scraperService;
    private final Executor ioExecutor;

    public FilmResponseService(
            TmdbPosterService posterService,
            TmdbStreamingService streamingService,
            LetterboxdScraperService scraperService,
            @Qualifier("ioExecutor") Executor ioExecutor) {
        this.posterService = posterService;
        this.streamingService = streamingService;
        this.scraperService = scraperService;
        this.ioExecutor = ioExecutor;
    }

    /**
     * @see #toDtos(List, boolean, StreamingFilter)
     */
    public List<FilmMatchDto> toDtos(List<Film> films, boolean random) {
        return toDtos(films, random, null);
    }

    /**
     * @param films  the films to build a response for
     * @param random {@code true} to return one random pick, {@code false} to
     *               return the whole list sorted alphabetically
     * @param filter when non-null, the random pick is re-rolled until it's
     *               streamable on one of the given services; ignored when
     *               {@code random} is {@code false}
     */
    public List<FilmMatchDto> toDtos(List<Film> films, boolean random, StreamingFilter filter) {
        if (!random) {
            return films.stream()
                    .sorted(Comparator.comparing(Film::title, String.CASE_INSENSITIVE_ORDER))
                    .map(film -> CompletableFuture.supplyAsync(() -> plainDto(film), ioExecutor))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .toList();
        }
        return pickRandom(films, filter).map(List::of).orElseGet(List::of);
    }

    /**
     * Picks one film. Without a filter that's a single draw; with one, it walks
     * a shuffled list, checking each film's streaming options, and stops at the
     * first that's on the group's services -- or, if none are, hands back the
     * last film it looked at.
     */
    private Optional<FilmMatchDto> pickRandom(List<Film> films, StreamingFilter filter) {
        if (films.isEmpty()) {
            return Optional.empty();
        }

        List<Film> shuffled = new ArrayList<>(films);
        Collections.shuffle(shuffled);

        int tries = filter == null ? 1 : Math.min(shuffled.size(), MAX_STREAMING_TRIES);
        FilmMatchDto lastTried = null;

        for (int i = 0; i < tries; i++) {
            Film film = shuffled.get(i);
            FilmDetails details = scraperService.fetchFilmDetails(film.slug());
            List<StreamingProvider> providers = (filter != null && details.tmdbId() != null)
                    ? streamingService.streamingOptions(details.tmdbId(), filter.region())
                    : List.of();

            FilmMatchDto dto = enrichedDto(film, details, providers);

            if (filter == null || providers.stream().anyMatch(p -> filter.providerIds().contains(p.id()))) {
                return Optional.of(dto);
            }
            lastTried = dto;
        }
        return Optional.ofNullable(lastTried);
    }

    private FilmMatchDto plainDto(Film film) {
        return dto(film, FilmDetails.empty(),
                posterService.findPosterUrl(film.title(), film.year()), List.of());
    }

    private FilmMatchDto enrichedDto(Film film, FilmDetails details, List<StreamingProvider> providers) {
        // The film page gives us the exact TMDB id -> exact poster; only fall
        // back to a title+year search when we don't have one.
        String posterUrl = details.tmdbId() != null
                ? posterService.findPosterUrlByTmdbId(details.tmdbId())
                : posterService.findPosterUrl(film.title(), film.year());
        return dto(film, details, posterUrl, providers);
    }

    private FilmMatchDto dto(Film film, FilmDetails details, String posterUrl, List<StreamingProvider> providers) {
        return new FilmMatchDto(
                film.title(),
                FILM_URL_TEMPLATE.formatted(film.slug()),
                film.year(),
                details.rating(),
                details.length(),
                posterUrl,
                providers);
    }
}
