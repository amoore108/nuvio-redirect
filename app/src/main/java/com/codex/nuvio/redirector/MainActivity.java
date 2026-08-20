package com.codex.nuvio.redirector;

import android.annotation.SuppressLint;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private AppPreferences preferences;
    private TextView serviceStatus;
    private TextView redirectStatus;
    private TextView captureStatus;
    private EditText credentialInput;
    private Button redirectToggle;
    private Spinner nuvioVariant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildUi() {
        LinearLayout content = TvUi.scrollableColumn(this);
        content.addView(TvUi.title(this, "Nuvio Redirect"));
        content.addView(TvUi.body(
                this,
                "Press OK on a Google TV recommendation to resolve its visible title through TMDB and open the matching Nuvio detail page. The utility only handles selections while the HOME launcher is active."
        ));

        content.addView(TvUi.heading(this, "Redirects"));
        redirectStatus = TvUi.status(this, "Checking redirect status…");
        content.addView(redirectStatus);
        redirectToggle = TvUi.button(this, "Disable redirects");
        redirectToggle.setOnClickListener(view -> {
            boolean enabled = !preferences.redirectEnabled();
            preferences.setRedirectEnabled(enabled);
            Toast.makeText(
                    this,
                    enabled ? "Nuvio redirects enabled" : "Nuvio redirects disabled",
                    Toast.LENGTH_SHORT
            ).show();
            refreshStatus();
        });
        content.addView(redirectToggle);
        content.addView(TvUi.body(
                this,
                "Disabling pauses all redirection while keeping the Accessibility service configured and the app installed."
        ));

        content.addView(TvUi.heading(this, "1. Accessibility service"));
        serviceStatus = TvUi.status(this, "Checking service…");
        content.addView(serviceStatus);
        Button accessibilitySettings = TvUi.button(this, "Open accessibility settings");
        accessibilitySettings.setOnClickListener(view -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        content.addView(accessibilitySettings);

        content.addView(TvUi.heading(this, "Last captured launcher card"));
        captureStatus = TvUi.status(this, "No recommendation captured yet.");
        captureStatus.setTextIsSelectable(true);
        content.addView(captureStatus);
        Button refresh = TvUi.button(this, "Refresh diagnostics");
        refresh.setOnClickListener(view -> refreshStatus());
        content.addView(refresh);

        content.addView(TvUi.heading(this, "2. TMDB credential"));
        content.addView(TvUi.body(
                this,
                "Enter either a TMDB v3 API key or the v4 Read Access Token from your TMDB account. It stays in this app's private storage on the TV."
        ));
        credentialInput = TvUi.edit(this, "TMDB v3 key or v4 Read Access Token");
        credentialInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        credentialInput.setText(preferences.tmdbCredential());
        content.addView(credentialInput);
        Button saveCredential = TvUi.button(this, "Save TMDB credential");
        saveCredential.setOnClickListener(view -> {
            preferences.setTmdbCredential(credentialInput.getText().toString());
            Toast.makeText(this, "TMDB credential saved", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });
        content.addView(saveCredential);

        content.addView(TvUi.heading(this, "3. Nuvio installation"));
        nuvioVariant = new Spinner(this);
        String[] labels = {"Auto-detect", "Full / GitHub build (com.nuvio.tv)", "Google Play build (com.nuvio.app)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        nuvioVariant.setAdapter(adapter);
        nuvioVariant.setSelection(packageToPosition(preferences.nuvioPackage()));
        nuvioVariant.setFocusable(true);
        nuvioVariant.setPadding(TvUi.dp(this, 12), TvUi.dp(this, 8), TvUi.dp(this, 12), TvUi.dp(this, 8));
        content.addView(nuvioVariant);
        Button saveVariant = TvUi.button(this, "Save Nuvio variant");
        saveVariant.setOnClickListener(view -> {
            preferences.setNuvioPackage(positionToPackage(nuvioVariant.getSelectedItemPosition()));
            Toast.makeText(this, "Nuvio variant saved", Toast.LENGTH_SHORT).show();
            refreshStatus();
        });
        content.addView(saveVariant);
        Button testNuvio = TvUi.button(this, "Test Nuvio deep link (Fight Club)");
        testNuvio.setOnClickListener(view -> {
            try {
                NuvioLauncher.openTest(this, preferences);
            } catch (ActivityNotFoundException failure) {
                Toast.makeText(this, failure.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        content.addView(testNuvio);

        content.addView(TvUi.heading(this, "Manual resolver test"));
        EditText manualTitle = TvUi.edit(this, "Movie or series title");
        content.addView(manualTitle);
        EditText manualYear = TvUi.edit(this, "Year (optional)");
        manualYear.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(manualYear);
        Spinner manualType = new Spinner(this);
        String[] types = {"Unknown type", "Movie", "Series"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        manualType.setAdapter(typeAdapter);
        manualType.setFocusable(true);
        content.addView(manualType);
        Button resolve = TvUi.button(this, "Resolve and open in Nuvio");
        resolve.setOnClickListener(view -> {
            String title = manualTitle.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Enter a title first", Toast.LENGTH_SHORT).show();
                return;
            }
            Integer year = null;
            try {
                if (!manualYear.getText().toString().trim().isEmpty()) {
                    year = Integer.parseInt(manualYear.getText().toString().trim());
                }
            } catch (NumberFormatException ignored) {
                Toast.makeText(this, "Enter a four-digit year", Toast.LENGTH_SHORT).show();
                return;
            }
            String type = manualType.getSelectedItemPosition() == 1
                    ? TileCandidate.TYPE_MOVIE
                    : manualType.getSelectedItemPosition() == 2
                    ? TileCandidate.TYPE_SERIES
                    : TileCandidate.TYPE_UNKNOWN;
            TileCandidate candidate = new TileCandidate(title, year, type, title, "manual", true);
            startActivity(ResolverActivity.createIntent(this, candidate));
        });
        content.addView(resolve);

        content.addView(TvUi.spacer(this, 36));
        content.addView(TvUi.body(
                this,
                "Safety: selections are not consumed until a title-like recommendation is detected and both TMDB and Nuvio are configured. If a title is ambiguous, a match picker appears instead of silently choosing."
        ));

        // ScrollView itself otherwise takes initial focus on some Google TV builds, causing the
        // first Down press to skip past this primary control.
        redirectToggle.post(redirectToggle::requestFocus);
    }

    private void refreshStatus() {
        if (serviceStatus == null) return;
        boolean redirectsEnabled = preferences.redirectEnabled();
        boolean serviceEnabled = isRedirectServiceEnabled();
        boolean hasCredential = !preferences.tmdbCredential().isEmpty();
        String nuvioPackage = NuvioLauncher.selectedInstalledPackage(this, preferences);
        serviceStatus.setText(
                (serviceEnabled ? "✓ Accessibility service enabled" : "✕ Accessibility service disabled")
                        + "\n" + (hasCredential ? "✓ TMDB credential saved" : "✕ TMDB credential missing")
                        + "\n" + (nuvioPackage == null ? "✕ Nuvio not detected" : "✓ Nuvio package: " + nuvioPackage)
        );
        redirectStatus.setText(
                redirectsEnabled
                        ? "✓ Redirects enabled\nHome-screen recommendations will open in Nuvio."
                        : "Ⅱ Redirects disabled\nGoogle TV will handle recommendation selections normally."
        );
        redirectToggle.setText(redirectsEnabled ? "Disable redirects" : "Enable redirects");
        captureStatus.setText(preferences.lastCaptureSummary());
    }

    private boolean isRedirectServiceEnabled() {
        AccessibilityManager manager = getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        );
        String expected = RecommendationAccessibilityService.class.getName();
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            if (getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && expected.equals(info.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }

    private static int packageToPosition(String packageName) {
        if (AppPreferences.NUVIO_FULL.equals(packageName)) return 1;
        if (AppPreferences.NUVIO_PLAY_STORE.equals(packageName)) return 2;
        return 0;
    }

    private static String positionToPackage(int position) {
        if (position == 1) return AppPreferences.NUVIO_FULL;
        if (position == 2) return AppPreferences.NUVIO_PLAY_STORE;
        return AppPreferences.NUVIO_AUTO;
    }
}
