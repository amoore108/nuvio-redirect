package com.codex.nuvio.redirector;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TmdbClientTest {
    @Test
    public void confidentExactMovieWithMatchingYear() {
        TileCandidate request = new TileCandidate(
                "Dune",
                2021,
                TileCandidate.TYPE_MOVIE,
                "Dune | 2021 | Movie",
                "recommendation_card",
                true
        );
        TmdbClient.Match correct = new TmdbClient.Match(
                438631,
                "Dune",
                "Dune",
                TileCandidate.TYPE_MOVIE,
                2021,
                1.0,
                0.98
        );
        TmdbClient.Match older = new TmdbClient.Match(
                841,
                "Dune",
                "Dune",
                TileCandidate.TYPE_MOVIE,
                1984,
                1.0,
                0.80
        );

        assertTrue(TmdbClient.isConfident(Arrays.asList(correct, older), request));
    }

    @Test
    public void ambiguousExactTitleWithoutYearShowsPicker() {
        TileCandidate request = new TileCandidate(
                "Dune",
                null,
                TileCandidate.TYPE_MOVIE,
                "Dune",
                "recommendation_card",
                true
        );
        TmdbClient.Match first = new TmdbClient.Match(
                438631, "Dune", "Dune", TileCandidate.TYPE_MOVIE, 2021, 1.0, 0.91
        );
        TmdbClient.Match second = new TmdbClient.Match(
                841, "Dune", "Dune", TileCandidate.TYPE_MOVIE, 1984, 1.0, 0.89
        );

        assertFalse(TmdbClient.isConfident(Arrays.asList(first, second), request));
    }

    @Test
    public void fuzzyTitleNeverAutoOpens() {
        TileCandidate request = new TileCandidate(
                "Mission Impossible",
                null,
                TileCandidate.TYPE_UNKNOWN,
                "Mission Impossible",
                "recommendation_card",
                true
        );
        TmdbClient.Match fuzzy = new TmdbClient.Match(
                575264,
                "Mission: Impossible - Dead Reckoning Part One",
                "Mission: Impossible - Dead Reckoning Part One",
                TileCandidate.TYPE_MOVIE,
                2023,
                0.72,
                0.66
        );

        assertFalse(TmdbClient.isConfident(Collections.singletonList(fuzzy), request));
    }

    @Test
    public void wrongMediaTypeNeverAutoOpens() {
        TileCandidate request = new TileCandidate(
                "Fallout",
                2024,
                TileCandidate.TYPE_SERIES,
                "Fallout | Series | 2024",
                "recommendation_card",
                true
        );
        TmdbClient.Match movie = new TmdbClient.Match(
                123,
                "Fallout",
                "Fallout",
                TileCandidate.TYPE_MOVIE,
                2024,
                1.0,
                0.94
        );

        assertFalse(TmdbClient.isConfident(Collections.singletonList(movie), request));
    }
}
