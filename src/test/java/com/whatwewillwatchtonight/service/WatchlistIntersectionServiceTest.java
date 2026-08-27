package com.watchlistintersector.service;

import com.watchlistintersector.model.Film;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WatchlistIntersectionServiceTest {

    private final WatchlistIntersectionService service = new WatchlistIntersectionService();

    @Test
    void returnsOnlyFilmsPresentOnBothWatchlists() {
        WatchlistResult result1 = WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("anora", "Anora (2024)", 2024)));
        WatchlistResult result2 = WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024),
                new Film("the-substance", "The Substance (2024)", 2024)));

        List<Film> matches = service.intersect(result1, result2);

        assertThat(matches).extracting(Film::slug).containsExactly("dune-part-two");
    }

    @Test
    void returnsEmptyListWhenNoFilmsInCommon() {
        WatchlistResult result1 = WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024)));
        WatchlistResult result2 = WatchlistResult.of("bob", Set.of(
                new Film("anora", "Anora (2024)", 2024)));

        assertThat(service.intersect(result1, result2)).isEmpty();
    }

    @Test
    void usesFilmDataFromTheFirstWatchlist() {
        WatchlistResult result1 = WatchlistResult.of("alice", Set.of(
                new Film("dune-part-two", "Dune: Part Two (2024)", 2024)));
        WatchlistResult result2 = WatchlistResult.of("bob", Set.of(
                new Film("dune-part-two", "Different Title", 1999)));

        List<Film> matches = service.intersect(result1, result2);

        assertThat(matches).containsExactly(new Film("dune-part-two", "Dune: Part Two (2024)", 2024));
    }
}
