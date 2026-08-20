package com.codex.nuvio.redirector;

final class CandidateSelector {
    private CandidateSelector() {}

    static TileCandidate choose(
            TileCandidate rootCandidate,
            TileCandidate recentCandidate,
            boolean recentCandidateValid
    ) {
        if (rootCandidate != null && rootCandidate.likelyRecommendation) {
            return rootCandidate;
        }
        if (recentCandidateValid
                && recentCandidate != null
                && recentCandidate.likelyRecommendation) {
            return recentCandidate;
        }
        return rootCandidate;
    }
}
