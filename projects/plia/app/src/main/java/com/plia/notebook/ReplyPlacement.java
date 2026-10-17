package com.plia.notebook;

/** A fixed answer location, separate from the input selection anchor. Height is remeasured. */
public final class ReplyPlacement {
    public static final int MAX_Y = 1000000;
    public final float left, top, width;
    /** True only after a deliberate user move; legacy refresh slots remain automatic. */
    public final boolean userPositioned;

    public ReplyPlacement(float left, float top, float width) {
        this(left, top, width, false);
    }
    public ReplyPlacement(float left, float top, float width, boolean userPositioned) {
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(width)
                || left < 0 || top < 0 || top > MAX_Y || width <= 0 || (double) left + width > 1000) {
            throw new IllegalArgumentException("Invalid reply placement");
        }
        this.left = left; this.top = top; this.width = width;
        this.userPositioned = userPositioned;
    }
}
