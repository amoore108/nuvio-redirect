package com.codex.nuvio.redirector;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

final class NuvioLauncher {
    private NuvioLauncher() {}

    static boolean isAnyNuvioInstalled(Context context) {
        return isInstalled(context, AppPreferences.NUVIO_FULL)
                || isInstalled(context, AppPreferences.NUVIO_PLAY_STORE);
    }

    static boolean canHandleDeepLink(Context context, AppPreferences preferences) {
        String packageName = selectedInstalledPackage(context, preferences);
        if (packageName == null) return false;
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("nuvio://tmdb/movie/550"))
                .setPackage(packageName);
        return context.getPackageManager().resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    static String selectedInstalledPackage(Context context, AppPreferences preferences) {
        String configured = preferences.nuvioPackage();
        if (!AppPreferences.NUVIO_AUTO.equals(configured)) {
            return isInstalled(context, configured) ? configured : null;
        }
        if (isInstalled(context, AppPreferences.NUVIO_FULL)) return AppPreferences.NUVIO_FULL;
        if (isInstalled(context, AppPreferences.NUVIO_PLAY_STORE)) return AppPreferences.NUVIO_PLAY_STORE;
        return null;
    }

    static void open(Context context, TmdbClient.Match match, AppPreferences preferences)
            throws ActivityNotFoundException {
        String packageName = selectedInstalledPackage(context, preferences);
        if (packageName == null) {
            throw new ActivityNotFoundException("Install Nuvio or choose the installed Nuvio variant in settings.");
        }
        String type = TileCandidate.TYPE_MOVIE.equals(match.mediaType) ? "movie" : "series";
        Uri deepLink = Uri.parse("nuvio://tmdb/" + type + "/" + match.id);
        Intent intent = new Intent(Intent.ACTION_VIEW, deepLink)
                .setPackage(packageName)
                // Nuvio's MainActivity keeps its previous detail state when Android delivers a
                // second deep link through onNewIntent. Start a fresh task so every launcher
                // selection replaces the previous title instead of leaving stale content visible.
                .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                );
        context.startActivity(intent);
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
