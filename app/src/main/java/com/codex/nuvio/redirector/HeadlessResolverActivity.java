package com.codex.nuvio.redirector;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves a launcher selection without creating a visible window. The normal resolver Activity
 * is started only when the search needs user input or an error needs to be explained.
 */
public final class HeadlessResolverActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppPreferences preferences;
    private int generation;

    static Intent createIntent(Context context, TileCandidate candidate) {
        return new Intent(context, HeadlessResolverActivity.class)
                .putExtra(ResolverActivity.EXTRA_TITLE, candidate.title)
                .putExtra(ResolverActivity.EXTRA_TYPE, candidate.mediaType)
                .putExtra(ResolverActivity.EXTRA_RAW, candidate.rawText)
                .putExtra(ResolverActivity.EXTRA_YEAR, candidate.year == null ? 0 : candidate.year);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        resolve(getIntent());
    }

    @Override
    protected void onDestroy() {
        generation++;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void resolve(Intent intent) {
        String title = intent.getStringExtra(ResolverActivity.EXTRA_TITLE);
        String type = intent.getStringExtra(ResolverActivity.EXTRA_TYPE);
        String raw = intent.getStringExtra(ResolverActivity.EXTRA_RAW);
        Integer year = intent.hasExtra(ResolverActivity.EXTRA_YEAR)
                ? intent.getIntExtra(ResolverActivity.EXTRA_YEAR, 0)
                : null;
        if (title == null || title.trim().isEmpty()) {
            openVisibleResolver(intent, null, "The launcher did not expose a usable title.");
            return;
        }
        TileCandidate candidate = new TileCandidate(
                title.trim(),
                year != null && year > 0 ? year : null,
                type == null ? TileCandidate.TYPE_UNKNOWN : type,
                raw == null ? title : raw,
                "resolver",
                true
        );
        int requestGeneration = ++generation;
        executor.execute(() -> {
            try {
                List<TmdbClient.Match> matches = new TmdbClient().search(
                        preferences.tmdbCredential(),
                        candidate
                );
                runOnUiThread(() -> {
                    if (isFinishing() || requestGeneration != generation) return;
                    if (TmdbClient.isConfident(matches, candidate)) {
                        openMatch(candidate, matches.get(0));
                    } else {
                        openVisibleResolver(intent, matches, null);
                    }
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isFinishing() || requestGeneration != generation) return;
                    openVisibleResolver(
                            intent,
                            null,
                            failure.getMessage() == null
                                    ? failure.getClass().getSimpleName()
                                    : failure.getMessage()
                    );
                });
            }
        });
    }

    private void openMatch(TileCandidate candidate, TmdbClient.Match match) {
        try {
            NuvioLauncher.open(this, match, preferences);
            finish();
        } catch (ActivityNotFoundException | SecurityException failure) {
            openVisibleResolver(
                    ResolverActivity.createIntent(this, candidate),
                    null,
                    failure.getMessage() == null
                            ? "Nuvio could not handle the deep link."
                            : failure.getMessage()
            );
        }
    }

    private void openVisibleResolver(Intent source, List<TmdbClient.Match> matches, String error) {
        Intent visible = ResolverActivity.createIntent(this, new TileCandidate(
                source.getStringExtra(ResolverActivity.EXTRA_TITLE),
                source.hasExtra(ResolverActivity.EXTRA_YEAR)
                        ? source.getIntExtra(ResolverActivity.EXTRA_YEAR, 0)
                        : null,
                source.getStringExtra(ResolverActivity.EXTRA_TYPE),
                source.getStringExtra(ResolverActivity.EXTRA_RAW),
                "resolver",
                true
        )).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (matches != null) {
            visible.putExtra(ResolverActivity.EXTRA_MATCHES, new ArrayList<>(matches));
        }
        if (error != null) visible.putExtra(ResolverActivity.EXTRA_ERROR, error);
        try {
            startActivity(visible);
        } catch (RuntimeException failure) {
            Toast.makeText(this, "Could not open Nuvio Redirect: " + failure.getMessage(), Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
