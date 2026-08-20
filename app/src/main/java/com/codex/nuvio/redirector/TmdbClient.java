package com.codex.nuvio.redirector;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class TmdbClient {
    private static final String API_BASE = "https://api.themoviedb.org/3";
    private static final int CONNECT_TIMEOUT_MS = 7_000;
    private static final int READ_TIMEOUT_MS = 12_000;

    List<Match> search(String credential, TileCandidate candidate) throws IOException, JSONException {
        if (credential == null || credential.trim().isEmpty()) {
            throw new IOException("Add a TMDB API credential in Nuvio Redirect first.");
        }

        String endpoint;
        if (TileCandidate.TYPE_MOVIE.equals(candidate.mediaType)) {
            endpoint = "/search/movie";
        } else if (TileCandidate.TYPE_SERIES.equals(candidate.mediaType)) {
            endpoint = "/search/tv";
        } else {
            endpoint = "/search/multi";
        }

        Uri.Builder uri = Uri.parse(API_BASE + endpoint).buildUpon()
                .appendQueryParameter("query", candidate.title)
                .appendQueryParameter("include_adult", "false")
                .appendQueryParameter("language", languageTag());

        String cleanCredential = credential.trim();
        boolean bearer = looksLikeBearerToken(cleanCredential);
        if (!bearer) {
            uri.appendQueryParameter("api_key", cleanCredential);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(uri.build().toString()).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "NuvioRedirector/0.1");
        if (bearer) {
            connection.setRequestProperty("Authorization", "Bearer " + stripBearerPrefix(cleanCredential));
        }

        int status = connection.getResponseCode();
        InputStream body = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String response = readAll(body);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            String apiMessage = response;
            try {
                apiMessage = new JSONObject(response).optString("status_message", response);
            } catch (JSONException ignored) {
                // Keep the raw body when TMDB did not return JSON.
            }
            throw new IOException("TMDB returned " + status + ": " + apiMessage);
        }

        JSONArray results = new JSONObject(response).optJSONArray("results");
        if (results == null) return Collections.emptyList();

        List<Match> matches = new ArrayList<>();
        for (int index = 0; index < results.length(); index++) {
            JSONObject item = results.optJSONObject(index);
            if (item == null) continue;

            String mediaType = item.optString("media_type", "");
            if (mediaType.isEmpty()) {
                mediaType = endpoint.endsWith("movie") ? "movie" : "tv";
            }
            if (!"movie".equals(mediaType) && !"tv".equals(mediaType)) continue;

            int id = item.optInt("id", 0);
            if (id <= 0) continue;
            String title = "movie".equals(mediaType)
                    ? item.optString("title", "")
                    : item.optString("name", "");
            String originalTitle = "movie".equals(mediaType)
                    ? item.optString("original_title", "")
                    : item.optString("original_name", "");
            String date = "movie".equals(mediaType)
                    ? item.optString("release_date", "")
                    : item.optString("first_air_date", "");
            Integer year = parseYear(date);
            double popularity = item.optDouble("popularity", 0.0);
            double titleSimilarity = Math.max(
                    similarity(candidate.title, title),
                    similarity(candidate.title, originalTitle)
            );

            if (titleSimilarity < 0.48) continue;
            double score = titleSimilarity * 0.78;
            if (TileCandidate.TYPE_MOVIE.equals(candidate.mediaType) && "movie".equals(mediaType)) score += 0.08;
            if (TileCandidate.TYPE_SERIES.equals(candidate.mediaType) && "tv".equals(mediaType)) score += 0.08;
            if (candidate.year != null && year != null) {
                int delta = Math.abs(candidate.year - year);
                if (delta == 0) score += 0.11;
                else if (delta == 1) score += 0.05;
                else score -= Math.min(0.12, delta * 0.025);
            }
            score += Math.min(0.03, Math.log10(Math.max(1.0, popularity)) * 0.01);

            matches.add(new Match(
                    id,
                    title,
                    originalTitle,
                    "movie".equals(mediaType) ? TileCandidate.TYPE_MOVIE : TileCandidate.TYPE_SERIES,
                    year,
                    titleSimilarity,
                    score
            ));
        }

        matches.sort(Comparator.comparingDouble((Match match) -> match.score).reversed());
        return matches.size() > 6 ? new ArrayList<>(matches.subList(0, 6)) : matches;
    }

    static boolean isConfident(List<Match> matches, TileCandidate request) {
        if (matches == null || matches.isEmpty()) return false;
        Match first = matches.get(0);
        boolean exactTitle = first.titleSimilarity >= 0.985;
        boolean yearCompatible = request.year == null || first.year == null
                || Math.abs(request.year - first.year) <= 1;
        boolean typeCompatible = TileCandidate.TYPE_UNKNOWN.equals(request.mediaType)
                || request.mediaType.equals(first.mediaType);
        if (!exactTitle || !yearCompatible || !typeCompatible || first.score < 0.77) return false;

        if (matches.size() == 1) return true;
        Match second = matches.get(1);
        if (second.titleSimilarity < 0.985) return true;
        if (request.year != null && first.year != null && request.year.equals(first.year)
                && (second.year == null || !request.year.equals(second.year))) {
            return true;
        }
        return first.score - second.score >= 0.055;
    }

    private static boolean looksLikeBearerToken(String credential) {
        String clean = stripBearerPrefix(credential);
        return credential.regionMatches(true, 0, "Bearer ", 0, 7)
                || clean.startsWith("eyJ")
                || clean.length() > 64;
    }

    private static String stripBearerPrefix(String credential) {
        return credential.regionMatches(true, 0, "Bearer ", 0, 7)
                ? credential.substring(7).trim()
                : credential;
    }

    private static String languageTag() {
        Locale locale = Locale.getDefault();
        String country = locale.getCountry();
        return country == null || country.isEmpty()
                ? locale.getLanguage()
                : locale.getLanguage() + "-" + country;
    }

    private static Integer parseYear(String date) {
        if (date == null || date.length() < 4) return null;
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static double similarity(String left, String right) {
        String a = TileExtractor.normalize(left);
        String b = TileExtractor.normalize(right);
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;

        Set<String> aTokens = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> bTokens = new HashSet<>(Arrays.asList(b.split(" ")));
        Set<String> intersection = new HashSet<>(aTokens);
        intersection.retainAll(bTokens);
        Set<String> union = new HashSet<>(aTokens);
        union.addAll(bTokens);
        double tokenScore = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

        int distance = levenshtein(a, b);
        double editScore = 1.0 - (double) distance / Math.max(a.length(), b.length());
        return Math.max(0.0, editScore * 0.58 + tokenScore * 0.42);
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        substitution
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    static final class Match implements Serializable {
        private static final long serialVersionUID = 1L;
        final int id;
        final String title;
        final String originalTitle;
        final String mediaType;
        final Integer year;
        final double titleSimilarity;
        final double score;

        Match(
                int id,
                String title,
                String originalTitle,
                String mediaType,
                Integer year,
                double titleSimilarity,
                double score
        ) {
            this.id = id;
            this.title = title;
            this.originalTitle = originalTitle;
            this.mediaType = mediaType;
            this.year = year;
            this.titleSimilarity = titleSimilarity;
            this.score = score;
        }

        String displayLabel() {
            String type = TileCandidate.TYPE_MOVIE.equals(mediaType) ? "Movie" : "Series";
            return title + (year == null ? "" : " (" + year + ")") + " · " + type;
        }
    }
}
