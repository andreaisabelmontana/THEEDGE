package com.plia.notebook;

/** Immutable reply preference. Storage, lifecycle and explicit Select admission belong to the caller. */
public final class ReplySettings {
    public final boolean initialized;
    public final boolean enabled;
    public final String accountId;
    public final String modelSlug;
    public final boolean blocked;

    private ReplySettings(boolean initialized, boolean enabled, String accountId, String modelSlug, boolean blocked) {
        this.initialized = initialized;
        this.enabled = enabled;
        this.accountId = accountId == null ? "" : accountId;
        this.modelSlug = modelSlug == null ? "" : modelSlug;
        this.blocked = blocked;
    }

    public static ReplySettings uninitialized() {
        return new ReplySettings(false, false, "", "", false);
    }

    public static ReplySettings restore(boolean initialized, boolean enabled, String accountId,
                                        String modelSlug, boolean blocked) {
        return new ReplySettings(initialized, enabled, accountId, modelSlug, blocked);
    }

    /** One-time migration authorized for the current connected account/model; never replaces a saved choice. */
    public ReplySettings migrate(String accountId, String modelSlug) {
        if (initialized) return this;
        return new ReplySettings(true, validPair(accountId, modelSlug), accountId, modelSlug, false);
    }

    public boolean matches(String accountId, String modelSlug) {
        return validPair(this.accountId, this.modelSlug) && validPair(accountId, modelSlug)
                && this.accountId.equals(accountId) && this.modelSlug.equals(modelSlug);
    }

    public boolean canSend(String accountId, String modelSlug) {
        return initialized && enabled && !blocked && matches(accountId, modelSlug);
    }

    /** An explicit enable binds this pair and clears a previous failure block. */
    public ReplySettings enable(String accountId, String modelSlug) {
        return new ReplySettings(true, validPair(accountId, modelSlug), accountId, modelSlug, false);
    }

    /** Explicit off must survive even when chosen before an account has been connected. */
    public ReplySettings disable() {
        return new ReplySettings(true, false, accountId, modelSlug, false);
    }

    /** Retain the desired preference while denying sends until the user explicitly enables again. */
    public ReplySettings block() {
        return new ReplySettings(true, enabled, accountId, modelSlug, true);
    }

    private static boolean validPair(String accountId, String modelSlug) {
        return nonblank(accountId) && nonblank(modelSlug);
    }

    private static boolean nonblank(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i)) && !Character.isSpaceChar(value.charAt(i))) return true;
        }
        return false;
    }
}
