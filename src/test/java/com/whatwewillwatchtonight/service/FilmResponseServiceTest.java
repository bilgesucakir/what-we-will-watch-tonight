package com.whatwewillwatchtonight.service;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.model.FilmDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilmResponseServiceTest {

    private TmdbPosterService posterService;
    private LetterboxdScraperService scraperService;
    private FilmResponseService service;

    @BeforeEach
    void setUp() {
        posterService = mock(TmdbPosterService.class);
        scraperService = mock(LetterboxdScraperService.class);
        when(scraperService.fetchFilmDetails(any())).thenReturn(FilmDetails.empty());
        service = new FilmResponseService(posterService, scraperService, Executors.newVirtualThreadPerTaskExecutor());
    }

    @Test
    void nonRandomModeReturnsAllFilmsWithPostersAndNoDetailLookups() {
        when(posterService.findPosterUrl(eq("Dune: Part Two (2024)"), eq(2024)))
                .thenReturn("https://image.tmdb.org/t/p/w342/dune.jpg");
        when(posterService.findPosterUrl(eq("Anora (2024)"), eq(2024)))
                .thenReturn("https://image.tmdb.org/t/p/w342/anora.jpg");

        List<Film> films = List.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024));

        List<FilmMatchDto> dtos = service.toDtos(films, false);

        assertThat(dtos).hasSize(2);
        assertThat(dtos).extracting(FilmMatchDto::title)
                .containsExactlyInAnyOrder("Dune: Part Two (2024)", "Anora (2024)");
        assertThat(dtos).extracting(FilmMatchDto::posterUrl)
                .containsExactlyInAnyOrder("https://image.tmdb.org/t/p/w342/dune.jpg", "https://image.tmdb.org/t/p/w342/anora.jpg");
        assertThat(dtos).extracting(FilmMatchDto::rating, FilmMatchDto::length).containsOnly(tuple(null, null));
        verify(scraperService, never()).fetchFilmDetails(any());
    }

    @Test
    void nonRandomModeSortsFilmsAlphabeticallyByTitleCaseInsensitive() {
        when(posterService.findPosterUrl(any(), any())).thenReturn(null);

        List<Film> films = List.of(
                new Film("the-substance", "The Substance (2024)", 2024),
                new Film("anora", "anora (2024)", 2024),
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024));

        List<FilmMatchDto> dtos = service.toDtos(films, false);

        assertThat(dtos).extracting(FilmMatchDto::title)
                .containsExactly("anora (2024)", "Dune: Part Two (2024)", "The Substance (2024)");
    }

    @Test
    void randomModeReturnsExactlyOneFilmWithItsRatingAndRuntime() {
        when(posterService.findPosterUrl(any(), any())).thenReturn("https://image.tmdb.org/t/p/w342/poster.jpg");
        when(scraperService.fetchFilmDetails(any())).thenReturn(new FilmDetails(4.1, 137, null));

        List<Film> films = List.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024));

        List<FilmMatchDto> dtos = service.toDtos(films, true);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).rating()).isEqualTo(4.1);
        assertThat(dtos.get(0).length()).isEqualTo(137);
    }

    @Test
    void randomModeUsesTheExactTmdbPosterWhenTheFilmPageHasAnId() {
        when(scraperService.fetchFilmDetails(any())).thenReturn(new FilmDetails(4.1, 137, 693134));
        when(posterService.findPosterUrlByTmdbId(693134))
                .thenReturn("https://image.tmdb.org/t/p/w342/exact.jpg");

        List<FilmMatchDto> dtos = service.toDtos(
                List.of(new Film("dune-part-two", "Dune: Part Two (2024)", 2024)), true);

        assertThat(dtos.get(0).posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w342/exact.jpg");
        verify(posterService, never()).findPosterUrl(any(), any());
    }

    @Test
    void randomModeFallsBackToATitleSearchWhenTheFilmPageHasNoTmdbId() {
        when(scraperService.fetchFilmDetails(any())).thenReturn(new FilmDetails(4.1, 137, null));
        when(posterService.findPosterUrl(any(), any()))
                .thenReturn("https://image.tmdb.org/t/p/w342/searched.jpg");

        List<FilmMatchDto> dtos = service.toDtos(
                List.of(new Film("dune-part-two", "Dune: Part Two (2024)", 2024)), true);

        assertThat(dtos.get(0).posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w342/searched.jpg");
        verify(posterService, never()).findPosterUrlByTmdbId(anyInt());
    }

    @Test
    void randomModeLooksUpAPosterAndDetailsOnlyForTheOneFilmItReturns() {
        when(posterService.findPosterUrl(any(), any())).thenReturn("https://image.tmdb.org/t/p/w342/poster.jpg");

        List<Film> films = List.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024));

        service.toDtos(films, true);

        verify(posterService, times(1)).findPosterUrl(any(), any());
        verify(scraperService, times(1)).fetchFilmDetails(any());
    }

    @Test
    void randomModeReturnsEmptyListWhenThereAreNoFilms() {
        assertThat(service.toDtos(List.of(), true)).isEmpty();
    }

    @Test
    void nonRandomModeReturnsEmptyListWhenThereAreNoFilms() {
        assertThat(service.toDtos(List.of(), false)).isEmpty();
    }
}
