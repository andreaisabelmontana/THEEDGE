package com.plia.notebook;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Local credential vault only: no networking, logging, notebook/export access, or
 * implicit authorization. Capture Snapshot.generation BEFORE beginning sign-in or
 * refresh and pass that original generation when committing the verified result.
 */
public final class SiwcStore {
    private static final Object LOCK = new Object();
    private static final String KEY_ALIAS = "plia.siwc.vault.v1";
    private static final byte[] MAGIC = new byte[] {'P', 'L', 'I', 'A', 'S', 'I', 'W', 'C', 1};
    private static final int MAX_BYTES = 8 * 1024 * 1024, MAX_ACCOUNTS = 16;
    private final AtomicFile vault, host;

    public static final class Account extends SiwcAccount {
        public Account(String clientId, String verifiedSub, String accessToken, String refreshToken,
                       String idToken, String oidcNonce, long expiresAtEpochSeconds,
                       List<String> scopes, String selectedModel) {
            super(clientId, verifiedSub, accessToken, refreshToken, idToken, oidcNonce,
                    expiresAtEpochSeconds, scopes, selectedModel);
        }
        public Account(String clientId, String verifiedSub, String accessToken, String refreshToken,
                       String idToken, String oidcNonce, long expiresAtEpochSeconds,
                       List<String> scopes, String selectedModel, String displayName) {
            super(clientId, verifiedSub, accessToken, refreshToken, idToken, oidcNonce,
                    expiresAtEpochSeconds, scopes, selectedModel, displayName);
        }
        private Account(SiwcAccount value) {
            this(value.clientId, value.verifiedSub, value.accessToken, value.refreshToken,
                    value.idToken, value.oidcNonce, value.expiresAtEpochSeconds, value.scopes, value.selectedModel, value.displayName);
        }
    }

    public static final class Snapshot {
        public final long generation;
        public final List<Account> accounts;
        public final String selectedId;
        private Snapshot(long generation, List<Account> accounts, String selectedId) {
            this.generation = generation;
            this.accounts = Collections.unmodifiableList(new ArrayList<>(accounts));
            this.selectedId = selectedId;
        }
        public Account selected() {
            for (Account account : accounts) if (account.id.equals(selectedId)) return account;
            return null;
        }
        @Override public String toString() { return "Sign-in storage (" + accounts.size() + " accounts)"; }
    }

    public SiwcStore(Context context) {
        File directory = context.getApplicationContext().getNoBackupFilesDir();
        if (directory == null) throw new IllegalStateException("Private sign-in storage is unavailable");
        vault = new AtomicFile(new File(directory, "siwc-vault-v1.bin"));
        host = new AtomicFile(new File(directory, "siwc-host-v1.txt"));
    }

    /** Stable non-secret UUIDv4 URI; old bare UUIDs retain their identity during migration. */
    public String hostId() throws IOException {
        synchronized (LOCK) {
            if (exists(host)) {
                String value = new String(readBytes(host, 128), StandardCharsets.UTF_8);
                try {
                    String canonical = SiwcProtocol.hostIdForInstallation(value);
                    if (!canonical.equals(value)) writeBytes(host, canonical.getBytes(StandardCharsets.UTF_8));
                    return canonical;
                } catch (SiwcProtocol.ProtocolException invalid) {
                    throw new IOException("Stored host identity is unreadable; nothing was replaced");
                }
            }
            String value = "urn:uuid:" + UUID.randomUUID().toString();
            writeBytes(host, value.getBytes(StandardCharsets.UTF_8));
            return value;
        }
    }

    public Snapshot read() throws IOException { synchronized (LOCK) { return readLocked(); } }

    /** Only invoke after the user explicitly initiated and completed a verified sign-in. */
    public Snapshot saveSignIn(Account replacement, String expectedClientId, String expectedVerifiedSub,
                               long expectedGeneration) throws IOException {
        requireIdentity(replacement, expectedClientId, expectedVerifiedSub);
        synchronized (LOCK) {
            Snapshot before = expected(expectedGeneration);
            ArrayList<Account> accounts = new ArrayList<>(before.accounts);
            int index = indexOf(accounts, replacement.id);
            if (index >= 0) { requireIdentity(accounts.get(index), expectedClientId, expectedVerifiedSub); accounts.set(index, replacement); }
            else {
                if (accounts.size() >= MAX_ACCOUNTS) throw new IOException("Sign-in storage has reached its account limit");
                accounts.add(replacement);
            }
            Snapshot after = new Snapshot(next(before.generation), accounts, replacement.id);
            writeLocked(after, true);
            return after;
        }
    }

    /** Rotation must target the exact existing registration + verified subject. */
    public Snapshot replaceTokens(Account replacement, String expectedClientId, String expectedVerifiedSub,
                                  long expectedGeneration) throws IOException {
        requireIdentity(replacement, expectedClientId, expectedVerifiedSub);
        synchronized (LOCK) {
            Snapshot before = expected(expectedGeneration);
            ArrayList<Account> accounts = new ArrayList<>(before.accounts);
            int index = indexOf(accounts, replacement.id);
            if (index < 0) throw new IOException("The account was forgotten; sign in again");
            requireIdentity(accounts.get(index), expectedClientId, expectedVerifiedSub);
            accounts.set(index, replacement);
            Snapshot after = new Snapshot(next(before.generation), accounts, before.selectedId);
            writeLocked(after, false);
            return after;
        }
    }

    public Snapshot select(String accountId, long expectedGeneration) throws IOException {
        synchronized (LOCK) {
            Snapshot before = expected(expectedGeneration);
            if (indexOf(before.accounts, accountId) < 0) throw new IOException("Account is not saved on this device");
            if (before.selectedId.equals(accountId)) return before;
            Snapshot after = new Snapshot(next(before.generation), before.accounts, accountId);
            writeLocked(after, false); return after;
        }
    }

    public Snapshot selectModel(String accountId, String model, long expectedGeneration) throws IOException {
        synchronized (LOCK) {
            Snapshot before = expected(expectedGeneration);
            ArrayList<Account> accounts = new ArrayList<>(before.accounts);
            int index = indexOf(accounts, accountId);
            if (index < 0) throw new IOException("Account is not saved on this device");
            Account replacement;
            try { replacement = new Account(accounts.get(index).withModel(model)); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid model identifier"); }
            if (accounts.get(index).selectedModel.equals(replacement.selectedModel)) return before;
            accounts.set(index, replacement);
            Snapshot after = new Snapshot(next(before.generation), accounts, before.selectedId);
            writeLocked(after, false); return after;
        }
    }

    /** Remove only this credential record. Keep an encrypted generation tombstone. */
    public Snapshot forget(String accountId, long expectedGeneration) throws IOException {
        synchronized (LOCK) {
            Snapshot before = expected(expectedGeneration);
            ArrayList<Account> accounts = new ArrayList<>(before.accounts);
            int index = indexOf(accounts, accountId);
            if (index < 0) throw new IOException("Account is not saved on this device");
            accounts.remove(index);
            Snapshot after = new Snapshot(next(before.generation), accounts,
                    before.selectedId.equals(accountId) ? "" : before.selectedId);
            writeLocked(after, false); return after;
        }
    }

    private static void requireIdentity(Account account, String client, String sub) throws IOException {
        try {
            if (account == null) throw new IllegalArgumentException();
            account.requireIdentity(client, sub);
        } catch (IllegalArgumentException invalid) { throw new IOException("Account identity mismatch; credentials were not saved"); }
    }
    private Snapshot expected(long generation) throws IOException {
        Snapshot current = readLocked();
        if (current.generation != generation) throw new IOException("Sign-in state changed; stale credentials were not saved");
        return current;
    }
    private static long next(long generation) throws IOException {
        if (generation == Long.MAX_VALUE) throw new IOException("Sign-in storage revision limit reached");
        return generation + 1;
    }
    private static int indexOf(List<Account> accounts, String id) {
        for (int i = 0; i < accounts.size(); i++) if (accounts.get(i).id.equals(id)) return i;
        return -1;
    }

    private Snapshot readLocked() throws IOException {
        byte[] plaintext = null;
        try {
            SecretKey key = key(false);
            if (!exists(vault)) {
                // A key with no file can indicate loss after a previous sign-in. Never reset its generation.
                if (key != null) throw new IOException("Sign-in vault is missing; nothing was replaced");
                return new Snapshot(0, Collections.emptyList(), "");
            }
            if (key == null) throw new IOException("The sign-in encryption key is unavailable; nothing was replaced");
            byte[] envelope = readBytes(vault, MAX_BYTES);
            if (envelope.length < MAGIC.length + 12 + 16) throw new IOException("Invalid sign-in vault");
            for (int i = 0; i < MAGIC.length; i++) if (envelope[i] != MAGIC[i]) throw new IOException("Unsupported sign-in vault");
            byte[] iv = Arrays.copyOfRange(envelope, MAGIC.length, MAGIC.length + 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv)); cipher.updateAAD(MAGIC);
            plaintext = cipher.doFinal(envelope, MAGIC.length + 12, envelope.length - MAGIC.length - 12);
            return decode(plaintext);
        } catch (Exception invalid) {
            // Never attach parser/provider exceptions: their messages can contain sensitive material.
            throw new IOException("Stored sign-in data could not be read; nothing was replaced");
        } finally { if (plaintext != null) Arrays.fill(plaintext, (byte)0); }
    }

    private void writeLocked(Snapshot value, boolean allowKeyCreation) throws IOException {
        byte[] plaintext = null;
        try {
            plaintext = encode(value);
            SecretKey key = key(allowKeyCreation);
            if (key == null) throw new IOException("Sign-in encryption key is unavailable");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key); cipher.updateAAD(MAGIC);
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length != 12) throw new IOException("Unsupported encryption nonce");
            byte[] encrypted = cipher.doFinal(plaintext);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(MAGIC); output.write(iv); output.write(encrypted);
            byte[] envelope = output.toByteArray();
            if (envelope.length > MAX_BYTES) throw new IOException("Sign-in vault is too large");
            writeBytes(vault, envelope);
        } catch (Exception failure) {
            throw new IOException("Sign-in data could not be saved; retry only after checking local account state");
        } finally { if (plaintext != null) Arrays.fill(plaintext, (byte)0); }
    }

    private static SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            java.security.Key value = store.getKey(KEY_ALIAS, null);
            if (!(value instanceof SecretKey)) throw new IOException("Invalid sign-in encryption key");
            return (SecretKey)value;
        }
        if (!create) return null;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static byte[] encode(Snapshot value) {
        JsonObject root = new JsonObject(); root.addProperty("schema", 1); root.addProperty("generation", value.generation);
        root.addProperty("selectedId", value.selectedId);
        JsonArray accounts = new JsonArray(); for (Account account : value.accounts) accounts.add(account.toJson());
        root.add("accounts", accounts);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static Snapshot decode(byte[] plaintext) {
        JsonElement parsed = JsonParser.parseString(new String(plaintext, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new IllegalArgumentException();
        JsonObject root = parsed.getAsJsonObject();
        if (root.size() != 4 || !root.get("schema").isJsonPrimitive()
                || !root.get("schema").getAsJsonPrimitive().isNumber() || !"1".equals(root.get("schema").getAsString())) throw new IllegalArgumentException();
        JsonElement generation = root.get("generation"), selected = root.get("selectedId"), records = root.get("accounts");
        if (!generation.isJsonPrimitive() || !generation.getAsJsonPrimitive().isNumber()
                || !generation.getAsString().matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
        long revision = Long.parseLong(generation.getAsString());
        if (!selected.isJsonPrimitive() || !selected.getAsJsonPrimitive().isString() || !records.isJsonArray()
                || records.getAsJsonArray().size() > MAX_ACCOUNTS) throw new IllegalArgumentException();
        ArrayList<Account> accounts = new ArrayList<>(); Set<String> identities = new HashSet<>();
        for (JsonElement record : records.getAsJsonArray()) {
            Account account = new Account(SiwcAccount.fromJson(record.getAsJsonObject()));
            if (!identities.add(account.id)) throw new IllegalArgumentException();
            accounts.add(account);
        }
        String selectedId = selected.getAsString();
        if (!selectedId.isEmpty() && !identities.contains(selectedId)) throw new IllegalArgumentException();
        return new Snapshot(revision, accounts, selectedId);
    }

    private static boolean exists(AtomicFile file) {
        File base = file.getBaseFile();
        return base.exists() || new File(base.getPath() + ".bak").exists() || new File(base.getPath() + ".new").exists();
    }
    private static byte[] readBytes(AtomicFile file, int limit) throws IOException {
        try (FileInputStream input = file.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > limit) throw new IOException("Private sign-in file is too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
    private static void writeBytes(AtomicFile file, byte[] data) throws IOException {
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(data); file.finishWrite(output); output = null;
            // AtomicFile may report filesystem failures through logging; verify what was committed.
            if (!MessageDigest.isEqual(data, readBytes(file, data.length))) throw new IOException("Private sign-in write did not commit");
        } catch (IOException | RuntimeException failure) {
            if (output != null) file.failWrite(output);
            throw new IOException("Private sign-in storage write failed");
        }
    }
}
