package com.plia.notebook;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative geometry only. Call for an explicit selection, never to infer actions from loaded ink. */
public final class BoxGesture {
    private BoxGesture() {}

    /** Coordinates and pressure are copied; callers cannot mutate the detector's snapshot. */
    public static final class Trace {
        private final float[] points;
        public final boolean erase;
        private final float left, top, right, bottom;

        public Trace(float[] triples, boolean erase) {
            if (triples == null || triples.length < 3 || triples.length % 3 != 0) throw new IllegalArgumentException("Expected x, y, pressure triples");
            points = triples.clone();
            this.erase = erase;
            float l = Float.POSITIVE_INFINITY, t = l, r = Float.NEGATIVE_INFINITY, b = r;
            for (int i = 0; i < points.length; i += 3) {
                if (!Float.isFinite(points[i]) || !Float.isFinite(points[i + 1]) || !Float.isFinite(points[i + 2]) || points[i + 2] < 0 || points[i + 2] > 1) throw new IllegalArgumentException("Invalid ink point");
                l = Math.min(l, points[i]); r = Math.max(r, points[i]);
                t = Math.min(t, points[i + 1]); b = Math.max(b, points[i + 1]);
            }
            left = l; top = t; right = r; bottom = b;
        }
    }

    public static final class Selection {
        public final float left, top, right, bottom;
        public final int firstBoundary;
        public final int[] selectedIndices;
        public final String fingerprint;

        private Selection(float left, float top, float right, float bottom, int firstBoundary, List<Integer> selected, List<Trace> all) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.firstBoundary = firstBoundary;
            selectedIndices = new int[selected.size()];
            for (int i = 0; i < selected.size(); i++) selectedIndices[i] = selected.get(i);
            fingerprint = BoxGesture.fingerprint(all, selectedIndices);
        }
    }

    /** A suffix of 1-4 rectangle strokes, or one continuous ellipse, may be the new boundary.
     * Only indices before recentStart are content; all newer traces are transient selection marks. */
    public static Selection detect(List<Trace> all, int recentStart) {
        if (all == null || recentStart < 0 || recentStart >= all.size()) return null;
        List<Trace> snapshot = new ArrayList<>(all);
        for (Trace trace : snapshot) if (trace == null) return null;
        for (int count = 1; count <= 4 && snapshot.size() - count >= recentStart; count++) {
            Selection selection = candidate(snapshot, snapshot.size() - count, recentStart);
            if (selection != null) return selection;
        }
        return ellipse(snapshot, snapshot.size() - 1, recentStart);
    }

    /** Fit only an almost closed, once-around ellipse. Segmented ellipses are deliberately unsupported. */
    private static Selection ellipse(List<Trace> all, int first, int contentEnd) {
        if (first == 0) return null;
        Trace boundary = all.get(first);
        if (boundary.erase || boundary.points.length < 17 * 3) return null;
        float l = boundary.left, t = boundary.top, r = boundary.right, b = boundary.bottom;
        float w = r - l, h = b - t, shortSide = Math.min(w, h);
        if (!Float.isFinite(w) || !Float.isFinite(h) || w < 72 || h < 60 || Math.max(w, h) / shortSide > 20) return null;
        float[] p = boundary.points;
        if (!near(p[0], p[1], p[p.length - 3], p[p.length - 2], Math.min(14, shortSide * .10f))) return null;
        double cx = l + w / 2.0, cy = t + h / 2.0, rx = w / 2.0, ry = h / 2.0;
        double minimumRadius = Double.POSITIVE_INFINITY, maximumRadius = 0;
        double turn = 0, travel = 0, radialError = 0, positive = 0, negative = 0;
        int movingSegments = 0;
        for (int i = 0; i < p.length; i += 3) {
            int next = (i + 3) % p.length;
            double x = (p[i] - cx) / rx, y = (p[i + 1] - cy) / ry;
            double nx = (p[next] - cx) / rx, ny = (p[next + 1] - cy) / ry;
            double radius = Math.hypot(x, y), nextRadius = Math.hypot(nx, ny);
            if (radius < .82 || radius > 1.16) return null;
            maximumRadius = Math.max(maximumRadius, radius);
            minimumRadius = Math.min(minimumRadius, originDistance(x, y, nx, ny));
            double angle = Math.atan2(x * ny - y * nx, x * nx + y * ny);
            if (Math.abs(angle) > Math.PI / 6) return null; // No sparse chords or sharp shortcuts.
            if (Math.abs(angle) > .001) movingSegments++;
            turn += angle; travel += Math.abs(angle);
            radialError += Math.abs((radius + nextRadius) / 2 - 1) * Math.abs(angle);
            if (angle > 0) positive += angle; else negative -= angle;
        }
        // One winding, near-uniform radius, and very little retracing reject spirals, scribbles and crosses.
        if (movingSegments < 16 || minimumRadius < .80 || Math.abs(Math.abs(turn) - Math.PI * 2) > .05
                || Math.min(positive, negative) > .12 || radialError / travel > .075) return null;

        double minorRadius = Math.min(rx, ry);
        double clearance = Math.max(8, Math.min(18, shortSide * .06));
        double inner = minimumRadius - clearance / minorRadius;
        ArrayList<Integer> selected = new ArrayList<>();
        for (int i = 0; i < contentEnd; i++) {
            Trace trace = all.get(i);
            // The inner ellipse lies strictly inside every boundary segment. Its convexity also
            // keeps sparse content segments inside. All uncertain edge/eraser cases fail closed.
            if (!trace.erase && insideEllipse(trace, cx, cy, rx, ry, inner)) selected.add(i);
            else if (intersectsEllipse(trace, cx, cy, rx, ry, maximumRadius + (trace.erase ? 20 : 10) / minorRadius)) return null;
        }
        return selected.isEmpty() ? null : new Selection(l, t, r, b, first, selected, all);
    }

    private static boolean insideEllipse(Trace trace, double cx, double cy, double rx, double ry, double radius) {
        float[] p = trace.points;
        for (int i = 0; i < p.length; i += 3) if (Math.hypot((p[i] - cx) / rx, (p[i + 1] - cy) / ry) >= radius) return false;
        return true;
    }

    private static boolean intersectsEllipse(Trace trace, double cx, double cy, double rx, double ry, double radius) {
        float[] p = trace.points;
        double x = (p[0] - cx) / rx, y = (p[1] - cy) / ry;
        if (Math.hypot(x, y) <= radius) return true;
        for (int i = 3; i < p.length; i += 3) {
            double nx = (p[i] - cx) / rx, ny = (p[i + 1] - cy) / ry;
            if (originDistance(x, y, nx, ny) <= radius) return true;
            x = nx; y = ny;
        }
        return false;
    }

    private static double originDistance(double x, double y, double nx, double ny) {
        double dx = nx - x, dy = ny - y, lengthSquared = dx * dx + dy * dy;
        double fraction = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, -(x * dx + y * dy) / lengthSquared));
        return Math.hypot(x + fraction * dx, y + fraction * dy);
    }

    private static Selection candidate(List<Trace> all, int first, int contentEnd) {
        if (first == 0) return null; // An empty box is never a request.
        float l = Float.POSITIVE_INFINITY, t = l, r = Float.NEGATIVE_INFINITY, b = r;
        for (int i = first; i < all.size(); i++) {
            Trace trace = all.get(i);
            if (trace.erase || trace.points.length < 6) return null;
            l = Math.min(l, trace.left); t = Math.min(t, trace.top);
            r = Math.max(r, trace.right); b = Math.max(b, trace.bottom);
        }
        float w = r - l, h = b - t, shortSide = Math.min(w, h);
        // Logical page units. Thin underlines and character-sized loops are not boxes.
        if (!Float.isFinite(w) || !Float.isFinite(h) || w < 32 || h < 26 || Math.max(w, h) / shortSide > 20) return null;
        float edge = Math.max(3, Math.min(24, shortSide * .16f));
        float corner = Math.max(4, Math.min(28, shortSide * .17f));
        float gap = Math.max(5, Math.min(35, shortSide * .25f));
        if (!closedLoop(all, first, gap)) return null;
        ArrayList<float[]> top = new ArrayList<>(), bottom = new ArrayList<>(), left = new ArrayList<>(), right = new ArrayList<>();
        boolean[] corners = new boolean[4];
        double length = 0;
        for (int i = first; i < all.size(); i++) {
            float[] p = all.get(i).points;
            for (int j = 0; j < p.length; j += 3) {
                float x = p[j], y = p[j + 1];
                corners[0] |= near(x, y, l, t, corner);
                corners[1] |= near(x, y, r, t, corner);
                corners[2] |= near(x, y, r, b, corner);
                corners[3] |= near(x, y, l, b, corner);
                if (Math.min(Math.min(x - l, r - x), Math.min(y - t, b - y)) > edge) return null;
                if (j == 0) continue;
                float px = p[j - 3], py = p[j - 2];
                length += Math.hypot(x - px, y - py);
                // Checking whole segments also catches sparsely sampled crosses/diagonals.
                if (segmentIntersects(px, py, x, y, l + edge + .01f, t + edge + .01f, r - edge - .01f, b - edge - .01f)) return null;
                if (Math.max(py, y) <= t + edge) add(top, px - l, x - l);
                if (Math.min(py, y) >= b - edge) add(bottom, px - l, x - l);
                if (Math.max(px, x) <= l + edge) add(left, py - t, y - t);
                if (Math.min(px, x) >= r - edge) add(right, py - t, y - t);
            }
        }
        for (boolean covered : corners) if (!covered) return null;
        double perimeter = 2.0 * (w + h);
        if (length < perimeter * .78 || length > perimeter * 1.5) return null;
        if (!covered(top, w, gap) || !covered(bottom, w, gap) || !covered(left, h, gap) || !covered(right, h, gap)) return null;

        float clearance = edge + 2;
        ArrayList<Integer> selected = new ArrayList<>();
        for (int i = 0; i < contentEnd; i++) {
            Trace trace = all.get(i);
            // The renderer's erasers can be 32 units wide; raw traces cannot prove remaining ink.
            if (trace.erase) {
                if (intersects(trace, l - 20, t - 20, r + 20, b + 20)) return null;
                continue;
            }
            if (trace.left > l + clearance && trace.right < r - clearance && trace.top > t + clearance && trace.bottom < b - clearance) selected.add(i);
            // Allow for the current pen's rendered width, not just its centerline.
            else if (intersects(trace, l - 10, t - 10, r + 10, b + 10)) return null;
        }
        return selected.isEmpty() ? null : new Selection(l, t, r, b, first, selected, all);
    }

    private static boolean near(float x, float y, float cx, float cy, float radius) { return Math.hypot(x - cx, y - cy) <= radius; }
    private static void add(List<float[]> intervals, float a, float b) { intervals.add(new float[] {Math.min(a, b), Math.max(a, b)}); }

    private static boolean covered(List<float[]> intervals, float size, float gap) {
        if (intervals.isEmpty()) return false;
        intervals.sort(Comparator.comparingDouble(interval -> interval[0]));
        float end = 0, covered = 0;
        for (float[] interval : intervals) {
            if (interval[0] - end > gap) return false;
            if (interval[1] > end) {
                covered += interval[1] - Math.max(end, interval[0]);
                end = interval[1];
            }
        }
        return size - end <= gap && covered >= size * .82f;
    }

    /** Endpoints must form one closed loop, independent of stroke drawing order/direction. */
    private static boolean closedLoop(List<Trace> all, int first, float tolerance) {
        int count = all.size() - first;
        float[] xs = new float[count * 2], ys = new float[count * 2];
        int[] pairs = new int[count * 2];
        java.util.Arrays.fill(pairs, -1);
        for (int i = 0; i < count; i++) {
            float[] p = all.get(first + i).points;
            xs[i * 2] = p[0]; ys[i * 2] = p[1];
            xs[i * 2 + 1] = p[p.length - 3]; ys[i * 2 + 1] = p[p.length - 2];
        }
        return pairEndpoints(xs, ys, pairs, tolerance);
    }

    private static boolean pairEndpoints(float[] xs, float[] ys, int[] pairs, float tolerance) {
        int a = 0;
        while (a < pairs.length && pairs[a] != -1) a++;
        if (a == pairs.length) {
            boolean[] visited = new boolean[pairs.length / 2];
            visited[0] = true;
            int current = pairs[0], visitedCount = 1;
            while (current / 2 != 0) {
                int next = current / 2;
                if (visited[next]) return false;
                visited[next] = true;
                visitedCount++;
                current = pairs[current ^ 1];
            }
            return visitedCount == visited.length;
        }
        for (int b = a + 1; b < pairs.length; b++) {
            if (pairs[b] != -1 || pairs.length > 2 && a / 2 == b / 2 || !near(xs[a], ys[a], xs[b], ys[b], tolerance)) continue;
            pairs[a] = b; pairs[b] = a;
            if (pairEndpoints(xs, ys, pairs, tolerance)) return true;
            pairs[a] = -1; pairs[b] = -1;
        }
        return false;
    }

    private static boolean intersects(Trace trace, float l, float t, float r, float b) {
        if (trace.right < l || trace.left > r || trace.bottom < t || trace.top > b) return false;
        float[] p = trace.points;
        if (p[0] >= l && p[0] <= r && p[1] >= t && p[1] <= b) return true;
        for (int i = 3; i < p.length; i += 3) if (segmentIntersects(p[i - 3], p[i - 2], p[i], p[i + 1], l, t, r, b)) return true;
        return false;
    }

    private static boolean segmentIntersects(float x1, float y1, float x2, float y2, float l, float t, float r, float b) {
        double low = 0, high = 1;
        double dx = x2 - x1, dy = y2 - y1;
        if (dx == 0) { if (x1 < l || x1 > r) return false; }
        else {
            double a = (l - x1) / dx, z = (r - x1) / dx;
            low = Math.max(low, Math.min(a, z)); high = Math.min(high, Math.max(a, z));
            if (low > high) return false;
        }
        if (dy == 0) { if (y1 < t || y1 > b) return false; }
        else {
            double a = (t - y1) / dy, z = (b - y1) / dy;
            low = Math.max(low, Math.min(a, z)); high = Math.min(high, Math.max(a, z));
            if (low > high) return false;
        }
        return true;
    }

    /** Hash only explicitly selected immutable content, independently of its selection boundary. */
    public static String fingerprint(List<Trace> traces, int[] selectedIndices) {
        if (traces == null || selectedIndices == null) throw new IllegalArgumentException("Missing content selection");
        List<Trace> all = new ArrayList<>(traces);
        int[] selected = selectedIndices.clone();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            integer(digest, 1); integer(digest, selected.length);
            for (int index : selected) {
                if (index < 0 || index >= all.size() || all.get(index) == null) throw new IllegalArgumentException("Invalid content index");
                Trace trace = all.get(index);
                float[] points = trace.points;
                integer(digest, trace.erase ? 1 : 0);
                integer(digest, points.length);
                for (float value : points) integer(digest, Float.floatToIntBits(value));
            }
            char[] hex = "0123456789abcdef".toCharArray();
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(hex[(value & 255) >>> 4]).append(hex[value & 15]);
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private static void integer(MessageDigest digest, int value) {
        digest.update((byte)(value >>> 24)); digest.update((byte)(value >>> 16)); digest.update((byte)(value >>> 8)); digest.update((byte)value);
    }
}
