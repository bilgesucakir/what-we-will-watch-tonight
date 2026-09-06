package com.whatwewillwatchtonight.service;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.model.FilmDetails;
import com.whatwewillwatchtonight.model.StreamingProvider;
import com.whatwewillwatchtonight.model.TmdbRef;
import com.whatwewillwatchtonight.service.TmdbPosterService.PosterMatch;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Builds the API response for a list of films. The full list just gets a poster
 * per film; a single random pick also gets its Letterboxd rating and runtime,
 * and -- when a {@link StreamingFilter} is in play -- is drawn from the films
 * on the group's services.
 */
@Service
public class FilmResponseService {

    private static final String FILM_URL_TEMPLATE = "https://letterboxd.com/film/%s/";

    // How many film pages a filtered pick probes in parallel at a time while
    // scanning the intersection for a streamable film.
    private static final int STREAMING_BATCH = 16;

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
     * Picks one film. Without a filter that's a single draw. With one, it
     * scans the <em>whole</em> shuffled intersection -- in bounded-parallel
     * batches -- and returns the first film on one of the group's services,
     * falling back to the last film checked when truly none are streamable.
     * Scanning every film, not a fixed-size sample, is what makes a "nothing
     * is streamable" answer trustworthy.
     */
    private Optional<FilmMatchDto> pickRandom(List<Film> films, StreamingFilter filter) {
        if (films.isEmpty()) {
            return Optional.empty();
        }

        List<Film> shuffled = new ArrayList<>(films);
        Collections.shuffle(shuffled);

        if (filter == null) {
            Film film = shuffled.get(0);
            return Optional.of(enrichedDto(film, scraperService.fetchFilmDetails(film.slug()), List.of()));
        }

        Candidate fallback = null;
        for (int start = 0; start < shuffled.size(); start += STREAMING_BATCH) {
            List<Film> batch = shuffled.subList(start, Math.min(start + STREAMING_BATCH, shuffled.size()));
            List<Candidate> candidates = batch.stream()
                    .map(film -> CompletableFuture.supplyAsync(() -> probe(film, filter), ioExecutor))
                    .toList()
                    .stream()
                    .map(CompletableFuture::join)
                    .toList();

            for (Candidate c : candidates) {
                if (c.streamableOn(filter.providerIds())) {
                    return Optional.of(enrichedDto(c.film(), c.details(), c.providers()));
                }
                fallback = c;
            }
        }
        return Optional.ofNullable(fallback)
                .map(c -> enrichedDto(c.film(), c.details(), c.providers()));
    }

    /** Fetches a film's details and, if it has a TMDB ref, its streaming options. */
    private Candidate probe(Film film, StreamingFilter filter) {
        FilmDetails details = scraperService.fetchFilmDetails(film.slug());
        TmdbRef ref = details.tmdbRef();
        List<StreamingProvider> providers = ref != null
                ? streamingService.streamingOptions(ref.id(), ref.type(), filter.region())
                : List.of();
        return new Candidate(film, details, providers);
    }

    private record Candidate(Film film, FilmDetails details, List<StreamingProvider> providers) {
        boolean streamableOn(Set<Integer> providerIds) {
            return providers.stream().anyMatch(p -> providerIds.contains(p.id()));
        }
    }

    private FilmMatchDto plainDto(Film film) {
        // Title search is enough for most films. When it can't pin the title +
        // year to a single result (common titles, obscure films, series), fall
        // back to the exact TMDB id off the film's Letterboxd page.
        PosterMatch match = posterService.findPoster(film.title(), film.year());
        String posterUrl = match.url();
        if (!match.confident()) {
            String exact = posterByExactId(film.slug());
            if (exact != null) {
                posterUrl = exact;
            }
        }
        return dto(film, FilmDetails.empty(), posterUrl, List.of());
    }

    private FilmMatchDto enrichedDto(Film film, FilmDetails details, List<StreamingProvider> providers) {
        // The film page already gave us the exact TMDB ref -> exact poster; only
        // fall back to a title+year search when we don't have one.
        TmdbRef ref = details.tmdbRef();
        String posterUrl = ref != null
                ? posterService.findPosterUrlByTmdbId(ref.id(), ref.type())
                : posterService.findPoster(film.title(), film.year()).url();
        return dto(film, details, posterUrl, providers);
    }

    private String posterByExactId(String slug) {
        TmdbRef ref = scraperService.fetchTmdbRef(slug);
        return ref == null ? null : posterService.findPosterUrlByTmdbId(ref.id(), ref.type());
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
