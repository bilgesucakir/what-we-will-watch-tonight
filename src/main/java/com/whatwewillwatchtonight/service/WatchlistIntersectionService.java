package com.watchlistintersector.service;

import com.watchlistintersector.model.Film;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WatchlistIntersectionService {

    /**
     * @param result1 the first user's watchlist
     * @param result2 the second user's watchlist
     * @return the films present on both, in no particular order, using each film's
     *         title and year from {@code result1}
     */
    public List<Film> intersect(WatchlistResult result1, WatchlistResult result2) {
        Map<String, Film> filmsBySlug = result1.films().stream()
                .collect(Collectors.toMap(Film::slug, film -> film, (a, b) -> a));

        return result2.films().stream()
                .filter(film -> filmsBySlug.containsKey(film.slug()))
                .map(film -> filmsBySlug.get(film.slug()))
                .toList();
    }
}
