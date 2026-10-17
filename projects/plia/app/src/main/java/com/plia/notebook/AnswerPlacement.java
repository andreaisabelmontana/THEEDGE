package com.plia.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure, deterministic paper-coordinate layout. Ink and earlier answers are never covered. */
public final class AnswerPlacement {
    public static final float MARGIN = 24, GAP = 24, MIN_SIDE_WIDTH = 360;
    public static final int MAX_REPLIES = 100;

    public static final class Rect {
        public final float left, top, right, bottom;
        public Rect(float left, float top, float right, float bottom) {
            if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right)
                    || !Float.isFinite(bottom) || right <= left || bottom <= top) {
                throw new IllegalArgumentException("Invalid paper rectangle");
            }
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        public float width() { return right - left; }
        public float height() { return bottom - top; }
        public boolean intersects(Rect other) {
            return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
        }
        /** Inclusive segment intersection, also used to stop a stroke before it crosses an answer. */
        public boolean crosses(float x1, float y1, float x2, float y2, float padding) {
            float l = left - padding, t = top - padding, r = right + padding, b = bottom + padding;
            if (Math.max(x1, x2) < l || Math.min(x1, x2) > r || Math.max(y1, y2) < t || Math.min(y1, y2) > b) return false;
            float dx = x2 - x1, dy = y2 - y1;
            float low = 0, high = 1;
            if (dx == 0) { if (x1 < l || x1 > r) return false; }
            else {
                float one = (l - x1) / dx, two = (r - x1) / dx;
                low = Math.max(low, Math.min(one, two)); high = Math.min(high, Math.max(one, two));
            }
            if (dy == 0) { if (y1 < t || y1 > b) return false; }
            else {
                float one = (t - y1) / dy, two = (b - y1) / dy;
                low = Math.max(low, Math.min(one, two)); high = Math.min(high, Math.max(one, two));
            }
            return low <= high;
        }
    }

    public interface HeightAtWidth { float measure(float logicalWidth); }
    private static final Comparator<Rect> ORDER = Comparator.comparingDouble((Rect r) -> r.top)
            .thenComparingDouble(r -> r.left).thenComparingDouble(r -> r.bottom).thenComparingDouble(r -> r.right);
    private final float width, minimumSideWidth;
    private final ArrayList<Rect> obstacles = new ArrayList<>(), replies = new ArrayList<>();
    private float inkBottom, height;

    public AnswerPlacement(float width, float baseHeight, List<Rect> ink, float minimumSideWidth) {
        if (!Float.isFinite(width) || width <= MARGIN * 2 || !Float.isFinite(baseHeight) || baseHeight <= 0
                || !Float.isFinite(minimumSideWidth) || minimumSideWidth <= 0) throw new IllegalArgumentException("Invalid paper dimensions");
        this.width = width; this.height = baseHeight;
        this.minimumSideWidth = Math.max(MIN_SIDE_WIDTH, minimumSideWidth);
        if (ink != null) for (Rect rect : ink) {
            if (rect == null) continue;
            obstacles.add(rect); inkBottom = Math.max(inkBottom, rect.bottom);
        }
        obstacles.sort(ORDER);
    }

    /** Keep an existing solution slot fixed while its replacement height is remeasured. */
    public void reserve(Rect rect) {
        if (rect == null || rect.left < 0 || rect.right > width || rect.top < 0)
            throw new IllegalArgumentException("Invalid reserved reply");
        if (replies.size() >= MAX_REPLIES) throw new IllegalStateException("Reply limit reached");
        replies.add(rect);
        int index = Collections.binarySearch(obstacles, rect, ORDER);
        obstacles.add(index < 0 ? -index - 1 : index, rect);
        height = Math.max(height, rect.bottom + MARGIN);
    }

    /** Calls measure for candidate widths, then commits exactly one rectangle. Null anchors go below all ink. */
    public Rect place(Rect anchor, HeightAtWidth measure) {
        if (replies.size() >= MAX_REPLIES) throw new IllegalStateException("Reply limit reached");
        if (measure == null) throw new IllegalArgumentException("Missing answer measurement");
        Rect result = null;
        if (anchor != null) {
            float top = Math.max(MARGIN, anchor.top);
            float rightStart = Math.max(MARGIN, anchor.right + GAP);
            float leftEnd = Math.min(width - MARGIN, anchor.left - GAP);
            // A drawn boundary has real stroke thickness outside its geometric anchor.
            // Include that small overhang before testing side space, or every pen box
            // would push its answer below despite having an empty adjacent column.
            for (Rect obstacle : obstacles) {
                if (obstacle.bottom <= anchor.top || obstacle.top >= anchor.bottom) continue;
                if (obstacle.left <= anchor.right && obstacle.right >= anchor.right && obstacle.right <= anchor.right + 48)
                    rightStart = Math.max(rightStart, obstacle.right + GAP);
                if (obstacle.right >= anchor.left && obstacle.left <= anchor.left && obstacle.left >= anchor.left - 48)
                    leftEnd = Math.min(leftEnd, obstacle.left - GAP);
            }
            if (width - MARGIN - rightStart >= minimumSideWidth) {
                result = free(rightStart, width - MARGIN, top, measure);
                if (result.top >= anchor.bottom) result = null;
            }
            if (result == null && leftEnd - MARGIN >= minimumSideWidth) {
                result = free(MARGIN, leftEnd, top, measure);
                if (result.top >= anchor.bottom) result = null;
            }
        }
        if (result == null) {
            float below = anchor == null ? inkBottom : anchor.bottom;
            result = free(MARGIN, width - MARGIN, Math.max(MARGIN, below + GAP), measure);
        }
        replies.add(result);
        int index = Collections.binarySearch(obstacles, result, ORDER);
        obstacles.add(index < 0 ? -index - 1 : index, result);
        height = Math.max(height, result.bottom + MARGIN);
        return result;
    }

    private Rect free(float left, float right, float top, HeightAtWidth measure) {
        float answerHeight = measure.measure(right - left);
        if (!Float.isFinite(answerHeight) || answerHeight <= 0) throw new IllegalArgumentException("Invalid answer measurement");
        for (Rect obstacle : obstacles) {
            if (obstacle.top >= top + answerHeight + GAP) break;
            if (left >= obstacle.right + GAP || right <= obstacle.left - GAP || top >= obstacle.bottom + GAP) continue;
            top = obstacle.bottom + GAP;
        }
        return new Rect(left, top, right, top + answerHeight);
    }

    public float height() { return height; }
    public List<Rect> replies() { return Collections.unmodifiableList(new ArrayList<>(replies)); }
}
