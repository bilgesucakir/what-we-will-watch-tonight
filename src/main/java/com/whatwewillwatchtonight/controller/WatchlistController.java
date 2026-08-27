package com.watchlistintersector.controller;

import com.watchlistintersector.controller.dto.ErrorResponseDto;
import com.watchlistintersector.controller.dto.FilmMatchDto;
import com.watchlistintersector.service.FilmResponseService;
import com.watchlistintersector.service.LetterboxdScraperService;
import com.watchlistintersector.service.WatchlistResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Returns the films on a single Letterboxd user's watchlist.
 */
@RestController
public class WatchlistController {

    private final LetterboxdScraperService scraperService;
    private final FilmResponseService filmResponseService;

    public WatchlistController(LetterboxdScraperService scraperService, FilmResponseService filmResponseService) {
        this.scraperService = scraperService;
        this.filmResponseService = filmResponseService;
    }

    @Operation(
            summary = "Get a user's watchlist",
            description = "Returns every film on one user's public watchlist, or a single random pick."
    )
    @ApiResponse(responseCode = "200", description = "The films, or a single random pick")
    @ApiResponse(responseCode = "400", description = "The username is blank, doesn't exist, or its watchlist is private")
    @GetMapping("/api/watchlist")
    public ResponseEntity<?> watchlist(
            @Parameter(description = "Letterboxd username") @RequestParam String user,
            @Parameter(description = "Return a single random film instead of the full list")
            @RequestParam(defaultValue = "false") boolean random) {
        if (user.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto("user is required."));
        }

        WatchlistResult result = scraperService.fetchWatchlist(user);
        if (!result.accessible()) {
            String error = "Watchlist inaccessible (private or nonexistent) for: " + user;
            return ResponseEntity.badRequest().body(new ErrorResponseDto(error));
        }

        List<FilmMatchDto> matches = filmResponseService.toDtos(List.copyOf(result.films()), random);

        return ResponseEntity.ok(matches);
    }
}
