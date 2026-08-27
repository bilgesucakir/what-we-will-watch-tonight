package com.watchlistintersector.controller;

import com.watchlistintersector.controller.dto.ErrorResponseDto;
import com.watchlistintersector.controller.dto.FilmMatchDto;
import com.watchlistintersector.model.Film;
import com.watchlistintersector.service.FilmResponseService;
import com.watchlistintersector.service.LetterboxdScraperService;
import com.watchlistintersector.service.WatchlistIntersectionService;
import com.watchlistintersector.service.WatchlistResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Finds films present on both of two Letterboxd users' watchlists.
 */
@RestController
public class IntersectController {

    private final LetterboxdScraperService scraperService;
    private final WatchlistIntersectionService intersectionService;
    private final FilmResponseService filmResponseService;
    private final Executor ioExecutor;

    public IntersectController(
            LetterboxdScraperService scraperService,
            WatchlistIntersectionService intersectionService,
            FilmResponseService filmResponseService,
            @Qualifier("ioExecutor") Executor ioExecutor) {
        this.scraperService = scraperService;
        this.intersectionService = intersectionService;
        this.filmResponseService = filmResponseService;
        this.ioExecutor = ioExecutor;
    }

    @Operation(
            summary = "Find films on both users' watchlists",
            description = "Returns every film present on both users' public watchlists, or a single random pick."
    )
    @ApiResponse(responseCode = "200", description = "The matching films, or a single random pick")
    @ApiResponse(responseCode = "400", description = "A username is blank, doesn't exist, or its watchlist is private")
    @GetMapping("/api/intersect")
    public ResponseEntity<?> intersect(
            @Parameter(description = "First Letterboxd username") @RequestParam String user1,
            @Parameter(description = "Second Letterboxd username") @RequestParam String user2,
            @Parameter(description = "Return a single random film instead of the full overlap")
            @RequestParam(defaultValue = "false") boolean random) {
        if (user1.isBlank() || user2.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto("Both user1 and user2 are required."));
        }

        CompletableFuture<WatchlistResult> future1 =
                CompletableFuture.supplyAsync(() -> scraperService.fetchWatchlist(user1), ioExecutor);
        CompletableFuture<WatchlistResult> future2 =
                CompletableFuture.supplyAsync(() -> scraperService.fetchWatchlist(user2), ioExecutor);

        WatchlistResult result1 = future1.join();
        WatchlistResult result2 = future2.join();

        List<String> inaccessible = List.of(result1, result2).stream()
                .filter(result -> !result.accessible())
                .map(WatchlistResult::username)
                .toList();

        if (!inaccessible.isEmpty()) {
            String error = "Watchlist inaccessible (private or nonexistent) for: " + String.join(", ", inaccessible);
            return ResponseEntity.badRequest().body(new ErrorResponseDto(error));
        }

        List<Film> matchedFilms = intersectionService.intersect(result1, result2);
        List<FilmMatchDto> matches = filmResponseService.toDtos(matchedFilms, random);

        return ResponseEntity.ok(matches);
    }
}
