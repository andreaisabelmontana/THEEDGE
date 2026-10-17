package com.plia.notebook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONException;
import org.json.JSONObject;

/** Companion transport only: the Android app never contains an OpenAI API key. */
public final class BridgeClient {
    public interface Waiting { void queued(); }
    private final String base, token;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile HttpURLConnection connection;
    public BridgeClient(String base, String token) { this.base = base; this.token = token; }
    public static String normalize(String value) throws Exception {
        URI uri = new URI(value.trim());
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new Exception("Use a server address such as http://192.168.1.10:3000, without a path or password.");
        String result = value.trim(); return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
    }
    public void cancel() { cancelled.set(true); HttpURLConnection current = connection; if (current != null) current.disconnect(); }
    private void check() throws InterruptedIOException { if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Stopped waiting"); }
    public JSONObject status() throws IOException, JSONException { return request("GET", "/api/status", null, null); }
    public JSONObject assist(JSONObject payload, Waiting waiting) throws IOException, JSONException {
        JSONObject result = request("POST", "/api/assist", payload, null);
        if (result.optInt("httpStatus") != 202) return result;
        String id = result.optString("requestId"), receipt = result.optString("receipt");
        if (!id.matches("[a-f0-9-]{36}") || !receipt.matches("[a-f0-9]{64}")) throw new IOException("The companion returned an invalid request receipt.");
        waiting.queued(); long deadline = android.os.SystemClock.elapsedRealtime() + 30 * 60 * 1000L;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            check(); try { Thread.sleep(3000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Stopped waiting"); }
            JSONObject response = request("GET", "/api/dot/requests/" + id, null, receipt);
            if ("completed".equals(response.optString("status"))) return response;
            if (!"waiting".equals(response.optString("status"))) throw new IOException("The companion returned an unknown request state.");
        }
        throw new IOException("This AIM request expired. Send it again when AIM is ready.");
    }
    private JSONObject request(String method, String path, JSONObject body, String receipt) throws IOException, JSONException {
        check(); HttpURLConnection request = (HttpURLConnection)new URL(base + path).openConnection(); connection = request;
        request.setInstanceFollowRedirects(false); request.setConnectTimeout(6000); request.setReadTimeout(65000); request.setRequestMethod(method);
        request.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) request.setRequestProperty("Authorization", "Bearer " + token);
        if (receipt != null) request.setRequestProperty("X-Plia-Receipt", receipt);
        try {
            check();
            if (body != null) { byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8); request.setDoOutput(true); request.setRequestProperty("Content-Type", "application/json"); request.setFixedLengthStreamingMode(bytes.length); try (OutputStream output = request.getOutputStream()) { output.write(bytes); } }
            int status = request.getResponseCode();
            if (status >= 300 && status < 400) throw new IOException("The server redirected the request. Enter its final address in Connection settings.");
            InputStream stream = status >= 400 ? request.getErrorStream() : request.getInputStream();
            if (stream == null) throw new IOException("The companion returned no response (" + status + ").");
            byte[] data;
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) { check(); if (output.size() + count > 256 * 1024) throw new IOException("The server response is too large."); output.write(buffer, 0, count); } data = output.toByteArray(); }
            JSONObject result;
            try { result = new JSONObject(new String(data, StandardCharsets.UTF_8)); } catch (JSONException invalid) { throw new IOException("The companion did not return readable JSON (" + status + ")."); }
            if (status >= 400) throw new IOException(result.optString("error", "The companion could not answer (" + status + ")."));
            result.put("httpStatus", status); check(); return result;
        } finally { request.disconnect(); if (connection == request) connection = null; }
    }
}
