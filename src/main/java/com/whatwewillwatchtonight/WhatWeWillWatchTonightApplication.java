package com.whatwewillwatchtonight;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
        info = @Info(
                title = "What We Will Watch Tonight API",
                version = "v1",
                description = "Scrapes public Letterboxd watchlists and helps a group of 2-4 people "
                        + "(or one person) pick something to watch: the full overlap, one random pick, "
                        + "or -- when a group has nothing in common -- a random underwatched film."
        )
)
@SpringBootApplication
public class WhatWeWillWatchTonightApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhatWeWillWatchTonightApplication.class, args);
    }
}
