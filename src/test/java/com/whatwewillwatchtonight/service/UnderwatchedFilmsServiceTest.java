package com.whatwewillwatchtonight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatwewillwatchtonight.model.Film;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnderwatchedFilmsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UnderwatchedFilmsService serviceFrom(String json) {
        return new UnderwatchedFilmsService(objectMapper, new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void alwaysPicksAFilmFromTheConfiguredList() {
        UnderwatchedFilmsService service = serviceFrom("""
                [ { "slug": "wanda", "title": "Wanda", "year": 1970 },
                  { "slug": "the-ascent", "title": "The Ascent", "year": 1977 } ]
                """);

        for (int i = 0; i < 25; i++) {
            assertThat(service.pickRandom()).get()
                    .extracting(Film::slug).isIn("wanda", "the-ascent");
        }
    }

    @Test
    void returnsEmptyWhenTheListIsEmpty() {
        assertThat(serviceFrom("[]").pickRandom()).isEmpty();
    }

    @Test
    void failsFastWhenTheJsonIsMalformed() {
        assertThatThrownBy(() -> serviceFrom("{ not json"))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void theBundledListParsesAndIsNotEmpty() {
        Resource bundled = new ClassPathResource("underwatched-films.json");

        UnderwatchedFilmsService service = new UnderwatchedFilmsService(objectMapper, bundled);

        assertThat(service.pickRandom()).isPresent();
    }
}
