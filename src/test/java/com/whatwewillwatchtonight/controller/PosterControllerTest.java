package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.service.FilmResponseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PosterController.class)
class PosterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FilmResponseService filmResponseService;

    private static String film(String slug, String title, Integer year) {
        return """
                {"url": "https://letterboxd.com/film/%s/", "title": "%s", "year": %s}""".formatted(slug, title, year);
    }

    private static String body(String... films) {
        return "[" + String.join(",", films) + "]";
    }

    @Test
    void returnsEachFilmsPosterInRequestOrder() throws Exception {
        when(filmResponseService.posterUrls(any()))
                .thenReturn(Arrays.asList("https://image.tmdb.org/t/p/w342/dune.jpg", null));

        mockMvc.perform(post("/api/posters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(film("dune-part-two", "Dune: Part Two (2024)", 2024), film("anora", "Anora", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].url").value("https://letterboxd.com/film/dune-part-two/"))
                .andExpect(jsonPath("$[0].posterUrl").value("https://image.tmdb.org/t/p/w342/dune.jpg"))
                .andExpect(jsonPath("$[1].url").value("https://letterboxd.com/film/anora/"))
                .andExpect(jsonPath("$[1].posterUrl").doesNotExist());
    }

    @Test
    @SuppressWarnings("unchecked")
    void turnsEachUrlBackIntoAFilmForTheLookup() throws Exception {
        when(filmResponseService.posterUrls(any())).thenReturn(Arrays.asList((String) null));

        mockMvc.perform(post("/api/posters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(film("ghosts-2020-2", "Ghosts (2020)", 2020))))
                .andExpect(status().isOk());

        ArgumentCaptor<List<Film>> captor = ArgumentCaptor.forClass(List.class);
        verify(filmResponseService).posterUrls(captor.capture());
        assertThat(captor.getValue()).containsExactly(new Film("ghosts-2020-2", "Ghosts (2020)", 2020));
    }

    @Test
    void rejectsAnEmptyRequest() throws Exception {
        mockMvc.perform(post("/api/posters").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ask for between 1 and 48 posters at a time."));
        verify(filmResponseService, never()).posterUrls(any());
    }

    @Test
    void rejectsMoreThanOnePagesWorth() throws Exception {
        String[] films = IntStream.range(0, PosterController.MAX_FILMS + 1)
                .mapToObj(i -> film("film-" + i, "Film " + i, 2020))
                .toArray(String[]::new);

        mockMvc.perform(post("/api/posters").contentType(MediaType.APPLICATION_JSON).content(body(films)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Ask for between 1 and 48 posters at a time."));
        verify(filmResponseService, never()).posterUrls(any());
    }

    @Test
    void rejectsAUrlThatIsNotALetterboxdFilm() throws Exception {
        String bad = """
                {"url": "https://example.com/film/anora/", "title": "Anora", "year": 2024}""";

        mockMvc.perform(post("/api/posters").contentType(MediaType.APPLICATION_JSON).content(body(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Not a Letterboxd film: https://example.com/film/anora/."));
        verify(filmResponseService, never()).posterUrls(any());
    }

    @Test
    void rejectsAFilmWithoutATitle() throws Exception {
        String untitled = """
                {"url": "https://letterboxd.com/film/anora/", "year": 2024}""";

        mockMvc.perform(post("/api/posters").contentType(MediaType.APPLICATION_JSON).content(body(untitled)))
                .andExpect(status().isBadRequest());
        verify(filmResponseService, never()).posterUrls(any());
    }

    @Test
    void rejectsANullEntry() throws Exception {
        mockMvc.perform(post("/api/posters").contentType(MediaType.APPLICATION_JSON).content("[null]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsABodyThatIsNotJson() throws Exception {
        mockMvc.perform(post("/api/posters").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The request body isn't valid JSON."));
    }
}
