package com.watchlistintersector.controller;

import com.watchlistintersector.service.LetterboxdScraperService;
import com.watchlistintersector.service.UsernameCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LetterboxdScraperService scraperService;

    @Test
    void returnsExistsTrueAndWatchlistPublicTrueForAPublicWatchlist() throws Exception {
        when(scraperService.checkUsername(eq("alice")))
                .thenReturn(UsernameCheck.existsWithWatchlist(true, "https://a.ltrbxd.com/resized/avatar/alice.jpg"));

        mockMvc.perform(get("/api/users/alice/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.watchlistPublic").value(true))
                .andExpect(jsonPath("$.avatarUrl").value("https://a.ltrbxd.com/resized/avatar/alice.jpg"));
    }

    @Test
    void returnsExistsTrueAndWatchlistPublicFalseForAPrivateWatchlist() throws Exception {
        when(scraperService.checkUsername(eq("bob"))).thenReturn(UsernameCheck.existsWithWatchlist(false, null));

        mockMvc.perform(get("/api/users/bob/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.watchlistPublic").value(false));
    }

    @Test
    void returnsExistsFalseForAnUnknownUsername() throws Exception {
        when(scraperService.checkUsername(eq("ghost"))).thenReturn(UsernameCheck.notFound());

        mockMvc.perform(get("/api/users/ghost/exists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(false))
                .andExpect(jsonPath("$.watchlistPublic").value(false))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.nullValue()));
    }
}
