package com.plia.notebook;

import java.util.ArrayList;
import java.util.List;

/** UI-thread reply-only transactions. No I/O, network, ink changes, or silent history eviction. */
public final class ReplyRefresh {
    public static final int MAX_BATCHES = 100, MAX_VISIBLE_REPLIES = 100;
    public static final int MAX_RETAINED_REPLIES = 1000, MAX_RETAINED_CHARS = 4000000;
    private static final int MAX_REPLY_CHARS = 10 + 4000 + 16000 + 20;

    private ReplyRefresh() {}

    /** Reserve a whole valid result before sending, so its later Undo cannot exceed the archive budget. */
    public static boolean canReplace(NotebookStore.Page page) {
        Usage used = usage(page);
        return used != null && used.batches < MAX_BATCHES
                && used.replies + 1 <= MAX_RETAINED_REPLIES
                && used.chars + MAX_REPLY_CHARS <= MAX_RETAINED_CHARS;
    }

    /** Atomically replace every visible reply. Caller must reject stale requests before entering. */
    public static boolean replace(NotebookStore.Page page, NotebookStore.Reply answer, ReplyPlacement fixed) {
        Usage used = usage(page);
        if (used == null || !validReply(answer) || used.batches >= MAX_BATCHES
                || used.replies + 1 > MAX_RETAINED_REPLIES
                || used.chars + chars(answer) > MAX_RETAINED_CHARS) return false;
        ArrayList<ArrayList<NotebookStore.Reply>> history = branchHistory(page);
        ArrayList<NotebookStore.Reply> previous = NotebookStore.copyReplies(page.replies);
        if (fixed != null && !previous.isEmpty() && previous.get(0).placement == null)
            previous.set(0, previous.get(0).withPlacement(fixed));
        history.add(previous); // Empty first state is undoable too.
        ArrayList<NotebookStore.Reply> visible = new ArrayList<>();
        visible.add(answer.withPlacement(fixed == null ? answer.placement : fixed));
        page.replyHistory = history; page.replyRedo = new ArrayList<>(); page.replies = visible;
        return true;
    }

    /** Clearing replies keeps ink untouched and stores the complete old group for Undo. */
    public static boolean clear(NotebookStore.Page page) {
        Usage used = usage(page);
        if (used == null || page.replies.isEmpty() || used.batches >= MAX_BATCHES) return false;
        ArrayList<ArrayList<NotebookStore.Reply>> history = branchHistory(page);
        history.add(NotebookStore.copyReplies(page.replies));
        page.replyHistory = history; page.replyRedo = new ArrayList<>(); page.replies = new ArrayList<>();
        return true;
    }

    public static boolean canUndo(NotebookStore.Page page) { return validPage(page) && !page.replyHistory.isEmpty(); }
    public static boolean canRedo(NotebookStore.Page page) { return validPage(page) && !page.replyRedo.isEmpty(); }
    public static boolean undo(NotebookStore.Page page) { return transfer(page, true); }
    public static boolean redo(NotebookStore.Page page) { return transfer(page, false); }

    private static boolean transfer(NotebookStore.Page page, boolean undo) {
        if (undo ? !canUndo(page) : !canRedo(page)) return false;
        ArrayList<ArrayList<NotebookStore.Reply>> history = NotebookStore.copyReplyBatches(page.replyHistory);
        ArrayList<ArrayList<NotebookStore.Reply>> redo = NotebookStore.copyReplyBatches(page.replyRedo);
        ArrayList<ArrayList<NotebookStore.Reply>> from = undo ? history : redo, to = undo ? redo : history;
        ArrayList<NotebookStore.Reply> visible = from.remove(from.size() - 1);
        to.add(NotebookStore.copyReplies(page.replies));
        page.replyHistory = history; page.replyRedo = redo; page.replies = visible;
        return true;
    }

    /** A branched edit retains abandoned redo results as older history instead of deleting them. */
    private static ArrayList<ArrayList<NotebookStore.Reply>> branchHistory(NotebookStore.Page page) {
        ArrayList<ArrayList<NotebookStore.Reply>> history = NotebookStore.copyReplyBatches(page.replyHistory);
        for (int i = page.replyRedo.size() - 1; i >= 0; i--) history.add(NotebookStore.copyReplies(page.replyRedo.get(i)));
        return history;
    }

    /** Capture with new ArrayList<>(livePage.replies); Page.copy intentionally changes identities. */
    public static boolean sameReplies(List<NotebookStore.Reply> expected, List<NotebookStore.Reply> current) {
        if (expected == null || current == null || expected.size() != current.size()) return false;
        for (int i = 0; i < expected.size(); i++) if (expected.get(i) != current.get(i)) return false;
        return true;
    }

    static boolean validPage(NotebookStore.Page page) { return usage(page) != null; }
    static boolean validReply(NotebookStore.Reply reply) {
        return reply != null && ("tutor".equals(reply.mode) || "think".equals(reply.mode))
                && ("dot".equals(reply.source) || "openai".equals(reply.source) || "chatgpt".equals(reply.source) || "demo".equals(reply.source))
                && reply.input != null && reply.input.length() <= 4000
                && reply.text != null && reply.text.length() <= 16000;
    }
    private static long chars(NotebookStore.Reply reply) {
        return (long) reply.mode.length() + reply.input.length() + reply.text.length() + reply.source.length();
    }
    private static final class Usage { int batches, replies; long chars; }
    private static Usage usage(NotebookStore.Page page) {
        if (page == null || page.replies == null || page.replyHistory == null || page.replyRedo == null) return null;
        Usage used = new Usage();
        used.batches = page.replyHistory.size() + page.replyRedo.size();
        if (used.batches > MAX_BATCHES || !count(page.replies, used)) return null;
        for (List<NotebookStore.Reply> group : page.replyHistory) if (!count(group, used)) return null;
        for (List<NotebookStore.Reply> group : page.replyRedo) if (!count(group, used)) return null;
        return used;
    }
    private static boolean count(List<NotebookStore.Reply> group, Usage used) {
        if (group == null || group.size() > MAX_VISIBLE_REPLIES) return false;
        used.replies += group.size();
        if (used.replies > MAX_RETAINED_REPLIES) return false;
        for (NotebookStore.Reply reply : group) {
            if (!validReply(reply)) return false;
            used.chars += chars(reply);
            if (used.chars > MAX_RETAINED_CHARS) return false;
        }
        return true;
    }
}
