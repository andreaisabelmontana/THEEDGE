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
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;

/** Fixed offline Move QA. Actual production views/model/JSON/ZIP; never opens a saved notebook. */
public final class MovePreviewActivity extends Activity {
    private static final String TAG = "PliaMoveQA";
    private static final int EXPECTED_CHECKS = 60;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Step> steps = new ArrayList<>();
    private NotebookStore.Notebook notebook, original;
    private PageEditor editor;
    private InkView ink;
    private PaperSurface paper;
    private ScrollView scroll;
    private TextView label;
    private Button run;
    private int generation, stepIndex, checks, commits, aiStarts, aiCompletes, inkChanges, warnings;
    private boolean running, destroyed;
    private long downTime, deadline;
    private float replyX, replyY;
    private String originalHash, movedInkHash, movedReplyHash, deletedHash, protectedHash;
    private byte[] deletedZip;
    private interface Action { void run() throws Exception; }
    private static final class Step { final String name; final Action action; Step(String name, Action action) { this.name = name; this.action = action; } }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        label = new TextView(this); label.setTextColor(Color.BLACK); label.setTextSize(14); label.setPadding(12, 10, 12, 4);
        root.addView(label, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this);
        run = new Button(this); run.setText("Run fixed Move checks"); run.setOnClickListener(v -> start());
        Button reset = new Button(this); reset.setText("Reset synthetic page"); reset.setOnClickListener(v -> reset());
        controls.addView(run, new LinearLayout.LayoutParams(0, -2, 1)); controls.addView(reset, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        selectIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); selectIntent(intent); }
    private void selectIntent(Intent intent) {
        reset(); boolean auto = false;
        try { auto = intent != null && intent.getBooleanExtra("autorun", false); } catch (RuntimeException invalidExtra) { /* Fixed defaults only. */ }
        if (auto) { final int token = generation; main.postDelayed(() -> { if (token == generation && !destroyed) start(); }, 600); }
    }
    private void reset() {
        stop(); checks = commits = aiStarts = aiCompletes = inkChanges = warnings = 0; stepIndex = 0;
        original = MoveQaData.fresh(); notebook = original.copy();
        try { originalHash = MoveQaData.hash(notebook); protectedHash = MoveQaData.protectedHash(notebook); }
        catch (Exception invalid) { fail("fixture_initialization", invalid); return; }
        rebuildScene(); prepareSteps();
        label.setText("Offline Move QA — synthetic page only\nReal ink, KaTeX, drag/cancel/delete and memory-only JSON/ZIP. No notebook file or model access.");
        run.setEnabled(true);
    }
    private void rebuildScene() {
        if (paper != null) paper.disposeReplyViews(); scroll.removeAllViews();
        editor = new PageEditor(PageEditor.State.from(notebook.active()));
        ink = new InkView(this); ink.setExternalHistory(true); ink.setPlainPaper(true); ink.setFingerDrawing(false); ink.load(editor.state().ink);
        ink.setListener(new InkView.Listener() {
            public void changed() { if (running) inkChanges++; }
            public void state(boolean undo, boolean redo, boolean value) {}
            public void warning(String text) { if (running) warnings++; }
            public void selectionStarted() { if (running) aiStarts++; }
            public void selectionCompleted(List<InkView.Stroke> strokes) { if (running) aiCompletes++; }
        });
        paper = new PaperSurface(this, ink, Typeface.DEFAULT);
        paper.setReplies(editor.state().replies, true); paper.setEditor(editor);
        paper.setEditListener(new PaperSurface.EditListener() {
            public void started() { /* No AI work exists in this Activity. */ }
            public void committed(PageEditor.State state) { commits++; apply(); }
            public void selectionChanged(boolean available) {}
            public void warning(String text) { warnings++; }
        });
        paper.setMoveMode(true); scroll.addView(paper, new ScrollView.LayoutParams(-1, -2)); scroll.scrollTo(0, 0);
    }
    private void apply() {
        editor.state().applyTo(notebook.active());
        ink.load(editor.state().ink); paper.setReplies(editor.state().replies, true);
    }
    private void start() {
        if (running || destroyed || paper == null) return;
        running = true; run.setEnabled(false); deadline = SystemClock.uptimeMillis() + 50000;
        Log.i(TAG, "BEGIN steps=" + steps.size() + " expected_checks=" + EXPECTED_CHECKS + " synthetic_only=true storage_access=none model_calls=0");
        awaitMath(generation);
    }
    private void awaitMath(int token) {
        if (!running || token != generation || destroyed) return;
        if (SystemClock.uptimeMillis() >= deadline) { fail("suite_timeout", null); return; }
        if (paper.getWidth() <= 0) { main.postDelayed(() -> awaitMath(token), 50); return; }
        WebView math = mathView(paper);
        if (math == null) { main.postDelayed(() -> advance(token), 120); return; }
        math.evaluateJavascript("window.solutionReady===true", ready -> {
            if (!running || token != generation || destroyed) return;
            if ("true".equals(ready)) main.postDelayed(() -> advance(token), 120);
            else main.postDelayed(() -> awaitMath(token), 100);
        });
    }
    private static WebView mathView(View view) {
        if (view instanceof WebView) return (WebView)view;
        if (view instanceof ViewGroup) { ViewGroup g = (ViewGroup)view; for (int i = 0; i < g.getChildCount(); i++) { WebView found = mathView(g.getChildAt(i)); if (found != null) return found; } }
        return null;
    }
    private void advance(int token) {
        if (!running || token != generation || destroyed) return;
        if (stepIndex == steps.size()) { complete(); return; }
        Step step = steps.get(stepIndex++); label.setText("Offline Move QA " + stepIndex + "/" + steps.size() + " — " + step.name);
        try { step.action.run(); Log.i(TAG, "STEP index=" + stepIndex + " checks=" + checks + " status=PASS name=" + step.name); }
        catch (Exception | AssertionError failure) { fail(step.name, failure); return; }
        awaitMath(token);
    }
    private void add(String name, Action action) { steps.add(new Step(name, action)); }
    private void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private void prepareSteps() {
        steps.clear();
        add("Initial actual views", () -> {
            check(ink.snapshot().size() == 3, "three fixed strokes");
            check(paper.getChildCount() == 3, "two actual reply panels");
            check(mathView(paper) != null, "actual KaTeX WebView present");
            check(paper.isMoveMode() && !ink.isSelectionMode(), "Move distinct from AI Select");
            check(editor.undoDepth() == 0, "fresh local edit history");
        });
        add("Region selects whole ink", () -> {
            region(80, 190, 230, 340);
            check(paper.hasEditSelection(), "region selects ink");
            check(MoveQaData.hash(notebook).equals(originalHash), "selection changes no data");
            check(editor.undoDepth() == 0, "selection adds no history");
        });
        add("Ink drag preview", () -> {
            send(MotionEvent.ACTION_DOWN, 140, 270); send(MotionEvent.ACTION_MOVE, 200, 310);
            check(MoveQaData.hash(notebook).equals(originalHash), "preview changes no data");
            check(commits == 0 && editor.undoDepth() == 0, "preview never commits");
        });
        add("Ink drag owner UP", () -> {
            send(MotionEvent.ACTION_UP, 200, 310); movedInkHash = MoveQaData.hash(notebook);
            check(commits == 1 && editor.undoDepth() == 1, "one gesture one commit");
            check(MoveQaData.translated(original.active().strokes.get(0), notebook.active().strokes.get(0), 60, 40)
                    && MoveQaData.translated(original.active().strokes.get(1), notebook.active().strokes.get(1), 60, 40), "common delta preserves all selected points/pressure");
            check(MoveQaData.translated(original.active().strokes.get(2), notebook.active().strokes.get(2), 0, 0), "unselected ink unchanged");
            check(MoveQaData.at(notebook.active().replies.get(0), 600, 100, 300) && MoveQaData.at(notebook.active().replies.get(1), 650, 900, 300), "ink move leaves replies fixed");
            check(MoveQaData.protectedHash(notebook).equals(protectedHash), "typed note and unrelated synthetic page untouched");
        });
        add("Undo ink move", () -> { check(editor.undo(), "Undo ink"); apply(); check(MoveQaData.hash(notebook).equals(originalHash), "Undo restores exact original bytes"); });
        add("Redo ink move", () -> { check(editor.redo(), "Redo ink"); apply(); check(MoveQaData.hash(notebook).equals(movedInkHash), "Redo restores exact moved bytes"); });
        add("Ink move JSON ZIP cold model", this::roundTrip);
        add("Tap actual math reply", () -> {
            tapReply(); check(paper.hasEditSelection(), "tap selects rendered KaTeX reply");
            check(MoveQaData.hash(notebook).equals(movedInkHash), "tap does not move reply");
        });
        add("Reply drag preview", () -> {
            replyPoint(); send(MotionEvent.ACTION_DOWN, replyX, replyY); send(MotionEvent.ACTION_MOVE, replyX - 80, replyY + 100);
            check(MoveQaData.hash(notebook).equals(movedInkHash), "reply preview changes no data");
            check(editor.undoDepth() == 1, "reply preview adds no history");
        });
        add("Cancel reply drag", () -> {
            send(MotionEvent.ACTION_CANCEL, replyX - 80, replyY + 100);
            check(MoveQaData.hash(notebook).equals(movedInkHash), "CANCEL restores original reply/ink");
            check(!paper.hasEditSelection(), "CANCEL clears transient selection");
            check(commits == 1 && editor.undoDepth() == 1, "CANCEL adds no history");
        });
        add("Move actual math reply", () -> {
            tapReply(); replyPoint(); send(MotionEvent.ACTION_DOWN, replyX, replyY); send(MotionEvent.ACTION_MOVE, replyX - 120, replyY + 160); send(MotionEvent.ACTION_UP, replyX - 120, replyY + 160);
            movedReplyHash = MoveQaData.hash(notebook);
            check(commits == 2 && editor.undoDepth() == 2, "reply movement commits once");
            check(MoveQaData.at(notebook.active().replies.get(0), 480, 260, 300), "reply uses exact moved left/top/fixed width");
            check(MoveQaData.sameReply(original.active().replies.get(0), notebook.active().replies.get(0)), "reply math/words/source/input/anchor intact");
            check(MoveQaData.translated(original.active().strokes.get(0), notebook.active().strokes.get(0), 60, 40)
                    && notebook.active().strokes.size() == 3, "reply move leaves ink intact");
            check(MoveQaData.sameReply(original.active().replies.get(1), notebook.active().replies.get(1)) && MoveQaData.at(notebook.active().replies.get(1), 650, 900, 300), "other reply unchanged");
        });
        add("Undo reply move", () -> { check(editor.undo(), "Undo reply move"); apply(); check(MoveQaData.hash(notebook).equals(movedInkHash), "reply Undo exact bytes"); });
        add("Redo reply move", () -> { check(editor.redo(), "Redo reply move"); apply(); check(MoveQaData.hash(notebook).equals(movedReplyHash), "reply Redo exact bytes"); });
        add("Reply move JSON ZIP cold model", this::roundTrip);
        add("Delete selected math reply", () -> {
            tapReply(); check(paper.hasEditSelection(), "moved reply selectable");
            check(paper.deleteEditSelection(), "explicit local Delete accepted"); deletedHash = MoveQaData.hash(notebook); deletedZip = MoveQaData.zip(notebook);
            check(commits == 3 && editor.undoDepth() == 3, "Delete commits once");
            check(notebook.active().replies.size() == 1 && MoveQaData.sameReply(original.active().replies.get(1), notebook.active().replies.get(0)), "Delete removes only selected whole reply");
            check(notebook.active().strokes.size() == 3 && MoveQaData.translated(original.active().strokes.get(0), notebook.active().strokes.get(0), 60, 40), "Delete leaves ink intact");
        });
        add("Deletion JSON ZIP cold model", this::roundTrip);
        add("Undo delete", () -> { check(editor.undo(), "Undo Delete"); apply(); check(MoveQaData.hash(notebook).equals(movedReplyHash), "Undo restores paired reply at moved slot"); });
        add("Redo delete", () -> { check(editor.redo(), "Redo Delete"); apply(); check(MoveQaData.hash(notebook).equals(deletedHash), "Redo restores exact deletion"); });
        add("Cold rebuild actual views from ZIP bytes", () -> {
            notebook = MoveQaData.fromZip(deletedZip); rebuildScene();
            check(MoveQaData.hash(notebook).equals(deletedHash), "cold model reconstructs exact committed content");
            check(editor.undoDepth() == 0 && editor.redoDepth() == 0, "cold edit history is correctly new");
        });
        add("Final reconstructed state", () -> {
            check(paper.getChildCount() == 2 && notebook.active().replies.size() == 1, "fresh view shows only retained reply");
            check(MoveQaData.translated(original.active().strokes.get(0), ink.snapshot().get(0), 60, 40) && MoveQaData.at(notebook.active().replies.get(0), 650, 900, 300), "fresh view receives persisted ink/reply positions");
            check(aiStarts == 0 && aiCompletes == 0 && inkChanges == 0, "Move never enters AI Select or pen callbacks");
            check(MoveQaData.protectedHash(notebook).equals(protectedHash), "all typed metadata and unrelated synthetic page unchanged");
            check(commits == 3, "only two moves and one Delete committed");
        });
    }
    private void roundTrip() throws Exception {
        byte[] expected = MoveQaData.json(notebook);
        NotebookStore.Notebook json = NotebookStore.decode(expected), zip = MoveQaData.fromZip(MoveQaData.zip(notebook));
        check(Arrays.equals(expected, MoveQaData.json(json)), "actual JSON decode preserves all committed data");
        check(Arrays.equals(expected, MoveQaData.json(zip)), "actual ZIP write/read preserves all committed data");
        PageEditor cold = new PageEditor(PageEditor.State.from(zip.active())); cold.state().applyTo(zip.active());
        check(Arrays.equals(expected, MoveQaData.json(zip)), "cold PageEditor reconstructs exact persisted geometry/content");
    }
    private void region(float x, float y, float right, float bottom) { send(MotionEvent.ACTION_DOWN, x, y); send(MotionEvent.ACTION_MOVE, right, bottom); send(MotionEvent.ACTION_UP, right, bottom); }
    private void replyPoint() { View panel = paper.getChildAt(1); float scale = paper.getWidth() / 1000f;
        replyX = (panel.getLeft() + panel.getWidth() / 2f) / scale; replyY = (panel.getTop() + Math.min(panel.getHeight() / 2f, 40 * scale)) / scale; }
    private void tapReply() { replyPoint(); send(MotionEvent.ACTION_DOWN, replyX, replyY); send(MotionEvent.ACTION_UP, replyX, replyY); }
    private void send(int action, float x, float y) {
        long now = SystemClock.uptimeMillis(); if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent.PointerProperties prop = new MotionEvent.PointerProperties(); prop.id = 0; prop.toolType = MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords(); float scale = paper.getWidth() / 1000f;
        coords.x = x * scale; coords.y = y * scale; coords.pressure = .5f; coords.size = .02f;
        MotionEvent event = MotionEvent.obtain(downTime, now, action, 1, new MotionEvent.PointerProperties[]{prop}, new MotionEvent.PointerCoords[]{coords}, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_STYLUS, 0);
        try { if (!paper.dispatchTouchEvent(event)) throw new AssertionError("Fixed Move event unhandled"); } finally { event.recycle(); }
    }
    private void complete() {
        try {
            if (checks != EXPECTED_CHECKS) throw new AssertionError("Unexpected check count: " + checks);
            String hash = MoveQaData.hash(notebook);
            JSONObject result = new JSONObject().put("checks_passed", checks).put("steps", steps.size()).put("edit_commits", commits)
                    .put("state_sha256", hash).put("synthetic_note_untouched", MoveQaData.protectedHash(notebook).equals(protectedHash))
                    .put("ai_callbacks", aiStarts + aiCompletes).put("warnings", warnings).put("storage_access", "none").put("backup_medium", "memory_only");
            Log.i(TAG, "MOVE_SUITE_COMPLETE " + result); label.setText("Move QA passed: " + checks + " checks\n" + hash + "\nSynthetic note untouched. No saved notebook or model accessed.");
        } catch (Exception | AssertionError failure) { fail("final", failure); return; }
        running = false;
    }
    private void fail(String stage, Throwable error) {
        running = false; main.removeCallbacksAndMessages(null);
        String reason = error == null ? stage : error.getMessage();
        Log.e(TAG, "MOVE_SUITE_FAILED stage=" + stage + " checks=" + checks + " reason=" + reason);
        if (label != null) label.setText("Move QA stopped: " + stage + "\n" + reason + "\nOnly synthetic data was used.");
        if (run != null) run.setEnabled(false);
    }
    private void stop() { running = false; generation++; main.removeCallbacksAndMessages(null); if (paper != null) paper.clearEditSelection(); }
    @Override protected void onPause() { if (running) fail("activity_paused", null); stop(); super.onPause(); }
    @Override protected void onDestroy() { destroyed = true; stop(); if (paper != null) paper.disposeReplyViews(); super.onDestroy(); }
}
