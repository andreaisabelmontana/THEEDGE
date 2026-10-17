package com.plia.notebook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable account record. The constructor requires an already verified subject. */
class SiwcAccount {
    public final String id;
    final String clientId, verifiedSub, accessToken, refreshToken, idToken, oidcNonce;
    public final long expiresAtEpochSeconds;
    public final List<String> scopes;
    public final String selectedModel, displayName;

    public SiwcAccount(String clientId, String verifiedSub, String accessToken,
                       String refreshToken, String idToken, String oidcNonce, long expiresAtEpochSeconds,
                       List<String> scopes, String selectedModel) {
        this(clientId, verifiedSub, accessToken, refreshToken, idToken, oidcNonce,
                expiresAtEpochSeconds, scopes, selectedModel, "");
    }

    /** displayName must come from the validated identity's verified email or name. */
    public SiwcAccount(String clientId, String verifiedSub, String accessToken,
                       String refreshToken, String idToken, String oidcNonce, long expiresAtEpochSeconds,
                       List<String> scopes, String selectedModel, String displayName) {
        this.clientId = required(clientId, 2048, "client identifier");
        this.verifiedSub = required(verifiedSub, 4096, "verified subject");
        this.accessToken = required(accessToken, 65536, "access token");
        this.refreshToken = optional(refreshToken, 65536, "refresh token");
        this.idToken = required(idToken, 131072, "ID token");
        this.oidcNonce = required(oidcNonce, 4096, "OIDC nonce");
        if (expiresAtEpochSeconds < 0) throw new IllegalArgumentException("Invalid token expiry");
        this.expiresAtEpochSeconds = expiresAtEpochSeconds;
        if (scopes == null || scopes.size() > 128) throw new IllegalArgumentException("Invalid scopes");
        ArrayList<String> copy = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String scope : scopes) {
            required(scope, 256, "scope");
            if (!scope.matches("[\\x21\\x23-\\x5B\\x5D-\\x7E]+") || !seen.add(scope)) {
                throw new IllegalArgumentException("Invalid scopes");
            }
            copy.add(scope);
        }
        this.scopes = Collections.unmodifiableList(copy);
        this.selectedModel = model(selectedModel);
        this.displayName = displayName(displayName);
        if (!this.displayName.isEmpty() && (this.displayName.equals(accessToken) || this.displayName.equals(refreshToken)
                || this.displayName.equals(idToken) || this.displayName.equals(oidcNonce))) {
            throw new IllegalArgumentException("Invalid account display name");
        }
        id = identity(this.clientId, this.verifiedSub);
    }

    /** Verified identity hint plus a stable disambiguator; never a token or subject. */
    public final String label() { return displayName.isEmpty() ? "Account " + id.substring(0, 10) : displayName + " \u00b7 " + id.substring(0, 10); }
    @Override public final String toString() { return "Account " + id.substring(0, 10); }
    public final boolean hasScope(String scope) { return scopes.contains(scope); }

    SiwcAccount withModel(String model) {
        return new SiwcAccount(clientId, verifiedSub, accessToken, refreshToken, idToken, oidcNonce,
                expiresAtEpochSeconds, scopes, model, displayName);
    }

    void requireIdentity(String expectedClientId, String expectedVerifiedSub) {
        if (!clientId.equals(expectedClientId) || !verifiedSub.equals(expectedVerifiedSub)) {
            throw new IllegalArgumentException("Account identity mismatch");
        }
    }

    static String identity(String clientId, String subject) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            // Length prefixes prevent delimiter ambiguities between registration and subject.
            hash.update((clientId.length() + ":" + clientId + subject.length() + ":" + subject)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte b : hash.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    /** Sensitive serialization: only the encrypted store may persist this object. */
    JsonObject toJson() {
        JsonObject value = new JsonObject();
        value.addProperty("id", id); value.addProperty("clientId", clientId);
        value.addProperty("verifiedSub", verifiedSub); value.addProperty("accessToken", accessToken);
        value.addProperty("refreshToken", refreshToken); value.addProperty("idToken", idToken); value.addProperty("oidcNonce", oidcNonce);
        value.addProperty("expiresAtEpochSeconds", expiresAtEpochSeconds);
        JsonArray array = new JsonArray(); for (String scope : scopes) array.add(scope);
        value.add("scopes", array); value.addProperty("selectedModel", selectedModel);
        value.addProperty("displayName", displayName);
        return value;
    }

    static SiwcAccount fromJson(JsonObject value) {
        if (value == null || value.size() != (value.has("displayName") ? 11 : 10)) throw new IllegalArgumentException("Invalid account record");
        JsonElement scopeValue = value.get("scopes");
        if (scopeValue == null || !scopeValue.isJsonArray()) throw new IllegalArgumentException("Invalid scopes");
        ArrayList<String> scopes = new ArrayList<>();
        for (JsonElement scope : scopeValue.getAsJsonArray()) scopes.add(string(scope));
        JsonElement expiry = value.get("expiresAtEpochSeconds");
        if (expiry == null || !expiry.isJsonPrimitive() || !expiry.getAsJsonPrimitive().isNumber()
                || !expiry.getAsString().matches("[0-9]{1,19}")) throw new IllegalArgumentException("Invalid token expiry");
        SiwcAccount account = new SiwcAccount(string(value.get("clientId")), string(value.get("verifiedSub")),
                string(value.get("accessToken")), string(value.get("refreshToken")), string(value.get("idToken")), string(value.get("oidcNonce")),
                Long.parseLong(expiry.getAsString()), scopes, string(value.get("selectedModel")),
                value.has("displayName") ? string(value.get("displayName")) : "");
        if (!account.id.equals(string(value.get("id")))) throw new IllegalArgumentException("Invalid account identity");
        return account;
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid account record");
        }
        return value.getAsString();
    }
    private static String required(String value, int limit, String field) {
        if (value == null || value.isEmpty() || value.length() > limit || hasControl(value)) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return value;
    }
    private static String optional(String value, int limit, String field) {
        if (value == null || value.length() > limit || hasControl(value)) throw new IllegalArgumentException("Invalid " + field);
        return value;
    }
    private static boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < 32 || value.charAt(i) == 127) return true;
        return false;
    }
    private static String model(String value) {
        if (value == null || !value.isEmpty() && !value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException("Invalid model identifier");
        }
        return value;
    }
    private static String displayName(String value) {
        if (value == null || value.length() > 320) throw new IllegalArgumentException("Invalid account display name");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || c == '\u2028' || c == '\u2029') {
                throw new IllegalArgumentException("Invalid account display name");
            }
        }
        return value.trim();
    }
}
