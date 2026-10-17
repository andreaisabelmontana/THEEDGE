package com.plia.notebook;

import android.content.Context;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Plia's own OAuth connection. Fixed official endpoints; no API-key fallback or credential logging. */
public final class ChatGptClient {
    private static final String TOKEN = "https://auth.openai.com/api/accounts/oauth/token";
    private static final String JWKS = "https://auth.openai.com/.well-known/jwks.json";
    private static final String MODELS = "https://api.openai.com/v1/models";
    private static final String RESPONSES = "https://api.openai.com/v1/responses";
    public final SiwcStore store;
    private String incompleteClientId;

    public ChatGptClient(Context context) { store = new SiwcStore(context); }

    /** Request-local diagnostics contain only fixed stages, categories and numeric metadata. */
    public static final class AnswerTrace {
        private volatile AiDiagnostic.Stage stage = AiDiagnostic.Stage.READ_ACCOUNT;
        private volatile int httpStatus;
        private volatile ResponseMetadata.ContentType contentType = ResponseMetadata.ContentType.NOT_OBSERVED;
        private volatile ResponseMetadata.BodyShape bodyShape = ResponseMetadata.BodyShape.NOT_INSPECTED;
        private final long startedAt = System.nanoTime();
        private Consumer<AiDiagnostic> observer;
        void observe(Consumer<AiDiagnostic> value) { observer = value; }
        private long elapsedMs() { return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L); }
        void stage(AiDiagnostic.Stage value) {
            stage = value;
            if (observer != null) observer.accept(metadata(AiDiagnostic.progress(stage, elapsedMs(), httpStatus)));
        }
        void responseStatus(int value) { httpStatus = value >= 100 && value <= 599 ? value : 0; stage(stage); }
        void responseMetadata(ResponseMetadata.ContentType type, ResponseMetadata.BodyShape shape) {
            contentType = type; bodyShape = shape; stage(stage);
        }
        private AiDiagnostic metadata(AiDiagnostic value) { return value.withResponseMetadata(contentType, bodyShape); }
        AiDiagnostic failure(Throwable error) { return metadata(AiDiagnostic.classify(stage, error, elapsedMs(), httpStatus)); }
        AiDiagnostic applying() { stage = AiDiagnostic.Stage.APPLY_REPLY; return metadata(AiDiagnostic.progress(stage, elapsedMs(), httpStatus)); }
        AiDiagnostic completed() { return metadata(AiDiagnostic.completed(elapsedMs(), httpStatus)); }
    }

    public static final class Cancellation {
        private volatile boolean cancelled;
        private HttpURLConnection connection;
        public synchronized void cancel() { cancelled = true; if (connection != null) connection.disconnect(); }
        public boolean cancelled() { return cancelled || Thread.currentThread().isInterrupted(); }
        void check() throws IOException { if (cancelled()) throw new IOException("Request cancelled."); }
        synchronized void attach(HttpURLConnection value) throws IOException {
            if (cancelled()) { value.disconnect(); throw new IOException("Request cancelled."); }
            connection = value;
        }
        synchronized void detach(HttpURLConnection value) { if (connection == value) connection = null; }
    }

    public static final class SignIn {
        private volatile boolean cancelled;
        private volatile AuthDiagnostic.Stage stage = AuthDiagnostic.Stage.READ_STORE;
        private volatile AuthDiagnostic.Code browserFailure;
        private final long startedAt = System.nanoTime();
        private Consumer<AuthDiagnostic> diagnostics;
        private ServerSocket listener;
        final Cancellation transport = new Cancellation();
        void observe(Consumer<AuthDiagnostic> listener) { diagnostics = listener; }
        void stage(AuthDiagnostic.Stage value) {
            stage = value;
            if (diagnostics != null) diagnostics.accept(AuthDiagnostic.progress(value, elapsedMs()));
        }
        private long elapsedMs() { return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L); }
        void browserUnavailable() { browserFailure(AuthDiagnostic.Code.BROWSER_UNAVAILABLE); }
        void browserFailure(AuthDiagnostic.Code code) {
            browserFailure = code == AuthDiagnostic.Code.BROWSER_DISABLED || code == AuthDiagnostic.Code.BROWSER_BLOCKED
                    || code == AuthDiagnostic.Code.INVALID_INPUT ? code : AuthDiagnostic.Code.BROWSER_UNAVAILABLE;
            cancel();
        }
        AuthDiagnostic failure(Throwable error) {
            if (browserFailure != null) return AuthDiagnostic.classify(AuthDiagnostic.Stage.OPEN_BROWSER,
                    new AuthDiagnostic.Failure(browserFailure), elapsedMs());
            return AuthDiagnostic.classify(stage, cancelled()
                    ? new AuthDiagnostic.Failure(AuthDiagnostic.Code.CANCELLED) : error, elapsedMs());
        }
        AuthDiagnostic completed() { return AuthDiagnostic.completed(elapsedMs()); }
        synchronized void attach(ServerSocket socket) throws IOException {
            if (cancelled) { socket.close(); throw new IOException("Sign-in cancelled."); }
            listener = socket;
        }
        public synchronized void cancel() {
            cancelled = true; transport.cancel();
            try { if (listener != null) listener.close(); } catch (IOException ignored) { }
        }
        boolean cancelled() { return cancelled || Thread.currentThread().isInterrupted(); }
    }

    /** Caller opens the returned authorization URL in the system browser, never a WebView. */
    public SiwcStore.Snapshot signIn(SiwcStore.Account selected, SignIn control, Consumer<String> openBrowser) throws Exception {
        control.stage(AuthDiagnostic.Stage.READ_STORE);
        SiwcStore.Snapshot before = store.read();
        String clientId = selected == null ? incompleteClientId : selected.clientId;
        String subject = selected == null ? null : selected.verifiedSub;
        control.stage(AuthDiagnostic.Stage.BIND_CALLBACK);
        try (ServerSocket listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))) {
            control.attach(listener); listener.setSoTimeout(1000);
            control.stage(AuthDiagnostic.Stage.PREPARE_REQUEST);
            SiwcProtocol.Attempt attempt = selected == null && clientId != null
                    ? SiwcProtocol.retryRegistration(store.hostId(), listener.getLocalPort(), clientId, now())
                    : SiwcProtocol.begin(store.hostId(), listener.getLocalPort(), clientId, subject, now());
            // The optional ID-token hint is omitted so no saved token enters a browser URL/history.
            control.stage(AuthDiagnostic.Stage.OPEN_BROWSER);
            openBrowser.accept(SiwcProtocol.authorizationUrl(attempt, null, null));
            control.stage(AuthDiagnostic.Stage.WAIT_CALLBACK);
            long deadline = System.nanoTime() + 300_000_000_000L;
            SiwcProtocol.Callback callback = null;
            int connectionErrors = 0;
            while (callback == null && !control.cancelled() && System.nanoTime() < deadline) {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(3000);
                    String target = callbackTarget(socket.getInputStream(), listener.getLocalPort());
                    if (target == null) { safeCallbackReply(socket, false); continue; }
                    try {
                        callback = SiwcProtocol.parseCallback("http://127.0.0.1:" + listener.getLocalPort() + target, attempt, now());
                    } catch (SiwcProtocol.ProtocolException invalid) {
                        safeCallbackReply(socket, false);
                        if (invalid.terminal) throw invalid;
                        // Invalid local callbacks never overwrite the pending authorization attempt.
                        continue;
                    }
                    safeCallbackReply(socket, true); break;
                } catch (SocketTimeoutException ignored) { }
                catch (IOException disconnected) {
                    if (control.cancelled()) throw new IOException("Sign-in cancelled.");
                    if (++connectionErrors >= 20) throw new IOException("The local sign-in callback could not complete. Start sign-in again.");
                }
            }
            if (control.cancelled()) throw new IOException("Sign-in cancelled.");
            if (callback == null) throw new AuthDiagnostic.Failure(AuthDiagnostic.Code.CALLBACK_TIMEOUT);
            incompleteClientId = callback.clientId;
            control.stage(AuthDiagnostic.Stage.TOKEN_EXCHANGE);
            JsonObject tokens = json(request(TOKEN, "POST", SiwcProtocol.codeExchangeForm(attempt, callback), null, control.transport));
            String idToken = required(tokens, "id_token");
            control.stage(AuthDiagnostic.Stage.FETCH_KEYS);
            String jwks = request(JWKS, "GET", null, null, control.transport);
            control.stage(AuthDiagnostic.Stage.VERIFY_IDENTITY);
            SiwcProtocol.Identity identity = SiwcProtocol.validateIdentity(idToken, jwks, attempt, callback.clientId, now());
            control.stage(AuthDiagnostic.Stage.VALIDATE_GRANT);
            List<String> scopes = scopes(required(tokens, "scope"));
            requirePermission(scopes);
            requireBearer(tokens);
            SiwcStore.Account account = new SiwcStore.Account(callback.clientId, identity.subject,
                    required(tokens, "access_token"), required(tokens, "refresh_token"), idToken, attempt.nonce,
                    expiry(tokens), scopes, selected == null ? "" : selected.selectedModel, accountLabel(identity));
            if (control.cancelled()) throw new IOException("Sign-in cancelled.");
            control.stage(AuthDiagnostic.Stage.SAVE_ACCOUNT);
            SiwcStore.Snapshot saved = store.saveSignIn(account, account.clientId, account.verifiedSub, before.generation);
            incompleteClientId = null;
            return saved;
        }
    }

    public List<SiwcInference.ModelInfo> models(String accountId, Cancellation cancellation) throws Exception {
        cancellation.check();
        SiwcStore.Account account = authorized(accountId, null);
        cancellation.check();
        return SiwcInference.parseCatalog(request(MODELS, "GET", null, account.accessToken, cancellation));
    }

    /** UI must obtain explicit disclosure-based consent for this session before calling this method. */
    public String answer(String accountId, SiwcInference.ModelInfo model, byte[] png, String mode,
                         String studyContext, List<String> history, boolean sessionConsent,
                         Cancellation cancellation) throws Exception {
        return answer(accountId, model, png, mode, studyContext, history, sessionConsent, cancellation, new AnswerTrace());
    }

    public String answer(String accountId, SiwcInference.ModelInfo model, byte[] png, String mode,
                         String studyContext, List<String> history, boolean sessionConsent,
                         Cancellation cancellation, AnswerTrace trace) throws Exception {
        trace.stage(AiDiagnostic.Stage.READ_ACCOUNT);
        if (!sessionConsent) throw new IOException("Replies are paused until this session is enabled.");
        return answerAuthorized(accountId, model, png, mode, studyContext, history, cancellation, trace);
    }

    /** One user-approved request under the account's current usage settings; does not enable automatic replies. */
    public String answerOnce(String accountId, SiwcInference.ModelInfo model, byte[] png, String mode,
                             String studyContext, List<String> history,
                             Cancellation cancellation, AnswerTrace trace) throws Exception {
        trace.stage(AiDiagnostic.Stage.READ_ACCOUNT);
        return answerAuthorized(accountId, model, png, mode, studyContext, history, cancellation, trace);
    }

    private String answerAuthorized(String accountId, SiwcInference.ModelInfo model, byte[] png, String mode,
                                    String studyContext, List<String> history,
                                    Cancellation cancellation, AnswerTrace trace) throws Exception {
        cancellation.check();
        SiwcStore.Account account = authorized(accountId, trace);
        cancellation.check();
        if (!account.selectedModel.equals(model.slug)) throw new IOException("Select the current account's handwriting model again.");
        trace.stage(AiDiagnostic.Stage.BUILD_REQUEST);
        String payload = SiwcInference.buildPayload(model, png, mode, studyContext, history);
        trace.stage(AiDiagnostic.Stage.CONNECT);
        HttpURLConnection connection = open(RESPONSES, "POST", account.accessToken, cancellation);
        try {
            trace.stage(AiDiagnostic.Stage.SEND_REQUEST);
            send(connection, payload, "application/json; charset=utf-8", cancellation);
            cancellation.check();
            trace.stage(AiDiagnostic.Stage.READ_RESPONSE);
            int status = connection.getResponseCode();
            return readAnswerResponse(connection, status, cancellation, trace);
        } finally { cancellation.detach(connection); connection.disconnect(); }
    }

    /** Diagnose one response in place. Only closed categories leave this method, never header/body strings. */
    static String readAnswerResponse(HttpURLConnection connection, int status, Cancellation cancellation, AnswerTrace trace) throws IOException {
        trace.responseStatus(status);
        String declared = connection.getHeaderField("Content-Type");
        ResponseMetadata.ContentType type = ResponseMetadata.classifyContentType(declared);
        trace.responseMetadata(type, ResponseMetadata.BodyShape.NOT_INSPECTED);
        cancellation.check();
        InputStream source;
        try { source = status >= 400 ? connection.getErrorStream() : connection.getInputStream(); }
        catch (IOException unreadable) {
            trace.responseMetadata(type, ResponseMetadata.BodyShape.UNREADABLE);
            cancellation.check();
            if (status != 200) throw new AiDiagnostic.HttpFailure(status);
            if (type != ResponseMetadata.ContentType.MISSING) requireEventStream(declared);
            throw unreadable;
        }
        try (PushbackInputStream input = source == null ? null : new PushbackInputStream(source, 512)) {
            try { inspectResponsePrefix(input, type, cancellation, trace); }
            catch (IOException unreadable) {
                trace.responseMetadata(type, ResponseMetadata.BodyShape.UNREADABLE);
                cancellation.check();
                if (status != 200) throw new AiDiagnostic.HttpFailure(status);
                if (type != ResponseMetadata.ContentType.MISSING) requireEventStream(declared);
                throw unreadable;
            }
            cancellation.check();
            if (status != 200) throw readHttpFailure(input, status);
            // Some responses expose no Content-Type. This endpoint-specific compatibility path
            // must verify strict SSE framing and the same terminal response.completed schema.
            // The short diagnostic prefix is deliberately not an acceptance test.
            if (type != ResponseMetadata.ContentType.MISSING) requireEventStream(declared);
            trace.stage(AiDiagnostic.Stage.PARSE_RESPONSE);
            return type == ResponseMetadata.ContentType.MISSING
                    ? SiwcInference.readCompletedWithoutContentType(input, cancellation::cancelled)
                    : SiwcInference.readCompleted(input, cancellation::cancelled);
        }
    }

    /** One short read only: never fill a buffer, wait for EOF, or consume bytes needed by the parser. */
    private static void inspectResponsePrefix(PushbackInputStream input, ResponseMetadata.ContentType type,
                                               Cancellation cancellation, AnswerTrace trace) throws IOException {
        cancellation.check();
        if (input == null) { trace.responseMetadata(type, ResponseMetadata.BodyShape.EMPTY); return; }
        byte[] prefix = new byte[512];
        int count = -1;
        try {
            count = input.read(prefix, 0, prefix.length);
            cancellation.check();
            ResponseMetadata.BodyShape shape = count < 0 ? ResponseMetadata.BodyShape.EMPTY
                    : count == 0 ? ResponseMetadata.BodyShape.UNREADABLE : ResponseMetadata.classifyPrefix(prefix, count);
            trace.responseMetadata(type, shape);
        } finally {
            if (count > 0) input.unread(prefix, 0, count);
            Arrays.fill(prefix, (byte)0);
        }
    }

    /** Validate the declared base media type; never infer a stream from response content. */
    static void requireEventStream(String contentType) throws AiDiagnostic.InvalidResponse {
        if (contentType == null) throw new AiDiagnostic.InvalidResponse();
        int parameters = contentType.indexOf(';');
        String mediaType = (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim();
        if (!"text/event-stream".equalsIgnoreCase(mediaType)) throw new AiDiagnostic.InvalidResponse();
    }

    /** Preserve the known HTTP status even if its error body is unreadable or too large. */
    static IOException readHttpFailure(HttpURLConnection connection, int status) {
        return readHttpFailure(connection.getErrorStream(), status);
    }

    private static IOException readHttpFailure(InputStream input, int status) {
        try { return SiwcInference.httpFailure(status, read(input, 131072)); }
        catch (IOException | RuntimeException unreadable) { return new AiDiagnostic.HttpFailure(status); }
    }

    private synchronized SiwcStore.Account authorized(String accountId, AnswerTrace trace) throws Exception {
        SiwcStore.Snapshot before = store.read();
        SiwcStore.Account account = before.selected();
        if (account == null || !account.id.equals(accountId)) throw new IOException("Choose your ChatGPT account again.");
        requirePermission(account.scopes);
        if (account.expiresAtEpochSeconds > now() + 60) return account;
        if (account.refreshToken.isEmpty()) throw new IOException("Sign in with ChatGPT again.");
        if (trace != null) trace.stage(AiDiagnostic.Stage.REFRESH_ACCOUNT);
        // Finish rotation even if a pen event cancels the inference that requested it.
        Cancellation refresh = new Cancellation();
        JsonObject tokens = json(request(TOKEN, "POST", SiwcProtocol.refreshForm(account.clientId, account.refreshToken), null, refresh));
        requireBearer(tokens);
        List<String> scopes = tokens.has("scope") ? scopes(required(tokens, "scope")) : account.scopes;
        requirePermission(scopes);
        String idToken = optional(tokens, "id_token", account.idToken);
        if (tokens.has("id_token")) {
            SiwcProtocol.validateRefreshIdentity(idToken, request(JWKS, "GET", null, null, refresh),
                    account.clientId, account.verifiedSub, account.oidcNonce, now());
        }
        SiwcStore.Account replacement = new SiwcStore.Account(account.clientId, account.verifiedSub,
                required(tokens, "access_token"), optional(tokens, "refresh_token", account.refreshToken),
                idToken, account.oidcNonce, expiry(tokens), scopes, account.selectedModel, account.displayName);
        return store.replaceTokens(replacement, account.clientId, account.verifiedSub, before.generation).selected();
    }

    private static void requirePermission(List<String> scopes) throws IOException {
        if (!scopes.contains("chatgpt.tokens.use.direct") || !scopes.contains("resource.invoke"))
              throw new AuthDiagnostic.Failure(AuthDiagnostic.Code.PERMISSION_MISSING);
    }
    private static String accountLabel(SiwcProtocol.Identity identity) {
        String value = identity.emailVerified && identity.email != null ? identity.email : identity.name;
        if (value == null) return "";
        value = value.replaceAll("\\p{Cntrl}", " ").trim();
        return value.substring(0, Math.min(160, value.length()));
    }
    private static List<String> scopes(String value) { return new ArrayList<>(Arrays.asList(value.trim().split("\\s+"))); }
    private static long now() { return System.currentTimeMillis() / 1000; }
    private static long expiry(JsonObject tokens) throws IOException {
        try {
            long seconds = tokens.get("expires_in").getAsLong();
            if (seconds <= 0 || seconds > 31_536_000) throw new IllegalArgumentException();
            return Math.addExact(now(), seconds);
        } catch (Exception invalid) { throw new AuthDiagnostic.Failure(AuthDiagnostic.Code.INVALID_RESPONSE); }
    }
    private static void requireBearer(JsonObject tokens) throws IOException {
        if (!"Bearer".equalsIgnoreCase(required(tokens, "token_type"))) throw new AuthDiagnostic.Failure(AuthDiagnostic.Code.INVALID_RESPONSE);
    }
    private static JsonObject json(String text) throws IOException {
        try { return SiwcProtocol.parseObject(text); }
        catch (SiwcProtocol.ProtocolException invalid) { throw new AuthDiagnostic.Failure(AuthDiagnostic.Code.INVALID_RESPONSE); }
    }
    private static String optional(JsonObject value, String field, String fallback) throws IOException {
        return value.has(field) ? required(value, field) : fallback;
    }
    private static String required(JsonObject value, String field) throws IOException {
        try {
            JsonElement item = value.get(field);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
            String result = item.getAsString();
            if (result.isEmpty() || result.length() > 131072) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException invalid) { throw new AuthDiagnostic.Failure(AuthDiagnostic.Code.INVALID_RESPONSE); }
    }

    private static HttpURLConnection open(String endpoint, String method, String accessToken, Cancellation cancellation) throws IOException {
        if (!(endpoint.equals(TOKEN) || endpoint.equals(JWKS) || endpoint.equals(MODELS) || endpoint.equals(RESPONSES))) throw new IOException("Unsupported service endpoint.");
        HttpURLConnection connection = (HttpURLConnection)new URL(endpoint).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000); connection.setReadTimeout(90000);
        connection.setRequestMethod(method); connection.setUseCaches(false);
        connection.setRequestProperty("Accept", endpoint.equals(RESPONSES) ? "text/event-stream" : "application/json");
        if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        cancellation.attach(connection);
        return connection;
    }
    private static void send(HttpURLConnection connection, String body, String contentType, Cancellation cancellation) throws IOException {
        cancellation.check();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
        connection.setRequestProperty("Content-Type", contentType);
        cancellation.check();
        try (OutputStream output = connection.getOutputStream()) {
            for (int offset = 0; offset < bytes.length; offset += 8192) {
                cancellation.check();
                output.write(bytes, offset, Math.min(8192, bytes.length - offset));
            }
        }
    }
    private static String request(String endpoint, String method, String body, String token, Cancellation cancellation) throws IOException {
        HttpURLConnection connection = open(endpoint, method, token, cancellation);
        try {
            if (body != null) send(connection, body, "application/x-www-form-urlencoded; charset=utf-8", cancellation);
            cancellation.check();
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                if (endpoint.equals(MODELS)) throw SiwcInference.httpFailure(status, read(connection.getErrorStream(), 131072));
                throw new AuthDiagnostic.HttpFailure(status);
            }
            cancellation.check();
            return read(connection.getInputStream(), 2 * 1024 * 1024);
        } finally { cancellation.detach(connection); connection.disconnect(); }
    }
    private static String read(InputStream input, int limit) throws IOException {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int length;
            while ((length = stream.read(buffer)) != -1) {
                if (bytes.size() + length > limit) throw new IOException("The service returned too much data.");
                bytes.write(buffer, 0, length);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Minimal local HTTP request reader, deliberately rejecting bodies, absolute targets and duplicates. */
    static String callbackTarget(InputStream input, int port) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int matched = 0;
        while (bytes.size() < 16384) {
            int next = input.read(); if (next < 0) return null; bytes.write(next);
            if (next == (matched % 2 == 0 ? '\r' : '\n')) matched++; else matched = next == '\r' ? 1 : 0;
            if (matched == 4) break;
        }
        String headers = new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
        if (!headers.endsWith("\r\n\r\n")) return null;
        String[] lines = headers.split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length != 3 || !request[0].equals("GET") || !request[1].startsWith("/auth/callback?") || !request[2].equals("HTTP/1.1")) return null;
        int hosts = 0;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':'); if (colon < 1) return null;
            String key = lines[i].substring(0, colon).trim();
            String value = lines[i].substring(colon + 1).trim();
            if (key.equalsIgnoreCase("Host")) { if (!value.equals("127.0.0.1:" + port)) return null; hosts++; }
            if (key.equalsIgnoreCase("Content-Length") || key.equalsIgnoreCase("Transfer-Encoding")) return null;
        }
        return hosts == 1 ? request[1] : null;
    }
    private static void safeCallbackReply(Socket socket, boolean accepted) {
        try { callbackReply(socket, accepted); } catch (IOException disconnected) { }
    }
    private static void callbackReply(Socket socket, boolean accepted) throws IOException {
        String text = accepted ? "Plia received the sign-in response. Return to Plia to see whether verification succeeded."
                : "This sign-in response was not accepted. Return to Plia or complete the original sign-in tab.";
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + (accepted ? "200 OK" : "400 Bad Request")
                + "\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Security-Policy: default-src 'none'\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\nContent-Length: " + body.length + "\r\n\r\n";
        socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(body); socket.getOutputStream().flush();
    }
}
