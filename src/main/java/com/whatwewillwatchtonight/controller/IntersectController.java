package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.controller.dto.FilmMatchDto;
import com.whatwewillwatchtonight.controller.error.BlankUsernameException;
import com.whatwewillwatchtonight.controller.error.InvalidUsernameCountException;
import com.whatwewillwatchtonight.controller.error.UserNotFoundException;
import com.whatwewillwatchtonight.controller.error.WatchlistUnavailableException;
import com.whatwewillwatchtonight.model.Film;
import com.whatwewillwatchtonight.service.FilmResponseService;
import com.whatwewillwatchtonight.service.LetterboxdScraperService;
import com.whatwewillwatchtonight.service.WatchlistIntersectionService;
import com.whatwewillwatchtonight.service.WatchlistResult;
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
 * Finds films present on every one of two to four Letterboxd users' watchlists.
 */
@RestController
public class IntersectController {

    private static final int MIN_USERS = 2;
    private static final int MAX_USERS = 4;

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
            summary = "Find films on every user's watchlist",
            description = "Returns every film present on all of the given users' public watchlists "
                    + "(2 to 4 users), or a single random pick."
    )
    @ApiResponse(responseCode = "200", description = "The matching films, or a single random pick")
    @ApiResponse(responseCode = "400", description = "Not 2-4 usernames, a username is blank, a user doesn't exist, "
            + "or a watchlist is private/empty (each is a distinct message)")
    @GetMapping("/api/intersect")
    public ResponseEntity<List<FilmMatchDto>> intersect(
            @Parameter(description = "Letterboxd usernames, repeated 2 to 4 times (e.g. ?user=alice&user=bob)")
            @RequestParam("user") List<String> user,
            @Parameter(description = "Return a single random film instead of the full overlap")
            @RequestParam(defaultValue = "false") boolean random) {
        List<String> usernames = user.stream().map(String::trim).toList();

        if (usernames.size() < MIN_USERS || usernames.size() > MAX_USERS) {
            throw new InvalidUsernameCountException(MIN_USERS, MAX_USERS);
        }
        if (usernames.stream().anyMatch(String::isBlank)) {
            throw new BlankUsernameException();
        }

        List<WatchlistResult> results = usernames.stream()
                .map(username -> CompletableFuture.supplyAsync(
                        () -> scraperService.fetchWatchlist(username), ioExecutor))
                .toList()
                .stream()
                .map(CompletableFuture::join)
                .toList();

        List<String> nonexistent = usernamesWithReason(results, WatchlistResult.Reason.NONEXISTENT);
        if (!nonexistent.isEmpty()) {
            throw new UserNotFoundException(nonexistent);
        }
        List<String> unavailable = usernamesWithReason(results, WatchlistResult.Reason.PRIVATE_OR_EMPTY);
        if (!unavailable.isEmpty()) {
            throw new WatchlistUnavailableException(unavailable);
        }

        List<Film> matchedFilms = intersectionService.intersect(results);
        return ResponseEntity.ok(filmResponseService.toDtos(matchedFilms, random));
    }

    private static List<String> usernamesWithReason(List<WatchlistResult> results, WatchlistResult.Reason reason) {
        return results.stream()
                .filter(result -> result.reason() == reason)
                .map(WatchlistResult::username)
                .toList();
    }
}
