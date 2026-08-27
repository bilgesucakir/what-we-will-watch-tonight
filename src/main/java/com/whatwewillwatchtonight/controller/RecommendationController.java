package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.service.FilmResponseService;
import com.whatwewillwatchtonight.service.UnderwatchedFilmsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Hands back a film when the group has nothing to watch together.
 */
@RestController
public class RecommendationController {

    private final UnderwatchedFilmsService underwatchedFilmsService;
    private final FilmResponseService filmResponseService;

    public RecommendationController(
            UnderwatchedFilmsService underwatchedFilmsService, FilmResponseService filmResponseService) {
        this.underwatchedFilmsService = underwatchedFilmsService;
        this.filmResponseService = filmResponseService;
    }

    @Operation(
            summary = "Pick a random underwatched film",
            description = "Hands back one film from a curated list of underseen films, with its poster. "
                    + "The frontend shows it when a group's watchlists have nothing in common."
    )
    @ApiResponse(responseCode = "200", description = "A single underwatched film")
    @ApiResponse(responseCode = "204", description = "The curated list is empty")
    @GetMapping("/api/underwatched-pick")
    public ResponseEntity<FilmMatchDto> underwatchedPick() {
        return underwatchedFilmsService.pickRandom()
                .map(film -> filmResponseService.toDtos(List.of(film), true).get(0))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
