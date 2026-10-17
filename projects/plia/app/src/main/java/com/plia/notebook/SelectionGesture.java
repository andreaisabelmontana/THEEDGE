package com.plia.notebook;

import java.util.Arrays;
import java.util.Collections;

/** Transient AI Select lasso. Only its owning pointer's deliberate UP closes the polygon. */
public final class SelectionGesture {
    public static final int MAX_POINTS = 8000, MAX_SEGMENTS = 16;
    public static final float CONTINUE_DISTANCE = 24; // Compatibility only; separate strokes never join.
    public enum Event { DOWN, MOVE, UP, CANCEL, HOVER, OTHER }
    public enum Result { IGNORED, STARTED, UPDATED, OPEN, CLOSED, CANCELLED, INVALID_LOOP, LIMIT }

    private final float[] xy = new float[MAX_POINTS * 2];
    private int points, pointer = -1;
    private boolean moved, limited;

    public Result handle(Event event, int pointerId, float x, float y) {
        if (event == null || event == Event.HOVER || event == Event.OTHER) return Result.IGNORED;
        if (event == Event.CANCEL) {
            if (pointer == -1) return Result.IGNORED;
            pointer = -1; moved = false;
            return limited ? Result.LIMIT : Result.CANCELLED;
        }
        if (event == Event.DOWN) {
            if (pointerId < 0 || !valid(x, y) || pointer != -1) return Result.IGNORED;
            // A new contact always starts a new lasso. CANCEL/pen lifts cannot create connectors.
            clear(); pointer = pointerId; xy[0] = x; xy[1] = y; points = 1;
            return Result.STARTED;
        }
        if (pointer == -1 || pointer != pointerId) return Result.IGNORED;
        if (event != Event.MOVE && event != Event.UP) return Result.IGNORED;
        if (!valid(x, y)) { pointer = -1; moved = false; return Result.CANCELLED; }
        Result appended = append(x, y);
        if (event == Event.MOVE) return appended;
        pointer = -1;
        if (appended == Result.LIMIT || limited) return Result.LIMIT;
        if (!moved || !threeDistinctPoints()) return Result.INVALID_LOOP;
        // This segment is intentional lasso closure on UP, never a drawn crossing or pen-lift bridge.
        if (lastX() != xy[0] || lastY() != xy[1]) {
            if (points >= MAX_POINTS) { limited = true; return Result.LIMIT; }
            xy[points * 2] = xy[0]; xy[points * 2 + 1] = xy[1]; points++;
        }
        // Retain existing extent, area and path-length guards; admission remains bounded O(n).
        if (SelectionOutline.from(Collections.singletonList(snapshot())) == null) return Result.INVALID_LOOP;
        return Result.CLOSED;
    }

    private Result append(float x, float y) {
        if (x == lastX() && y == lastY()) return Result.IGNORED;
        if (points >= MAX_POINTS) { limited = true; return Result.LIMIT; }
        xy[points * 2] = x; xy[points * 2 + 1] = y; points++; moved = true;
        return Result.UPDATED;
    }

    private boolean threeDistinctPoints() {
        if (points < 3) return false;
        int second = 1;
        while (second < points && xy[second * 2] == xy[0] && xy[second * 2 + 1] == xy[1]) second++;
        for (int i = second + 1; i < points; i++) {
            float x = xy[i * 2], y = xy[i * 2 + 1];
            if ((x != xy[0] || y != xy[1]) && (x != xy[second * 2] || y != xy[second * 2 + 1])) return true;
        }
        return false;
    }

    public void clear() { points = 0; pointer = -1; moved = false; limited = false; }
    public boolean active() { return pointer != -1; }
    public int pointCount() { return points; }
    public float lastX() { return points == 0 ? 0 : xy[(points - 1) * 2]; }
    public float lastY() { return points == 0 ? 0 : xy[(points - 1) * 2 + 1]; }
    public float[] snapshot() { return Arrays.copyOf(xy, points * 2); }

    private static boolean valid(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && x >= 0 && x <= 1000 && y >= 0 && y <= 1600;
    }
}
