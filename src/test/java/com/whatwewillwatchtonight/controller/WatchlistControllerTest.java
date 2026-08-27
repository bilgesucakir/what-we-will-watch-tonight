package com.watchlistintersector.controller;

import com.watchlistintersector.controller.dto.FilmMatchDto;
import com.watchlistintersector.model.Film;
import com.watchlistintersector.service.FilmResponseService;
import com.watchlistintersector.service.LetterboxdScraperService;
import com.watchlistintersector.service.WatchlistResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LetterboxdScraperService scraperService;

    @MockBean
    private FilmResponseService filmResponseService;

    @Test
    void returnsWhateverFilmResponseServiceProducesForTheWatchlist() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(false))).thenReturn(List.of(
                new FilmMatchDto("Anora (2024)", "https://letterboxd.com/film/anora/", 2024,
                        "https://image.tmdb.org/t/p/w342/anora.jpg"),
                new FilmMatchDto("Dune: Part Two (2024)", "https://letterboxd.com/film/dune-part-two/", 2024, null)));

        mockMvc.perform(get("/api/watchlist").param("user", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Anora (2024)"))
                .andExpect(jsonPath("$[0].posterUrl").value("https://image.tmdb.org/t/p/w342/anora.jpg"));
    }

    @Test
    void passesEveryFilmInTheWatchlistToFilmResponseService() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("the-substance", "The Substance (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(false))).thenReturn(List.of());

        mockMvc.perform(get("/api/watchlist").param("user", "alice"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<Film>> filmsCaptor = ArgumentCaptor.forClass(List.class);
        verify(filmResponseService).toDtos(filmsCaptor.capture(), eq(false));
        assertThat(filmsCaptor.getValue()).containsExactlyInAnyOrder(
                new Film("anora", "Anora (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024));
    }

    @Test
    void passesRandomFlagThroughToFilmResponseService() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("anora", "Anora (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(true))).thenReturn(List.of());

        mockMvc.perform(get("/api/watchlist").param("user", "alice").param("random", "true"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(any(), eq(true));
    }

    @Test
    void returns400WhenWatchlistIsInaccessible() throws Exception {
        when(scraperService.fetchWatchlist(eq("ghost"))).thenReturn(WatchlistResult.inaccessible("ghost"));

        mockMvc.perform(get("/api/watchlist").param("user", "ghost"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("ghost")));
    }

    @Test
    void returns400WhenUserIsBlank() throws Exception {
        mockMvc.perform(get("/api/watchlist").param("user", " "))
                .andExpect(status().isBadRequest());
    }
}
