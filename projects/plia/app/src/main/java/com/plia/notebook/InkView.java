package com.plia.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Native ink with logical coordinates; committed strokes are immutable. */
public final class InkView extends View {
    // Extend the paper below existing ink; keep every saved point at its original coordinate.
    public static final int WIDTH = 1000, HEIGHT = 1600, MAX_POINTS = 200000;
    private static final int LEGACY_INK_COLOR = 0xff171917;
    private static final int SEGMENT_POINTS = 8000;
    private static final PorterDuffXfermode CLEAR = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);
    public interface Listener {
        default void started() {}
        default void ended(boolean completed) {}
        default void selectionStarted() {}
        default void selectionCompleted(List<Stroke> gesture) {}
        void changed();
        void state(boolean undo, boolean redo, boolean ink);
        void warning(String message);
    }
    public static final class Point {
        public final float x, y, pressure;
        Point(float x, float y, float pressure) { this.x = x; this.y = y; this.pressure = pressure; }
    }
    public static final class Stroke {
        public final boolean erase;
        public final float width;
        public final int color;
        public final List<Point> points;
        private final boolean immutable;
        Stroke(boolean erase, float width) { this(erase, width, LEGACY_INK_COLOR); }
        Stroke(boolean erase, float width, int color) {
            if ((color >>> 24) != 0xff) throw new IllegalArgumentException("Ink color must be opaque ARGB");
            this.erase = erase; this.width = width; this.color = color; points = new ArrayList<>(); immutable = false;
        }
        private Stroke(Stroke source) {
            erase = source.erase; width = source.width; color = source.color;
            points = Collections.unmodifiableList(new ArrayList<>(source.points));
            immutable = true;
        }
        /** Committed editor snapshots share immutable points, never a caller's mutable list. */
        public static Stroke immutableCopy(Stroke source) { return source.immutable ? source : new Stroke(source); }
        JSONObject json() throws JSONException {
            JSONArray data = new JSONArray();
            for (Point p : points) data.put(new JSONArray().put(p.x).put(p.y).put(p.pressure));
            return new JSONObject().put("erase", erase).put("width", width).put("color", color).put("points", data);
        }
        static Stroke fromJson(JSONObject value) throws JSONException {
            double width = value.getDouble("width");
            if (!Double.isFinite(width) || width < .5 || width > 40) throw new JSONException("Invalid ink width");
            int color = LEGACY_INK_COLOR;
            if (value.has("color")) {
                Object raw = value.get("color");
                // JSONObject.getInt coerces strings, fractions and overflow. Accept only exact
                // signed integer JSON values; absence alone means a legacy black stroke.
                if (!(raw instanceof Integer) && !(raw instanceof Long)) throw new JSONException("Invalid ink color");
                long number = ((Number)raw).longValue();
                if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE || (((int)number) >>> 24) != 0xff)
                    throw new JSONException("Invalid ink color");
                color = (int)number;
            }
            Stroke stroke = new Stroke(value.getBoolean("erase"), (float) width, color);
            JSONArray points = value.getJSONArray("points");
            if (points.length() < 1 || points.length() > SEGMENT_POINTS) throw new JSONException("Invalid ink length");
            for (int i = 0; i < points.length(); i++) {
                JSONArray p = points.getJSONArray(i);
                if (p.length() != 3) throw new JSONException("Invalid ink point");
                double x = p.getDouble(0), y = p.getDouble(1), pressure = p.getDouble(2);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(pressure) || x < 0 || x > WIDTH || y < 0 || y > HEIGHT || pressure < 0 || pressure > 1) throw new JSONException("Invalid ink point");
                stroke.points.add(new Point((float)x, (float)y, (float)pressure));
            }
            return stroke;
        }
    }
    private static final class Action {
        final boolean clear;
        final ArrayList<Stroke> strokes;
        Action(boolean clear, List<Stroke> strokes) { this.clear = clear; this.strokes = new ArrayList<>(strokes); }
    }
    private final Bitmap layer = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    private final Canvas layerCanvas = new Canvas(layer);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect dirty = new Rect();
    private final ArrayList<Stroke> strokes = new ArrayList<>(), active = new ArrayList<>();
    private final SelectionGesture selection = new SelectionGesture();
    private final Stroke selectionStyle = new Stroke(false, 2.5f);
    private Bitmap selectionLayer;
    private Canvas selectionCanvas;
    private final ArrayList<Action> undo = new ArrayList<>(), redo = new ArrayList<>();
    private Listener listener;
    private int pointerId = -1, pointCount;
    private boolean eraser, fingers, plainPaper, selectionMode, selectionOverlayOnParent, externalHistory;
    private float penWidth = 2f;
    private int penColor = LEGACY_INK_COLOR;

    public InkView(Context context) {
        super(context);
        setBackgroundColor(Color.WHITE);
        setFocusable(true);
        setContentDescription("Notebook handwriting area. Use a stylus. Fingers scroll unless finger drawing is enabled.");
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        selectionOverlayPaint.setAlpha(150);
    }
    public void setListener(Listener value) { listener = value; state(); }
    /** Main's PageEditor owns bounded edit history; do not retain a second native history. */
    public void setExternalHistory(boolean value) {
        if (externalHistory == value) return;
        finish(); externalHistory = value; undo.clear(); redo.clear(); state();
    }
    public void setEraser(boolean value) { finish(); clearSelection(); selectionMode = false; eraser = value; }
    public void setFingerDrawing(boolean value) { finish(); clearSelection(); fingers = value; }
    public void setSelectionMode(boolean value) { finish(); clearSelection(); selectionMode = value; }
    public boolean isSelectionMode() { return selectionMode; }
    public void setSelectionOverlayOnParent(boolean value) {
        if (selectionOverlayOnParent == value) return;
        selectionOverlayOnParent = value; invalidate();
        if (getParent() instanceof View) ((View)getParent()).invalidate();
    }
    /** Discard only transient selection marks. Saved ink and its undo history are untouched. */
    public void clearSelection() {
        if (!selection.active() && selection.pointCount() == 0) return;
        if (selection.active()) {
            pointerId = -1;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        selection.clear();
        if (selectionLayer != null) selectionLayer.eraseColor(Color.TRANSPARENT);
        invalidate(); invalidateSelectionParent();
    }
    public void setPlainPaper(boolean value) { if (plainPaper != value) { plainPaper = value; invalidate(); } }
    public float getPenWidth() { return penWidth; }
    public int getPenColor() { return penColor; }
    public void setPenWidth(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid pen width");
        finish(); if (selectionMode) clearSelection(); penWidth = Math.max(.5f, Math.min(12f, value));
    }
    public void setPenColor(int value) {
        if ((value >>> 24) != 0xff) throw new IllegalArgumentException("Ink color must be opaque ARGB");
        finish(); if (selectionMode) clearSelection(); penColor = value;
    }
    public boolean hasInk() { for (Stroke stroke : strokes) if (!stroke.erase) return true; for (Stroke stroke : active) if (!stroke.erase) return true; return false; }
    public ArrayList<Stroke> snapshot() { ArrayList<Stroke> copy = new ArrayList<>(strokes); copy.addAll(active); return copy; }
    public void load(List<Stroke> value) {
        finish(); clearSelection(); strokes.clear(); strokes.addAll(value); undo.clear(); redo.clear(); recount(); redraw(); state();
    }
    public void clear() {
        finish(); clearSelection(); if (strokes.isEmpty()) return;
        if (!externalHistory) remember(new Action(true, strokes)); strokes.clear(); changed();
    }
    public void undo() {
        finish(); clearSelection(); if (undo.isEmpty()) return;
        Action action = undo.remove(undo.size() - 1);
        if (action.clear) strokes.addAll(action.strokes);
        else for (int i = 0; i < action.strokes.size(); i++) strokes.remove(strokes.size() - 1);
        redo.add(action); changed();
    }
    public void redo() {
        finish(); clearSelection(); if (redo.isEmpty()) return;
        Action action = redo.remove(redo.size() - 1);
        if (action.clear) strokes.clear(); else strokes.addAll(action.strokes);
        undo.add(action); changed();
    }
    public void finish() {
        finish(false);
    }
    private void finish(boolean completed) {
        if (pointerId == -1) return;
        if (selectionMode) { finishSelection(completed); return; }
        pointerId = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        if (!active.isEmpty()) {
            // Freeze only this completed gesture; the shared editor can reuse older stroke identities.
            for (int i = 0; i < active.size(); i++) active.set(i, Stroke.immutableCopy(active.get(i)));
            strokes.addAll(active); if (!externalHistory) remember(new Action(false, active)); active.clear();
            if (listener != null) listener.changed();
        }
        state();
        if (listener != null) listener.ended(completed);
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, Math.round(width * (float)HEIGHT / WIDTH));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save(); canvas.scale(getWidth() / (float)WIDTH, getHeight() / (float)HEIGHT);
        if (!plainPaper) {
            paint.setXfermode(null); paint.setColor(0xffe4e6df); paint.setStrokeWidth(1);
            for (int y = 36; y < HEIGHT; y += 36) canvas.drawLine(0, y, WIDTH, y, paint);
        }
        canvas.drawBitmap(layer, 0, 0, null);
        canvas.restore();
        if (!selectionOverlayOnParent) drawSelectionOverlay(canvas);
    }
    /** Draw once above reply panels; the caller's canvas is in InkView pixel coordinates. */
    public void drawSelectionOverlay(Canvas canvas) {
        if (!selectionMode || selectionLayer == null || selection.pointCount() == 0) return;
        canvas.save(); canvas.scale(getWidth() / (float)WIDTH, getHeight() / (float)HEIGHT);
        canvas.drawBitmap(selectionLayer, 0, 0, selectionOverlayPaint); canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        dirty.setEmpty();
        int action = event.getActionMasked(), index = event.getActionIndex();
        // Hover/unknown actions must never erase, append to or complete the visible outline.
        if (selectionMode && (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE
                || action == MotionEvent.ACTION_HOVER_EXIT)) return true;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int tool = event.getToolType(index);
            boolean pen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER || tool == MotionEvent.TOOL_TYPE_MOUSE;
            if (!pen && !fingers) return pointerId != -1;
            if (selectionMode && action == MotionEvent.ACTION_DOWN && pointerId != -1) finishSelection(false);
            if (pointerId != -1) return true;
            if (selectionMode) { startSelection(event, index, tool); return true; }
            if (pointCount >= MAX_POINTS || strokes.size() >= 12000) { limit(); return true; }
            pointerId = event.getPointerId(index);
            if (listener != null) listener.started();
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            boolean erase = eraser || tool == MotionEvent.TOOL_TYPE_ERASER;
            Stroke stroke = new Stroke(erase, erase ? 32 : penWidth, penColor);
            Point point = point(event.getX(index), event.getY(index), event.getPressure(index), tool);
            stroke.points.add(point); active.add(stroke); pointCount++;
            drawStroke(layerCanvas, stroke, paint); markDirty(stroke, point, point); invalidateDirty(); state(); return true;
        }
        if (pointerId == -1) return false;
        index = event.findPointerIndex(pointerId);
        if (selectionMode && action == MotionEvent.ACTION_MOVE && index < 0) {
            finishSelection(false); return true; // Lost pointer: retain feedback, never invent a pen-up.
        }
        if (action == MotionEvent.ACTION_MOVE && index >= 0) {
            for (int h = 0; h < event.getHistorySize() && pointerId != -1; h++) appendCurrent(point(event.getHistoricalX(index, h), event.getHistoricalY(index, h), event.getHistoricalPressure(index, h), event.getToolType(index)));
            if (pointerId != -1) appendCurrent(point(event.getX(index), event.getY(index), event.getPressure(index), event.getToolType(index)));
            invalidateDirty();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL || (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && event.getPointerId(event.getActionIndex()) == pointerId) {
            boolean completed = action != MotionEvent.ACTION_CANCEL && (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0;
            if (completed && index >= 0 && (selection.active() || !active.isEmpty())) {
                float pressure = selectionMode ? .5f : active.get(active.size() - 1).points.get(active.get(active.size() - 1).points.size() - 1).pressure;
                Point point = point(event.getX(index), event.getY(index), pressure, MotionEvent.TOOL_TYPE_STYLUS);
                appendCurrent(point);
            }
            invalidateDirty(); finish(completed); if (completed) performClick(); return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    private Point point(float x, float y, float pressure, int tool) {
        if (selectionMode && (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0 || x > getWidth() || y > getHeight())) {
            return new Point(Float.NaN, Float.NaN, .5f); // Do not turn off-paper motion into a closing edge.
        }
        return new Point(Math.max(0, Math.min(WIDTH, x * WIDTH / Math.max(1, getWidth()))), Math.max(0, Math.min(HEIGHT, y * HEIGHT / Math.max(1, getHeight()))), tool == MotionEvent.TOOL_TYPE_MOUSE ? .5f : Math.max(.05f, Math.min(1, pressure)));
    }
    private void startSelection(MotionEvent event, int index, int tool) {
        Point point = point(event.getX(index), event.getY(index), event.getPressure(index), tool);
        SelectionGesture.Result result = selection.handle(SelectionGesture.Event.DOWN, event.getPointerId(index), point.x, point.y);
        if (result == SelectionGesture.Result.LIMIT) { selectionLimit(); return; }
        if (result != SelectionGesture.Result.STARTED) return;
        if (selectionLayer == null) {
            selectionLayer = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            selectionCanvas = new Canvas(selectionLayer);
        }
        pointerId = event.getPointerId(index);
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
        if (listener != null) listener.selectionStarted();
        // The listener may switch pages/tools while cancelling pending work.
        if (!selection.active()) return;
        redrawSelection();
    }
    private void appendCurrent(Point point) { if (selectionMode) appendSelection(point); else append(point); }
    private void appendSelection(Point point) {
        if (!selection.active()) return;
        if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) { finishSelection(false); return; }
        Point previous = new Point(selection.lastX(), selection.lastY(), .5f);
        SelectionGesture.Result result = selection.handle(SelectionGesture.Event.MOVE, pointerId, point.x, point.y);
        if (result == SelectionGesture.Result.LIMIT) { finishSelection(false); return; }
        if (result != SelectionGesture.Result.UPDATED) return;
        Point next = new Point(point.x, point.y, .5f);
        configure(paint, selectionStyle); segment(selectionCanvas, paint, selectionStyle, previous, next); paint.setXfermode(null);
        markDirty(selectionStyle, previous, next);
    }
    private void finishSelection(boolean completed) {
        int owner = pointerId;
        pointerId = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        SelectionGesture.Result result = selection.handle(completed ? SelectionGesture.Event.UP : SelectionGesture.Event.CANCEL,
                owner, selection.lastX(), selection.lastY());
        if (result == SelectionGesture.Result.IGNORED) return;
        // The transient outline is retained for feedback, never added to saved handwriting.
        if (result == SelectionGesture.Result.CLOSED) {
            // Show the automatic closing segment of the exact admitted lasso.
            redrawSelection();
            Stroke outline = selectionStroke();
            if (listener != null) listener.selectionCompleted(Collections.singletonList(new Stroke(outline)));
        } else if (listener != null) {
            listener.ended(false); // Release MainActivity's pen-down state without submitting a selection.
            if (result == SelectionGesture.Result.LIMIT) selectionLimit();
            else if (result == SelectionGesture.Result.OPEN || result == SelectionGesture.Result.INVALID_LOOP)
                listener.warning("Circle a larger area around the written problem, then lift. Endpoints do not need to meet. Nothing sent.");
        }
        invalidate(); invalidateSelectionParent();
    }
    private Stroke selectionStroke() {
        Stroke outline = new Stroke(false, 2.5f);
        float[] xy = selection.snapshot();
        for (int i = 0; i < xy.length; i += 2) outline.points.add(new Point(xy[i], xy[i + 1], .5f));
        return outline;
    }
    private void redrawSelection() {
        selectionLayer.eraseColor(Color.TRANSPARENT);
        if (selection.pointCount() > 0) drawStroke(selectionCanvas, selectionStroke(), paint);
        invalidate(); invalidateSelectionParent();
    }
    private void selectionLimit() {
        if (listener != null) listener.warning("Selection limit reached. The outline is kept; tap AI Select again to draw a simpler loop.");
    }
    private void append(Point point) {
        Stroke stroke = active.get(active.size() - 1); Point previous = stroke.points.get(stroke.points.size() - 1);
        if (previous.x == point.x && previous.y == point.y && previous.pressure == point.pressure) return;
        boolean split = stroke.points.size() >= SEGMENT_POINTS;
        if (pointCount + (split ? 2 : 1) > MAX_POINTS || split && strokes.size() + active.size() >= 12000) { finish(); limit(); return; }
        if (split) { stroke = new Stroke(stroke.erase, stroke.width, stroke.color); stroke.points.add(previous); active.add(stroke); pointCount++; }
        stroke.points.add(point); pointCount++;
        configure(paint, stroke); segment(layerCanvas, paint, stroke, previous, point); paint.setXfermode(null);
        markDirty(stroke, previous, point);
    }
    /** Historical samples share one invalidation; the bitmap still receives every segment. */
    private void markDirty(Stroke stroke, Point previous, Point point) {
        float margin = stroke.width + 2, sx = getWidth() / (float)WIDTH, sy = getHeight() / (float)HEIGHT;
        dirty.union((int)Math.floor((Math.min(previous.x, point.x) - margin) * sx), (int)Math.floor((Math.min(previous.y, point.y) - margin) * sy), (int)Math.ceil((Math.max(previous.x, point.x) + margin) * sx), (int)Math.ceil((Math.max(previous.y, point.y) + margin) * sy));
    }
    private void invalidateDirty() {
        if (!dirty.isEmpty()) {
            invalidate(dirty);
            // Parent-drawn overlay commands need their own display-list invalidation. Ordinary ink
            // retains the existing child-only dirty-rectangle path.
            if (selectionMode && selectionOverlayOnParent && getParent() instanceof View) ((View)getParent()).invalidate(dirty);
        }
    }
    private void invalidateSelectionParent() {
        if (selectionOverlayOnParent && getParent() instanceof View) ((View)getParent()).invalidate();
    }
    private void limit() { if (listener != null) listener.warning("This page has reached its ink capacity. Your strokes are kept; start a new page."); }
    private void remember(Action action) { undo.add(action); if (undo.size() > 40) undo.remove(0); redo.clear(); }
    private void recount() { pointCount = 0; for (Stroke stroke : strokes) pointCount += stroke.points.size(); }
    private void changed() { recount(); redraw(); if (listener != null) listener.changed(); state(); }
    private void state() { if (listener != null) listener.state(!externalHistory && (!undo.isEmpty() || !selectionMode && pointerId != -1), !externalHistory && !redo.isEmpty(), hasInk()); }
    private void redraw() { layer.eraseColor(Color.TRANSPARENT); for (Stroke stroke : strokes) drawStroke(layerCanvas, stroke, paint); invalidate(); }
    private static float size(Stroke stroke, Point point) { return stroke.erase ? stroke.width : stroke.width * (.45f + .9f * point.pressure); }
    private static void configure(Paint paint, Stroke stroke) { paint.setColor(stroke.color); paint.setStyle(Paint.Style.FILL); paint.setStrokeCap(Paint.Cap.ROUND); paint.setXfermode(stroke.erase ? CLEAR : null); }
    private static void segment(Canvas canvas, Paint paint, Stroke stroke, Point previous, Point point) { paint.setStrokeWidth((size(stroke, previous) + size(stroke, point)) / 2); canvas.drawLine(previous.x, previous.y, point.x, point.y, paint); canvas.drawCircle(point.x, point.y, size(stroke, point) / 2, paint); }
    private static void drawStroke(Canvas canvas, Stroke stroke, Paint paint) { configure(paint, stroke); Point first = stroke.points.get(0); canvas.drawCircle(first.x, first.y, size(stroke, first) / 2, paint); for (int i = 1; i < stroke.points.size(); i++) segment(canvas, paint, stroke, stroke.points.get(i - 1), stroke.points.get(i)); paint.setXfermode(null); }
    /** Erasure happens on its own transparent layer, never on the destination paper. */
    public static Bitmap bitmap(List<Stroke> strokes, boolean white) {
        Bitmap result = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Stroke stroke : strokes) drawStroke(canvas, stroke, paint);
        if (!white) return result;
        Bitmap paper = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas background = new Canvas(paper); background.drawColor(Color.WHITE); background.drawBitmap(result, 0, 0, null); result.recycle(); return paper;
    }
}
