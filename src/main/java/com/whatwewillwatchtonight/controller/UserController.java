package com.watchlistintersector.controller;

import com.watchlistintersector.controller.dto.ErrorResponseDto;
import com.watchlistintersector.controller.dto.UsernameCheckDto;
import com.watchlistintersector.service.LetterboxdScraperService;
import com.watchlistintersector.service.UsernameCheck;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks whether a Letterboxd username exists and whether its watchlist is
 * public.
 */
@RestController
public class UserController {

    private final LetterboxdScraperService scraperService;

    public UserController(LetterboxdScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @Operation(
            summary = "Check a Letterboxd username",
            description = "Checks whether a username exists and whether its watchlist is public, "
                    + "without doing a full watchlist scrape."
    )
    @ApiResponse(responseCode = "200", description = "The check result (existence is reflected in the body, not the status code)")
    @ApiResponse(responseCode = "400", description = "The username is blank")
    @GetMapping("/api/users/{username}/exists")
    public ResponseEntity<?> exists(@Parameter(description = "Letterboxd username") @PathVariable String username) {
        if (username.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponseDto("username is required."));
        }

        UsernameCheck check = scraperService.checkUsername(username);
        return ResponseEntity.ok(new UsernameCheckDto(check.userExists(), check.watchlistPublic(), check.avatarUrl()));
    }
}
