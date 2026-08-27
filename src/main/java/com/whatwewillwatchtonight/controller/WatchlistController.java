package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.controller.error.BlankUsernameException;
import com.whatwewillwatchtonight.controller.error.UserNotFoundException;
import com.whatwewillwatchtonight.controller.error.WatchlistUnavailableException;
import com.whatwewillwatchtonight.service.FilmResponseService;
import com.whatwewillwatchtonight.service.LetterboxdScraperService;
import com.whatwewillwatchtonight.service.WatchlistResult;
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
    @ApiResponse(responseCode = "400", description = "The username is blank, the user doesn't exist, "
            + "or their watchlist is private/empty (each is a distinct message)")
    @GetMapping("/api/watchlist")
    public ResponseEntity<List<FilmMatchDto>> watchlist(
            @Parameter(description = "Letterboxd username") @RequestParam String user,
            @Parameter(description = "Return a single random film instead of the full list")
            @RequestParam(defaultValue = "false") boolean random) {
        if (user.isBlank()) {
            throw new BlankUsernameException();
        }

        WatchlistResult result = scraperService.fetchWatchlist(user);
        if (!result.accessible()) {
            throw switch (result.reason()) {
                case NONEXISTENT -> new UserNotFoundException(List.of(user));
                case PRIVATE_OR_EMPTY -> new WatchlistUnavailableException(List.of(user));
            };
        }

        List<FilmMatchDto> matches = filmResponseService.toDtos(List.copyOf(result.films()), random);

        return ResponseEntity.ok(matches);
    }
}
