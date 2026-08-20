package com.codex.nuvio.redirector;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPreferences {
    static final String NUVIO_AUTO = "auto";
    static final String NUVIO_FULL = "com.nuvio.tv";
    static final String NUVIO_PLAY_STORE = "com.nuvio.app";

    private static final String FILE = "redirector_settings";
    private static final String KEY_TMDB_CREDENTIAL = "tmdb_credential";
    private static final String KEY_REDIRECT_ENABLED = "redirect_enabled";
    private static final String KEY_NUVIO_PACKAGE = "nuvio_package";
    private static final String KEY_LAST_TITLE = "last_title";
    private static final String KEY_LAST_RAW = "last_raw";
    private static final String KEY_LAST_VIEW_ID = "last_view_id";
    private static final String KEY_LAST_PACKAGE = "last_package";
    private static final String KEY_LAST_CAPTURED_AT = "last_captured_at";
    private static final String KEY_LAST_LIKELY = "last_likely";

    private final SharedPreferences preferences;

    AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    String tmdbCredential() {
        return preferences.getString(KEY_TMDB_CREDENTIAL, "").trim();
    }

    void setTmdbCredential(String credential) {
        preferences.edit().putString(KEY_TMDB_CREDENTIAL, credential == null ? "" : credential.trim()).apply();
    }

    boolean redirectEnabled() {
        return preferences.getBoolean(KEY_REDIRECT_ENABLED, true);
    }

    void setRedirectEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_REDIRECT_ENABLED, enabled).apply();
    }

    String nuvioPackage() {
        return preferences.getString(KEY_NUVIO_PACKAGE, NUVIO_AUTO);
    }

    void setNuvioPackage(String packageName) {
        preferences.edit().putString(KEY_NUVIO_PACKAGE, packageName).apply();
    }

    void saveCapture(TileCandidate candidate, String packageName) {
        preferences.edit()
                .putString(KEY_LAST_TITLE, candidate.title)
                .putString(KEY_LAST_RAW, candidate.rawText)
                .putString(KEY_LAST_VIEW_ID, candidate.viewId)
                .putString(KEY_LAST_PACKAGE, packageName == null ? "" : packageName)
                .putBoolean(KEY_LAST_LIKELY, candidate.likelyRecommendation)
                .putLong(KEY_LAST_CAPTURED_AT, System.currentTimeMillis())
                .apply();
    }

    String lastCaptureSummary() {
        String title = preferences.getString(KEY_LAST_TITLE, "");
        if (title.isEmpty()) {
            return "No recommendation captured yet.";
        }
        String sourcePackage = preferences.getString(KEY_LAST_PACKAGE, "");
        String viewId = preferences.getString(KEY_LAST_VIEW_ID, "");
        String raw = preferences.getString(KEY_LAST_RAW, "");
        boolean likely = preferences.getBoolean(KEY_LAST_LIKELY, false);
        long capturedAt = preferences.getLong(KEY_LAST_CAPTURED_AT, 0L);
        long ageSeconds = capturedAt == 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - capturedAt) / 1000L);
        return "Title: " + title
                + "\nLauncher: " + sourcePackage
                + "\nView id: " + (viewId.isEmpty() ? "(none exposed)" : viewId)
                + "\nWill intercept: " + (likely ? "yes" : "no — card fingerprint was not confident")
                + "\nCaptured: " + ageSeconds + "s ago"
                + "\nRaw accessibility text: " + raw;
    }
}
