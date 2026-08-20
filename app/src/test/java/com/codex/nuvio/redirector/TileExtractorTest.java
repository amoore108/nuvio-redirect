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
}
