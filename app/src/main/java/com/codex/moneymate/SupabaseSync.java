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
        JSONObject response = request(
                normalizeUrl(projectUrl) + "/auth/v1/token?grant_type=password",
                "POST",
                publishableKey,
                null,
                body.toString(),
                null
        );
        JSONObject user = response.optJSONObject("user");
        String token = response.optString("access_token");
        String userId = user == null ? "" : user.optString("id");
        if (token.isEmpty() || userId.isEmpty()) throw new IllegalArgumentException("Supabase no devolvio una sesion valida.");
        return new Session(token, userId);
    }

    static Session signUp(String projectUrl, String publishableKey, String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        JSONObject response = request(
                normalizeUrl(projectUrl) + "/auth/v1/signup",
                "POST",
                publishableKey,
                null,
                body.toString(),
                null
        );
        JSONObject user = response.optJSONObject("user");
        String token = response.optString("access_token");
        String userId = user == null ? "" : user.optString("id");
        if (userId.isEmpty()) throw new IllegalArgumentException("Supabase no pudo crear la cuenta.");
        return new Session(token, userId);
    }

    static void upload(String projectUrl, String publishableKey, Session session, JSONObject snapshot) throws Exception {
        JSONArray rows = new JSONArray();
        JSONObject row = new JSONObject();
        row.put("user_id", session.userId);
        row.put("data", snapshot);
        rows.put(row);
        request(
                normalizeUrl(projectUrl) + "/rest/v1/money_snapshots?on_conflict=user_id",
                "POST",
                publishableKey,
                session.accessToken,
                rows.toString(),
                "resolution=merge-duplicates,return=minimal"
        );
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
        final String userId;

        Session(String accessToken, String userId) {
            this.accessToken = accessToken;
            this.userId = userId;
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
