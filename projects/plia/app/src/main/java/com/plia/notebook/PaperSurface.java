package com.plia.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** One white paper: fixed original ink coordinates, with answers in unoccupied paper space. */
public final class PaperSurface extends ViewGroup {
    public interface EditListener {
        /** Cancel pending AI work before a local edit gesture; this must never send a request. */
        void started();
        /** Apply editor.state() to the current page, refresh ink/replies and save once. */
        void committed(PageEditor.State state);
        void selectionChanged(boolean available);
        void warning(String message);
    }
    private final InkView ink;
    private final Typeface handwriting;
    private final ArrayList<NotebookStore.Reply> replies = new ArrayList<>();
    private final ArrayList<AnswerPlacement.Rect> inkBounds = new ArrayList<>(), answerBounds = new ArrayList<>();
    private boolean think;
    private boolean repliesVisible = true;
    private boolean inkBoundsDirty = true;
    private int gesturePointer = -1;
    private float lastX, lastY;
    private boolean blockedGesture;
    private View editChildTarget;
    private PageEditor editor;
    private EditListener editListener;
    private PageEditor.Selection editSelection;
    private PageEditor.State editStartState;
    private final ArrayList<AnswerPlacement.Rect> editFrames = new ArrayList<>();
    private final Paint editPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean moveMode, editDragging;
    private int editPointer = -1;
    private float editStartX, editStartY, editX, editY, previewX, previewY;
    private static final float EDIT_SLOP = 4;

    public PaperSurface(Context context, InkView ink, Typeface handwriting) {
        super(context);
        if (ink == null || ink.getParent() != null) throw new IllegalArgumentException("Ink must be unattached");
        this.ink = ink; this.handwriting = handwriting;
        ink.setSelectionOverlayOnParent(true);
        setBackgroundColor(Color.WHITE);
        setClipChildren(true);
        addView(ink, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        inkChanged();
    }

    public void setEditor(PageEditor value) {
        clearEditSelection(); editor = value;
    }
    public void setEditListener(EditListener value) { editListener = value; notifyEditSelection(); }
    public boolean isMoveMode() { return moveMode; }
    public void setMoveMode(boolean value) {
        clearEditSelection(); moveMode = value;
        if (value) ink.setSelectionMode(false);
    }
    public boolean hasEditSelection() {
        return moveMode && editor != null && editor.isCurrent(editSelection);
    }
    /** Context, layout and tool changes discard only transient edit UI, never content/history. */
    public void clearEditSelection() {
        if (editPointer == -1 && editSelection == null && editStartState == null && editFrames.isEmpty()) return;
        editPointer = -1; editDragging = false; previewX = previewY = 0;
        editSelection = null; editStartState = null; editFrames.clear();
        if (editor != null) editor.cancelSelection();
        requestDisallowInterceptTouchEvent(false); invalidate(); notifyEditSelection();
    }
    /** Explicit Delete toolbar action. The editor retains one undoable before-state. */
    public boolean deleteEditSelection() {
        if (!hasEditSelection() || editPointer != -1) return false;
        PageEditor source = editor; PageEditor.Selection selected = editSelection;
        if (editListener != null) editListener.started();
        if (!moveMode || editor != source || !source.isCurrent(selected)) return false;
        PageEditor.Result result = source.delete(selected);
        if (!result.applied()) { editWarning(result.status); return false; }
        editCommitted(source); return true;
    }

    /** Think uses saved anchors; other modes retain every reply below the handwriting. */
    public void setReplies(List<NotebookStore.Reply> values, boolean think) {
        if (values != null && values.size() > AnswerPlacement.MAX_REPLIES) throw new IllegalArgumentException("Too many replies");
        clearEditSelection(); this.think = think;
        disposeReplyViews();
        while (getChildCount() > 1) removeViewAt(getChildCount() - 1);
        replies.clear();
        if (values != null) for (NotebookStore.Reply reply : values) {
            if (reply == null) continue;
            replies.add(reply);
            LinearLayout panel = new LinearLayout(getContext());
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setVisibility(repliesVisible ? View.VISIBLE : View.GONE);
            panel.setBackgroundColor(Color.WHITE);
            int padding = dp(8); panel.setPadding(padding, padding, padding, padding);
            if ("demo".equals(reply.source)) {
                TextView warning = new TextView(getContext());
                warning.setText("Sample only; handwriting was not assessed"); warning.setTextColor(0xff555555); warning.setTextSize(13);
                panel.addView(warning, new LinearLayout.LayoutParams(-1, -2));
            }
            panel.addView(new SolutionView(getContext(), reply.text, handwriting), new LinearLayout.LayoutParams(-1, -2));
            addView(panel, new LayoutParams(-2, -2));
        }
        inkChanged();
    }

    /** Release every detached renderer; old refresh callbacks cannot resize a replacement. */
    public void disposeReplyViews() {
        for (int i = 1; i < getChildCount(); i++) {
            View panel = getChildAt(i);
            if (panel instanceof ViewGroup) {
                ViewGroup group = (ViewGroup)panel;
                for (int j = 0; j < group.getChildCount(); j++)
                    if (group.getChildAt(j) instanceof SolutionView) ((SolutionView)group.getChildAt(j)).dispose();
            }
        }
    }

    /** Visibility only. The original reply objects, text and anchors remain intact. */
    public void setRepliesVisible(boolean visible) {
        if (repliesVisible == visible) return;
        clearEditSelection();
        repliesVisible = visible;
        for (int i = 1; i < getChildCount(); i++) getChildAt(i).setVisibility(visible ? View.VISIBLE : View.GONE);
        answerBounds.clear();
        requestLayout(); invalidate();
    }

    /** Actual first solution position, including when answers are hidden. */
    public ReplyPlacement firstReplyPlacement() {
        if (replies.isEmpty()) return null;
        if (getWidth() > 0) measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        if (answerBounds.isEmpty()) return null;
        AnswerPlacement.Rect rect = answerBounds.get(0);
        ReplyPlacement original = replies.get(0).placement;
        return new ReplyPlacement(rect.left, rect.top, rect.width(), original != null && original.userPositioned);
    }

    /** Call after loading, clearing, undoing or committing ink, not for each stylus sample. */
    public void inkChanged() {
        clearEditSelection();
        inkBoundsDirty = true;
        // InkView already invalidates its bitmap. Only automatic visible answers depend on ink.
        if (repliesVisible && hasAutomaticReplies()) { requestLayout(); invalidate(); }
    }

    private boolean hasAutomaticReplies() {
        float scale = Math.max(1, getMeasuredWidth()) / (float)InkView.WIDTH;
        float minimumWidth = readableWidth(scale);
        for (NotebookStore.Reply reply : replies) if (needsAutomaticLayout(reply, minimumWidth)) return true;
        return false;
    }

    private float readableWidth(float scale) {
        // Side columns must hold a readable worked example; otherwise use the page below the ink.
        return Math.min(InkView.WIDTH - 2 * AnswerPlacement.MARGIN, Math.max(680, dp(520) / scale));
    }

    private static boolean needsAutomaticLayout(NotebookStore.Reply reply, float minimumWidth) {
        ReplyPlacement pin = reply.placement;
        return pin == null || (!pin.userPositioned && pin.width + .01f < minimumWidth);
    }

    /** Hidden answers are resolved on explicit measure/capture/show, not on every pen-up. */
    private void updateInkBoundsIfNeeded(float minimumWidth) {
        boolean automatic = false;
        for (NotebookStore.Reply reply : replies) if (needsAutomaticLayout(reply, minimumWidth)) { automatic = true; break; }
        if (!inkBoundsDirty || !automatic) return;
        inkBounds.clear();
        for (InkView.Stroke stroke : ink.snapshot()) {
            // Conservative bounds deliberately keep erased old ink reserved; no raster inference is needed.
            if (stroke.erase || stroke.points.isEmpty()) continue;
            float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY, right = 0, bottom = 0;
            for (InkView.Point point : stroke.points) {
                left = Math.min(left, point.x); right = Math.max(right, point.x);
                top = Math.min(top, point.y); bottom = Math.max(bottom, point.y);
            }
            float padding = stroke.width + 2;
            inkBounds.add(new AnswerPlacement.Rect(Math.max(0, left - padding), Math.max(0, top - padding),
                    Math.min(InkView.WIDTH, right + padding), Math.min(InkView.HEIGHT, bottom + padding)));
        }
        inkBoundsDirty = false;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = Math.max(1, MeasureSpec.getSize(widthSpec));
        if (width != getWidth()) clearEditSelection();
        float scale = width / (float)InkView.WIDTH;
        ink.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(InkView.HEIGHT * scale), MeasureSpec.EXACTLY));
        float minimumWidth = readableWidth(scale);
        updateInkBoundsIfNeeded(minimumWidth);
        AnswerPlacement layout = new AnswerPlacement(InkView.WIDTH, InkView.HEIGHT, inkBounds,
                minimumWidth);
        ArrayList<AnswerPlacement.Rect> previousBounds = new ArrayList<>(answerBounds);
        answerBounds.clear();
        // Reserve every explicit position first so earlier automatic replies cannot cover it.
        for (int i = 0; i < replies.size(); i++) answerBounds.add(null);
        for (int i = 0; i < replies.size(); i++) {
            ReplyPlacement pin = replies.get(i).placement;
            if (needsAutomaticLayout(replies.get(i), minimumWidth)) continue;
            View answer = getChildAt(i + 1);
            float height = measureReply(answer, pin.width, scale);
            AnswerPlacement.Rect placed = new AnswerPlacement.Rect(pin.left, pin.top, pin.left + pin.width, pin.top + height);
            answerBounds.set(i, placed); layout.reserve(placed);
        }
        for (int i = 0; i < replies.size(); i++) {
            if (answerBounds.get(i) != null) continue;
            final View answer = getChildAt(i + 1);
            PaperAnchor anchor = think ? replies.get(i).anchor : null;
            AnswerPlacement.Rect selected = anchor == null ? null
                    : new AnswerPlacement.Rect(anchor.left, anchor.top, anchor.right, anchor.bottom);
            AnswerPlacement.Rect placed = layout.place(selected, logicalWidth -> {
                int pixels = Math.max(1, (int)Math.floor(logicalWidth * scale));
                answer.measure(MeasureSpec.makeMeasureSpec(pixels, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                return Math.max(1, (float)Math.ceil(answer.getMeasuredHeight() / scale));
            });
            // A rejected side candidate may have been measured last; restore the chosen width.
            answer.measure(MeasureSpec.makeMeasureSpec(Math.max(1, (int)Math.floor(placed.width() * scale)), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            answerBounds.set(i, placed);
        }
        if (!sameBounds(previousBounds, answerBounds)) clearEditSelection();
        setMeasuredDimension(width, repliesVisible ? (int)Math.ceil(layout.height() * scale) : ink.getMeasuredHeight());
    }

    private float measureReply(View answer, float width, float scale) {
        answer.measure(MeasureSpec.makeMeasureSpec(Math.max(1, (int)Math.floor(width * scale)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return Math.max(1, (float)Math.ceil(answer.getMeasuredHeight() / scale));
    }
    private static boolean sameBounds(List<AnswerPlacement.Rect> a, List<AnswerPlacement.Rect> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            AnswerPlacement.Rect x = a.get(i), y = b.get(i);
            if (x == y) continue;
            if (x == null || y == null || x.left != y.left || x.top != y.top || x.right != y.right || x.bottom != y.bottom) return false;
        }
        return true;
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        ink.layout(0, 0, getMeasuredWidth(), ink.getMeasuredHeight());
        float scale = getMeasuredWidth() / (float)InkView.WIDTH;
        for (int i = 0; i < answerBounds.size(); i++) {
            AnswerPlacement.Rect rect = answerBounds.get(i);
            View answer = getChildAt(i + 1);
            int x = Math.round(rect.left * scale), y = Math.round(rect.top * scale);
            answer.layout(x, y, x + answer.getMeasuredWidth(), y + answer.getMeasuredHeight());
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        // Keep the temporary selection visible over every reply child, including opaque panels.
        ink.drawSelectionOverlay(canvas);
        drawEditOverlay(canvas);
    }

    /** Prevent both starting on an answer and dragging an existing stroke across one. */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && editChildTarget != null) {
            MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
            dispatchEditChild(editChildTarget, cancel); cancel.recycle(); editChildTarget = null;
        } else if (editChildTarget != null) {
            View target = editChildTarget;
            if (target.getParent() != this) { editChildTarget = null; return false; }
            boolean handled = dispatchEditChild(target, event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) editChildTarget = null;
            return handled;
        }
        if (moveMode) return dispatchEditEvent(event);
        // AI selection is an overlay, not ink. Reply panels must not intercept or cancel it.
        // PaperDraft renders only stored handwriting, so crossing a reply never uploads its text.
        if (ink.isSelectionMode()) {
            gesturePointer = -1; blockedGesture = false;
            if (action == MotionEvent.ACTION_DOWN && (event.getY() < 0 || event.getY() > ink.getHeight())) return false;
            return ink.dispatchTouchEvent(event);
        }
        float scale = getWidth() / (float)InkView.WIDTH;
        if (scale <= 0) return super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_DOWN) {
            gesturePointer = -1; blockedGesture = false;
            float x = event.getX() / scale, y = event.getY() / scale;
            int tool = event.getToolType(0);
            boolean pen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER || tool == MotionEvent.TOOL_TYPE_MOUSE;
            if (crossesAnswer(x, y, x, y)) {
                if (!pen) return super.dispatchTouchEvent(event); // Preserve equation swipes and enclosing vertical scrolling.
                blockedGesture = true; requestDisallowInterceptTouchEvent(true); return true;
            }
            boolean handled = super.dispatchTouchEvent(event);
            if (handled) { gesturePointer = event.getPointerId(0); lastX = x; lastY = y; }
            return handled;
        }
        if (blockedGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                blockedGesture = false; gesturePointer = -1; requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        int pointer = gesturePointer < 0 ? -1 : event.findPointerIndex(gesturePointer);
        boolean pointerEnding = action == MotionEvent.ACTION_UP || (action == MotionEvent.ACTION_POINTER_UP
                && event.getPointerId(event.getActionIndex()) == gesturePointer);
        if (pointer >= 0 && (action == MotionEvent.ACTION_MOVE || pointerEnding)) {
            boolean crosses = false;
            for (int i = 0; i <= event.getHistorySize(); i++) {
                float x = (i == event.getHistorySize() ? event.getX(pointer) : event.getHistoricalX(pointer, i)) / scale;
                float y = (i == event.getHistorySize() ? event.getY(pointer) : event.getHistoricalY(pointer, i)) / scale;
                if (crossesAnswer(lastX, lastY, x, y)) { crosses = true; break; }
                lastX = x; lastY = y;
            }
            if (crosses) {
                MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel); cancel.recycle();
                gesturePointer = -1; blockedGesture = !pointerEnding;
                requestDisallowInterceptTouchEvent(blockedGesture); return true;
            }
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (pointerEnding || action == MotionEvent.ACTION_CANCEL) gesturePointer = -1;
        return handled;
    }

    private boolean crossesAnswer(float x1, float y1, float x2, float y2) {
        if (!repliesVisible) return false;
        for (AnswerPlacement.Rect rect : answerBounds) if (rect.crosses(x1, y1, x2, y2, 20)) return true;
        return false;
    }

    /** Local edit gestures never enter InkView or its AI-selection completion callback. */
    private boolean dispatchEditEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (!isEnabled() || editor == null) { if (editPointer != -1) clearEditSelection(); return false; }
        if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE
                || action == MotionEvent.ACTION_HOVER_EXIT) return editPointer != -1;
        if (action == MotionEvent.ACTION_DOWN) {
            if (editPointer != -1) clearEditSelection();
            int index = event.getActionIndex(), tool = event.getToolType(index);
            if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER && tool != MotionEvent.TOOL_TYPE_MOUSE) {
                // A saved finger-drawing preference must not draw ink while Move is selected.
                float x = editLogical(event.getX(index)), y = editLogical(event.getY(index));
                int reply = repliesVisible ? hitReply(answerBounds, x, y) : -1;
                if (reply < 0) return false;
                // Keep the entire child swipe together, without entering local edit or AI admission.
                View target = getChildAt(reply + 1);
                boolean handled = dispatchEditChild(target, event);
                if (handled) editChildTarget = target;
                return handled; // A plain-text/padding miss may scroll; never fall through to ink.
            }
            float x = editLogical(event.getX(index)), y = editLogical(event.getY(index));
            if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || x > InkView.WIDTH || y < 0 || y > editLogical(getHeight())) return false;
            PageEditor source = editor;
            if (editListener != null) editListener.started();
            if (!moveMode || editor != source || !isEnabled()) return true;
            List<AnswerPlacement.Rect> rects = renderedEditRects();
            if (rects == null) { clearEditSelection(); editWarning(PageEditor.Status.STALE_SELECTION); return true; }
            if (hasEditSelection() && contains(editSelection.bounds, x, y)) {
                editDragging = true;
            } else {
                clearEditSelection();
                int reply = hitReply(rects, x, y);
                if (reply >= 0) {
                    selectForEdit(source.selectReply(reply, rects)); editDragging = true;
                }
            }
            editPointer = event.getPointerId(index); editStartState = source.state();
            editStartX = editX = x; editStartY = editY = y;
            previewX = previewY = 0;
            requestDisallowInterceptTouchEvent(true); invalidate(); return true;
        }
        if (editPointer == -1) return false;
        if (action == MotionEvent.ACTION_CANCEL) { clearEditSelection(); return true; }
        if (editor.state() != editStartState) { clearEditSelection(); editWarning(PageEditor.Status.STALE_SELECTION); return true; }
        if (action == MotionEvent.ACTION_POINTER_DOWN) return true;
        int ownerIndex = event.findPointerIndex(editPointer);
        if (ownerIndex < 0) { clearEditSelection(); return true; }
        boolean ownerUp = (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                && event.getPointerId(event.getActionIndex()) == editPointer;
        if (action != MotionEvent.ACTION_MOVE && !ownerUp) return true;
        if (ownerUp && (event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) { clearEditSelection(); return true; }
        float x = editLogical(event.getX(ownerIndex)), y = editLogical(event.getY(ownerIndex));
        if (!Float.isFinite(x) || !Float.isFinite(y)) { clearEditSelection(); return true; }
        editX = x; editY = y;
        if (editDragging) {
            if (!hasEditSelection()) { clearEditSelection(); editWarning(PageEditor.Status.STALE_SELECTION); return true; }
            PageEditor.Result preview = editor.preview(editSelection, x - editStartX, y - editStartY);
            previewX = preview.status == PageEditor.Status.READY ? preview.dx : 0;
            previewY = preview.status == PageEditor.Status.READY ? preview.dy : 0;
            invalidate();
            if (!ownerUp) return true;
            editPointer = -1; editDragging = false; requestDisallowInterceptTouchEvent(false);
            if (Math.hypot(x - editStartX, y - editStartY) < EDIT_SLOP) { previewX = previewY = 0; invalidate(); return true; }
            PageEditor source = editor;
            PageEditor.Result result = source.move(editSelection, x - editStartX, y - editStartY);
            if (result.applied()) editCommitted(source);
            else { previewX = previewY = 0; editWarning(result.status); invalidate(); }
            return true;
        }
        editX = Math.max(0, Math.min(InkView.WIDTH, editX));
        editY = Math.max(0, Math.min(editLogical(getHeight()), editY));
        invalidate();
        if (!ownerUp) return true;
        editPointer = -1; requestDisallowInterceptTouchEvent(false);
        List<AnswerPlacement.Rect> rects = renderedEditRects();
        if (rects == null) { clearEditSelection(); editWarning(PageEditor.Status.STALE_SELECTION); return true; }
        if (Math.abs(editX - editStartX) < EDIT_SLOP || Math.abs(editY - editStartY) < EDIT_SLOP) { clearEditSelection(); return true; }
        selectForEdit(editor.selectRegion(new AnswerPlacement.Rect(Math.min(editStartX, editX), Math.min(editStartY, editY),
                Math.max(editStartX, editX), Math.max(editStartY, editY)), rects));
        if (editSelection == null && editListener != null) editListener.warning("Draw around the whole handwriting or reply block to select it.");
        return true;
    }

    private float editLogical(float pixels) { return pixels * InkView.WIDTH / Math.max(1, getWidth()); }
    private boolean dispatchEditChild(View child, MotionEvent event) {
        MotionEvent local = MotionEvent.obtain(event);
        local.offsetLocation(-child.getLeft(), -child.getTop());
        boolean handled = child.dispatchTouchEvent(local); local.recycle(); return handled;
    }
    private static boolean contains(AnswerPlacement.Rect r, float x, float y) {
        return r != null && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom;
    }
    private int hitReply(List<AnswerPlacement.Rect> rects, float x, float y) {
        for (int i = rects.size() - 1; i >= 0; i--) if (contains(rects.get(i), x, y)) return i;
        return -1;
    }
    private List<AnswerPlacement.Rect> renderedEditRects() {
        if (editor == null || editor.state().replies.size() != replies.size()
                || repliesVisible && answerBounds.size() != replies.size()) return null;
        ArrayList<AnswerPlacement.Rect> result = new ArrayList<>();
        for (int i = 0; i < replies.size(); i++) {
            if (editor.state().replies.get(i) != replies.get(i)) return null;
            result.add(repliesVisible ? answerBounds.get(i) : null);
        }
        return result;
    }
    private void selectForEdit(PageEditor.Selection selected) {
        editSelection = selected; editFrames.clear(); previewX = previewY = 0;
        if (selected != null) {
            // Compute component bounds once per selection, never traverse ink points on MOVE/draw.
            for (int i : selected.strokeIndices) editFrames.add(PageEditor.strokeBounds(editor.state().ink.get(i)));
            for (int i : selected.replyIndices) editFrames.add(answerBounds.get(i));
        }
        notifyEditSelection(); invalidate();
    }
    private void notifyEditSelection() { if (editListener != null) editListener.selectionChanged(hasEditSelection()); }
    private void editCommitted(PageEditor source) {
        PageEditor.State state = source.state(); clearEditSelection();
        if (editListener != null) editListener.committed(state);
    }
    private void editWarning(PageEditor.Status status) {
        if (editListener == null) return;
        if (status == PageEditor.Status.ERASER_COLLISION) editListener.warning("Move overlaps erased handwriting. Choose another position.");
        else if (status == PageEditor.Status.STALE_SELECTION) editListener.warning("Selection changed. Select again.");
        else if (status == PageEditor.Status.INVALID_DELTA) editListener.warning("That position is unavailable. Select again.");
    }
    private void drawEditOverlay(Canvas canvas) {
        if (!moveMode) return;
        float scale = getWidth() / (float)InkView.WIDTH;
        canvas.save(); canvas.scale(scale, scale);
        editPaint.setStyle(Paint.Style.STROKE); editPaint.setColor(Color.BLACK); editPaint.setStrokeWidth(2);
        if (hasEditSelection()) {
            editPaint.setAlpha(140);
            for (AnswerPlacement.Rect rect : editFrames) drawEditRect(canvas, rect, 0, 0);
            editPaint.setAlpha(230);
            drawEditRect(canvas, editSelection.bounds, previewX, previewY);
            if (previewX != 0 || previewY != 0) for (AnswerPlacement.Rect rect : editFrames) drawEditRect(canvas, rect, previewX, previewY);
        }
        if (editPointer != -1 && !editDragging) {
            editPaint.setAlpha(230);
            canvas.drawRect(Math.min(editStartX, editX), Math.min(editStartY, editY), Math.max(editStartX, editX), Math.max(editStartY, editY), editPaint);
        }
        canvas.restore();
    }
    private void drawEditRect(Canvas canvas, AnswerPlacement.Rect rect, float dx, float dy) {
        canvas.drawRect(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy, editPaint);
    }
    private int dp(int value) { return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density)); }
}
