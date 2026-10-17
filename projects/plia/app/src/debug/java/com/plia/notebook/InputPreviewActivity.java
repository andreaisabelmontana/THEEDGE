package com.plia.notebook;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/** Debug-only, in-memory input QA. Never constructs NotebookStore or any account/network client. */
public final class InputPreviewActivity extends Activity {
    private static final String TAG = "PliaInputQA";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<FixedInputCases.Case> cases = FixedInputCases.all();
    private ScrollView scroll;
    private TextView status;
    private Button run;
    private InkView ink;
    private PaperSurface paper;
    private int index, eventIndex, generation;
    private boolean running, destroyed, counting, suiteMode;
    private long downTime, due, initialInkHash, pendingFrameAt, pendingDrawAt;
    private Stats stats;
    private Window.OnFrameMetricsAvailableListener frameListener;
    private ViewTreeObserver.OnDrawListener drawListener;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
    private final Choreographer.FrameCallback nextFrame = ignored -> {
        if (running && stats != null && pendingFrameAt != 0) stats.frameWait.add(System.nanoTime() - pendingFrameAt);
        pendingFrameAt = 0;
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        status = new TextView(this); status.setTextColor(Color.BLACK); status.setTextSize(14); status.setPadding(12, 10, 12, 4);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(this);
        run = new Button(this); run.setOnClickListener(v -> { if (running) finishCase("manual_finished"); else startCase(); });
        Button next = new Button(this); next.setText("Next fixture"); next.setOnClickListener(v -> { suiteMode = false; index = (index + 1) % cases.size(); resetScene(); });
        Button reset = new Button(this); reset.setText("Reset fixture"); reset.setOnClickListener(v -> { suiteMode = false; resetScene(); });
        for (Button button : new Button[] { run, next, reset }) buttons.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        frameListener = (window, metrics, dropped) -> {
            if (running && stats != null) { stats.frameTotal.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)); stats.frameDrops += dropped; }
        };
        getWindow().addOnFrameMetricsAvailableListener(frameListener, main);
        selectIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); selectIntent(intent); }
    private void selectIntent(Intent intent) {
        boolean auto = false;
        suiteMode = false;
        try { index = Math.max(0, Math.min(cases.size() - 1, intent.getIntExtra("index", 0))); auto = intent.getBooleanExtra("autorun", false);
            suiteMode = intent.getBooleanExtra("suite", false); if (suiteMode) { index = 0; auto = true; } }
        catch (RuntimeException invalidExtra) { index = 0; suiteMode = false; }
        resetScene();
        if (auto) { final int token = generation; main.postDelayed(() -> { if (token == generation && !destroyed) startCase(); }, 700); }
    }

    private void resetScene() {
        stopWork();
        releaseScene();
        scroll.removeAllViews();
        ink = new InkView(this); ink.setPlainPaper(true); ink.setFingerDrawing(false); ink.load(seedInk());
        paper = new PaperSurface(this, ink, Typeface.DEFAULT);
        FixedInputCases.Case test = cases.get(index);
        ArrayList<NotebookStore.Reply> replies = new ArrayList<>();
        for (int i = 0; i < test.replies; i++) {
            // Model value object only: no NotebookStore instance, files, or real page data.
            ReplyPlacement place = new ReplyPlacement(i == 0 && index == 5 ? 620 : 35,
                    i == 0 && index == 5 ? 210 : 650 + i * 210, i == 0 && index == 5 ? 330 : 930, true);
            String text = index == 5 ? "## To solve\n| Steps in math | Steps in words |\n| --- | --- |\n"
                    + "| F'(x)=2^{3(x^4+x)-2}(4x^3+1)-2^{3x^4-2}(4x^3) | Differentiate the two terms and retain the exponent. |\n"
                    + "| x^2+3 | The small 2 stays attached to x. |"
                    : "Synthetic reply " + (i + 1) + "\nFixed math work and explanation.\nNo model call.\nNo user notebook is loaded.";
            replies.add(new NotebookStore.Reply("think", "Fixed synthetic input", text, "demo", null, place));
        }
        paper.setReplies(replies, true); ink.setSelectionMode(test.select);
        ink.setListener(new InkView.Listener() {
            @Override public void started() { if (counting) stats.penStarts++; }
            @Override public void ended(boolean completed) { if (counting) { stats.ends++; if (completed) stats.completedEnds++; } }
            @Override public void selectionStarted() { if (counting) stats.selectionStarts++; }
            @Override public void selectionCompleted(List<InkView.Stroke> gesture) {
                if (counting) { stats.selectionCompleted++; stats.outlinePoints = 0;
                    for (InkView.Stroke stroke : gesture) stats.outlinePoints += stroke.points.size(); }
                // Deliberately no PaperSession, PaperDraft, callback into MainActivity, or network client.
            }
            @Override public void changed() {
                if (!counting) return; stats.inkCommits++;
                if (cases.get(index).replies > 0) { long begin = System.nanoTime(); paper.inkChanged(); stats.replyLayout.add(System.nanoTime() - begin); }
            }
            @Override public void state(boolean undo, boolean redo, boolean hasInk) {}
            @Override public void warning(String warning) { if (counting) { stats.warnings++; stats.lastWarning = warning; } }
        });
        drawListener = () -> {
            if (running && pendingDrawAt != 0 && stats != null) { stats.drawWait.add(System.nanoTime() - pendingDrawAt); pendingDrawAt = 0; }
        };
        layoutListener = () -> { if (running && stats != null) stats.globalLayouts++; };
        paper.getViewTreeObserver().addOnDrawListener(drawListener);
        paper.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        paper.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> { if (running && stats != null) stats.paperBoundsChanges++; });
        scroll.addView(paper, new ScrollView.LayoutParams(-1, -2)); scroll.scrollTo(0, 0);
        status.setText("Offline input QA " + index + "/" + (cases.size() - 1) + " — " + test.name
                + "\nSynthetic notes only. Frame timing is not physical pen latency.\n" + InputQaNativeProbe.snapshot(ink));
        run.setText(test.manual ? "Start manual" : "Run synthetic"); run.setEnabled(true);
    }

    private void startCase() {
        if (running || destroyed || paper.getWidth() <= 0) return;
        run.setEnabled(false);
        waitForMathReady(generation, SystemClock.uptimeMillis() + 8000);
    }
    private void waitForMathReady(int token, long deadline) {
        if (token != generation || destroyed || running) return;
        WebView math = firstMath(paper);
        if (math == null) {
            if (index == 5) { Log.i(TAG, "ABORT index=5 reason=math_renderer_unavailable"); status.setText("KaTeX fixture unavailable; no input timing recorded."); return; }
            beginCase(); return;
        }
        math.evaluateJavascript("window.solutionReady===true", value -> {
            if (token != generation || destroyed || running) return;
            if ("true".equals(value)) { main.postDelayed(() -> { if (token == generation && !destroyed && !running) beginCase(); }, 120); }
            else if (SystemClock.uptimeMillis() < deadline) main.postDelayed(() -> waitForMathReady(token, deadline), 100);
            else { Log.i(TAG, "ABORT index=" + index + " reason=math_ready_timeout"); status.setText("Math readiness timeout; no input timing recorded."); }
        });
    }
    private static WebView firstMath(View view) {
        if (view instanceof WebView) return (WebView)view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) { WebView found = firstMath(group.getChildAt(i)); if (found != null) return found; } }
        return null;
    }
    private void beginCase() {
        if (running || destroyed) return;
        stats = new Stats(); initialInkHash = inkHash(); eventIndex = 0; counting = running = true;
        due = SystemClock.uptimeMillis();
        FixedInputCases.Case test = cases.get(index);
        Log.i(TAG, "BEGIN index=" + index + " name=" + test.name + " synthetic=" + !test.manual
                + " source_interval_ms=" + FixedInputCases.INTERVAL_MS + " initial_strokes=" + ink.snapshot().size() + " actual_ink_width_px=" + ink.getWidth()
                + " native=" + InputQaNativeProbe.snapshot(ink) + " physical_latency_measured=false");
        status.setText("Running " + test.name + (test.manual ? "\nDraw on this synthetic page, then Finish manual." : "\nFixed stylus events, no model calls.")
                + "\n" + InputQaNativeProbe.snapshot(ink));
        run.setText(test.manual ? "Finish manual" : "Running"); run.setEnabled(test.manual);
        if (!test.manual) sendNext(generation);
    }

    private void sendNext(int token) {
        if (!running || token != generation || destroyed) return;
        FixedInputCases.Case test = cases.get(index);
        if (eventIndex == test.samples.size()) { main.postDelayed(() -> { if (token == generation) finishCase("complete"); }, 350); return; }
        FixedInputCases.Sample sample = test.samples.get(eventIndex++);
        long now = SystemClock.uptimeMillis(); if (sample.action == MotionEvent.ACTION_DOWN) downTime = now;
        stats.scheduleDelay.add(Math.max(0, now - due) * 1000000L);
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties(); properties.id = 0; properties.toolType = MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords point = new MotionEvent.PointerCoords();
        point.x = sample.x * ink.getWidth() / InkView.WIDTH; point.y = sample.y * ink.getHeight() / InkView.HEIGHT;
        point.pressure = sample.action == MotionEvent.ACTION_HOVER_MOVE ? 0 : .5f; point.size = .02f;
        MotionEvent event = MotionEvent.obtain(downTime, now, sample.action, 1,
                new MotionEvent.PointerProperties[] { properties }, new MotionEvent.PointerCoords[] { point },
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_STYLUS, 0);
        long begin = beginEvent(); boolean handled;
        try { handled = paper.dispatchTouchEvent(event); }
        catch (RuntimeException problem) { event.recycle(); finishCase("dispatch_error_" + problem.getClass().getSimpleName()); return; }
        finishEvent(begin, handled); event.recycle();
        due += FixedInputCases.INTERVAL_MS; main.postAtTime(() -> sendNext(token), due);
    }

    /** Real input reaches the ordinary Activity/View dispatch path; synthetic input is dispatched above. */
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        boolean sample = running && cases.get(index).manual && event.getPointerCount() > 0
                && (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER);
        long begin = sample ? beginEvent() : 0;
        boolean handled = super.dispatchTouchEvent(event);
        if (sample && running) finishEvent(begin, handled);
        return handled;
    }
    private long beginEvent() {
        long begin = System.nanoTime();
        if (pendingFrameAt == 0) { pendingFrameAt = begin; Choreographer.getInstance().postFrameCallback(nextFrame); }
        if (pendingDrawAt == 0) pendingDrawAt = begin;
        return begin;
    }
    private void finishEvent(long begin, boolean handled) { stats.dispatch.add(System.nanoTime() - begin); stats.events++; if (handled) stats.handled++; }
    private void finishCase(String reason) {
        if (!running) return;
        FixedInputCases.Case test = cases.get(index); counting = running = false;
        boolean unchanged = initialInkHash == inkHash();
        try {
            JSONObject result = stats.json().put("index", index).put("name", test.name).put("reason", reason)
                    .put("synthetic", !test.manual).put("expected_selection_callbacks", test.expectedCompleted)
                    .put("closure_matches_target", test.manual ? JSONObject.NULL : stats.selectionCompleted == test.expectedCompleted)
                    .put("ink_unchanged", unchanged).put("selection_preserves_ink", test.select ? unchanged : JSONObject.NULL)
                    .put("final_strokes", ink.snapshot().size()).put("native", InputQaNativeProbe.snapshot(ink))
                    .put("ink_width_px", ink.getWidth()).put("ink_height_px", ink.getHeight()).put("paper_measured_height_px", paper.getMeasuredHeight())
                    .put("on_measure_count", "UNAVAILABLE_UNINSTRUMENTED_PRODUCTION")
                    .put("physical_latency_measured", false);
            Log.i(TAG, "RESULT " + result);
            status.setText(test.name + "\n" + reason + ": events=" + stats.events + ", selection callbacks=" + stats.selectionCompleted
                    + ", expected=" + test.expectedCompleted + ", ink unchanged=" + unchanged
                    + "\n" + stats.lastWarning + "\nSee PliaInputQA log. " + InputQaNativeProbe.snapshot(ink));
        } catch (org.json.JSONException impossible) { Log.e(TAG, "RESULT_ERROR " + impossible.getClass().getSimpleName()); }
        run.setText("Reset to run again"); run.setEnabled(false);
        Choreographer.getInstance().removeFrameCallback(nextFrame); pendingFrameAt = pendingDrawAt = 0;
        if (suiteMode && "complete".equals(reason)) {
            if (index == 7) { suiteMode = false; Log.i(TAG, "SUITE_COMPLETE cases=8 physical_latency_measured=false"); }
            else { final int token = generation; main.postDelayed(() -> {
                if (token != generation || destroyed || !suiteMode) return;
                index++; resetScene(); final int nextToken = generation;
                main.postDelayed(() -> { if (nextToken == generation && !destroyed && suiteMode) startCase(); }, 700);
            }, 250); }
        }
    }
    private void stopWork() {
        counting = running = false; generation++; main.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(nextFrame); pendingFrameAt = pendingDrawAt = 0;
        if (ink != null) ink.finish();
    }
    private static ArrayList<InkView.Stroke> seedInk() {
        ArrayList<InkView.Stroke> strokes = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            InkView.Stroke stroke = new InkView.Stroke(false, 2);
            for (int p = 0; p < 50; p++) stroke.points.add(new InkView.Point(50 + p * 17, 900 + i * 7 + (float)Math.sin(p) * 2, .5f));
            strokes.add(stroke);
        }
        // Fixed vector x²+3 inside every long oval: exponent points stay above/beside the base x.
        addStroke(strokes, 280, 300, 320, 340); addStroke(strokes, 320, 300, 280, 340);
        addStroke(strokes, 325, 282, 331, 277, 341, 277, 347, 282, 347, 287, 326, 298, 348, 298);
        addStroke(strokes, 366, 319, 393, 319); addStroke(strokes, 379, 305, 379, 333);
        addStroke(strokes, 410, 302, 432, 302, 442, 308, 442, 315, 426, 320, 440, 324, 442, 334, 434, 341, 410, 341);
        return strokes;
    }
    private static void addStroke(ArrayList<InkView.Stroke> strokes, float... xy) {
        InkView.Stroke stroke = new InkView.Stroke(false, 3);
        for (int i = 0; i < xy.length; i += 2) stroke.points.add(new InkView.Point(xy[i], xy[i + 1], .5f));
        strokes.add(stroke);
    }
    private long inkHash() {
        long hash = 1469598103934665603L;
        for (InkView.Stroke stroke : ink.snapshot()) {
            hash = (hash ^ (stroke.erase ? 1 : 0)) * 1099511628211L;
            hash = (hash ^ Float.floatToIntBits(stroke.width)) * 1099511628211L;
            for (InkView.Point point : stroke.points) { hash = (hash ^ Float.floatToIntBits(point.x)) * 1099511628211L; hash = (hash ^ Float.floatToIntBits(point.y)) * 1099511628211L; hash = (hash ^ Float.floatToIntBits(point.pressure)) * 1099511628211L; }
        }
        return hash;
    }
    private void releaseScene() {
        if (paper == null) return;
        ViewTreeObserver observer = paper.getViewTreeObserver();
        if (observer.isAlive()) { if (drawListener != null) observer.removeOnDrawListener(drawListener); if (layoutListener != null) observer.removeOnGlobalLayoutListener(layoutListener); }
        drawListener = null; layoutListener = null; paper.disposeReplyViews();
    }
    @Override protected void onPause() { suiteMode = false; if (running) finishCase("paused"); stopWork(); super.onPause(); }
    @Override protected void onDestroy() {
        destroyed = true; stopWork();
        if (frameListener != null) getWindow().removeOnFrameMetricsAvailableListener(frameListener);
        releaseScene(); super.onDestroy();
    }
    private static final class Times {
        final ArrayList<Long> values = new ArrayList<>();
        void add(long ns) { if (ns >= 0 && values.size() < 10000) values.add(ns); }
        JSONObject json() throws org.json.JSONException {
            JSONObject value = new JSONObject().put("samples", values.size()); if (values.isEmpty()) return value;
            ArrayList<Long> sorted = new ArrayList<>(values); Collections.sort(sorted); long total = 0; for (long n : sorted) total += n;
            return value.put("mean_ms", total / (double)sorted.size() / 1000000).put("p95_ms", sorted.get((int)Math.ceil(sorted.size() * .95) - 1) / 1000000.0).put("max_ms", sorted.get(sorted.size() - 1) / 1000000.0);
        }
    }
    private static final class Stats {
        int events, handled, penStarts, ends, completedEnds, selectionStarts, selectionCompleted, outlinePoints, inkCommits, warnings, frameDrops, globalLayouts, paperBoundsChanges;
        String lastWarning = "";
        final Times dispatch = new Times(), scheduleDelay = new Times(), frameWait = new Times(), drawWait = new Times(), replyLayout = new Times(), frameTotal = new Times();
        JSONObject json() throws org.json.JSONException {
            return new JSONObject().put("events", events).put("handled", handled).put("pen_starts", penStarts).put("ends", ends).put("completed_ends", completedEnds)
                    .put("selection_starts", selectionStarts).put("selection_callbacks", selectionCompleted).put("outline_points", outlinePoints).put("ink_commits", inkCommits)
                    .put("warnings", warnings).put("last_warning", lastWarning).put("frame_metric_drops", frameDrops).put("dispatch", dispatch.json()).put("schedule_delay", scheduleDelay.json())
                    .put("global_layout_callbacks", globalLayouts).put("paper_bounds_changes", paperBoundsChanges).put("ink_changed_calls", replyLayout.values.size())
                    .put("event_to_next_frame_callback", frameWait.json()).put("event_to_draw_start", drawWait.json()).put("reply_ink_changed", replyLayout.json()).put("window_frame_total", frameTotal.json());
        }
    }
}
