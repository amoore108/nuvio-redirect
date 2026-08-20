package com.codex.nuvio.redirector;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TileExtractor {
    private static final int MAX_NODES = 80;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(?:^|\\D)((?:19|20)\\d{2})(?:\\D|$)");
    private static final Pattern TRAILING_MEDIA_LABEL = Pattern.compile(
            "(?i)[,;|•·\\-–— ]+(movie|film|tv show|television show|series|episode|season(?: \\d+)?)$"
    );
    private static final Pattern ACTION_PREFIX = Pattern.compile(
            "(?i)^(watch|play|open|continue watching|resume|view details for)\\s+"
    );
    private static final Pattern PROVIDER_SUFFIX = Pattern.compile(
            "(?i)\\s+(?:on|from|with)\\s+(netflix|prime video|amazon prime video|disney(?:\\+| plus)|hulu|paramount(?:\\+| plus)|max|apple tv(?:\\+| plus)?|peacock|youtube)(?:\\s.*)?$"
    );
    private static final Pattern LAUNCHER_PLACEHOLDER = Pattern.compile(
            "(?i)(?:column|row|item|card|poster|tile)\\s+\\d+"
    );

    private static final Set<String> BLOCKED_TITLES = new HashSet<>(Arrays.asList(
            "home", "for you", "live", "apps", "library", "shop", "search", "settings",
            "inputs", "notifications", "profile", "accounts", "customize", "see all",
            "netflix", "prime video", "amazon prime video", "disney plus", "disney+",
            "hulu", "paramount plus", "paramount+", "max", "peacock", "youtube",
            "youtube music", "spotify", "google play store", "play store", "app store",
            "movie", "film", "tv show", "television show", "series", "episode", "season"
    ));

    private static final List<String> RECOMMENDATION_ID_HINTS = Arrays.asList(
            "recommend", "program", "preview", "content", "entity", "poster", "hero",
            "watch_next", "top_pick", "movie", "show", "media", "card"
    );
    private static final List<String> NON_CONTENT_ID_HINTS = Arrays.asList(
            "app_banner", "app_icon", "settings", "notification", "profile", "input",
            "navigation", "nav_item", "top_tab", "search_button"
    );
    private static final List<String> RECOMMENDATION_CONTEXT_HINTS = Arrays.asList(
            "top picks", "recommended", "recommendations", "because you watched",
            "continue watching", "watch next", "popular movies", "popular shows",
            "movies and shows", "trending", "new releases", "free to watch"
    );
    private static final List<String> NON_CONTENT_CONTEXT_HINTS = Arrays.asList(
            "your apps", "favorite apps", "favourite apps", "app library", "settings",
            "notifications", "inputs", "profiles"
    );

    private TileExtractor() {}

    static TileCandidate extractFromRoot(AccessibilityNodeInfo root) {
        if (root == null) return null;
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }
        if (focused == null) {
            focused = findFocusedNode(root);
        }
        return extract(focused);
    }

    static TileCandidate extract(AccessibilityNodeInfo focused) {
        if (focused == null) return null;

        AccessibilityNodeInfo card = findCardAncestor(focused);
        LinkedHashSet<String> rankedText = new LinkedHashSet<>();
        StringBuilder viewIds = new StringBuilder();
        collectRegion(card, rankedText, viewIds);
        String ancestorContext = collectAncestorContext(card);

        // Some launchers put a direct label on the focused child while the click target is
        // its parent. Keep that label first without traversing any neighbouring cards.
        LinkedHashSet<String> focusedText = new LinkedHashSet<>();
        addText(focusedText, focused.getContentDescription());
        addText(focusedText, focused.getText());
        focusedText.addAll(rankedText);
        rankedText = focusedText;

        if (rankedText.isEmpty()) return null;

        String raw = TextUtils.join(" | ", rankedText);
        if (!ancestorContext.isEmpty()) raw += " || row: " + ancestorContext;
        ParsedLabel best = null;
        for (String value : rankedText) {
            for (String part : splitLabel(value)) {
                ParsedLabel parsed = parseLabel(part, value);
                if (parsed == null) continue;
                if (best == null || parsed.score > best.score) {
                    best = parsed;
                }
            }
        }
        if (best == null) return null;

        String ids = viewIds.toString();
        String normalizedIds = ids.toLowerCase(Locale.ROOT);
        boolean hasContentHint = containsAny(normalizedIds, RECOMMENDATION_ID_HINTS);
        boolean hasNonContentHint = containsAny(normalizedIds, NON_CONTENT_ID_HINTS);
        String normalizedContext = normalize(ancestorContext);
        boolean hasRecommendationContext = containsAny(
                normalizedContext,
                RECOMMENDATION_CONTEXT_HINTS
        );
        boolean hasNonContentContext = containsAny(
                normalizedContext,
                NON_CONTENT_CONTEXT_HINTS
        );
        boolean richDescription = focused.getContentDescription() != null
                && focused.getContentDescription().length() > best.title.length();
        boolean likely = !hasNonContentHint
                && !hasNonContentContext
                && (hasContentHint || richDescription || hasRecommendationContext);

        return new TileCandidate(
                best.title,
                best.year,
                best.mediaType,
                raw,
                ids,
                likely
        );
    }

    private static void collectRegion(
            AccessibilityNodeInfo start,
            LinkedHashSet<String> text,
            StringBuilder viewIds
    ) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(start);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_NODES) {
            AccessibilityNodeInfo node = queue.removeFirst();
            addText(text, node.getContentDescription());
            addText(text, node.getText());
            String viewId = node.getViewIdResourceName();
            if (!TextUtils.isEmpty(viewId) && viewIds.indexOf(viewId) < 0) {
                if (viewIds.length() > 0) viewIds.append(" | ");
                viewIds.append(viewId);
            }
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.addLast(child);
            }
        }
    }

    private static AccessibilityNodeInfo findCardAncestor(AccessibilityNodeInfo focused) {
        AccessibilityNodeInfo current = focused;
        AccessibilityNodeInfo best = focused;
        for (int depth = 0; current != null && depth < 4; depth++) {
            best = current;
            if (current.isClickable() || (depth > 0 && current.isFocusable())) {
                return current;
            }
            current = current.getParent();
        }
        return best;
    }

    private static String collectAncestorContext(AccessibilityNodeInfo card) {
        LinkedHashSet<String> context = new LinkedHashSet<>();
        AccessibilityNodeInfo current = card == null ? null : card.getParent();
        for (int depth = 0; current != null && depth < 4; depth++) {
            addText(context, current.getContentDescription());
            addText(context, current.getText());
            current = current.getParent();
        }
        return TextUtils.join(" | ", context);
    }

    private static AccessibilityNodeInfo findFocusedNode(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 300) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isFocused() || node.isAccessibilityFocused()) return node;
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.addLast(child);
            }
        }
        return null;
    }

    private static void addText(Set<String> destination, CharSequence value) {
        if (value == null) return;
        String clean = value.toString().replaceAll("\\s+", " ").trim();
        if (!clean.isEmpty() && clean.length() <= 500) destination.add(clean);
    }

    private static List<String> splitLabel(String value) {
        List<String> parts = new ArrayList<>();
        parts.add(value);
        for (String part : value.split("[\\n|•]")) {
            String clean = part.trim();
            if (!clean.isEmpty() && !clean.equals(value)) parts.add(clean);
        }
        String[] commaParts = value.split(",");
        if (commaParts.length > 1) {
            for (int index = 1; index < commaParts.length; index++) {
                String suffix = commaParts[index].trim().toLowerCase(Locale.ROOT);
                if (suffix.matches("(?:19|20)\\d{2}")
                        || suffix.matches("movie|film|tv show|television show|series|episode|season(?: \\d+)?")
                        || suffix.matches("netflix|prime video|amazon prime video|disney\\+|hulu|paramount\\+|max|peacock|youtube")
                        || suffix.matches("(?:\\d+\\s*)?(?:h|hr|hrs|hour|hours|min|mins|minutes)(?:\\s.*)?")) {
                    StringBuilder titlePrefix = new StringBuilder();
                    for (int prefix = 0; prefix < index; prefix++) {
                        if (prefix > 0) titlePrefix.append(',');
                        titlePrefix.append(commaParts[prefix]);
                    }
                    String clean = titlePrefix.toString().trim();
                    if (!clean.isEmpty() && !clean.equals(value)) parts.add(clean);
                    break;
                }
            }
        }
        return parts;
    }

    private static ParsedLabel parseLabel(String input, String fullValue) {
        String value = input.trim();
        if (value.length() < 2 || value.length() > 160) return null;

        Integer year = null;
        Matcher yearMatcher = YEAR_PATTERN.matcher(fullValue);
        if (yearMatcher.find()) {
            try {
                year = Integer.parseInt(yearMatcher.group(1));
            } catch (NumberFormatException ignored) {
                year = null;
            }
        }

        String mediaType = TileCandidate.TYPE_UNKNOWN;
        String lowerFull = fullValue.toLowerCase(Locale.ROOT);
        if (lowerFull.matches(".*(?:^|[,|•])\\s*(movie|film)(?:\\s*[,|•].*|$)")) {
            mediaType = TileCandidate.TYPE_MOVIE;
        } else if (lowerFull.matches(".*(?:^|[,|•])\\s*(tv show|television show|series|season(?: \\d+)?|episode)(?:\\s*[,|•].*|$)")) {
            mediaType = TileCandidate.TYPE_SERIES;
        }

        value = ACTION_PREFIX.matcher(value).replaceFirst("");
        value = PROVIDER_SUFFIX.matcher(value).replaceFirst("");
        value = YEAR_PATTERN.matcher(value).replaceAll(" ");
        value = TRAILING_MEDIA_LABEL.matcher(value).replaceFirst("");
        value = value.replaceAll("(?i)\\b(uhd|4k|hdr|dolby vision|included with subscription)\\b", " ");
        value = value.replaceAll("\\s+", " ").replaceAll("^[,;:\\-–— ]+|[,;:\\-–— ]+$", "").trim();

        String normalized = normalize(value);
        if (value.length() < 2
                || value.length() > 120
                || BLOCKED_TITLES.contains(normalized)
                || LAUNCHER_PLACEHOLDER.matcher(normalized).matches()) {
            return null;
        }
        if (!value.matches(".*[\\p{L}\\p{N}].*")) return null;

        int score = 20;
        if (input.equals(fullValue)) score += 15;
        if (year != null) score += 10;
        if (!TileCandidate.TYPE_UNKNOWN.equals(mediaType)) score += 10;
        if (value.length() >= 3 && value.length() <= 60) score += 10;
        if (value.split(" ").length <= 10) score += 5;
        if (input.contains(",") && value.equals(input)) score -= 8;
        return new ParsedLabel(value, year, mediaType, score);
    }

    static String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('&', ' ')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String cleanedTitleForTest(String raw) {
        ParsedLabel best = null;
        for (String part : splitLabel(raw)) {
            ParsedLabel parsed = parseLabel(part, raw);
            if (parsed != null && (best == null || parsed.score > best.score)) best = parsed;
        }
        return best == null ? "" : best.title;
    }

    private static boolean containsAny(String value, List<String> needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static final class ParsedLabel {
        final String title;
        final Integer year;
        final String mediaType;
        final int score;

        ParsedLabel(String title, Integer year, String mediaType, int score) {
            this.title = title;
            this.year = year;
            this.mediaType = mediaType;
            this.score = score;
        }
    }
}
