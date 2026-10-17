package com.plia.notebook;

/** Immutable selection bounds in the paper's logical coordinates. */
public final class PaperAnchor {
    public final float left, top, right, bottom;

    public PaperAnchor(float left, float top, float right, float bottom) {
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)
                || left < 0 || top < 0 || right > 1000 || bottom > 1600
                || left >= right || top >= bottom) {
            throw new IllegalArgumentException("Invalid paper anchor");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }
}
