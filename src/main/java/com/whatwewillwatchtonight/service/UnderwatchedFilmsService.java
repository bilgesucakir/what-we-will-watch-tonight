package com.whatwewillwatchtonight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whatwewillwatchtonight.model.Film;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds a hand-picked list of criminally underwatched films, loaded once at
 * startup from {@code src/main/resources/underwatched-films.json}. Used to hand
 * something back when a group's watchlists turn out to have nothing in common.
 *
 * <p>The seed list is Letterboxd's "Top 100 Underseen Films"
 * (<a href="https://letterboxd.com/official/list/top-100-underseen-films/">
 * letterboxd.com/official/list/top-100-underseen-films</a>). It's kept static
 * on purpose -- no runtime scraping -- so edit the JSON file to curate it.
 */
@Service
public class UnderwatchedFilmsService {

    private static final Logger log = LoggerFactory.getLogger(UnderwatchedFilmsService.class);

    private final List<Film> films;

    public UnderwatchedFilmsService(
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("classpath:underwatched-films.json") Resource source) {
        this.films = load(objectMapper, source);
        log.info("Loaded {} underwatched films", films.size());
    }

    private static List<Film> load(ObjectMapper objectMapper, Resource source) {
        try (InputStream in = source.getInputStream()) {
            return List.of(objectMapper.readValue(in, Film[].class));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read underwatched-films.json", e);
        }
    }

    /**
     * @return a random film from the list, or empty if the list is empty
     */
    public Optional<Film> pickRandom() {
        if (films.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(films.get(ThreadLocalRandom.current().nextInt(films.size())));
    }
}
