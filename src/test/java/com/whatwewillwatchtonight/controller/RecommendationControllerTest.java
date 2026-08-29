package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.service.FilmResponseService;
import com.whatwewillwatchtonight.service.UnderwatchedFilmsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnderwatchedFilmsService underwatchedFilmsService;

    @MockBean
    private FilmResponseService filmResponseService;

    @Test
    void returnsARandomUnderwatchedFilmWithItsPoster() throws Exception {
        when(underwatchedFilmsService.pickRandom())
                .thenReturn(Optional.of(new Film("wanda", "Wanda", 1970)));
        when(filmResponseService.toDtos(any(), eq(true))).thenReturn(List.of(
                new FilmMatchDto("Wanda", "https://letterboxd.com/film/wanda/", 1970,
                        3.8, 103, "https://image.tmdb.org/t/p/w342/wanda.jpg")));

        mockMvc.perform(get("/api/underwatched-pick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Wanda"))
                .andExpect(jsonPath("$.url").value("https://letterboxd.com/film/wanda/"))
                .andExpect(jsonPath("$.year").value(1970))
                .andExpect(jsonPath("$.rating").value(3.8))
                .andExpect(jsonPath("$.length").value(103))
                .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w342/wanda.jpg"));
    }

    @Test
    void returns204WhenThereAreNoUnderwatchedFilmsConfigured() throws Exception {
        when(underwatchedFilmsService.pickRandom()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/underwatched-pick"))
                .andExpect(status().isNoContent());
    }
}
