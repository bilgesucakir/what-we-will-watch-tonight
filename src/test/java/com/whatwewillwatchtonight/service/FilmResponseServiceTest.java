package com.watchlistintersector.service;

import com.watchlistintersector.controller.dto.FilmMatchDto;
import com.watchlistintersector.model.Film;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilmResponseServiceTest {

    private TmdbPosterService posterService;
    private FilmResponseService service;

    @BeforeEach
    void setUp() {
        posterService = mock(TmdbPosterService.class);
        service = new FilmResponseService(posterService, Executors.newVirtualThreadPerTaskExecutor());
    }

    @Test
    void nonRandomModeReturnsAllFilmsWithPosters() {
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
    void randomModeReturnsExactlyOneFilm() {
        when(posterService.findPosterUrl(any(), any())).thenReturn("https://image.tmdb.org/t/p/w342/poster.jpg");

        List<Film> films = List.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024));

        List<FilmMatchDto> dtos = service.toDtos(films, true);

        assertThat(dtos).hasSize(1);
    }

    @Test
    void randomModeOnlyLooksUpAPosterForTheOneFilmItReturns() {
        when(posterService.findPosterUrl(any(), any())).thenReturn("https://image.tmdb.org/t/p/w342/poster.jpg");

        List<Film> films = List.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024));

        service.toDtos(films, true);

        verify(posterService, times(1)).findPosterUrl(any(), any());
    }

    @Test
    void randomModeReturnsEmptyListWhenThereAreNoFilms() {
        List<FilmMatchDto> dtos = service.toDtos(List.of(), true);

        assertThat(dtos).isEmpty();
    }

    @Test
    void nonRandomModeReturnsEmptyListWhenThereAreNoFilms() {
        List<FilmMatchDto> dtos = service.toDtos(List.of(), false);

        assertThat(dtos).isEmpty();
    }
}
