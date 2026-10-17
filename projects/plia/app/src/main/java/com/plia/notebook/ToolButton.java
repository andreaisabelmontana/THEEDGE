package com.plia.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Original vector toolbar icons with native click, keyboard and accessibility behavior. */
public final class ToolButton extends View {
    public enum Icon { BACK, PEN, COLOR, ERASER, UNDO, REDO, PREVIOUS, NEXT, SELECT, MOVE, DELETE, MORE,
        NEW_PAGE, EXPORT, COLLAPSE, EXPAND }

    private final String label;
    private final Icon icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF bounds = new RectF();
    private boolean showLabel;
    private float penWidth;
    private int inkColor = 0xff171917;

    public ToolButton(Context context, String label, Icon icon, Runnable action) {
        super(context);
        this.label = label == null ? "" : label;
        this.icon = icon == null ? Icon.MORE : icon;
        setMinimumWidth(dp(48));
        setMinimumHeight(dp(48));
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(this.label);
        setTooltipText(this.label);
        setOnClickListener(view -> { if (isEnabled() && action != null) action.run(); });
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
    }

    public void setShowLabel(boolean value) {
        if (showLabel == value) return;
        showLabel = value;
        requestLayout();
        invalidate();
    }

    /** Optional pen-preset preview in icon units; changing it does not select the tool. */
    public void setPenWidth(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return;
        penWidth = Math.max(0.5f, Math.min(6f, value));
        invalidate();
    }

    public void setInkColor(int value) { inkColor = value; invalidate(); }

    @Override public void setSelected(boolean value) { super.setSelected(value); invalidate(); }
    @Override public void setEnabled(boolean value) { super.setEnabled(value); invalidate(); }
    @Override protected void drawableStateChanged() { super.drawableStateChanged(); invalidate(); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.max(getSuggestedMinimumWidth(), dp(48));
        if (showLabel) width = Math.max(width, (int)Math.ceil(textPaint.measureText(label)) + dp(12));
        int height = Math.max(getSuggestedMinimumHeight(), dp(showLabel ? 64 : 48));
        setMeasuredDimension(resolveSizeAndState(width, widthSpec, 0), resolveSizeAndState(height, heightSpec, 0));
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.setSelected(isSelected());
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = dp(3);
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        if (isSelected() || isPressed()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(!isEnabled() ? Color.rgb(240, 240, 240)
                    : isPressed() ? Color.rgb(199, 199, 199) : Color.rgb(220, 220, 220));
            canvas.drawRoundRect(bounds, dp(7), dp(7), paint);
        }
        if (isFocused() || (isSelected() && isEnabled())) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(95, 95, 95));
            canvas.drawRoundRect(bounds, dp(7), dp(7), paint);
        }
        int foreground = isEnabled() ? Color.rgb(35, 35, 35) : Color.rgb(155, 155, 155);
        float labelSpace = showLabel ? dp(16) : 0;
        float availableWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        float availableHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom() - labelSpace);
        float size = Math.min(dp(32), Math.min(availableWidth, availableHeight));
        float left = getPaddingLeft() + (availableWidth - size) / 2;
        float top = getPaddingTop() + (availableHeight - size) / 2;
        if (size > 0) {
            int saved = canvas.save();
            canvas.translate(left, top);
            canvas.scale(size / 32f, size / 32f);
            paint.setColor(foreground);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.8f);
            drawIcon(canvas);
            canvas.restoreToCount(saved);
        }
        if (showLabel) {
            textPaint.setColor(foreground);
            CharSequence shown = TextUtils.ellipsize(label, textPaint, Math.max(0, availableWidth - dp(4)), TextUtils.TruncateAt.END);
            canvas.drawText(shown.toString(), getPaddingLeft() + availableWidth / 2, top + size + dp(12), textPaint);
        }
    }

    private void drawIcon(Canvas canvas) {
        path.reset();
        switch (icon) {
            case COLOR:
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(isEnabled() ? inkColor : Color.rgb(155, 155, 155));
                canvas.drawCircle(16, 16, 10, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.rgb(35, 35, 35));
                canvas.drawCircle(16, 16, 10, paint);
                if (isSelected()) {
                    paint.setColor(Color.WHITE); paint.setStrokeWidth(2.4f);
                    path.moveTo(11, 16); path.lineTo(15, 20); path.lineTo(22, 12);
                    canvas.drawPath(path, paint);
                }
                break;
            case BACK:
                canvas.drawLine(7, 16, 26, 16, paint);
                path.moveTo(14, 8); path.lineTo(6, 16); path.lineTo(14, 24);
                canvas.drawPath(path, paint); break;
            case PEN:
                path.moveTo(7, 24); path.lineTo(9, 18); path.lineTo(22, 5);
                path.lineTo(27, 10); path.lineTo(14, 23); path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(19, 8, 24, 13, paint);
                canvas.drawLine(9, 18, 14, 23, paint);
                if (penWidth > 0) { paint.setStrokeWidth(penWidth); canvas.drawLine(7, 29, 25, 29, paint); }
                break;
            case ERASER:
                path.moveTo(5, 20); path.lineTo(17, 7); path.quadTo(19, 5, 21, 7);
                path.lineTo(27, 13); path.lineTo(15, 26); path.lineTo(11, 26); path.close();
                canvas.drawPath(path, paint);
                canvas.drawLine(10, 15, 20, 21, paint);
                canvas.drawLine(11, 27, 27, 27, paint); break;
            case REDO:
                canvas.translate(32, 0); canvas.scale(-1, 1);
                drawUndo(canvas); break;
            case UNDO: drawUndo(canvas); break;
            case NEXT:
                canvas.translate(32, 0); canvas.scale(-1, 1);
                drawPrevious(canvas); break;
            case PREVIOUS: drawPrevious(canvas); break;
            case SELECT: drawSelectionHand(canvas); break;
            case MOVE:
                canvas.drawRect(7, 7, 25, 25, paint);
                canvas.drawLine(3, 16, 29, 16, paint); canvas.drawLine(16, 3, 16, 29, paint);
                path.moveTo(3, 16); path.lineTo(7, 12); path.moveTo(3, 16); path.lineTo(7, 20);
                path.moveTo(29, 16); path.lineTo(25, 12); path.moveTo(29, 16); path.lineTo(25, 20);
                path.moveTo(16, 3); path.lineTo(12, 7); path.moveTo(16, 3); path.lineTo(20, 7);
                path.moveTo(16, 29); path.lineTo(12, 25); path.moveTo(16, 29); path.lineTo(20, 25);
                canvas.drawPath(path, paint); break;
            case DELETE:
                canvas.drawLine(6, 8, 26, 8, paint);
                path.moveTo(9, 8); path.lineTo(10, 28); path.lineTo(22, 28); path.lineTo(23, 8);
                path.moveTo(12, 8); path.lineTo(12, 4); path.lineTo(20, 4); path.lineTo(20, 8);
                canvas.drawPath(path, paint);
                canvas.drawLine(14, 12, 14, 24, paint); canvas.drawLine(18, 12, 18, 24, paint); break;
            case MORE:
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(8, 16, 1.8f, paint); canvas.drawCircle(16, 16, 1.8f, paint);
                canvas.drawCircle(24, 16, 1.8f, paint); break;
            case NEW_PAGE:
                path.moveTo(7, 3); path.lineTo(21, 3); path.lineTo(27, 9); path.lineTo(27, 29);
                path.lineTo(7, 29); path.close(); canvas.drawPath(path, paint);
                canvas.drawLine(21, 3, 21, 9, paint); canvas.drawLine(21, 9, 27, 9, paint);
                canvas.drawLine(12, 19, 22, 19, paint); canvas.drawLine(17, 14, 17, 24, paint); break;
            case EXPORT:
                path.moveTo(7, 17); path.lineTo(7, 27); path.lineTo(25, 27); path.lineTo(25, 17);
                canvas.drawPath(path, paint); canvas.drawLine(16, 23, 16, 4, paint);
                path.reset(); path.moveTo(10, 10); path.lineTo(16, 4); path.lineTo(22, 10);
                canvas.drawPath(path, paint); break;
            case EXPAND:
                canvas.translate(32, 0); canvas.scale(-1, 1);
                drawCollapse(canvas); break;
            case COLLAPSE: drawCollapse(canvas); break;
        }
    }

    private void drawUndo(Canvas canvas) {
        path.moveTo(5, 13); path.cubicTo(14, 6, 27, 11, 27, 20); path.quadTo(27, 25, 23, 27);
        canvas.drawPath(path, paint);
        path.reset(); path.moveTo(6, 6); path.lineTo(5, 13); path.lineTo(12, 14);
        canvas.drawPath(path, paint);
    }

    private void drawPrevious(Canvas canvas) {
        path.moveTo(21, 6); path.lineTo(11, 16); path.lineTo(21, 26); canvas.drawPath(path, paint);
    }

    private void drawCollapse(Canvas canvas) {
        canvas.drawLine(7, 5, 7, 27, paint);
        path.moveTo(25, 8); path.lineTo(16, 16); path.lineTo(25, 24); canvas.drawPath(path, paint);
    }

    private void drawSelectionHand(Canvas canvas) {
        paint.setStrokeWidth(1.5f);
        path.moveTo(10, 18); path.lineTo(10, 10); path.quadTo(10, 8, 12, 8); path.quadTo(14, 8, 14, 10);
        path.lineTo(14, 15); path.lineTo(14, 6); path.quadTo(14, 4, 16, 4); path.quadTo(18, 4, 18, 6);
        path.lineTo(18, 15); path.lineTo(18, 8); path.quadTo(18, 6, 20, 6); path.quadTo(22, 6, 22, 8);
        path.lineTo(22, 16); path.lineTo(22, 12); path.quadTo(22, 10, 24, 10); path.quadTo(26, 10, 26, 12);
        path.lineTo(26, 20); path.quadTo(26, 24, 22, 27); path.lineTo(13, 27);
        path.quadTo(9, 24, 6, 19); path.quadTo(5, 17, 7, 16); path.quadTo(8, 16, 10, 18);
        path.close(); canvas.drawPath(path, paint);
        paint.setStrokeWidth(1.2f);
        path.reset(); path.moveTo(7, 3); path.lineTo(3, 3); path.lineTo(3, 8);
        path.moveTo(25, 3); path.lineTo(29, 3); path.lineTo(29, 8);
        path.moveTo(3, 24); path.lineTo(3, 29); path.lineTo(8, 29);
        path.moveTo(25, 29); path.lineTo(29, 29); path.lineTo(29, 24); canvas.drawPath(path, paint);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
