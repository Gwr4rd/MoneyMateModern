package com.codex.moneymate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class SupabaseSync {
    private SupabaseSync() {
    }

    static Session signIn(String projectUrl, String publishableKey, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        return parseSession(request(
                normalizeUrl(projectUrl) + "/auth/v1/token?grant_type=password",
                "POST",
                publishableKey,
                null,
                body.toString(),
                null
        ), false);
    }

    static Session signUp(String projectUrl, String publishableKey, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        return parseSession(request(
                normalizeUrl(projectUrl) + "/auth/v1/signup",
                "POST",
                publishableKey,
                null,
                body.toString(),
                null
        ), true);
    }

    static Session refreshSession(String projectUrl, String publishableKey, String refreshToken) throws Exception {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalArgumentException("La sesion guardada no se puede renovar.");
        }
        JSONObject body = new JSONObject();
        body.put("refresh_token", refreshToken);
        return parseSession(request(
                normalizeUrl(projectUrl) + "/auth/v1/token?grant_type=refresh_token",
                "POST",
                publishableKey,
                null,
                body.toString(),
                null
        ), false);
    }

    static String upload(String projectUrl, String publishableKey, Session session, JSONObject snapshot) throws Exception {
        JSONArray rows = new JSONArray();
        JSONObject row = new JSONObject();
        row.put("user_id", session.userId);
        row.put("data", snapshot);
        rows.put(row);
        JSONObject response = request(
                normalizeUrl(projectUrl) + "/rest/v1/money_snapshots?on_conflict=user_id&select=updated_at",
                "POST",
                publishableKey,
                session.accessToken,
                rows.toString(),
                "resolution=merge-duplicates,return=representation"
        );
        JSONArray resultRows = response.optJSONArray("rows");
        return resultRows == null || resultRows.length() == 0
                ? ""
                : resultRows.getJSONObject(0).optString("updated_at");
    }

    static RemoteSnapshot download(String projectUrl, String publishableKey, Session session) throws Exception {
        JSONObject response = request(
                normalizeUrl(projectUrl) + "/rest/v1/money_snapshots?select=data,updated_at&user_id=eq." + session.userId + "&limit=1",
                "GET",
                publishableKey,
                session.accessToken,
                null,
                null
        );
        JSONArray rows = response.optJSONArray("rows");
        if (rows == null || rows.length() == 0) throw new IllegalArgumentException("Todavia no existe una copia en Supabase.");
        JSONObject row = rows.getJSONObject(0);
        return new RemoteSnapshot(row.getJSONObject("data"), row.optString("updated_at"));
    }

    private static Session parseSession(JSONObject response, boolean allowUnconfirmed) {
        JSONObject user = response.optJSONObject("user");
        String userId = user == null ? "" : user.optString("id");
        String accessToken = response.optString("access_token");
        String refreshToken = response.optString("refresh_token");
        long expiresAt = response.optLong("expires_at", 0);
        if (expiresAt <= 0 && response.optLong("expires_in", 0) > 0) {
            expiresAt = System.currentTimeMillis() / 1000L + response.optLong("expires_in");
        }
        if (userId.isEmpty()) {
            throw new IllegalArgumentException(allowUnconfirmed
                    ? "Supabase no pudo crear la cuenta."
                    : "Supabase no devolvio una sesion valida.");
        }
        if (!allowUnconfirmed && (accessToken.isEmpty() || refreshToken.isEmpty())) {
            throw new IllegalArgumentException("Supabase no devolvio una sesion renovable.");
        }
        return new Session(accessToken, refreshToken, userId, expiresAt);
    }

    private static JSONObject request(String endpoint, String method, String apiKey, String accessToken, String body, String prefer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("apikey", apiKey);
        connection.setRequestProperty("Accept", "application/json");
        if (accessToken != null && !accessToken.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        if (prefer != null) connection.setRequestProperty("Prefer", prefer);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = connection.getResponseCode();
        String text = read(status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String message = text;
            try {
                JSONObject error = new JSONObject(text);
                message = error.optString("msg", error.optString("message", error.optString("error_description", text)));
            } catch (Exception ignored) {
            }
            throw new IllegalArgumentException("Supabase (" + status + "): " + message);
        }
        if (text.trim().isEmpty()) return new JSONObject();
        if (text.trim().startsWith("[")) {
            JSONObject wrapped = new JSONObject();
            wrapped.put("rows", new JSONArray(text));
            return wrapped;
        }
        return new JSONObject(text);
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String normalizeUrl(String value) {
        String url = value == null ? "" : value.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (!url.startsWith("https://")) throw new IllegalArgumentException("La URL de Supabase debe comenzar con https://");
        return url;
    }

    static final class Session {
        final String accessToken;
        final String refreshToken;
        final String userId;
        final long expiresAtEpochSeconds;

        Session(String accessToken, String refreshToken, String userId, long expiresAtEpochSeconds) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        }

        boolean needsRefresh() {
            return expiresAtEpochSeconds <= 0
                    || expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 90L;
        }
    }

    static final class RemoteSnapshot {
        final JSONObject data;
        final String updatedAt;

        RemoteSnapshot(JSONObject data, String updatedAt) {
            this.data = data;
            this.updatedAt = updatedAt;
        }
    }
}
