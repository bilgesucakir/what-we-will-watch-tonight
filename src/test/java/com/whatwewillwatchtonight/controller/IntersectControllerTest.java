package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.config.AsyncConfig;
import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.service.FilmResponseService;
import com.whatwewillwatchtonight.service.LetterboxdScraperService;
import com.whatwewillwatchtonight.service.WatchlistIntersectionService;
import com.whatwewillwatchtonight.service.WatchlistResult;
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
import static org.mockito.ArgumentMatchers.isNull;
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
        when(filmResponseService.toDtos(any(), eq(false), any())).thenReturn(List.of(
                new FilmMatchDto("Dune: Part Two (2024)", "https://letterboxd.com/film/dune-part-two/", 2024,
                        null, null, "https://image.tmdb.org/t/p/w342/poster.jpg", List.of())));

        mockMvc.perform(get("/api/intersect").param("user", "alice").param("user", "bob"))
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
        when(filmResponseService.toDtos(any(), eq(false), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect").param("user", "alice").param("user", "bob"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(
                eq(List.of(new Film("dune-part-two", "Dune: Part Two (2024)", 2024))), eq(false), isNull());
    }

    @Test
    void passesRandomFlagThroughToFilmResponseService() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(true), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect").param("user", "alice").param("user", "bob").param("random", "true"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(any(), eq(true), isNull());
    }

    @Test
    void buildsAStreamingFilterFromTheProviderAndRegionParamsForARandomPick() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(true), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect")
                        .param("user", "alice").param("user", "bob")
                        .param("random", "true")
                        .param("provider", "8").param("provider", "337")
                        .param("region", "TR"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(any(), eq(true),
                eq(new com.whatwewillwatchtonight.service.StreamingFilter("TR", Set.of(8, 337))));
    }

    @Test
    void doesNotBuildAStreamingFilterWhenProvidersAreGivenWithoutARegion() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(true), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect")
                        .param("user", "alice").param("user", "bob")
                        .param("random", "true")
                        .param("provider", "8"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(any(), eq(true), isNull());
    }

    @Test
    void ignoresTheProviderParamsWhenNotARandomPick() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(false), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect")
                        .param("user", "alice").param("user", "bob")
                        .param("provider", "8"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(any(), eq(false), isNull());
    }

    @Test
    void returnsEmptyListWhenNoFilmsInCommon() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("anora", "Anora (2024)", 2024))));
        when(filmResponseService.toDtos(eq(List.of()), eq(false), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect").param("user", "alice").param("user", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returns400NamingOnlyTheNonexistentUser() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice")))
                .thenReturn(WatchlistResult.inaccessible("alice", WatchlistResult.Reason.NONEXISTENT));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("anora", "Anora (2024)", 2024))));

        mockMvc.perform(get("/api/intersect").param("user", "alice").param("user", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("no Letterboxd user")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("alice")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("bob"))));
    }

    @Test
    void returns400WithADistinctMessageForPrivateWatchlists() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice")))
                .thenReturn(WatchlistResult.inaccessible("alice", WatchlistResult.Reason.PRIVATE_OR_EMPTY));
        when(scraperService.fetchWatchlist(eq("bob")))
                .thenReturn(WatchlistResult.inaccessible("bob", WatchlistResult.Reason.PRIVATE_OR_EMPTY));

        mockMvc.perform(get("/api/intersect").param("user", "alice").param("user", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("private or empty")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("alice"),
                        org.hamcrest.Matchers.containsString("bob"))));
    }

    @Test
    void nonexistentUserTakesPrecedenceOverPrivateWatchlistInTheError() throws Exception {
        when(scraperService.fetchWatchlist(eq("ghost")))
                .thenReturn(WatchlistResult.inaccessible("ghost", WatchlistResult.Reason.NONEXISTENT));
        when(scraperService.fetchWatchlist(eq("shy")))
                .thenReturn(WatchlistResult.inaccessible("shy", WatchlistResult.Reason.PRIVATE_OR_EMPTY));

        mockMvc.perform(get("/api/intersect").param("user", "ghost").param("user", "shy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("no Letterboxd user")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("ghost")));
    }

    @Test
    void returns400ListingEveryNonexistentUser() throws Exception {
        when(scraperService.fetchWatchlist(eq("ghost")))
                .thenReturn(WatchlistResult.inaccessible("ghost", WatchlistResult.Reason.NONEXISTENT));
        when(scraperService.fetchWatchlist(eq("phantom")))
                .thenReturn(WatchlistResult.inaccessible("phantom", WatchlistResult.Reason.NONEXISTENT));

        mockMvc.perform(get("/api/intersect").param("user", "ghost").param("user", "phantom"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("ghost"),
                        org.hamcrest.Matchers.containsString("phantom"))));
    }

    @Test
    void returns400WithAMissingParameterMessageWhenNoUsersAtAll() throws Exception {
        mockMvc.perform(get("/api/intersect"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Missing required parameter")));
    }

    @Test
    void returns400WithADistinctMessageWhenAUsernameIsBlank() throws Exception {
        mockMvc.perform(get("/api/intersect").param("user", " ").param("user", "bob"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Fill in")));
    }

    @Test
    void returns400WithADistinctMessageWhenAUsernameIsRepeated() throws Exception {
        mockMvc.perform(get("/api/intersect")
                        .param("user", "alice").param("user", "bob").param("user", "Alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("different username")));
    }

    @Test
    void returns400WithADistinctMessageWhenFewerThanTwoUsernames() throws Exception {
        mockMvc.perform(get("/api/intersect").param("user", "alice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("between 2 and 4")));
    }

    @Test
    void returns400WithADistinctMessageWhenMoreThanFourUsernames() throws Exception {
        mockMvc.perform(get("/api/intersect")
                        .param("user", "a").param("user", "b").param("user", "c")
                        .param("user", "d").param("user", "e"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("between 2 and 4")));
    }

    @Test
    void intersectsEveryWatchlistWhenGivenThreeUsers() throws Exception {
        when(scraperService.fetchWatchlist(eq("alice"))).thenReturn(WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("bob"))).thenReturn(WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024))));
        when(scraperService.fetchWatchlist(eq("carol"))).thenReturn(WatchlistResult.of("carol", Set.of(
                new Film("anora", "Anora (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024))));
        when(filmResponseService.toDtos(any(), eq(false), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/intersect")
                        .param("user", "alice").param("user", "bob").param("user", "carol"))
                .andExpect(status().isOk());

        verify(filmResponseService).toDtos(
                eq(List.of(new Film("anora", "Anora (2024)", 2024))), eq(false), isNull());
    }
}
