package com.plia.notebook;

/**
 * UI-thread state for a single paper conversation; contains no I/O or model calls.
 * Call inkChanged only for user edits, never for loading a page or displaying a reply.
 * Times must come from one monotonic clock. A successful ready(now) check is required
 * immediately before claim(). Background snapshot work must also compare stamp().
 */
public final class PaperSession {
    public static final long IDLE_DELAY_MS = 2500;
    public static final long FOCUS_DELAY_MS = 650;
    public static final String IDLE = "idle";
    public static final String FOCUS = "focus";

    public static final class Ticket {
        public final long id;
        public final String pageId;
        public final long revision;
        public final String fingerprint;
        public final String kind;
        public final int retryCount;
        private final long generation;

        private Ticket(long id, String pageId, long revision, String fingerprint,
                       String kind, long generation, int retryCount) {
            this.id = id;
            this.pageId = pageId;
            this.revision = revision;
            this.fingerprint = fingerprint;
            this.kind = kind;
            this.generation = generation;
            this.retryCount = retryCount;
        }
    }

    private String pageId;
    private boolean enabled;
    private boolean writing;
    private boolean dirty;
    private boolean checkedReady;
    private long generation;
    private long revision;
    private long nextId;
    private long readyAt = -1;
    private String pendingKind = IDLE;
    private String lastFingerprint;
    private String lastSuccessfulFingerprint;
    private Ticket current;
    private Ticket failed;

    /** A changed page/mode invalidates work; merely reactivating the same state is a no-op. */
    public void activate(String pageId, boolean think) {
        if (pageId == null || pageId.isEmpty()) throw new IllegalArgumentException("pageId required");
        if (pageId.equals(this.pageId) && enabled == think) return;
        boolean changedPage = !pageId.equals(this.pageId);
        cancel();
        this.pageId = pageId;
        enabled = think;
        if (changedPage) {
            revision = 0;
            lastFingerprint = null;
            lastSuccessfulFingerprint = null;
        }
    }

    /** Reject a reply as soon as the pen touches down, even before another point is added. */
    public void penDown() {
        invalidate();
        writing = enabled;
        pendingKind = IDLE;
    }

    /** A meaningful user edit, including undo/erase, advances the ink revision. */
    public void inkChanged() {
        invalidate();
        revision++;
        dirty = enabled;
    }

    public void penUp(long now) {
        writing = false;
        if (enabled && dirty) {
            checkedReady = false;
            readyAt = now + (FOCUS.equals(pendingKind) ? FOCUS_DELAY_MS : IDLE_DELAY_MS);
        }
    }

    /** A recognized box is an optional explicit focus; it does not remove any user ink. */
    public void focused(long now) {
        if (!enabled) return;
        invalidate();
        dirty = true;
        pendingKind = FOCUS;
        if (!writing) readyAt = now + FOCUS_DELAY_MS;
    }

    public String pendingKind() { return pendingKind; }

    /** Capture before asynchronous snapshot work, and compare again before claiming it. */
    public long stamp() { return generation; }

    public boolean ready(long now) {
        checkedReady = enabled && !writing && dirty && readyAt >= 0 && now >= readyAt;
        return checkedReady;
    }

    /**
     * At most one local request is active. A duplicate snapshot is consumed without
     * producing another ticket. Fingerprints must include the selected ink/context.
     */
    public Ticket claim(String fingerprint, String kind) {
        return claim(fingerprint, kind, false);
    }

    /** A fresh deliberate, validated selection may refresh the same problem once. */
    public Ticket claimSelectionRefresh(String fingerprint) {
        return claim(fingerprint, FOCUS, true);
    }

    private Ticket claim(String fingerprint, String kind, boolean deliberateRefresh) {
        if (!checkedReady || !enabled || writing || !dirty || !pendingKind.equals(kind)) return null;
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw new IllegalArgumentException("fingerprint required");
        }
        checkedReady = false;
        dirty = false;
        readyAt = -1;
        if (!deliberateRefresh && (fingerprint.equals(lastFingerprint) || fingerprint.equals(lastSuccessfulFingerprint))) return null;
        lastFingerprint = fingerprint;
        current = new Ticket(++nextId, pageId, revision, fingerprint, kind, generation, 0);
        return current;
    }

    /**
     * Claim an explicitly requested whole-page snapshot without inventing an ink edit.
     * The caller must check its captured stamp before this call, then retain the new
     * stamp for reply checks. Single-use network authorization belongs to the caller.
     */
    public Ticket claimManual(String fingerprint) {
        if (!enabled || writing) return null;
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw new IllegalArgumentException("fingerprint required");
        }
        cancel();
        lastFingerprint = fingerprint;
        current = new Ticket(++nextId, pageId, revision, fingerprint, IDLE, generation, 0);
        return current;
    }

    /** Checking/applying a response must happen on the same thread as user events. */
    public boolean accepts(Ticket ticket) {
        return enabled && !writing && ticket != null && ticket == current
                && ticket.generation == generation && ticket.revision == revision
                && ticket.pageId.equals(pageId);
    }

    /** Consume a matching reply once; displaying it does not schedule further input. */
    public boolean complete(Ticket ticket) {
        if (!accepts(ticket)) return false;
        lastSuccessfulFingerprint = ticket.fingerprint;
        current = null;
        failed = null;
        return true;
    }

    /** A failed request is not a completed selection. Failure never schedules another request. */
    public boolean fail(Ticket ticket) {
        if (!accepts(ticket)) return false;
        current = null;
        lastFingerprint = lastSuccessfulFingerprint;
        failed = ticket.retryCount == 0 ? ticket : null;
        dirty = false;
        checkedReady = false;
        readyAt = -1;
        return true;
    }

    /** One explicit retry of this unchanged snapshot; every edit/context change discards it. */
    public boolean canRetry(Ticket ticket) {
        return enabled && !writing && current == null && ticket != null && ticket == failed
                && ticket.retryCount == 0 && ticket.generation == generation
                && ticket.revision == revision && ticket.pageId.equals(pageId);
    }

    public Ticket retry(Ticket ticket) {
        if (!canRetry(ticket)) return null;
        failed = null;
        lastFingerprint = ticket.fingerprint;
        current = new Ticket(++nextId, pageId, revision, ticket.fingerprint, ticket.kind, generation, 1);
        return current;
    }

    public void cancel() {
        invalidate();
        writing = false;
        dirty = false;
        pendingKind = IDLE;
    }

    /** Enabling releases local-only input, while keeping completed selections deduplicated. */
    public void startConnectedSession() {
        cancel();
        lastFingerprint = lastSuccessfulFingerprint;
    }

    public void background() {
        cancel();
        enabled = false;
    }

    private void invalidate() {
        generation++;
        current = null;
        failed = null;
        checkedReady = false;
        readyAt = -1;
    }
}
