package com.watchlistintersector.controller;

import com.watchlistintersector.config.AsyncConfig;
import com.watchlistintersector.controller.dto.FilmMatchDto;
import com.watchlistintersector.model.Film;
import com.watchlistintersector.service.FilmResponseService;
import com.watchlistintersector.service.LetterboxdScraperService;
import com.watchlistintersector.service.WatchlistIntersectionService;
import com.watchlistintersector.service.WatchlistResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntersectController.class)
@Import({WatchlistIntersectionService.class, AsyncConfig.class})
class IntersectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LetterboxdScraperService scraperService;

    @MockBean
    private FilmResponseService filmResponseService;

    @Test
    void returnsWhateverFilmResponseServiceProducesForTheIntersection() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(false))).thenReturn(List.of(
                new FilmMatchDto("Dune: Part Two (2024)", "https://letterboxd.com/film/dune-part-two/", 2024,
                        "https://image.tmdb.org/t/p/w342/poster.jpg")));

        mockMvc.perform(get("/api/intersect").param("user1", "alice").param("user2", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Dune: Part Two (2024)"))
                .andExpect(jsonPath("$[0].url").value("https://letterboxd.com/film/dune-part-two/"))
                .andExpect(jsonPath("$[0].year").value(2024))
                .andExpect(jsonPath("$[0].posterUrl").value("https://image.tmdb.org/t/p/w342/poster.jpg"));
    }

    @Test
    void onlyPassesFilmsPresentOnBothWatchlistsToFilmResponseService() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(false))).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect").param("user1", "alice").param("user2", "bob"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(
                eq(List.of(new Film("dune-part-two", "Dune: Part Two (2024)", 2024))), eq(false));
    }

    @Test
    void passesRandomFlagThroughToFilmResponseService() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(true))).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect").param("user1", "alice").param("user2", "bob").param("random", "true"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(any(), eq(true));
    }

    @Test
    void returnsEmptyListWhenNoFilmsInCommon() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("anora", "Anora (2024)", 2024))));
        when(filmResponseService.toDtos(eq(List.of()), eq(false))).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect").param("user1", "alice").param("user2", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returns400NamingInaccessibleUser() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.inaccessible("alice"));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("anora", "Anora (2024)", 2024))));

        mockMvc.perform(get("/api/intersect").param("user1", "alice").param("user2", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("alice")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("bob"))));
    }

    @Test
    void returns400NamingBothUsersWhenBothInaccessible() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.inaccessible("alice"));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.inaccessible("bob"));

        mockMvc.perform(get("/api/intersect").param("user1", "alice").param("user2", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("alice"),
                        org.hamcrest.Matchers.containsString("bob"))));
    }

    @Test
    void returns400WhenUsernameIsBlank() throws Exception {
        mockMvc.perform(get("/api/intersect").param("user1", " ").param("user2", "bob"))
                .andExpect(status().isBadRequest());
    }
}
