package com.codex.nuvio.redirector;

final class TileCandidate {
    static final String TYPE_MOVIE = "movie";
    static final String TYPE_SERIES = "series";
    static final String TYPE_UNKNOWN = "unknown";

    final String title;
    final Integer year;
    final String mediaType;
    final String rawText;
    final String viewId;
    final boolean likelyRecommendation;

    TileCandidate(
            String title,
            Integer year,
            String mediaType,
            String rawText,
            String viewId,
            boolean likelyRecommendation
    ) {
        this.title = title;
        this.year = year;
        this.mediaType = mediaType;
        this.rawText = rawText;
        this.viewId = viewId;
        this.likelyRecommendation = likelyRecommendation;
    }
}
