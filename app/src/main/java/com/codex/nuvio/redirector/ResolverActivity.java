package com.codex.nuvio.redirector;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public final class ResolverActivity extends Activity {
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_YEAR = "year";
    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_RAW = "raw";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AppPreferences preferences;
    private LinearLayout matchContainer;
    private TextView status;
    private int generation;

    static Intent createIntent(Context context, TileCandidate candidate) {
        Intent intent = new Intent(context, ResolverActivity.class)
                .putExtra(EXTRA_TITLE, candidate.title)
                .putExtra(EXTRA_TYPE, candidate.mediaType)
                .putExtra(EXTRA_RAW, candidate.rawText);
        if (candidate.year != null) intent.putExtra(EXTRA_YEAR, candidate.year);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        showLoadingSurface();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showLoadingSurface();
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        generation++;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        if (status != null) return;
        LinearLayout content = TvUi.scrollableColumn(this);
        content.addView(TvUi.title(this, "Opening in Nuvio"));
        status = TvUi.status(this, "Reading recommendation…");
        content.addView(status);
        matchContainer = new LinearLayout(this);
        matchContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(matchContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void showLoadingSurface() {
        status = null;
        matchContainer = null;
        View transparentSurface = new View(this);
        transparentSurface.setBackgroundColor(Color.TRANSPARENT);
        setContentView(transparentSurface);
    }

    private void handleIntent(Intent intent) {
        int requestGeneration = ++generation;
        showLoadingSurface();
        String title = intent.getStringExtra(EXTRA_TITLE);
        String type = intent.getStringExtra(EXTRA_TYPE);
        String raw = intent.getStringExtra(EXTRA_RAW);
        Integer year = intent.hasExtra(EXTRA_YEAR) ? intent.getIntExtra(EXTRA_YEAR, 0) : null;
        if (title == null || title.trim().isEmpty()) {
            showError("The launcher did not expose a usable title.");
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
        executor.execute(() -> {
            try {
                List<TmdbClient.Match> matches = new TmdbClient().search(preferences.tmdbCredential(), candidate);
                runOnUiThread(() -> {
                    if (isFinishing() || requestGeneration != generation) return;
                    showMatches(candidate, matches);
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isFinishing() || requestGeneration != generation) return;
                    showError(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                });
            }
        });
    }

    private void showMatches(TileCandidate candidate, List<TmdbClient.Match> matches) {
        if (matches == null || matches.isEmpty()) {
            showError("No close TMDB match was found for “" + candidate.title + "”.");
            return;
        }
        if (TmdbClient.isConfident(matches, candidate)) {
            openMatch(matches.get(0));
            return;
        }

        buildUi();
        status.setText("Choose the match for “" + candidate.title + "”. Nothing was selected automatically because the result was ambiguous.");
        for (TmdbClient.Match match : matches) {
            Button choice = TvUi.button(this, match.displayLabel());
            choice.setOnClickListener(view -> openMatch(match));
            matchContainer.addView(choice);
        }
        addCancelButton();
        if (matchContainer.getChildCount() > 0) matchContainer.getChildAt(0).requestFocus();
    }

    private void openMatch(TmdbClient.Match match) {
        if (status != null) {
            status.setText("Opening " + match.displayLabel() + " in Nuvio…");
        }
        try {
            NuvioLauncher.open(this, match, preferences);
            finish();
        } catch (ActivityNotFoundException | SecurityException failure) {
            showError(failure.getMessage() == null ? "Nuvio could not handle the deep link." : failure.getMessage());
        }
    }

    private void showError(String message) {
        buildUi();
        matchContainer.removeAllViews();
        status.setText("Could not redirect\n" + message);
        addCancelButton();
        if (matchContainer.getChildCount() > 0) matchContainer.getChildAt(0).requestFocus();
    }

    private void addCancelButton() {
        Button cancel = TvUi.button(this, "Back to Google TV (do not redirect)");
        cancel.setOnClickListener(view -> finish());
        matchContainer.addView(cancel);
    }
}
