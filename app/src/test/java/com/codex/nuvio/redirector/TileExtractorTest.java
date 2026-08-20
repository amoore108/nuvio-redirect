package com.codex.nuvio.redirector;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TileExtractorTest {
    @Test
    public void removesPipeSeparatedMetadata() {
        assertEquals("Dune", TileExtractor.cleanedTitleForTest("Dune | 2021 | Movie"));
    }

    @Test
    public void preservesCommaInsideRealTitle() {
        assertEquals(
                "Dude, Where's My Car?",
                TileExtractor.cleanedTitleForTest("Dude, Where's My Car?, 2000, Movie")
        );
    }

    @Test
    public void preservesMovieWhenItBelongsToTitle() {
        assertEquals("Movie 43", TileExtractor.cleanedTitleForTest("Movie 43 | 2013 | Movie"));
    }

    @Test
    public void stripsSpokenProviderSuffix() {
        assertEquals("Andor", TileExtractor.cleanedTitleForTest("Watch Andor on Disney Plus"));
    }

    @Test
    public void ignoresObfuscatedLauncherPlaceholders() {
        assertEquals("", TileExtractor.cleanedTitleForTest("Column 1"));
        assertEquals("", TileExtractor.cleanedTitleForTest("Poster 12"));
    }

    @Test
    public void identifiesAppRowsAsClearlyNonContent() {
        TileCandidate app = new TileCandidate(
                "Nuvio Redirect",
                null,
                TileCandidate.TYPE_UNKNOWN,
                "Nuvio Redirect || row: Your apps",
                "com.google.android.apps.tv.launcherx:id/0_resource_name_obfuscated",
                false
        );

        org.junit.Assert.assertTrue(TileExtractor.isClearlyNonContent(app));
    }

    @Test
    public void keepsEntityDetailTitleClean() {
        assertEquals("Ted 2", TileExtractor.cleanedTitleForTest("Ted 2"));
    }

    @Test
    public void removesNewReleasePricingAndRatingMetadata() {
        assertEquals(
                "Obsession",
                TileExtractor.cleanedTitleForTest(
                        "Obsession, costs: £11.99, fresh rating: 94% on Rotten Tomatoes"
                )
        );
        assertEquals(
                "Michael",
                TileExtractor.cleanedTitleForTest(
                        "Michael, costs: £5.49, original price: £15.99, rotten rating: 38% on Rotten Tomatoes"
                )
        );
        assertEquals(
                "1408",
                TileExtractor.cleanedTitleForTest(
                        "1408, Rakuten TV, fresh rating: 80% on Rotten Tomatoes"
                )
        );
    }

    @Test
    public void preservesCommaInsideTitleBeforePricingMetadata() {
        assertEquals(
                "I, Robot",
                TileExtractor.cleanedTitleForTest(
                        "I, Robot, costs: £3.49, fresh rating: 57% on Rotten Tomatoes"
                )
        );
    }
}
