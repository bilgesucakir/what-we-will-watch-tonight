package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.model.StreamingProvider;
import com.whatwewillwatchtonight.service.TmdbStreamingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StreamingController.class)
class StreamingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TmdbStreamingService streamingService;

    @Test
    void returnsTheProvidersForTheRequestedRegion() throws Exception {
        when(streamingService.providersInRegion("TR")).thenReturn(List.of(
                new StreamingProvider(8, "Netflix", "https://image.tmdb.org/t/p/w45/netflix.jpg")));

        mockMvc.perform(get("/api/streaming-providers").param("region", "TR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].name").value("Netflix"))
                .andExpect(jsonPath("$[0].logoUrl").value("https://image.tmdb.org/t/p/w45/netflix.jpg"));
    }

    @Test
    void returns400WhenNoRegionIsGiven() throws Exception {
        mockMvc.perform(get("/api/streaming-providers"))
                .andExpect(status().isBadRequest());

        verify(streamingService, never()).providersInRegion(any());
    }
}
