package com.codex.nuvio.redirector;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class RecommendationAccessibilityService extends AccessibilityService {
    private static final String TAG = "NuvioRedirectService";
    // Keep the focused recommendation long enough to read its synopsis before pressing OK.
    // Focus changes still clear or replace this cache immediately.
    private static final long CAPTURE_MAX_AGE_MS = 30_000L;
    private static final long ENTITY_REDIRECT_MAX_WAIT_MS = 8_000L;
    private static final Set<String> KNOWN_GOOGLE_TV_LAUNCHERS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher"
    ));

    private AppPreferences preferences;
    private String homePackage = "";
    private String lastEventPackage = "";
    private TileCandidate lastCandidate;
    private long lastCandidateAt;
    private int lastCandidateWindowId = -1;
    private boolean consumingSelectPress;
    private long lastMissingSetupNoticeAt;
    private long awaitingEntityDetailsUntil;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        preferences = new AppPreferences(this);
        refreshHomePackage();
        Log.i(TAG, "Accessibility service connected; home=" + homePackage);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (preferences == null) preferences = new AppPreferences(this);
        if (!preferences.redirectEnabled()) {
            awaitingEntityDetailsUntil = 0L;
            clearCandidate();
            return;
        }
        String packageName = event.getPackageName().toString();
        lastEventPackage = packageName;
        if (!isHomePackage(packageName)) {
            awaitingEntityDetailsUntil = 0L;
            clearCandidate();
            return;
        }

        // LauncherX opens its own details activity for recommendation cards whose focused
        // accessibility node contains only a placeholder such as "Column 1". Detecting that
        // activity is more reliable than depending solely on the select-key callback (which is
        // not delivered for every remote/input implementation), and it is specific to Google TV
        // content details rather than app tiles.
        if (isGoogleTvEntityEvent(event) && awaitingEntityDetailsUntil == 0L) {
            awaitingEntityDetailsUntil = SystemClock.uptimeMillis()
                    + ENTITY_REDIRECT_MAX_WAIT_MS;
            Log.i(TAG, "Detected Google TV entity activity; awaiting title");
        }

        if (awaitingEntityDetailsUntil > 0L) {
            if (SystemClock.uptimeMillis() > awaitingEntityDetailsUntil) {
                awaitingEntityDetailsUntil = 0L;
            } else {
                TileCandidate entityCandidate = TileExtractor.extractEntityDetails(event.getSource());
                if (entityCandidate == null) {
                    AccessibilityNodeInfo root = getRootInActiveWindow();
                    entityCandidate = TileExtractor.extractEntityDetails(root);
                }
                if (entityCandidate != null) {
                    Log.i(TAG, "Captured entity details: " + entityCandidate.title);
                    awaitingEntityDetailsUntil = 0L;
                    preferences.saveCapture(entityCandidate, packageName);
                    openResolverFromEntity(entityCandidate);
                    return;
                }
            }
        }

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_SELECTED) {
            return;
        }

        AccessibilityNodeInfo source = event.getSource();
        TileCandidate candidate = TileExtractor.extract(source);
        if (candidate == null) {
            candidate = TileExtractor.extractFromRoot(getRootInActiveWindow());
        }
        if (candidate == null) {
            clearCandidate();
            return;
        }
        if (!candidate.likelyRecommendation) {
            // App and navigation tiles must clear the fallback, but should not overwrite the
            // last useful recommendation diagnostic just because the user reopened this app.
            clearCandidate();
            return;
        }
        lastCandidate = candidate;
        lastCandidateAt = SystemClock.uptimeMillis();
        lastCandidateWindowId = event.getWindowId();
        preferences.saveCapture(candidate, packageName);
    }

    @Override
    public void onInterrupt() {
        consumingSelectPress = false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isSelectKey(event.getKeyCode())) return false;

        Log.i(
                TAG,
                "Select key action=" + event.getAction()
                        + " repeat=" + event.getRepeatCount()
                        + " consuming=" + consumingSelectPress
        );

        if (event.getAction() == KeyEvent.ACTION_UP && consumingSelectPress) {
            consumingSelectPress = false;
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return consumingSelectPress;
        }

        if (preferences == null) preferences = new AppPreferences(this);
        if (!preferences.redirectEnabled()) return false;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        String activePackage = root != null && root.getPackageName() != null
                ? root.getPackageName().toString()
                : lastEventPackage;
        if (!isHomePackage(activePackage)) return false;

        TileCandidate rootCandidate = TileExtractor.extractFromRoot(root);
        boolean recentCandidateValid = lastCandidate != null
                && root != null
                && root.getWindowId() == lastCandidateWindowId
                && activePackage.equals(lastEventPackage)
                && SystemClock.uptimeMillis() - lastCandidateAt <= CAPTURE_MAX_AGE_MS;
        TileCandidate candidate = CandidateSelector.choose(
                rootCandidate,
                lastCandidate,
                recentCandidateValid
        );
        Log.i(
                TAG,
                "Selection activePackage=" + activePackage
                        + " rootCandidate=" + describeCandidate(rootCandidate)
                        + " recentCandidate=" + describeCandidate(lastCandidate)
                        + " recentValid=" + recentCandidateValid
        );

        // Save the actual selection attempt before applying the confidence gate. This keeps an
        // uncertain launcher fingerprint available for troubleshooting.
        if (candidate != null && !TileExtractor.isClearlyNonContent(candidate)) {
            preferences.saveCapture(candidate, activePackage);
        }
        if (candidate == null || !candidate.likelyRecommendation) {
            if (TileExtractor.hasFocusedRecommendationPlaceholder(root)) {
                awaitingEntityDetailsUntil = SystemClock.uptimeMillis()
                        + ENTITY_REDIRECT_MAX_WAIT_MS;
                Log.i(TAG, "Armed entity-details fallback");
            } else {
                Log.i(TAG, "Allowed select: no recommendation candidate or placeholder");
            }
            return false;
        }

        if (preferences.tmdbCredential().isEmpty()
                || !NuvioLauncher.canHandleDeepLink(this, preferences)) {
            Log.i(TAG, "Allowed select: redirect setup is incomplete");
            showMissingSetupNotice();
            return false;
        }

        Intent resolver = ResolverActivity.createIntent(this, candidate)
                .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                );
        try {
            Log.i(TAG, "Opening resolver directly for " + candidate.title);
            startActivity(resolver);
            consumingSelectPress = true;
            return true;
        } catch (RuntimeException failure) {
            Toast.makeText(this, "Could not start Nuvio Redirect: " + failure.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void refreshHomePackage() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(home, 0);
        homePackage = resolved != null && resolved.activityInfo != null
                ? resolved.activityInfo.packageName
                : "";
    }

    private boolean isHomePackage(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        if (homePackage.isEmpty()) refreshHomePackage();
        return packageName.equals(homePackage) || KNOWN_GOOGLE_TV_LAUNCHERS.contains(packageName);
    }

    private void showMissingSetupNotice() {
        long now = SystemClock.uptimeMillis();
        if (now - lastMissingSetupNoticeAt < 5_000L) return;
        lastMissingSetupNoticeAt = now;
        Toast.makeText(
                this,
                preferences.tmdbCredential().isEmpty()
                        ? "Add your TMDB credential in Nuvio Redirect first."
                        : "Nuvio is not installed or the wrong variant is selected.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void openResolverFromEntity(TileCandidate candidate) {
        if (preferences.tmdbCredential().isEmpty()
                || !NuvioLauncher.canHandleDeepLink(this, preferences)) {
            showMissingSetupNotice();
            return;
        }
        Intent resolver = ResolverActivity.createIntent(this, candidate)
                .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                );
        try {
            Log.i(TAG, "Opening resolver from entity details for " + candidate.title);
            startActivity(resolver);
        } catch (RuntimeException failure) {
            Toast.makeText(
                    this,
                    "Could not start Nuvio Redirect: " + failure.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void clearCandidate() {
        lastCandidate = null;
        lastCandidateAt = 0L;
        lastCandidateWindowId = -1;
    }

    private static boolean isSelectKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean isGoogleTvEntityEvent(AccessibilityEvent event) {
        if (!"com.google.android.apps.tv.launcherx".contentEquals(event.getPackageName())) {
            return false;
        }
        CharSequence className = event.getClassName();
        return className != null
                && className.toString().endsWith(".entity.EntityActivity");
    }

    private static String describeCandidate(TileCandidate candidate) {
        if (candidate == null) return "none";
        return candidate.title + "/likely=" + candidate.likelyRecommendation;
    }
}
