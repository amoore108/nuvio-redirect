package com.codex.nuvio.redirector;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public final class CandidateSelectorTest {
    @Test
    public void usesRecentRecommendationWhenRootFocusIsNavigation() {
        TileCandidate navigation = candidate("Home", false);
        TileCandidate recommendation = candidate("Obsession", true);

        assertSame(recommendation, CandidateSelector.choose(navigation, recommendation, true));
    }

    @Test
    public void keepsConfidentRootRecommendation() {
        TileCandidate root = candidate("Dune", true);
        TileCandidate recent = candidate("Obsession", true);

        assertSame(root, CandidateSelector.choose(root, recent, true));
    }

    @Test
    public void doesNotUseExpiredOrUncertainFallback() {
        TileCandidate navigation = candidate("Home", false);
        TileCandidate recommendation = candidate("Obsession", true);

        assertSame(navigation, CandidateSelector.choose(navigation, recommendation, false));
        assertSame(navigation, CandidateSelector.choose(navigation, candidate("Apps", false), true));
    }

    private static TileCandidate candidate(String title, boolean likelyRecommendation) {
        return new TileCandidate(
                title,
                null,
                TileCandidate.TYPE_UNKNOWN,
                title,
                "test",
                likelyRecommendation
        );
    }
}
