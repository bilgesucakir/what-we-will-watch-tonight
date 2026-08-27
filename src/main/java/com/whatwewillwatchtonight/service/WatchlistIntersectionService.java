package com.whatwewillwatchtonight.service;

import com.whatwewillwatchtonight.model.Film;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WatchlistIntersectionService {

    /**
     * @param results the watchlists to intersect (two to four of them)
     * @return the films present on every one, in no particular order, using each
     *         film's title and year from the first watchlist
     */
    public List<Film> intersect(List<WatchlistResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }

        List<Set<String>> slugsPerWatchlist = results.stream()
                .map(result -> result.films().stream().map(Film::slug).collect(Collectors.toSet()))
                .toList();

        return results.get(0).films().stream()
                .filter(film -> slugsPerWatchlist.stream().allMatch(slugs -> slugs.contains(film.slug())))
                .toList();
    }
}
