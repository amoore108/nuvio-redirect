package com.codex.nuvio.redirector;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class RecommendationAccessibilityService extends AccessibilityService {
    private static final long CAPTURE_MAX_AGE_MS = 4_000L;
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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        preferences = new AppPreferences(this);
        refreshHomePackage();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        lastEventPackage = packageName;
        if (!isHomePackage(packageName)) {
            clearCandidate();
            return;
        }

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_FOCUSED
                && type != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            return;
        }

        AccessibilityNodeInfo source = event.getSource();
        TileCandidate candidate = TileExtractor.extract(source);
        if (candidate == null) {
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

        TileCandidate candidate = TileExtractor.extractFromRoot(root);
        if (candidate == null && lastCandidate != null
                && root != null
                && root.getWindowId() == lastCandidateWindowId
                && activePackage.equals(lastEventPackage)
                && SystemClock.uptimeMillis() - lastCandidateAt <= CAPTURE_MAX_AGE_MS) {
            candidate = lastCandidate;
        }
        if (candidate == null || !candidate.likelyRecommendation) return false;

        preferences.saveCapture(candidate, activePackage);
        if (preferences.tmdbCredential().isEmpty()
                || !NuvioLauncher.canHandleDeepLink(this, preferences)) {
            showMissingSetupNotice();
            return false;
        }

        Intent resolver = ResolverActivity.createIntent(this, candidate)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
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
}
