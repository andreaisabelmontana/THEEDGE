package com.plia.notebook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/** Local-only whole-component editing. No transport, AI admission, drawing or persistence I/O.
 * All coordinates are paper units. UI commits once on UP; CANCEL only invalidates selection.
 * Eraser dependencies use conservative painted bounds, so extra intersecting components may be
 * selected together. This never silently detaches an eraser from ink it could have erased. */
public final class PageEditor {
    public static final int HISTORY_LIMIT = 40;
    /** Shared immutable stroke identities count once across current/undo/redo. A tiny stroke costs
     * at least eight point units to account conservatively for its Stroke/list wrapper objects. */
    public static final int RETAINED_GEOMETRY_BUDGET = 1000000;

    public static final class State {
        public final String pageId;
        public final List<InkView.Stroke> ink;
        public final List<NotebookStore.Reply> replies;

        public State(String pageId, List<InkView.Stroke> ink, List<NotebookStore.Reply> replies) {
            this(pageId, freezeInk(ink, null), freezeReplies(replies), true);
        }
        private State(String pageId, List<InkView.Stroke> ink, List<NotebookStore.Reply> replies, boolean trusted) {
            if (pageId == null || pageId.isEmpty()) throw new IllegalArgumentException("Missing page identity");
            this.pageId = pageId; this.ink = immutableList(ink); this.replies = immutableList(replies);
        }
        public static State from(NotebookStore.Page page) { return new State(page.id, page.strokes, page.replies); }
        /** Preserves title, prompts, mode and all other page metadata. */
        public void applyTo(NotebookStore.Page page) {
            if (!pageId.equals(page.id)) throw new IllegalArgumentException("Different page");
            page.strokes = new ArrayList<>(ink); page.replies = new ArrayList<>(replies);
        }
    }

    public enum Status { APPLIED, READY, NO_CHANGE, STALE_SELECTION, ERASER_COLLISION, INVALID_DELTA }
    public static final class Result {
        public final Status status;
        public final float dx, dy;
        private Result(Status status, float dx, float dy) { this.status = status; this.dx = dx; this.dy = dy; }
        public boolean applied() { return status == Status.APPLIED; }
    }
    public static final class Selection {
        public final List<Integer> strokeIndices, replyIndices;
        public final AnswerPlacement.Rect bounds;
        private final PageEditor owner;
        private final long generation;
        private final boolean[] strokes, replies;
        private final List<AnswerPlacement.Rect> strokeBounds, collisionBounds, replyBounds;
        private final List<Integer> unselectedInk, unselectedErasers, collisionSources;
        private final float minDx, maxDx, minDy, maxDy;
        private Selection(PageEditor owner, long generation, boolean[] strokes, boolean[] replies,
                          List<AnswerPlacement.Rect> strokeBounds, List<AnswerPlacement.Rect> collisionBounds, List<AnswerPlacement.Rect> replyBounds) {
            this.owner = owner; this.generation = generation; this.strokes = strokes; this.replies = replies;
            this.strokeIndices = indices(strokes); this.replyIndices = indices(replies);
            this.strokeBounds = immutableList(strokeBounds); this.collisionBounds = immutableList(collisionBounds); this.replyBounds = immutableList(replyBounds);
            ArrayList<Integer> otherInk = new ArrayList<>(), otherErasers = new ArrayList<>();
            for (int i = 0; i < strokes.length; i++) if (!strokes[i]) {
                if (owner.current.ink.get(i).erase) otherErasers.add(i); else otherInk.add(i);
            }
            unselectedInk = immutableList(otherInk); unselectedErasers = immutableList(otherErasers);
            ArrayList<Integer> possible = new ArrayList<>();
            for (int i : strokeIndices) {
                boolean erase = owner.current.ink.get(i).erase;
                if (erase ? !otherInk.isEmpty() && otherInk.get(0) < i
                        : !otherErasers.isEmpty() && otherErasers.get(otherErasers.size() - 1) > i) possible.add(i);
            }
            collisionSources = immutableList(possible);
            AnswerPlacement.Rect total = null;
            float lx = -Float.MAX_VALUE, rx = Float.MAX_VALUE, ty = -Float.MAX_VALUE, by = Float.MAX_VALUE;
            for (int i : strokeIndices) {
                AnswerPlacement.Rect b = strokeBounds.get(i); total = union(total, b);
                lx = Math.max(lx, -b.left); rx = Math.min(rx, InkView.WIDTH - b.right);
                ty = Math.max(ty, -b.top); by = Math.min(by, InkView.HEIGHT - b.bottom);
            }
            for (int i : replyIndices) {
                AnswerPlacement.Rect b = replyBounds.get(i); total = union(total, b);
                // Cache representable bounds once, rather than traverse selected components on MOVE.
                float lastLeft = floorFloat((double)InkView.WIDTH - b.width());
                lx = Math.max(lx, -b.left); rx = Math.min(rx, floorFloat((double)lastLeft - b.left));
                ty = Math.max(ty, -b.top); by = Math.min(by, floorFloat((double)ReplyPlacement.MAX_Y - b.top));
            }
            minDx = lx; maxDx = rx; minDy = ty; maxDy = by;
            bounds = total;
        }
    }

    private State current;
    private long generation;
    private final ArrayDeque<State> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private final IdentityHashMap<InkView.Stroke, GeometryRef> retainedStrokes = new IdentityHashMap<>();
    private long retainedCost, retainedPoints, retainedAccountingWork;
    public PageEditor(State state) { resetForPage(state); }
    public State state() { return current; }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public int undoDepth() { return undo.size(); }
    public int redoDepth() { return redo.size(); }
    /** Conservative retained point-equivalent count, useful for regression checks and diagnostics. */
    public long retainedGeometryCost() { return retainedCost; }
    /** Actual summed point counts by unique immutable Stroke identity (shared Points may overcount). */
    public long retainedPointCount() { return retainedPoints; }
    /** Stroke-reference visits used to add/remove retained states since the last page reset.
     * This deterministic diagnostic excludes validation/selection work and never scans history. */
    public long retainedGeometryWorkCount() { return retainedAccountingWork; }
    public void cancelSelection() { generation++; }
    public boolean isCurrent(Selection selection) { return selection != null && selection.owner == this && selection.generation == generation; }

    /** Page change/load: discards history. Never use this for a UI refresh or a new pen stroke. */
    public void resetForPage(State state) {
        current = Objects.requireNonNull(state); undo.clear(); redo.clear(); retainedStrokes.clear();
        retainedCost = retainedPoints = retainedAccountingWork = 0;
        retainGeometry(current); cancelSelection();
    }
    /** One completed pen gesture, clear, or newly received reply becomes one shared history entry.
     * Equal input is a no-op, including equivalent newly loaded lists. Only changed strokes freeze. */
    public boolean recordExternalChange(List<InkView.Stroke> ink, List<NotebookStore.Reply> replies) {
        List<InkView.Stroke> frozen = freezeInk(ink, current.ink);
        List<NotebookStore.Reply> response = freezeReplies(replies);
        if (sameInk(current.ink, frozen) && sameReplies(current.replies, response)) return false;
        commit(new State(current.pageId, frozen, response, true)); return true;
    }
    public boolean recordExternalChange(NotebookStore.Page page) {
        if (!current.pageId.equals(page.id)) throw new IllegalArgumentException("Use resetForPage for another page");
        return recordExternalChange(page.strokes, page.replies);
    }
    public boolean undo() {
        cancelSelection(); if (undo.isEmpty()) return false;
        redo.addLast(current); current = undo.removeLast(); return true;
    }
    public boolean redo() {
        cancelSelection(); if (redo.isEmpty()) return false;
        pushUndo(current); current = redo.removeLast(); return true;
    }

    /** Reply rects are measured current positions, indexed exactly like state.replies; null means
     * hidden/unavailable. UI must cancel/reselect when layout or visibility changes. Selecting an
     * eraser or its earlier ink closes over every potentially affected earlier ink/later eraser. */
    public Selection select(List<Integer> strokes, List<Integer> replies, List<AnswerPlacement.Rect> renderedReplyRects) {
        cancelSelection();
        boolean[] selectedInk = flags(strokes, current.ink.size()), selectedReplies = flags(replies, current.replies.size());
        List<AnswerPlacement.Rect> replyRects = replyRects(renderedReplyRects);
        for (int i = 0; i < selectedReplies.length; i++) if (selectedReplies[i] && replyRects.get(i) == null)
            throw new IllegalArgumentException("Selected reply is not rendered");
        ArrayList<AnswerPlacement.Rect> inkRects = new ArrayList<>();
        ArrayList<AnswerPlacement.Rect> collisionRects = new ArrayList<>();
        for (InkView.Stroke stroke : current.ink) { inkRects.add(strokeBounds(stroke)); collisionRects.add(paintBounds(stroke)); }
        ArrayList<Integer> allInk = new ArrayList<>(), allErasers = new ArrayList<>();
        for (int i = 0; i < current.ink.size(); i++) { if (current.ink.get(i).erase) allErasers.add(i); else allInk.add(i); }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < selectedInk.length; i++) if (selectedInk[i]) queue.add(i);
        while (!queue.isEmpty()) {
            int i = queue.removeFirst();
            boolean erase = current.ink.get(i).erase;
            for (int j : erase ? allInk : allErasers) {
                if (erase && j >= i) break;
                if (!erase && j <= i) continue;
                if (!selectedInk[j] && touches(collisionRects.get(i), collisionRects.get(j))) {
                    selectedInk[j] = true; queue.addLast(j);
                }
            }
        }
        if (indices(selectedInk).isEmpty() && indices(selectedReplies).isEmpty()) return null;
        return new Selection(this, generation, selectedInk, selectedReplies, inkRects, collisionRects, replyRects);
    }
    /** A region selects fully enclosed whole painted stroke bounds/reply blocks, then eraser closure.
     * It never selects hidden replies and never fragments a stroke. */
    public Selection selectRegion(AnswerPlacement.Rect region, List<AnswerPlacement.Rect> renderedReplyRects) {
        Objects.requireNonNull(region);
        List<AnswerPlacement.Rect> rects = replyRects(renderedReplyRects);
        ArrayList<Integer> ink = new ArrayList<>(), replies = new ArrayList<>();
        for (int i = 0; i < current.ink.size(); i++) if (!current.ink.get(i).erase && contains(region, strokeBounds(current.ink.get(i)))) ink.add(i);
        for (int i = 0; i < rects.size(); i++) if (rects.get(i) != null && contains(region, rects.get(i))) replies.add(i);
        return select(ink, replies, rects);
    }
    public Selection selectReply(int index, List<AnswerPlacement.Rect> renderedReplyRects) {
        return select(Collections.emptyList(), Collections.singletonList(index), renderedReplyRects);
    }

    /** Allocation-light preview: does not move points, mutate state, consume selection or add history.
     * The same delta applies to every component; a collision rejects the entire move. */
    public Result preview(Selection selection, float dx, float dy) {
        if (!isCurrent(selection)) return result(Status.STALE_SELECTION);
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return result(Status.INVALID_DELTA);
        float x = Math.max(selection.minDx, Math.min(selection.maxDx, dx)), y = Math.max(selection.minDy, Math.min(selection.maxDy, dy));
        if (x == 0 && y == 0) return result(Status.NO_CHANGE);
        if (selection.collisionSources.isEmpty()) return new Result(Status.READY, x, y);
        for (int i : selection.collisionSources) {
            boolean erase = current.ink.get(i).erase;
            AnswerPlacement.Rect moved = selection.collisionBounds.get(i);
            for (int j : erase ? selection.unselectedInk : selection.unselectedErasers) {
                if (erase && j >= i) break;
                if (!erase && j <= i) continue;
                if (touchesTranslated(moved, x, y, selection.collisionBounds.get(j)))
                    return result(Status.ERASER_COLLISION);
            }
        }
        return new Result(Status.READY, x, y);
    }
    public Result move(Selection selection, float dx, float dy) {
        Result change = preview(selection, dx, dy);
        if (change.status != Status.READY) return change;
        ArrayList<InkView.Stroke> ink = new ArrayList<>(current.ink);
        for (int i : selection.strokeIndices) ink.set(i, translate(ink.get(i), change.dx, change.dy));
        ArrayList<NotebookStore.Reply> replies = new ArrayList<>(current.replies);
        for (int i : selection.replyIndices) {
            AnswerPlacement.Rect rect = selection.replyBounds.get(i);
            replies.set(i, replies.get(i).withPlacement(new ReplyPlacement(rect.left + change.dx, rect.top + change.dy, rect.width(), true)));
        }
        commit(new State(current.pageId, ink, replies, true));
        return new Result(Status.APPLIED, change.dx, change.dy);
    }
    public Result delete(Selection selection) {
        if (!isCurrent(selection)) return result(Status.STALE_SELECTION);
        ArrayList<InkView.Stroke> ink = new ArrayList<>(); ArrayList<NotebookStore.Reply> replies = new ArrayList<>();
        for (int i = 0; i < current.ink.size(); i++) if (!selection.strokes[i]) ink.add(current.ink.get(i));
        for (int i = 0; i < current.replies.size(); i++) if (!selection.replies[i]) replies.add(current.replies.get(i));
        commit(new State(current.pageId, ink, replies, true)); return result(Status.APPLIED);
    }

    private void commit(State next) {
        // The old current state stays retained when transferred to undo. Only new/discarded
        // states change reference counts; shared old pages are never rescanned on every UP.
        while (!redo.isEmpty()) releaseGeometry(redo.removeLast());
        retainGeometry(next); pushUndo(current); current = next; cancelSelection();
        trimRetainedGeometry();
    }
    private void pushUndo(State state) {
        undo.addLast(state);
        if (undo.size() > HISTORY_LIMIT) releaseGeometry(undo.removeFirst());
    }
    private void trimRetainedGeometry() {
        // A valid page has at most 200k points / 12k strokes, so current plus its immediate
        // before-state always fits this budget. Retain that newest reversible action.
        while (undo.size() > 1 && retainedCost > RETAINED_GEOMETRY_BUDGET) releaseGeometry(undo.removeFirst());
    }
    private void retainGeometry(State state) {
        for (InkView.Stroke stroke : state.ink) {
            retainedAccountingWork++;
            GeometryRef ref = retainedStrokes.get(stroke);
            if (ref != null) { ref.references++; continue; }
            retainedStrokes.put(stroke, new GeometryRef());
            int count = stroke.points.size(); retainedPoints += count; retainedCost += Math.max(8, count);
        }
    }
    private void releaseGeometry(State state) {
        for (InkView.Stroke stroke : state.ink) {
            retainedAccountingWork++;
            GeometryRef ref = retainedStrokes.get(stroke);
            if (--ref.references != 0) continue;
            retainedStrokes.remove(stroke);
            int count = stroke.points.size(); retainedPoints -= count; retainedCost -= Math.max(8, count);
        }
    }
    private static final class GeometryRef { int references = 1; }
    private List<AnswerPlacement.Rect> replyRects(List<AnswerPlacement.Rect> rects) {
        if (rects == null || rects.size() != current.replies.size()) throw new IllegalArgumentException("Reply rectangles must match state indices");
        for (AnswerPlacement.Rect r : rects) if (r != null && (r.left < 0 || r.top < 0 || r.right > InkView.WIDTH || r.top > ReplyPlacement.MAX_Y))
            throw new IllegalArgumentException("Reply rectangle outside paper");
        return rects;
    }
    private static List<InkView.Stroke> freezeInk(List<InkView.Stroke> source, List<InkView.Stroke> previous) {
        if (source == null || source.size() > 12000) throw new IllegalArgumentException("Invalid ink list");
        ArrayList<InkView.Stroke> result = new ArrayList<>(source.size()); int points = 0;
        for (int i = 0; i < source.size(); i++) {
            InkView.Stroke stroke = source.get(i);
            if (stroke == null || !Float.isFinite(stroke.width) || stroke.width < .5f || stroke.width > 40
                    || stroke.points.isEmpty() || stroke.points.size() > 8000) throw new IllegalArgumentException("Invalid stroke");
            points += stroke.points.size(); if (points > InkView.MAX_POINTS) throw new IllegalArgumentException("Too many ink points");
            InkView.Stroke old = previous != null && i < previous.size() ? previous.get(i) : null;
            if (sameStroke(old, stroke)) { result.add(old); continue; }
            for (InkView.Point p : stroke.points) if (p == null || !Float.isFinite(p.x) || !Float.isFinite(p.y) || !Float.isFinite(p.pressure)
                    || p.x < 0 || p.x > InkView.WIDTH || p.y < 0 || p.y > InkView.HEIGHT || p.pressure < 0 || p.pressure > 1)
                throw new IllegalArgumentException("Invalid ink point");
            result.add(InkView.Stroke.immutableCopy(stroke));
        }
        return result;
    }
    private static List<NotebookStore.Reply> freezeReplies(List<NotebookStore.Reply> replies) {
        if (replies == null || replies.size() > 100) throw new IllegalArgumentException("Invalid reply list");
        for (NotebookStore.Reply reply : replies) if (reply == null || reply.mode == null || reply.input == null || reply.text == null || reply.source == null)
            throw new IllegalArgumentException("Invalid reply");
        return replies; // Each Reply and its optional geometry is immutable.
    }
    private static InkView.Stroke translate(InkView.Stroke stroke, float dx, float dy) {
        InkView.Stroke moved = new InkView.Stroke(stroke.erase, stroke.width, stroke.color);
        for (InkView.Point p : stroke.points) moved.points.add(new InkView.Point(p.x + dx, p.y + dy, p.pressure));
        return InkView.Stroke.immutableCopy(moved);
    }
    public static AnswerPlacement.Rect strokeBounds(InkView.Stroke stroke) {
        AnswerPlacement.Rect bounds = paintBounds(stroke);
        return new AnswerPlacement.Rect(Math.max(0, bounds.left), Math.max(0, bounds.top),
                Math.min(InkView.WIDTH, bounds.right), Math.min(InkView.HEIGHT, bounds.bottom));
    }
    private static AnswerPlacement.Rect paintBounds(InkView.Stroke stroke) {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE, radius = 0;
        for (InkView.Point p : stroke.points) {
            l = Math.min(l, p.x); t = Math.min(t, p.y); r = Math.max(r, p.x); b = Math.max(b, p.y);
            radius = Math.max(radius, stroke.erase ? stroke.width / 2 : stroke.width * (.45f + .9f * p.pressure) / 2);
        }
        // Include an anti-alias pixel. Rendering clips the original bitmap at the paper edge.
        radius += 1;
        return new AnswerPlacement.Rect(l - radius, t - radius, r + radius, b + radius);
    }
    private static boolean sameInk(List<InkView.Stroke> a, List<InkView.Stroke> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!sameStroke(a.get(i), b.get(i))) return false;
        return true;
    }
    private static boolean sameStroke(InkView.Stroke a, InkView.Stroke b) {
        if (a == b) return true;
        if (a == null || b == null || a.erase != b.erase || a.width != b.width || a.color != b.color || a.points.size() != b.points.size()) return false;
        for (int i = 0; i < a.points.size(); i++) {
            InkView.Point p = a.points.get(i), q = b.points.get(i);
            if (p != q && (q == null || p.x != q.x || p.y != q.y || p.pressure != q.pressure)) return false;
        }
        return true;
    }
    private static boolean sameReplies(List<NotebookStore.Reply> a, List<NotebookStore.Reply> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            NotebookStore.Reply p = a.get(i), q = b.get(i); if (p == q) continue;
            if (!p.mode.equals(q.mode) || !p.input.equals(q.input) || !p.text.equals(q.text) || !p.source.equals(q.source)
                    || !sameAnchor(p.anchor, q.anchor) || !samePlacement(p.placement, q.placement)) return false;
        }
        return true;
    }
    private static boolean sameAnchor(PaperAnchor a, PaperAnchor b) { return a == b || a != null && b != null && a.left == b.left && a.top == b.top && a.right == b.right && a.bottom == b.bottom; }
    private static boolean samePlacement(ReplyPlacement a, ReplyPlacement b) { return a == b || a != null && b != null && a.left == b.left && a.top == b.top && a.width == b.width && a.userPositioned == b.userPositioned; }
    private static boolean[] flags(List<Integer> indices, int count) {
        if (indices == null) throw new IllegalArgumentException("Missing indices");
        boolean[] result = new boolean[count];
        for (Integer i : indices) { if (i == null || i < 0 || i >= count) throw new IllegalArgumentException("Invalid component index"); result[i] = true; }
        return result;
    }
    private static List<Integer> indices(boolean[] flags) { ArrayList<Integer> result = new ArrayList<>(); for (int i = 0; i < flags.length; i++) if (flags[i]) result.add(i); return immutableList(result); }
    private static <T> List<T> immutableList(List<T> values) { return Collections.unmodifiableList(new ArrayList<>(values)); }
    private static boolean contains(AnswerPlacement.Rect outer, AnswerPlacement.Rect inner) { return outer.left <= inner.left && outer.top <= inner.top && outer.right >= inner.right && outer.bottom >= inner.bottom; }
    private static boolean touches(AnswerPlacement.Rect a, AnswerPlacement.Rect b) { return touchesTranslated(a, 0, 0, b); }
    private static boolean touchesTranslated(AnswerPlacement.Rect a, float dx, float dy, AnswerPlacement.Rect b) { return a.left + dx <= b.right && a.right + dx >= b.left && a.top + dy <= b.bottom && a.bottom + dy >= b.top; }
    private static AnswerPlacement.Rect union(AnswerPlacement.Rect a, AnswerPlacement.Rect b) { return a == null ? b : new AnswerPlacement.Rect(Math.min(a.left, b.left), Math.min(a.top, b.top), Math.max(a.right, b.right), Math.max(a.bottom, b.bottom)); }
    private static Result result(Status status) { return new Result(status, 0, 0); }
    private static float floorFloat(double value) { float rounded = (float)value; return rounded > value ? Math.nextDown(rounded) : rounded; }
}
