package com.watchlistintersector;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
        info = @Info(
                title = "Watchlist Intersector API",
                version = "v1",
                description = "Finds films shared between two Letterboxd users' public watchlists "
                        + "(or picks one at random), and does the same for a single user's own watchlist."
        )
)
@SpringBootApplication
public class WatchlistIntersectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchlistIntersectorApplication.class, args);
    }
}
