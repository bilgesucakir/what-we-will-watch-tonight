package com.whatwewillwatchtonight.controller;

import com.whatwewillwatchtonight.model.StreamingProvider;
import com.whatwewillwatchtonight.service.TmdbStreamingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lists the streaming services available in a region, for the "only pick
 * something we can stream" filter. Data is TMDB's, powered by JustWatch.
 */
@RestController
public class StreamingController {

    private final TmdbStreamingService streamingService;

    public StreamingController(TmdbStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @Operation(
            summary = "List streaming services in a region",
            description = "Every subscription/free/ad-supported service TMDB (via JustWatch) lists for "
                    + "movies in the given country, most mainstream first. Used to build the streaming filter."
    )
    @ApiResponse(responseCode = "200", description = "The providers (empty if TMDB has no key or no data)")
    @GetMapping("/api/streaming-providers")
    public ResponseEntity<List<StreamingProvider>> providers(
            @Parameter(description = "ISO-3166-1 country code, e.g. GB, TR, US")
            @RequestParam String region) {
        return ResponseEntity.ok(streamingService.providersInRegion(region));
    }
}
