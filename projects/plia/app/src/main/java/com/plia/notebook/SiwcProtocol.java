package com.plia.notebook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Public-client SIWC protocol only: no I/O, logging, credentials, or network calls.
 * Controller must consume each attempt once and obtain JWKS only from JWKS_URI via HTTPS.
 * Sources: developers.openai.com/siwc/token-sharing-open-source/sign-in,
 * developers.openai.com/siwc/website, auth.openai.com/.well-known/openid-configuration.
 */
public final class SiwcProtocol {
    public static final String ISSUER = "https://auth.openai.com";
    public static final String AUTHORIZATION_ENDPOINT = ISSUER + "/api/accounts/authorize";
    public static final String TOKEN_ENDPOINT = ISSUER + "/api/accounts/oauth/token";
    public static final String JWKS_URI = ISSUER + "/.well-known/jwks.json";
    public static final String RESOURCE = "https://api.openai.com/v1";
    public static final String BOOTSTRAP_CLIENT_ID = "dynamic_agent_client";
    public static final String SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
    private static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private static final long CLOCK_SKEW = 5, ATTEMPT_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();
    private SiwcProtocol() {}

    public enum Failure { INVALID_INPUT, CALLBACK_REJECTED, OAUTH_ERROR, ATTEMPT_EXPIRED, UNKNOWN_KEY, TOKEN_REJECTED }
    public static final class ProtocolException extends Exception {
        public final Failure failure;
        public final boolean terminal;
        private ProtocolException(Failure failure, String message) { super(message); this.failure = failure; terminal = failure == Failure.OAUTH_ERROR; }
    }

    /** Sensitive transient fields: keep in memory, do not log or persist this object. */
    public static final class Attempt {
        public final String hostId, clientId, expectedSubject, redirectUri, state, nonce, verifier, challenge;
        public final long createdAt, expiresAt;
        public final boolean bootstrap;
        private Attempt(String hostId, int port, String clientId, String expectedSubject, long now) throws ProtocolException {
            this.hostId = hostId; this.clientId = clientId; this.expectedSubject = expectedSubject;
            bootstrap = BOOTSTRAP_CLIENT_ID.equals(clientId);
            redirectUri = "http://127.0.0.1:" + port + "/auth/callback";
            state = randomValue(); nonce = randomValue(); verifier = randomValue();
            challenge = pkceChallenge(verifier);
            createdAt = now; expiresAt = now + ATTEMPT_SECONDS;
        }
    }

    public static final class Callback {
        public final String code, clientId;
        private final Attempt attempt;
        private Callback(String code, String clientId, Attempt attempt) { this.code = code; this.clientId = clientId; this.attempt = attempt; }
    }

    /** Only constructed after cryptographic and semantic validation. */
    public static final class Identity {
        public final String issuer, clientId, subject, email, name, nonce;
        public final boolean emailVerified;
        public final long expiresAt, issuedAt;
        private Identity(String clientId, String subject, String email, String name, String nonce, boolean emailVerified, long expiresAt, long issuedAt) {
            issuer = ISSUER; this.clientId = clientId; this.subject = subject;
            this.email = email; this.name = name; this.nonce = nonce; this.emailVerified = emailVerified;
            this.expiresAt = expiresAt; this.issuedAt = issuedAt;
        }
    }

    /** Preserve the installation UUID while migrating the pre-0.1.2 bare representation. */
    public static String hostIdForInstallation(String stored) throws ProtocolException {
        if (stored != null && stored.matches(UUID_V4)) return "urn:uuid:" + stored;
        requireHostId(stored);
        return stored;
    }

    private static void requireHostId(String hostId) throws ProtocolException {
        // Plia uses the documented UUIDv4 URI option, generated once per installation.
        if (hostId == null || !hostId.matches("urn:uuid:" + UUID_V4))
            throw fail(Failure.INVALID_INPUT, "Invalid installation host identity.");
    }

    public static Attempt begin(String hostId, int port, String savedClientId, String expectedSubject, long nowSeconds) throws ProtocolException {
        requireHostId(hostId);
        if (port < 1 || port > 65535 || nowSeconds < 0 || nowSeconds > Long.MAX_VALUE - ATTEMPT_SECONDS) throw fail(Failure.INVALID_INPUT, "Invalid sign-in attempt.");
        if (savedClientId == null) {
            if (expectedSubject != null) throw fail(Failure.INVALID_INPUT, "A new registration cannot select an existing identity.");
            savedClientId = BOOTSTRAP_CLIENT_ID;
        } else { issuedClient(savedClientId); text(expectedSubject, 512, false); }
        return new Attempt(hostId, port, savedClientId, expectedSubject, nowSeconds);
    }

    /** Retry only an issued ID retained in memory from this controller's validated callback. */
    public static Attempt retryRegistration(String hostId, int port, String issuedClientId, long nowSeconds) throws ProtocolException {
        requireHostId(hostId); issuedClient(issuedClientId);
        if (port < 1 || port > 65535 || nowSeconds < 0 || nowSeconds > Long.MAX_VALUE - ATTEMPT_SECONDS) throw fail(Failure.INVALID_INPUT, "Invalid sign-in attempt.");
        return new Attempt(hostId, port, issuedClientId, null, nowSeconds);
    }

    public static String authorizationUrl(Attempt attempt, String idTokenHint, String loginHint) throws ProtocolException {
        requireAttempt(attempt);
        LinkedHashMap<String, String> p = new LinkedHashMap<>();
        p.put("client_id", attempt.clientId);
        if (attempt.bootstrap) {
            if (idTokenHint != null || loginHint != null) throw fail(Failure.INVALID_INPUT, "New registration cannot use saved account hints.");
            p.put("agent_name_hint", "Plia");
        } else {
            if (idTokenHint != null) { text(idTokenHint, 32768, false); p.put("id_token_hint", idTokenHint); }
            if (loginHint != null) { text(loginHint, 320, false); p.put("login_hint", loginHint); }
        }
        p.put("ext_agent_host_id", attempt.hostId);
        p.put("response_type", "code"); p.put("redirect_uri", attempt.redirectUri);
        p.put("scope", SCOPES); p.put("resource", RESOURCE);
        p.put("state", attempt.state); p.put("nonce", attempt.nonce);
        p.put("code_challenge_method", "S256"); p.put("code_challenge", attempt.challenge);
        return AUTHORIZATION_ENDPOINT + "?" + form(p);
    }

    public static Callback parseCallback(String absoluteUrl, Attempt attempt, long nowSeconds) throws ProtocolException {
        fresh(attempt, nowSeconds);
        text(absoluteUrl, 16384, false);
        URI uri;
        try { uri = new URI(absoluteUrl); } catch (Exception invalid) { throw fail(Failure.CALLBACK_REJECTED, "Invalid callback URI."); }
        String expectedAuthority = URI.create(attempt.redirectUri).getRawAuthority();
        if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || !expectedAuthority.equals(uri.getRawAuthority())
                || !"/auth/callback".equals(uri.getRawPath()) || uri.getRawUserInfo() != null || uri.getRawFragment() != null)
            throw fail(Failure.CALLBACK_REJECTED, "Callback does not match this attempt.");
        Map<String, String> p = query(uri.getRawQuery());
        if (!constantEquals(attempt.state, p.get("state"))) throw fail(Failure.CALLBACK_REJECTED, "Sign-in state did not match.");
        if (p.containsKey("error")) throw fail(Failure.OAUTH_ERROR, "ChatGPT sign-in was not completed.");
        String code = p.get("code"); text(code, 8192, false);
        String client = p.get("client_id");
        if (attempt.bootstrap) issuedClient(client);
        else if (client == null) client = attempt.clientId;
        else if (!constantEquals(client, attempt.clientId)) throw fail(Failure.CALLBACK_REJECTED, "The selected registration changed.");
        // RFC 9207 issuer response parameter, when supplied, must identify this provider.
        if (p.containsKey("iss") && !ISSUER.equals(p.get("iss"))) throw fail(Failure.CALLBACK_REJECTED, "Callback issuer did not match.");
        return new Callback(code, client, attempt);
    }

    public static String codeExchangeForm(Attempt attempt, Callback callback) throws ProtocolException {
        requireAttempt(attempt);
        if (callback == null || callback.attempt != attempt) throw fail(Failure.INVALID_INPUT, "Callback belongs to a different attempt.");
        issuedClient(callback.clientId);
        LinkedHashMap<String, String> p = new LinkedHashMap<>();
        p.put("grant_type", "authorization_code"); p.put("client_id", callback.clientId); p.put("code", callback.code);
        p.put("code_verifier", attempt.verifier); p.put("redirect_uri", attempt.redirectUri); p.put("resource", RESOURCE);
        return form(p);
    }

    public static String refreshForm(String clientId, String refreshToken) throws ProtocolException {
        issuedClient(clientId); text(refreshToken, 32768, false);
        LinkedHashMap<String, String> p = new LinkedHashMap<>();
        p.put("grant_type", "refresh_token"); p.put("client_id", clientId); p.put("refresh_token", refreshToken); p.put("resource", RESOURCE);
        return form(p);
    }

    public static Identity validateIdentity(String jwt, String jwksJson, Attempt attempt, String issuedClientId, long nowSeconds) throws ProtocolException {
        fresh(attempt, nowSeconds); issuedClient(issuedClientId);
        if (!attempt.bootstrap && !constantEquals(attempt.clientId, issuedClientId)) throw fail(Failure.TOKEN_REJECTED, "The selected registration changed.");
        return verify(jwt, jwksJson, issuedClientId, attempt.expectedSubject, attempt.nonce, true, attempt.createdAt, nowSeconds);
    }

    public static Identity validateRefreshIdentity(String jwt, String jwksJson, String clientId, String expectedSubject, long nowSeconds) throws ProtocolException {
        return validateRefreshIdentity(jwt, jwksJson, clientId, expectedSubject, null, nowSeconds);
    }

    /** OIDC Core 12.2: refresh nonce may be absent; if present it must match the original. */
    public static Identity validateRefreshIdentity(String jwt, String jwksJson, String clientId, String expectedSubject, String originalNonce, long nowSeconds) throws ProtocolException {
        issuedClient(clientId); text(expectedSubject, 512, false);
        if (originalNonce != null) text(originalNonce, 512, false);
        return verify(jwt, jwksJson, clientId, expectedSubject, originalNonce, false, 0, nowSeconds);
    }

    private static Identity verify(String jwt, String jwksJson, String clientId, String expectedSubject, String expectedNonce, boolean requireNonce, long minimumIssuedAt, long now) throws ProtocolException {
        text(jwt, 32768, false);
        if (now < 0 || now > Long.MAX_VALUE - CLOCK_SKEW) throw fail(Failure.INVALID_INPUT, "Invalid validation time.");
        String[] parts = jwt.split("\\.", -1);
        if (parts.length != 3) throw fail(Failure.TOKEN_REJECTED, "Malformed identity token.");
        JsonObject header = object(utf8(base64(parts[0], 4096)), 4096);
        if (!"RS256".equals(string(header, "alg", 16, true))) throw fail(Failure.TOKEN_REJECTED, "Unsupported identity signature.");
        String kid = string(header, "kid", 256, true);
        if (header.has("crit") || header.has("b64") || header.has("jku") || header.has("jwk") || header.has("x5u") || header.has("x5c"))
            throw fail(Failure.TOKEN_REJECTED, "Unsupported identity header.");
        String type = string(header, "typ", 32, false);
        if (type != null && !"JWT".equals(type)) throw fail(Failure.TOKEN_REJECTED, "Unexpected identity token type.");
        RSAPublicKey key = key(jwksJson, kid);
        byte[] signatureBytes = base64(parts[2], 1024);
        if (signatureBytes.length != (key.getModulus().bitLength() + 7) / 8) throw fail(Failure.TOKEN_REJECTED, "Invalid identity signature.");
        // Reject malformed base64url payload before verification, but do not interpret claims yet.
        byte[] payload = base64(parts[1], 24576);
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key); signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(signatureBytes)) throw fail(Failure.TOKEN_REJECTED, "Invalid identity signature.");
        } catch (GeneralSecurityException invalid) { throw fail(Failure.TOKEN_REJECTED, "Identity signature could not be verified."); }
        JsonObject claims = object(utf8(payload), 24576);
        if (!ISSUER.equals(string(claims, "iss", 256, true))) throw fail(Failure.TOKEN_REJECTED, "Identity issuer did not match.");
        audience(claims, clientId);
        String subject = string(claims, "sub", 512, true);
        if (expectedSubject != null && !constantEquals(expectedSubject, subject)) throw fail(Failure.TOKEN_REJECTED, "The selected account changed.");
        long issued = integer(claims, "iat", true), expires = integer(claims, "exp", true);
        if (issued < 0 || expires <= issued || issued > now + CLOCK_SKEW || issued < minimumIssuedAt - CLOCK_SKEW || expires <= now - CLOCK_SKEW)
            throw fail(Failure.TOKEN_REJECTED, "Identity token is outside its validity period.");
        if (claims.has("nbf")) {
            long notBefore = integer(claims, "nbf", true);
            if (notBefore < 0 || notBefore > now + CLOCK_SKEW || notBefore >= expires) throw fail(Failure.TOKEN_REJECTED, "Identity token is not yet valid.");
        }
        String nonce = string(claims, "nonce", 512, requireNonce);
        if ((requireNonce || nonce != null) && !constantEquals(expectedNonce, nonce)) throw fail(Failure.TOKEN_REJECTED, "Identity nonce did not match.");
        String email = string(claims, "email", 320, false), name = string(claims, "name", 512, false);
        boolean emailVerified = false;
        if (claims.has("email_verified")) {
            JsonElement value = claims.get("email_verified");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw fail(Failure.TOKEN_REJECTED, "Invalid email verification claim.");
            emailVerified = value.getAsBoolean();
        }
        return new Identity(clientId, subject, email, name, nonce, emailVerified, expires, issued);
    }

    private static void audience(JsonObject claims, String client) throws ProtocolException {
        JsonElement value = claims.get("aud");
        boolean matched = false; int count = 0;
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) { matched = client.equals(value.getAsString()); count = 1; }
        else if (value != null && value.isJsonArray() && value.getAsJsonArray().size() <= 16) {
            Set<String> seen = new HashSet<>();
            for (JsonElement item : value.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw fail(Failure.TOKEN_REJECTED, "Invalid identity audience.");
                String itemValue = item.getAsString(); text(itemValue, 256, false);
                if (!seen.add(itemValue)) throw fail(Failure.TOKEN_REJECTED, "Duplicate identity audience.");
                matched |= client.equals(itemValue); count++;
            }
        }
        if (!matched) throw fail(Failure.TOKEN_REJECTED, "Identity audience did not match.");
        String party = string(claims, "azp", 256, count > 1);
        if (party != null && !client.equals(party)) throw fail(Failure.TOKEN_REJECTED, "Identity authorized party did not match.");
    }

    private static RSAPublicKey key(String jwksJson, String kid) throws ProtocolException {
        JsonObject jwks = object(jwksJson, 262144);
        JsonElement keys = jwks.get("keys");
        if (keys == null || !keys.isJsonArray() || keys.getAsJsonArray().size() > 64) throw fail(Failure.TOKEN_REJECTED, "Invalid signing-key document.");
        JsonObject chosen = null;
        for (JsonElement value : keys.getAsJsonArray()) {
            if (!value.isJsonObject()) throw fail(Failure.TOKEN_REJECTED, "Invalid signing key.");
            JsonObject candidate = value.getAsJsonObject();
            String candidateKid = string(candidate, "kid", 256, true);
            if (kid.equals(candidateKid)) {
                if (chosen != null) throw fail(Failure.TOKEN_REJECTED, "Ambiguous signing key.");
                chosen = candidate;
            }
        }
        if (chosen == null) throw fail(Failure.UNKNOWN_KEY, "Signing key is not in the trusted key set.");
        if (!"RSA".equals(string(chosen, "kty", 16, true))) throw fail(Failure.TOKEN_REJECTED, "Unsupported signing key.");
        String alg = string(chosen, "alg", 16, false), use = string(chosen, "use", 16, false);
        if (alg != null && !"RS256".equals(alg) || use != null && !"sig".equals(use)) throw fail(Failure.TOKEN_REJECTED, "Signing key is not valid for this signature.");
        if (chosen.has("key_ops")) {
            JsonElement ops = chosen.get("key_ops");
            if (!ops.isJsonArray() || ops.getAsJsonArray().size() != 1 || !ops.getAsJsonArray().get(0).isJsonPrimitive()
                    || !ops.getAsJsonArray().get(0).getAsJsonPrimitive().isString() || !"verify".equals(ops.getAsJsonArray().get(0).getAsString()))
                throw fail(Failure.TOKEN_REJECTED, "Signing key does not allow verification.");
        }
        BigInteger n = new BigInteger(1, base64(string(chosen, "n", 1400, true), 1024));
        BigInteger e = new BigInteger(1, base64(string(chosen, "e", 16, true), 8));
        if (n.bitLength() < 2048 || n.bitLength() > 8192 || !n.testBit(0) || e.bitLength() > 32 || e.compareTo(BigInteger.valueOf(3)) < 0 || !e.testBit(0))
            throw fail(Failure.TOKEN_REJECTED, "Invalid RSA signing key.");
        try { return (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)); }
        catch (GeneralSecurityException invalid) { throw fail(Failure.TOKEN_REJECTED, "Invalid RSA signing key."); }
    }

    /** Strict duplicate-rejecting JSON parser also usable for token endpoint responses. */
    public static JsonObject parseObject(String json) throws ProtocolException { return object(json, 262144); }
    static JsonObject parseObject(String json, int maximum) throws ProtocolException {
        if (maximum < 1 || maximum > 262144) throw fail(Failure.INVALID_INPUT, "Invalid JSON size limit.");
        return object(json, maximum);
    }

    private static JsonObject object(String json, int maximum) throws ProtocolException {
        if (json == null || json.length() > maximum) throw fail(Failure.TOKEN_REJECTED, "Invalid JSON document.");
        strictLexicalJson(json);
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonElement value = read(reader, 0, new int[] {0});
            if (!value.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw fail(Failure.TOKEN_REJECTED, "Invalid JSON document.");
            return value.getAsJsonObject();
        } catch (IOException | IllegalStateException | NumberFormatException invalid) { throw fail(Failure.TOKEN_REJECTED, "Malformed JSON document."); }
    }

    /** Gson 2.10's legacy strict mode still accepts some non-JSON string/literal forms. */
    private static void strictLexicalJson(String json) throws ProtocolException {
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || "{}[],:".indexOf(c) >= 0) continue;
            if (c == '"') {
                boolean closed = false;
                while (++i < json.length()) {
                    c = json.charAt(i);
                    if (c == '"') { closed = true; break; }
                    if (c < 32) throw fail(Failure.TOKEN_REJECTED, "Invalid JSON string.");
                    if (c == '\\') {
                        if (++i >= json.length()) throw fail(Failure.TOKEN_REJECTED, "Invalid JSON escape.");
                        c = json.charAt(i);
                        if (c == 'u') {
                            if (i + 4 >= json.length()) throw fail(Failure.TOKEN_REJECTED, "Invalid JSON escape.");
                            for (int n = 0; n < 4; n++) {
                                char hex = json.charAt(++i);
                                if (!(hex >= '0' && hex <= '9' || hex >= 'a' && hex <= 'f' || hex >= 'A' && hex <= 'F')) throw fail(Failure.TOKEN_REJECTED, "Invalid JSON escape.");
                            }
                        } else if ("\"\\/bfnrt".indexOf(c) < 0) throw fail(Failure.TOKEN_REJECTED, "Invalid JSON escape.");
                    }
                }
                if (!closed) throw fail(Failure.TOKEN_REJECTED, "Unterminated JSON string.");
                continue;
            }
            int start = i;
            while (i + 1 < json.length() && " \t\r\n{}[],:".indexOf(json.charAt(i + 1)) < 0) i++;
            String token = json.substring(start, i + 1);
            if (!token.equals("true") && !token.equals("false") && !token.equals("null") && !token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))
                throw fail(Failure.TOKEN_REJECTED, "Invalid JSON literal.");
        }
    }

    private static JsonElement read(JsonReader reader, int depth, int[] nodes) throws IOException, ProtocolException {
        if (depth > 16 || ++nodes[0] > 8192) throw fail(Failure.TOKEN_REJECTED, "JSON document exceeds limits.");
        switch (reader.peek()) {
            case BEGIN_OBJECT:
                JsonObject object = new JsonObject(); Set<String> names = new HashSet<>(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName(); unicode(name);
                    if (!names.add(name)) throw fail(Failure.TOKEN_REJECTED, "Duplicate JSON member.");
                    object.add(name, read(reader, depth + 1, nodes));
                }
                reader.endObject(); return object;
            case BEGIN_ARRAY:
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1, nodes));
                reader.endArray(); return array;
            case STRING:
                String text = reader.nextString(); unicode(text); return new JsonPrimitive(text);
            case NUMBER:
                return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN:
                return new JsonPrimitive(reader.nextBoolean());
            case NULL:
                reader.nextNull(); return JsonNull.INSTANCE;
            default: throw fail(Failure.TOKEN_REJECTED, "Malformed JSON value.");
        }
    }

    private static String string(JsonObject object, String name, int maximum, boolean required) throws ProtocolException {
        JsonElement value = object.get(name);
        if (value == null) { if (!required) return null; throw fail(Failure.TOKEN_REJECTED, "Missing identity field."); }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw fail(Failure.TOKEN_REJECTED, "Invalid identity field type.");
        return text(value.getAsString(), maximum, false);
    }

    private static long integer(JsonObject object, String name, boolean required) throws ProtocolException {
        JsonElement value = object.get(name);
        if (value == null && !required) return 0;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw fail(Failure.TOKEN_REJECTED, "Invalid identity timestamp.");
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException invalid) { throw fail(Failure.TOKEN_REJECTED, "Invalid identity timestamp."); }
    }

    private static Map<String, String> query(String raw) throws ProtocolException {
        if (raw == null || raw.isEmpty()) throw fail(Failure.CALLBACK_REJECTED, "Missing callback parameters.");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String[] pairs = raw.split("&", -1);
        if (pairs.length > 32) throw fail(Failure.CALLBACK_REJECTED, "Too many callback parameters.");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            if (separator <= 0) throw fail(Failure.CALLBACK_REJECTED, "Malformed callback parameters.");
            String name = decode(pair.substring(0, separator)), value = decode(pair.substring(separator + 1));
            text(name, 128, false);
            if (result.containsKey(name)) throw fail(Failure.CALLBACK_REJECTED, "Duplicate callback parameter.");
            result.put(name, value);
        }
        return result;
    }

    private static String decode(String raw) throws ProtocolException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '%') {
                if (i + 2 >= raw.length()) throw fail(Failure.CALLBACK_REJECTED, "Malformed callback encoding.");
                int high = Character.digit(raw.charAt(++i), 16), low = Character.digit(raw.charAt(++i), 16);
                if (high < 0 || low < 0) throw fail(Failure.CALLBACK_REJECTED, "Malformed callback encoding.");
                bytes.write(high * 16 + low);
            } else {
                if (c > 127) throw fail(Failure.CALLBACK_REJECTED, "Malformed callback encoding.");
                bytes.write(c == '+' ? ' ' : c);
            }
        }
        return text(utf8(bytes.toByteArray()), 16384, true);
    }

    private static String form(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (result.length() > 0) result.append('&');
            try { result.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8")); }
            catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
        }
        return result.toString();
    }

    public static String pkceChallenge(String verifier) throws ProtocolException {
        if (verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw fail(Failure.INVALID_INPUT, "Invalid PKCE verifier.");
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (GeneralSecurityException impossible) { throw new AssertionError(impossible); }
    }

    private static String randomValue() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static void requireAttempt(Attempt attempt) throws ProtocolException { if (attempt == null) throw fail(Failure.INVALID_INPUT, "Missing sign-in attempt."); }
    private static void fresh(Attempt attempt, long now) throws ProtocolException {
        requireAttempt(attempt);
        if (now < 0 || now < attempt.createdAt - CLOCK_SKEW || now >= attempt.expiresAt) throw fail(Failure.ATTEMPT_EXPIRED, "Sign-in attempt expired.");
    }
    private static void issuedClient(String value) throws ProtocolException {
        text(value, 256, false);
        if (BOOTSTRAP_CLIENT_ID.equals(value) || value.indexOf(' ') >= 0) throw fail(Failure.INVALID_INPUT, "Missing issued client registration.");
    }
    private static boolean constantEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
    private static String text(String value, int maximum, boolean empty) throws ProtocolException {
        if (value == null || value.length() > maximum || !empty && value.isEmpty()) throw fail(Failure.INVALID_INPUT, "Missing or invalid protocol field.");
        unicode(value);
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < 32 || value.charAt(i) == 127) throw fail(Failure.INVALID_INPUT, "Invalid protocol field.");
        return value;
    }
    private static void unicode(String value) throws ProtocolException {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) { if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw fail(Failure.INVALID_INPUT, "Invalid Unicode text."); }
            else if (Character.isLowSurrogate(c)) throw fail(Failure.INVALID_INPUT, "Invalid Unicode text.");
        }
    }
    private static byte[] base64(String value, int maximumBytes) throws ProtocolException {
        if (value == null || value.isEmpty() || value.length() > (maximumBytes * 4L + 2) / 3 || !value.matches("[A-Za-z0-9_-]+")) throw fail(Failure.TOKEN_REJECTED, "Malformed token encoding.");
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (bytes.length > maximumBytes || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value)) throw fail(Failure.TOKEN_REJECTED, "Noncanonical token encoding.");
            return bytes;
        } catch (IllegalArgumentException invalid) { throw fail(Failure.TOKEN_REJECTED, "Malformed token encoding."); }
    }
    private static String utf8(byte[] bytes) throws ProtocolException {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (java.nio.charset.CharacterCodingException invalid) { throw fail(Failure.TOKEN_REJECTED, "Malformed UTF-8 input."); }
    }
    private static ProtocolException fail(Failure failure, String message) { return new ProtocolException(failure, message); }
}
