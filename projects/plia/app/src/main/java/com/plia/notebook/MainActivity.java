package com.plia.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.Editable;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native paper notebook with a separately authorized ChatGPT connection. */
public final class MainActivity extends Activity {
    private static final int CREATE_DOCUMENT = 10, OPEN_BACKUP = 11, OPEN_TUTOR = 603;
    private static final String FIREFOX_PACKAGE = "org.mozilla.firefox";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService draftWorker = Executors.newSingleThreadExecutor();
    private final ExecutorService aiWorker = Executors.newSingleThreadExecutor();
    private ChatGptClient chatGpt;
    private SiwcStore.Snapshot chatGptAccounts;
    private SiwcInference.ModelInfo handwritingModel;
    private Future<?> aiTask, accountTask;
    private ChatGptClient.Cancellation aiCancellation, accountCancellation;
    private ChatGptClient.AnswerTrace aiTrace;
    private ChatGptClient.SignIn signInControl;
    private boolean accountBusy, accountReady, repliesEnabled;
    private ReplySettings replySettings = ReplySettings.uninitialized();
    private String chatGptStatus = "Checking saved ChatGPT connection.";
    private String pendingSignInStatus;
    private AuthDiagnostic lastAuthDiagnostic;
    private TextView authDiagnosticView;
    private AiDiagnostic lastAiDiagnostic;
    private TextView aiStatusView;
    private boolean pendingSignInModels;
    private long aiStartedAt;
    private final PaperSession paperSession = new PaperSession();
    private Future<?> draftTask;
    private Runnable draftDelay, readyDelay;
    private PaperDraft localDraft;
    private PaperSession.Ticket localTicket;
    private String retryAccountId;
    private SiwcInference.ModelInfo retryModel;
    private boolean foreground, paperPenDown, selectionTool, tutorRenderersReleased;
    private boolean initialWorkspaceRoute, returnToThinkRequested;
    private String paperStatus = "Use Select to send a chosen area. Ordinary writing stays on this device.";
    private NotebookStore storage;
    private NotebookStore.Notebook notebook;
    private InkView ink;
    private String inkPageId, serverUrl, token = "", provider = "dot";
    private Typeface handwriting;
    private LinearLayout content, tutorFields, tutorChrome, tutorFooter, sideTools;
    private ScrollView pageScroll;
    private PaperSurface paper;
    private PageEditor pageEditor;
    private boolean applyingEditor;
    private View pageMenu;
    private ToolButton topUndo, topRedo, topPrevious, topNext, selectTool, penTool, eraserTool, moveTool, removeTool;
    private final List<ToolButton> inkColors = new ArrayList<>();
    private static final int[] PEN_COLORS = {PenSettings.BLACK, PenSettings.BLUE, PenSettings.RED};
    private static final String[] PEN_COLOR_NAMES = {"Black", "Blue", "Red"};
    private TextView notebookName, pageCounter;
    private Button repliesVisibility;
    private boolean repliesVisible = true;
    private boolean replyPending;
    private CheckBox finger;
    private TextView saving, connection, notice, position, questionLabel, problemSource;
    private EditText title, goal, problem, question;
    private Button previous, next, newPage, tutor, think, undo, redo, clear, send, generate, stop, export, backup, importBackup;
    private CheckBox includeImage;
    private boolean rendering, loaded, readOnly, exportBusy;
    private volatile boolean destroyed;
    private long revision, savedRevision;
    private Future<?> networkTask;
    private volatile BridgeClient client;
    private boolean busy;
    private byte[] exportBytes;
    private NotebookStore.Notebook exportNotebook;
    private boolean exportRecovery;
    private final Runnable delayedSave = this::flushSave;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        initialWorkspaceRoute = state == null || state.getBoolean("initialWorkspaceRoute", false);
        returnToThinkRequested = state != null && state.getBoolean("returnToThink", false);
        readReplySettings();
        try { repliesVisible = getSharedPreferences("paper_view", MODE_PRIVATE).getBoolean("replies_visible", true); }
        catch (ClassCastException invalid) { repliesVisible = true; }
        serverUrl = getPreferences(MODE_PRIVATE).getString("companionUrl", "");
        SharedPreferences diagnostics = getSharedPreferences("auth_diagnostics", MODE_PRIVATE);
        try {
            lastAuthDiagnostic = AuthDiagnostic.restore(diagnostics.getString("stage", ""),
                    diagnostics.getString("code", ""), diagnostics.getInt("http_status", 0), diagnostics.getLong("elapsed_ms", 0));
        } catch (ClassCastException invalid) { lastAuthDiagnostic = null; }
        SharedPreferences aiDiagnostics = getSharedPreferences("ai_diagnostics", MODE_PRIVATE);
        try {
            lastAiDiagnostic = AiDiagnostic.restore(aiDiagnostics.getString("stage", ""),
                    aiDiagnostics.getString("code", ""), aiDiagnostics.getInt("http_status", 0), aiDiagnostics.getLong("elapsed_ms", 0),
                    aiDiagnostics.getString("content_type", "NOT_OBSERVED"), aiDiagnostics.getString("body_shape", "NOT_INSPECTED"));
        } catch (ClassCastException invalid) { lastAiDiagnostic = null; }
        try { handwriting = Typeface.createFromAsset(getAssets(), "handwriting.ttf"); } catch (RuntimeException missing) { handwriting = Typeface.create("casual", Typeface.NORMAL); }
        storage = new NotebookStore(this); buildUi();
        if (lastAiDiagnostic != null && lastAiDiagnostic.code != AiDiagnostic.Code.COMPLETED) {
            showAiStatus(lastAiDiagnostic.code == AiDiagnostic.Code.IN_PROGRESS
                    ? "The previous reply was interrupted. Automatic replies are paused. Your ink is saved."
                    : "The last reply did not complete. Replies off. Tap for details.");
        }
        loadChatGptAccounts();
        NotebookStore.IO.execute(() -> {
            try { NotebookStore.Notebook value = storage.read(); main.post(() -> { if (destroyed) return; notebook = value; loaded = true; loadPage(); setEnabled(true); saving.setText("Saved on this device"); restoreWorkspaceRoute(); }); }
            catch (Exception | OutOfMemoryError error) { main.post(() -> { if (destroyed) return; notebook = NotebookStore.Notebook.fresh(); loaded = true; readOnly = true; loadPage(); setEnabled(true); saving.setText("Storage unreadable · original file kept"); announce("The saved notebook could not be read. Its file has been preserved. Export a recovery backup before repairing it. New work stays in memory until you restore a valid backup."); }); }
        });
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, float size) { TextView view = new TextView(this); view.setText(value); view.setTextColor(0xff20251e); view.setTextSize(size); view.setPadding(0, dp(7), 0, dp(7)); return view; }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private void weighted(LinearLayout row, View child) { row.addView(child, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)); }
    private Button button(String label, Runnable action) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(48)); button.setTextSize(14); button.setOnClickListener(view -> action.run()); return button; }
    private EditText field(String hint, int max, int lines) { EditText view = new EditText(this); view.setHint(hint); view.setContentDescription(hint); view.setTextColor(0xff171917); view.setTextSize(17); view.setMinHeight(dp(48)); view.setMinLines(lines); view.setMaxLines(Math.max(lines, 5)); view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | (lines > 1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0)); view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)}); return view; }
    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private Button modeButton(String label, String mode) {
        Button view = button(label, () -> { if ("tutor".equals(mode)) openTutor(); else changeMode(mode); }); view.setMinHeight(0); view.setMinimumHeight(0); view.setMinWidth(0); view.setMinimumWidth(0);
        view.setPadding(0, 0, 0, 0); view.setTextSize(13); view.setStateListAnimator(null); return view;
    }
    /** Open the goal collection while preserving the current notebook page and its saved mode. */
    private void openTutor() {
        if (!loaded || tutorRenderersReleased) return;
        captureInk(); resetPaperContext(); cancelNetwork(); flushSave();
        paper.disposeReplyViews(); tutorRenderersReleased = true;
        initialWorkspaceRoute = false;
        startActivityForResult(new Intent(this, TutorLibraryActivity.class), OPEN_TUTOR);
    }
    private void restoreWorkspaceRoute() {
        if (!loaded || !foreground || destroyed) return;
        if (returnToThinkRequested) {
            returnToThinkRequested = false; initialWorkspaceRoute = false;
            changeMode("think"); // The user explicitly chose Think; ink and typed content remain on the page.
        } else if (initialWorkspaceRoute) {
            initialWorkspaceRoute = false;
            if (current().mode.equals("tutor")) openTutor();
        }
    }
    private void buildUi() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) { android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); }
            else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout top = row(); top.setPadding(dp(4), 0, dp(4), 0);
        top.addView(new ToolButton(this, "Pages and notes", ToolButton.Icon.BACK, this::showNotebookMenu), new LinearLayout.LayoutParams(dp(48), dp(48)));
        notebookName = text("Plia", 14); notebookName.setSingleLine(true); notebookName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        notebookName.setOnClickListener(view -> { if (loaded) showNotebookMenu(); });
        top.addView(notebookName, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout modes = row(); GradientDrawable outline = rounded(0xfff1f2ee, 21); outline.setStroke(dp(1), 0xffd7dad2); modes.setBackground(outline); modes.setPadding(dp(2), dp(2), dp(2), dp(2));
        tutor = modeButton("Tutor", "tutor"); think = modeButton("Think", "think");
        modes.addView(tutor, new LinearLayout.LayoutParams(dp(52), dp(36))); modes.addView(think, new LinearLayout.LayoutParams(dp(52), dp(36)));
        top.addView(modes);
        if (getResources().getConfiguration().screenWidthDp >= 800) {
            for (int i = 0; i < PEN_COLORS.length; i++) {
                final int color = PEN_COLORS[i];
                ToolButton preset = colorButton(PEN_COLOR_NAMES[i], color, () -> chooseInkColor(color));
                inkColors.add(preset); top.addView(preset, new LinearLayout.LayoutParams(dp(48), dp(48)));
            }
        }
        topUndo = new ToolButton(this, "Undo edit", ToolButton.Icon.UNDO, () -> restoreEdit(false));
        topRedo = new ToolButton(this, "Redo edit", ToolButton.Icon.REDO, () -> restoreEdit(true));
        topPrevious = new ToolButton(this, "Previous page", ToolButton.Icon.PREVIOUS, () -> movePage(-1));
        topNext = new ToolButton(this, "Next page", ToolButton.Icon.NEXT, () -> movePage(1));
        top.addView(topUndo, new LinearLayout.LayoutParams(dp(48), dp(48))); top.addView(topRedo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(topPrevious, new LinearLayout.LayoutParams(dp(48), dp(48)));
        pageCounter = text("", 12); pageCounter.setGravity(Gravity.CENTER); top.addView(pageCounter, new LinearLayout.LayoutParams(dp(38), dp(48)));
        top.addView(topNext, new LinearLayout.LayoutParams(dp(48), dp(48)));
        repliesVisibility = new Button(this); repliesVisibility.setAllCaps(false); repliesVisibility.setTextSize(12);
        repliesVisibility.setMinWidth(0); repliesVisibility.setMinimumWidth(0); repliesVisibility.setPadding(dp(4), 0, dp(4), 0);
        repliesVisibility.setBackgroundColor(Color.TRANSPARENT); updateReplyVisibilityLabel();
        repliesVisibility.setOnClickListener(view -> toggleReplyVisibility());
        top.addView(repliesVisibility, new LinearLayout.LayoutParams(dp(82), dp(48)));
        pageMenu = new ToolButton(this, "Page tools and actions", ToolButton.Icon.MORE, this::showPageMenu);
        top.addView(pageMenu, new LinearLayout.LayoutParams(dp(48), dp(48))); root.addView(top);
        aiStatusView = text("", 13); aiStatusView.setPadding(dp(20), dp(6), dp(20), dp(6));
        aiStatusView.setMaxLines(2); aiStatusView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        aiStatusView.setBackgroundColor(0xfff1f2ee); aiStatusView.setVisibility(View.GONE);
        aiStatusView.setOnClickListener(view -> { if (retryAvailable()) confirmRetrySelection(); else showChatGptConnection(); }); root.addView(aiStatusView);
        pageScroll = new ScrollView(this); pageScroll.setSmoothScrollingEnabled(false); pageScroll.setFillViewport(true); pageScroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(Color.WHITE);
        pageScroll.addView(content);
        LinearLayout workspace = row(); workspace.setGravity(Gravity.TOP);
        sideTools = new LinearLayout(this); sideTools.setOrientation(LinearLayout.VERTICAL); sideTools.setBackgroundColor(0xfffafaf8);
        penTool = new ToolButton(this, "Pen", ToolButton.Icon.PEN, () -> useTool("pen"));
        penTool.setOnLongClickListener(view -> { showPenSettings(); return true; });
        penTool.setContentDescription("Pen. Hold for pen settings."); penTool.setTooltipText("Pen — hold for settings");
        eraserTool = new ToolButton(this, "Eraser", ToolButton.Icon.ERASER, () -> useTool("eraser"));
        selectTool = new ToolButton(this, "AI Select", ToolButton.Icon.SELECT, () -> useTool("select")); selectTool.setShowLabel(true);
        moveTool = new ToolButton(this, "Move", ToolButton.Icon.MOVE, () -> useTool("move")); moveTool.setShowLabel(true);
        moveTool.setContentDescription("Move Select"); moveTool.setTooltipText("Move Select");
        removeTool = new ToolButton(this, "Remove", ToolButton.Icon.DELETE, this::removeSelected); removeTool.setShowLabel(true);
        removeTool.setContentDescription("Remove selected ink and replies"); removeTool.setVisibility(View.GONE);
        sideTools.addView(penTool, new LinearLayout.LayoutParams(dp(56), dp(52)));
        sideTools.addView(eraserTool, new LinearLayout.LayoutParams(dp(56), dp(52)));
        sideTools.addView(selectTool, new LinearLayout.LayoutParams(dp(56), dp(62)));
        sideTools.addView(moveTool, new LinearLayout.LayoutParams(dp(56), dp(62)));
        sideTools.addView(removeTool, new LinearLayout.LayoutParams(dp(56), dp(62)));
        sideTools.addView(new ToolButton(this, "New page", ToolButton.Icon.NEW_PAGE, this::addPage), new LinearLayout.LayoutParams(dp(56), dp(52)));
        sideTools.addView(new ToolButton(this, "Export page PNG", ToolButton.Icon.EXPORT, this::exportImage), new LinearLayout.LayoutParams(dp(56), dp(52)));
        workspace.addView(sideTools, new LinearLayout.LayoutParams(dp(56), -1)); workspace.addView(pageScroll, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); root.requestApplyInsets();

        tutorChrome = new LinearLayout(this); tutorChrome.setOrientation(LinearLayout.VERTICAL); content.addView(tutorChrome);
        saving = text("Opening notebook.", 12); tutorChrome.addView(saving);
        LinearLayout pages = row(); previous = button("< Previous", () -> movePage(-1)); next = button("Next >", () -> movePage(1)); newPage = button("New page", this::addPage); weighted(pages, previous); weighted(pages, next); weighted(pages, newPage); tutorChrome.addView(pages);
        position = text("", 12); tutorChrome.addView(position); title = field("Page title", 100, 1); title.setTextSize(24); tutorChrome.addView(title);
        tutorFields = new LinearLayout(this); tutorFields.setOrientation(LinearLayout.VERTICAL); tutorFields.addView(text("What are you learning?", 14)); goal = field("Learning goal", 1500, 1); tutorFields.addView(goal); generate = button("Ask AIM for a problem", () -> ask("problem")); tutorFields.addView(generate);
        problemSource = text("The problem", 14); tutorFields.addView(problemSource); problem = field("Your problem", 4000, 3); tutorFields.addView(problem); tutorChrome.addView(tutorFields);
        questionLabel = text("Typed note or answer (optional)", 14); tutorChrome.addView(questionLabel); question = field("Type here, or write on the page", 4000, 2); tutorChrome.addView(question);
        LinearLayout tools = row(); weighted(tools, button("Pen", () -> useTool("pen"))); weighted(tools, button("Erase", () -> useTool("eraser"))); weighted(tools, button("Pen settings", this::showPenSettings)); tutorChrome.addView(tools);
        LinearLayout history = row(); undo = button("Undo", () -> restoreEdit(false)); redo = button("Redo", () -> restoreEdit(true)); clear = button("Clear ink", this::confirmClear); weighted(history, undo); weighted(history, redo); weighted(history, clear); tutorChrome.addView(history);
        finger = new CheckBox(this); finger.setText("Draw with a finger"); finger.setMinHeight(dp(48)); finger.setOnCheckedChangeListener((button, checked) -> { if (loaded) resetPaperContext(); ink.setFingerDrawing(checked); }); tutorChrome.addView(finger);
        ink = new InkView(this); ink.setPenWidth(PenSettings.width(this)); ink.setPenColor(PenSettings.color(this));
        updateInkColorButtons(); ink.setExternalHistory(true); paper = new PaperSurface(this, ink, handwriting); content.addView(paper, new LinearLayout.LayoutParams(-1, -2));
        paper.setEditListener(new PaperSurface.EditListener() {
            public void started() { localEditStarted(); }
            public void committed(PageEditor.State state) { applyEditState(state); }
            public void selectionChanged(boolean available) { refreshEditButtons(); }
            public void warning(String message) { announce(message); }
        });
        ink.setListener(new InkView.Listener() {
            public void started() { paperStarted(); }
            public void changed() {
                if (!loaded || applyingEditor) return;
                NotebookStore.Page page = notebook.find(inkPageId);
                if (page != null) {
                    page.strokes = ink.snapshot();
                    if (pageEditor != null && pageEditor.state().pageId.equals(page.id)) pageEditor.recordExternalChange(page);
                    dirty(); if (!page.replies.isEmpty()) placeReplies(); refreshEditButtons();
                }
                if (!rendering && foreground && current().mode.equals("think")) {
                    clearPaperWork(); paperSession.inkChanged(); cancelNetwork();
                    paperStatus = "Use Select to send a chosen area. " + automaticStatus();
                    if (!paperPenDown) paperSession.cancel();
                }
            }
            public void ended(boolean completed) { paperEnded(completed); }
            public void selectionStarted() { selectionStartedOnPaper(); }
            public void selectionCompleted(List<InkView.Stroke> gesture) { selectionCompletedOnPaper(gesture); }
            public void state(boolean canUndo, boolean canRedo, boolean hasInk) { refreshEditButtons(); }
            public void warning(String message) {
                if (selectionTool) { paperStatus = message; showAiStatus(message); }
                announce(message);
            }
        });
        tutorFooter = new LinearLayout(this); tutorFooter.setOrientation(LinearLayout.VERTICAL); content.addView(tutorFooter);
        includeImage = new CheckBox(this); includeImage.setText("Include ink in manual AIM sends"); includeImage.setChecked(true); includeImage.setMinHeight(dp(48)); tutorFooter.addView(includeImage);
        LinearLayout sendRow = row(); send = button("Send to AIM", () -> ask(current().mode.equals("tutor") ? "feedback" : "ask")); stop = button("Stop waiting", () -> { stopReply(); cancelNetwork(); announce("Stopped waiting. A request already sent may still be completed by AIM; your writing is safe."); }); weighted(sendRow, send); weighted(sendRow, stop); tutorFooter.addView(sendRow);
        connection = text("Companion not checked", 13); tutorFooter.addView(connection); tutorFooter.addView(button("Connection settings", this::settings));
        notice = text("", 14); notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); tutorFooter.addView(notice);
        LinearLayout files = row(); export = button("Save page PNG", this::exportImage); backup = button("Export backup", this::exportBackup); weighted(files, export); weighted(files, backup); tutorFooter.addView(files);
        importBackup = button("Import backup", () -> { Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/json"}).addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(intent, OPEN_BACKUP); }); tutorFooter.addView(importBackup);
        watch(title, "title"); watch(goal, "goal"); watch(problem, "problem"); watch(question, "prompt"); setEnabled(false);
    }
    private void useTool(String tool) {
        if (!loaded || ink == null) return;
        boolean select = "select".equals(tool), move = "move".equals(tool);
        if (select && !current().mode.equals("think")) { announce("AI Select is available on a Think page."); return; }
        resetPaperContext(); ink.clearSelection(); selectionTool = select;
        ink.setEraser("eraser".equals(tool)); ink.setSelectionMode(select);
        paper.setMoveMode(move);
        penTool.setSelected("pen".equals(tool)); eraserTool.setSelected("eraser".equals(tool)); selectTool.setSelected(select);
        moveTool.setSelected(move); refreshEditButtons();
        if (select) {
            paperStatus = "Circle the problem, then lift. Plia closes the lasso for you. " + automaticStatus();
            showAiStatus(paperStatus);
            announce("Circle or box a problem. " + (automaticReady() ? "A completed selection requests an answer." : "Replies are paused; the selection stays on this device."));
        } else if (move) {
            showAiStatus("Move: draw a box around whole strokes or tap a reply. Drag the selection, or tap Remove. Undo restores local edits.");
        } else showAiStatus(automaticStatus() + " Tap for controls.");
    }
    private ToolButton colorButton(String name, int color, Runnable action) {
        ToolButton button = new ToolButton(this, name, ToolButton.Icon.COLOR, action);
        button.setInkColor(color); button.setShowLabel(true);
        button.setContentDescription(name + " ink"); button.setTooltipText(name + " ink");
        return button;
    }
    private void updateInkColorButtons() {
        if (ink == null) return;
        for (int i = 0; i < inkColors.size(); i++) inkColors.get(i).setSelected(ink.getPenColor() == PEN_COLORS[i]);
    }
    private void chooseInkColor(int color) {
        if (!loaded || ink == null || !ink.isEnabled()) return;
        useTool("pen"); ink.setPenColor(color);
        PenSettings.save(this, ink.getPenWidth(), ink.getPenColor()); updateInkColorButtons();
    }
    private void showPenSettings() {
        if (!loaded || ink == null || !ink.isEnabled()) return;
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(4), dp(20), dp(8)); panel.addView(text("Stroke width", 14));
        RadioGroup widths = new RadioGroup(this); widths.setOrientation(RadioGroup.HORIZONTAL);
        String[] names = {"Thin", "Medium", "Bold"}; float[] values = {2f, 3.5f, 6f};
        for (int i = 0; i < values.length; i++) {
            final float width = values[i]; RadioButton choice = new RadioButton(this);
            choice.setId(View.generateViewId()); choice.setText(names[i]); choice.setTextColor(0xff171917);
            choice.setTextSize(13); choice.setMinHeight(dp(48));
            widths.addView(choice, new RadioGroup.LayoutParams(0, -2, 1));
            choice.setChecked(ink.getPenWidth() == width);
            choice.setOnClickListener(view -> {
                useTool("pen"); ink.setPenWidth(width);
                PenSettings.save(this, ink.getPenWidth(), ink.getPenColor());
            });
        }
        panel.addView(widths); panel.addView(text("Ink color", 14));
        LinearLayout colors = row(); List<ToolButton> choices = new ArrayList<>();
        for (int i = 0; i < PEN_COLORS.length; i++) {
            final int color = PEN_COLORS[i];
            ToolButton choice = colorButton(PEN_COLOR_NAMES[i], color, () -> {
                chooseInkColor(color);
                for (int j = 0; j < choices.size(); j++) choices.get(j).setSelected(ink.getPenColor() == PEN_COLORS[j]);
            });
            choice.setSelected(ink.getPenColor() == color); choices.add(choice);
            colors.addView(choice, new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        panel.addView(colors);
        new AlertDialog.Builder(this).setTitle("Pen settings").setView(panel).setPositiveButton("Done", null).show();
    }
    /** Cancel admission without invalidating the Move selection captured by drag/Remove. */
    private void localEditStarted() {
        if (!loaded) return;
        clearPaperWork(); paperSession.cancel(); paperPenDown = false; cancelNetwork();
    }
    private void refreshEditButtons() {
        boolean enabled = loaded && ink != null && ink.isEnabled() && pageEditor != null;
        boolean canUndo = enabled && pageEditor.canUndo(), canRedo = enabled && pageEditor.canRedo();
        if (undo != null) undo.setEnabled(canUndo);
        if (redo != null) redo.setEnabled(canRedo);
        if (topUndo != null) topUndo.setEnabled(canUndo);
        if (topRedo != null) topRedo.setEnabled(canRedo);
        if (clear != null) clear.setEnabled(enabled && ink.hasInk());
        if (removeTool != null) {
            removeTool.setVisibility(paper != null && paper.isMoveMode() ? View.VISIBLE : View.GONE);
            removeTool.setEnabled(enabled && paper != null && paper.hasEditSelection());
        }
    }
    /** Page replacement or a successful persisted reply transaction is a local-history boundary. */
    private void resetEditHistory() {
        PageEditor.State state = PageEditor.State.from(current());
        // Keep the saved page, InkView and editor on the same immutable stroke identities.
        current().strokes = new ArrayList<>(state.ink);
        if (pageEditor == null) pageEditor = new PageEditor(state); else pageEditor.resetForPage(state);
        paper.setEditor(pageEditor); refreshEditButtons();
    }
    private void restoreEdit(boolean redoEdit) {
        if (!loaded || pageEditor == null || !ink.isEnabled()) return;
        resetPaperContext(); cancelNetwork(); ink.finish();
        boolean changed = redoEdit ? pageEditor.redo() : pageEditor.undo();
        if (changed) applyEditState(pageEditor.state()); else refreshEditButtons();
    }
    private void removeSelected() {
        if (!loaded || paper == null || !ink.isEnabled()) return;
        if (!paper.deleteEditSelection()) announce("Select whole strokes or a reply with Move first.");
    }
    /** Apply only the local editor's current immutable state; archive metadata remains untouched. */
    private void applyEditState(PageEditor.State state) {
        if (!loaded || applyingEditor || pageEditor == null || state != pageEditor.state()
                || !state.pageId.equals(current().id) || !state.pageId.equals(inkPageId)) return;
        boolean repliesChanged = !ReplyRefresh.sameReplies(current().replies, state.replies);
        boolean inkChanged = current().strokes.size() != state.ink.size();
        if (!inkChanged) for (int i = 0; i < state.ink.size(); i++)
            if (current().strokes.get(i) != state.ink.get(i)) { inkChanged = true; break; }
        applyingEditor = true;
        try { state.applyTo(current()); if (inkChanged) ink.load(state.ink); }
        finally { applyingEditor = false; }
        if (repliesChanged) renderReplies(); else if (inkChanged) paper.inkChanged();
        dirty(); flushSave(); refreshEditButtons();
    }
    private void confirmClear() {
        String pageId = current().id;
        new AlertDialog.Builder(this).setTitle("Clear this page's ink?").setMessage("Saved answers and typed text stay. Undo can restore the ink while this page stays open.").setNegativeButton("Cancel", null).setPositiveButton("Clear ink", (dialog, which) -> {
            if (current().id.equals(pageId)) { resetPaperContext(); cancelNetwork(); ink.clear(); } else announce("The active page changed. No ink was cleared.");
        }).show();
    }
    private void showPageMenu() {
        if (!loaded) return;
        String[] items = {"Pen", "Eraser", "Pen settings", "Undo", "Redo", "AI Select", "Pages and notes", "Input options", "Connection and status", "Export / import", "Clear ink", "Stop waiting", "ChatGPT connection", "Show / hide tools", "Undo reply refresh", "Redo reply refresh", "Clear replies", "Refresh reply: select problem", "Move Select", "Remove selected"};
        new AlertDialog.Builder(this).setTitle(current().title.isEmpty() ? "Page tools" : current().title).setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: useTool("pen"); break;
                case 1: useTool("eraser"); break;
                case 2: showPenSettings(); break;
                case 3: restoreEdit(false); break;
                case 4: restoreEdit(true); break;
                case 5: useTool("select"); break;
                case 6: showNotebookMenu(); break;
                case 7: new AlertDialog.Builder(this).setTitle("Input options").setMultiChoiceItems(new String[]{"Draw with a finger", "Include ink in manual AIM sends"}, new boolean[]{finger.isChecked(), includeImage.isChecked()}, (choice, item, checked) -> { if (item == 0) finger.setChecked(checked); else includeImage.setChecked(checked); }).setPositiveButton("Done", null).show(); break;
                case 8: new AlertDialog.Builder(this).setTitle("Connection and status").setMessage(saving.getText() + "\n\n" + paperStatus + "\n\n" + chatGptStatus + "\n" + automaticStatus() + "\n\n" + aiDiagnosticText() + "\n\n" + connection.getText() + "\n\n" + notice.getText() + "\n\nThe manual AIM bridge still needs an explicit handoff. Select replies use the separate ChatGPT connection. Its enabled setting is saved for this account and model.").setNegativeButton("Close", null).setPositiveButton("AIM settings", (choice, item) -> settings()).show(); break;
                case 9: new AlertDialog.Builder(this).setTitle("Export / import").setItems(new String[]{"Save page PNG", "Export notebook backup", "Import backup"}, (choice, item) -> { if (item == 0) export.performClick(); else if (item == 1) backup.performClick(); else importBackup.performClick(); }).show(); break;
                case 10: confirmClear(); break;
                case 11: stopReply(); cancelNetwork(); break;
                case 12: showChatGptConnection(); break;
                case 13: sideTools.setVisibility(sideTools.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); break;
                case 14: restoreReplyHistory(false); break;
                case 15: restoreReplyHistory(true); break;
                case 16: confirmClearReplies(); break;
                case 17: useTool("select"); showAiStatus("Circle the problem and lift. The new reply replaces the first solution in place."); break;
                case 18: useTool("move"); break;
                case 19: removeSelected(); break;
            }
        }).show();
    }
    private void confirmSend() {
        String pageId = current().id;
        String message = "Send this page's " + (includeImage.isChecked() ? "handwriting and " : "") + "typed context to your configured companion?";
        if (provider.equals("dot")) message += "\n\nThis queues a request. AIM must be explicitly asked to read it and write a real reply; it is not woken automatically.";
        new AlertDialog.Builder(this).setTitle("Send to " + providerLabel(provider)).setMessage(message).setNegativeButton("Cancel", null).setPositiveButton("Send page", (dialog, which) -> {
            if (current().id.equals(pageId)) ask(current().mode.equals("tutor") ? "feedback" : "ask"); else announce("The active page changed. No page was sent.");
        }).show();
    }
    private void showNotebookMenu() {
        if (!loaded) return;
        new AlertDialog.Builder(this).setTitle("Pages and notes").setItems(new String[]{"Choose page", "New page", "Rename page", "Typed question / note", "Answer font"}, (dialog, which) -> {
            if (which == 0) {
                String[] names = new String[notebook.pages.size()]; for (int i = 0; i < names.length; i++) names[i] = (i + 1) + ". " + notebook.pages.get(i).title;
                new AlertDialog.Builder(this).setTitle("Your pages").setSingleChoiceItems(names, notebook.pages.indexOf(current()), (choice, index) -> { captureInk(); notebook.activeId = notebook.pages.get(index).id; dirty(); loadPage(); choice.dismiss(); }).setNegativeButton("Close", null).show();
            } else if (which == 1) addPage();
            else if (which == 4) showAnswerFont();
            else editHiddenField(which == 2 ? title : question, which == 2 ? "Page title" : "Typed question / note", which == 2 ? 100 : 4000);
        }).show();
    }
    private void showAnswerFont() {
        if (!ExplanationFont.available(this)) {
            new AlertDialog.Builder(this).setTitle("Answer font").setMessage("Your handwriting font is unavailable in this build. Readable default is active.").setPositiveButton("OK", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Explanation font").setSingleChoiceItems(
                new String[]{"Andrea handwriting", "Readable default"}, ExplanationFont.enabled(this) ? 0 : 1,
                (dialog, which) -> {
                    ExplanationFont.setEnabled(this, which == 0); dialog.dismiss();
                    if (loaded && !replyPending) { captureInk(); renderReplies(); }
                    else if (replyPending) announce("Font choice saved. It will apply when the current reply finishes.");
                }).setNegativeButton("Close", null).show();
    }
    private void editHiddenField(EditText target, String label, int limit) {
        String pageId = current().id; boolean rename = target == title;
        EditText editor = field(label, limit, target == title ? 1 : 4); editor.setText(target.getText());
        LinearLayout panel = new LinearLayout(this); panel.setPadding(dp(20), dp(6), dp(20), dp(6)); panel.addView(editor, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle(label).setView(panel).setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> {
            NotebookStore.Page page = notebook.find(pageId); if (page == null) return;
            if (rename) page.title = editor.getText().toString(); else page.prompt = editor.getText().toString();
            if (current().id.equals(pageId)) { rendering = true; target.setText(editor.getText()); rendering = false; if (rename) notebookName.setText(page.title.isEmpty() ? "Plia" : page.title); }
            if (!rename && current().id.equals(pageId)) paperContextChanged();
            dirty();
        }).show();
    }
    private void setEnabled(boolean enabled) { for (ToolButton color : inkColors) color.setEnabled(enabled); pageMenu.setEnabled(enabled); title.setEnabled(enabled); goal.setEnabled(enabled); problem.setEnabled(enabled); question.setEnabled(enabled); ink.setEnabled(enabled); paper.setEnabled(enabled); newPage.setEnabled(enabled); tutor.setEnabled(enabled); think.setEnabled(enabled); export.setEnabled(enabled && !exportBusy); backup.setEnabled(enabled && !exportBusy); importBackup.setEnabled(enabled && !exportBusy); for (int i = 0; i < sideTools.getChildCount(); i++) sideTools.getChildAt(i).setEnabled(enabled); if (loaded) renderMode(); refreshButtons(); refreshEditButtons(); }
    private NotebookStore.Page current() { return notebook.active(); }
    private void watch(EditText view, String key) { view.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s, int start, int count, int after) {} public void onTextChanged(CharSequence s, int start, int before, int count) { if (!loaded || rendering) return; NotebookStore.Page p = current(); String value = s.toString(); switch (key) { case "title": p.title = value; break; case "goal": p.goal = value; break; case "problem": p.problem = value; p.problemSource = "Your problem"; problemSource.setText(p.problemSource); break; default: p.prompt = value; } if (!key.equals("title")) paperContextChanged(); dirty(); } public void afterTextChanged(Editable value) {} }); }
    private void captureInk() { if (ink != null && loaded) { ink.finish(); NotebookStore.Page page = notebook.find(inkPageId); if (page != null) page.strokes = ink.snapshot(); } }
    private void dirty() { revision++; saving.setText(readOnly ? "Not saved · original file preserved" : "Saving…"); main.removeCallbacks(delayedSave); main.postDelayed(delayedSave, 400); }
    private void flushSave() {
        main.removeCallbacks(delayedSave); if (!loaded || readOnly || revision == savedRevision) return;
        NotebookStore.Notebook snapshot = notebook.copy(); long version = revision;
        NotebookStore.IO.execute(() -> { try { storage.write(snapshot); main.post(() -> { if (destroyed) return; savedRevision = Math.max(savedRevision, version); if (revision == version) saving.setText("Saved on this device"); }); } catch (Exception | OutOfMemoryError error) { main.post(() -> { if (!destroyed) { saving.setText("Not saved · export a backup"); announce("Could not save: " + error.getMessage() + ". Your current work remains on screen."); } }); } });
    }
    private void loadPage() {
        cancelNetwork(); resetPaperContext();
        rendering = true; NotebookStore.Page p = current(); resetEditHistory();
        applyingEditor = true;
        try { ink.load(pageEditor.state().ink); } finally { applyingEditor = false; }
        inkPageId = p.id;
        title.setText(p.title); goal.setText(p.goal); problem.setText(p.problem); question.setText(p.prompt); problemSource.setText(p.problemSource.isEmpty() ? "The problem" : "The problem · " + p.problemSource);
        rendering = false; renderMode(); renderReplies(); refreshButtons();
        paperSession.activate(p.id, foreground && p.mode.equals("think"));
        position.setText("Page " + (notebook.pages.indexOf(p) + 1) + " of " + notebook.pages.size());
        notebookName.setText(p.title.isEmpty() ? "Plia" : p.title); pageCounter.setText((notebook.pages.indexOf(p) + 1) + "/" + notebook.pages.size());
        useTool("pen");
    }
    private void movePage(int delta) { if (!loaded) return; int index = notebook.pages.indexOf(current()) + delta; if (index < 0 || index >= notebook.pages.size()) return; captureInk(); notebook.activeId = notebook.pages.get(index).id; dirty(); loadPage(); }
    private void addPage() { if (!loaded) return; if (notebook.pages.size() >= NotebookStore.MAX_PAGES) { announce("The notebook has reached 100 pages. Export a backup before creating another notebook."); return; } captureInk(); NotebookStore.Page p = new NotebookStore.Page(); p.mode = current().mode; p.title = "Page " + (notebook.pages.size() + 1); notebook.pages.add(p); notebook.activeId = p.id; dirty(); loadPage(); }
    private void changeMode(String mode) { if (!loaded || current().mode.equals(mode)) return; captureInk(); cancelNetwork(); resetPaperContext(); current().mode = mode; paperSession.activate(current().id, foreground && mode.equals("think")); useTool("pen"); dirty(); renderMode(); renderReplies(); pageScroll.scrollTo(0, 0); }
    private void renderMode() {
        boolean isTutor = current().mode.equals("tutor");
        tutorChrome.setVisibility(isTutor ? View.VISIBLE : View.GONE); tutorFooter.setVisibility(isTutor ? View.VISIBLE : View.GONE);
        content.setPadding(isTutor ? dp(16) : 0, 0, isTutor ? dp(16) : 0, isTutor ? dp(24) : 0);
        tutorFields.setVisibility(isTutor ? View.VISIBLE : View.GONE); ink.setPlainPaper(!isTutor);
        tutor.setEnabled(true); think.setEnabled(true); tutor.setSelected(isTutor); think.setSelected(!isTutor);
        tutor.setTypeface(null, isTutor ? Typeface.BOLD : Typeface.NORMAL); think.setTypeface(null, isTutor ? Typeface.NORMAL : Typeface.BOLD);
        tutor.setBackground(rounded(isTutor ? 0xffdbe1d5 : Color.TRANSPARENT, 19)); think.setBackground(rounded(isTutor ? Color.TRANSPARENT : 0xffdbe1d5, 19));
        tutor.setTextColor(0xff293326); think.setTextColor(0xff293326);
        tutor.setContentDescription(isTutor ? "Tutor, selected" : "Switch to Tutor"); think.setContentDescription(isTutor ? "Switch to Think" : "Think, selected");
        questionLabel.setText("Typed note or answer (optional)"); selectTool.setEnabled(!isTutor && loaded); placeReplies();
    }
    private static String providerLabel(String provider) { return "dot".equals(provider) ? "AIM" : "openai".equals(provider) ? "OpenAI" : "chatgpt".equals(provider) ? "ChatGPT" : "Demo"; }
    private void refreshButtons() { if (send == null) return; send.setEnabled(loaded && !busy); generate.setEnabled(loaded && !busy); stop.setEnabled(busy || aiCancellation != null); send.setText(busy ? "Waiting…" : "Send to " + providerLabel(provider)); generate.setText("Ask " + providerLabel(provider) + " for a problem"); if (loaded) { int index = notebook.pages.indexOf(current()); previous.setEnabled(index > 0); next.setEnabled(index + 1 < notebook.pages.size()); topPrevious.setEnabled(index > 0); topNext.setEnabled(index + 1 < notebook.pages.size()); } else { previous.setEnabled(false); next.setEnabled(false); topPrevious.setEnabled(false); topNext.setEnabled(false); } }
    private void renderReplies() {
        if (loaded) {
            replyPending = false;
            paper.setRepliesVisible(repliesVisible);
            paper.setReplies(current().replies, current().mode.equals("think"));
        }
    }

    private void restoreReplyHistory(boolean redoReply) {
        if (!loaded) return;
        resetPaperContext(); cancelNetwork();
        boolean changed = redoReply ? ReplyRefresh.redo(current()) : ReplyRefresh.undo(current());
        if (!changed) { announce(redoReply ? "No reply refresh to redo." : "No reply refresh to undo."); return; }
        resetEditHistory();
        renderReplies(); dirty(); flushSave();
        showAiStatus(redoReply ? "Reply refresh redone. Ink unchanged." : "Previous reply group restored. Ink unchanged.");
    }

    private void confirmClearReplies() {
        final String pageId = current().id;
        new AlertDialog.Builder(this).setTitle("Clear replies?")
                .setMessage("Your handwriting stays. Restore the replies with Page tools > Undo reply refresh.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear replies", (dialog, which) -> {
                    if (!loaded || !current().id.equals(pageId)) return;
                    resetPaperContext(); cancelNetwork();
                    if (!ReplyRefresh.clear(current())) { announce("No replies cleared. History may be full; export a backup."); return; }
                    resetEditHistory();
                    renderReplies(); dirty(); flushSave(); showAiStatus("Replies cleared. Ink unchanged. Undo reply refresh restores them.");
                }).show();
    }

    private void showReplyPending(ReplyPlacement placement, PaperAnchor anchor) {
        replyPending = true;
        paper.setReplies(Collections.singletonList(new NotebookStore.Reply(current().mode, "",
                "Preparing your new reply…", "chatgpt", anchor, placement)), current().mode.equals("think"));
    }

    private void restorePendingReply() { if (replyPending && loaded) renderReplies(); }

    /** View preference only: never changes ink, stored replies, selection or request admission. */
    private void toggleReplyVisibility() {
        boolean visible = !repliesVisible;
        if (!getSharedPreferences("paper_view", MODE_PRIVATE).edit().putBoolean("replies_visible", visible).commit()) {
            announce("Could not save reply visibility. Nothing changed."); return;
        }
        repliesVisible = visible;
        if (paper != null) paper.setRepliesVisible(visible);
        updateReplyVisibilityLabel();
    }

    private void updateReplyVisibilityLabel() {
        if (repliesVisibility == null) return;
        String label = repliesVisible ? "Hide replies" : "Show replies";
        repliesVisibility.setText(label); repliesVisibility.setContentDescription(label);
    }
    private void placeReplies() {
        if (loaded && paper != null) paper.inkChanged();
    }
    private void announce(String message) {
        if (notice != null) notice.setText(message);
        if (loaded && current().mode.equals("think") && message != null && !message.isEmpty()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** Ordinary writing invalidates a pending answer but never schedules a new request. */
    private void paperStarted() {
        if (!loaded || rendering || !foreground || !current().mode.equals("think")) return;
        clearPaperWork(); paperSession.penDown(); cancelNetwork(); paperPenDown = true;
        paperStatus = "Use Select to send a chosen area. " + automaticStatus();
    }

    private void paperEnded(boolean completed) {
        boolean wasDown = paperPenDown; paperPenDown = false;
        if (!wasDown || !loaded || rendering || !current().mode.equals("think")) return;
        // Pen and eraser strokes never create a snapshot or start a request, even closed loops.
        paperSession.cancel();
    }

    private void selectionStartedOnPaper() {
        if (!loaded || rendering || !foreground || !selectionTool || !current().mode.equals("think")) return;
        clearPaperWork(); paperSession.penDown(); cancelNetwork(); paperPenDown = true;
    }

    private void selectionCompletedOnPaper(List<InkView.Stroke> gesture) {
        paperPenDown = false;
        if (!loaded || rendering || !foreground || !selectionTool || !current().mode.equals("think")) return;
        if (gesture == null || gesture.isEmpty()) { paperSession.cancel(); return; }
        prepareSelection(ink.snapshot(), new ArrayList<>(gesture));
    }

    private void prepareSelection(ArrayList<InkView.Stroke> frozen, List<InkView.Stroke> selection) {
        if (!selectionTool || selection == null || selection.isEmpty()) return;
        clearPaperWork();
        long endedAt = SystemClock.elapsedRealtime(); paperSession.penUp(endedAt);
        final String pageId = current().id;
        final long stamp = paperSession.stamp();
        final NotebookStore.Page page = current();
        final String context = contextPart(page.mode) + contextPart(page.prompt) + contextPart(page.goal) + contextPart(page.problem);
        draftDelay = () -> {
            draftDelay = null;
            if (!paperMatches(pageId, stamp)) return;
            draftTask = draftWorker.submit(() -> {
                try {
                    PaperDraft draft = PaperDraft.createSelection(frozen, selection, context);
                    if (Thread.currentThread().isInterrupted()) return;
                    main.post(() -> {
                        if (!paperMatches(pageId, stamp)) return;
                        draftTask = null;
                        if (draft == null || draft.selectedStrokeCount == 0) {
                            paperStatus = "No visible handwriting in that area. Circle the written problem and lift. Nothing sent.";
                            if (selection != null) showAiStatus(paperStatus); return;
                        }
                        paperSession.focused(endedAt);
                        long claimStamp = paperSession.stamp();
                        ink.clearSelection();
                        finishPaperDraft(pageId, claimStamp, draft);
                    });
                } catch (Exception | OutOfMemoryError error) {
                    main.post(() -> {
                        if (!paperMatches(pageId, stamp)) return;
                        draftTask = null; paperSession.cancel();
                        paperStatus = "Could not prepare this selection. Your ink is preserved. Nothing was sent.";
                        announce(paperStatus);
                    });
                }
            });
        };
        main.postDelayed(draftDelay, PaperSession.FOCUS_DELAY_MS);
    }

    /** Cancel timers/work without altering saved ink, pages, or credentials. */
    private void clearPaperWork() {
        cancelAiRequest();
        if (draftDelay != null) main.removeCallbacks(draftDelay);
        if (readyDelay != null) main.removeCallbacks(readyDelay);
        draftDelay = null; readyDelay = null;
        if (draftTask != null) draftTask.cancel(true);
        draftTask = null; localDraft = null; localTicket = null;
        retryAccountId = null; retryModel = null;
    }

    private void resetPaperContext() {
        clearPaperWork(); paperSession.cancel();
        paperPenDown = false;
        if (ink != null) ink.clearSelection();
        if (paper != null) paper.clearEditSelection();
        paperStatus = automaticStatus();
    }

    private void paperContextChanged() {
        resetPaperContext();
        if (current().mode.equals("think")) cancelNetwork();
    }

    private boolean paperMatches(String pageId, long stamp) {
        return !destroyed && foreground && loaded && !paperPenDown && current().id.equals(pageId)
                && current().mode.equals("think") && paperSession.stamp() == stamp;
    }

    private static String contextPart(String value) { return value.length() + ":" + value; }

    private void finishPaperDraft(String pageId, long stamp, PaperDraft draft) {
        if (!selectionTool || !paperMatches(pageId, stamp) || !paperSession.ready(SystemClock.elapsedRealtime())) return;
        PaperSession.Ticket ticket = paperSession.claimSelectionRefresh(draft.fingerprint);
        if (ticket == null) { announce("This selection is unchanged. Nothing was sent."); return; }
        if (!paperMatches(pageId, stamp) || !paperSession.accepts(ticket)) return;
        localDraft = draft; localTicket = ticket;
        if (!automaticReady() || selectedChatGptAccount() == null) {
            paperStatus = "Selection ready · Replies off";
            showAiStatus(paperStatus + " Tap for connection controls."); return;
        }
        submitPaperDraft(pageId, stamp, draft, ticket, false);
    }

    /** Only a new Select ticket or its explicitly confirmed retry can enter this request path. */
    private void submitPaperDraft(String pageId, long stamp, PaperDraft draft, PaperSession.Ticket ticket,
                                 boolean explicitRetry) {
        SiwcStore.Account account = selectedChatGptAccount();
        if (!paperMatches(pageId, stamp) || !paperSession.accepts(ticket) || !chatGptReady()
                || (!explicitRetry && !automaticReady()) || aiCancellation != null || account == null) return;
        if (explicitRetry && ticket.retryCount != 1) return;
        if (!explicitRetry && (!selectionTool || !PaperSession.FOCUS.equals(ticket.kind))) return;
        final boolean requestConsent = explicitRetry || repliesEnabled;
        final long startedAt = SystemClock.elapsedRealtime();
        final NotebookStore.Page requested = current().copy();
        if (!ReplyRefresh.canReplace(current())) {
            resetPaperContext(); paperStatus = "Reply history is full. Export a backup and use another page. Nothing sent.";
            showAiStatus(paperStatus); return;
        }
        final ArrayList<NotebookStore.Reply> expectedReplies = new ArrayList<>(current().replies);
        final ReplyPlacement replyPlacement = paper.firstReplyPlacement();
        // All context is frozen on the UI thread. No other notebook pages, chats or files are read.
        final String studyContext = "Goal: " + requested.goal + "\nProblem: " + requested.problem + "\nNote: " + requested.prompt;
        final String capturedInput = studyContext.substring(0, Math.min(4000, studyContext.length()));
        final ArrayList<String> recentReplies = new ArrayList<>();
        for (int i = requested.replies.size() - 1; i >= 0 && recentReplies.size() < 2; i--) {
            NotebookStore.Reply reply = requested.replies.get(i);
            if (!reply.source.equals("demo") && reply.mode.equals(requested.mode)) recentReplies.add(0, reply.text.substring(0, Math.min(2000, reply.text.length())));
        }
        final List<String> history = Collections.unmodifiableList(recentReplies);
        final String accountId = account.id;
        final SiwcInference.ModelInfo model = handwritingModel;
        final ChatGptClient.Cancellation cancellation = new ChatGptClient.Cancellation();
        final ChatGptClient.AnswerTrace trace = new ChatGptClient.AnswerTrace();
        trace.observe(diagnostic -> main.post(() -> {
            if (!destroyed && aiCancellation == cancellation) recordAiDiagnostic(diagnostic);
        }));
        aiCancellation = cancellation; aiTrace = trace; aiStartedAt = startedAt;
        showReplyPending(replyPlacement, draft.anchor);
        paperStatus = "ChatGPT is reading this handwriting snapshot. New writing cancels the pending reply.";
        showAiStatus("Reading your handwriting. Keep this page open; new writing stops the pending reply.");
        refreshButtons();
        aiTask = aiWorker.submit(() -> {
            if (destroyed || cancellation.cancelled()) return;
            try {
                String answer = chatGpt.answer(accountId, model, draft.pngCopy(), requested.mode, studyContext, history, requestConsent, cancellation, trace);
                main.post(() -> {
                    if (destroyed || aiCancellation != cancellation) return;
                    aiTask = null; aiCancellation = null; aiTrace = null; refreshButtons();
                    recordAiDiagnostic(trace.applying());
                    SiwcStore.Account active = selectedChatGptAccount();
                    NotebookStore.Page target = notebook.find(pageId);
                    if ((!explicitRetry && !repliesEnabled) || active == null || !active.id.equals(accountId)
                            || handwritingModel != model || !paperMatches(pageId, stamp)
                            || target == null || !ReplyRefresh.sameReplies(target.replies, expectedReplies)
                            || !ReplyRefresh.canReplace(target)
                            || !paperSession.complete(ticket)) {
                        restorePendingReply();
                        recordAiDiagnostic(trace.failure(new java.util.concurrent.CancellationException()));
                        showAiStatus("Reply discarded because this page or session changed. Your ink is saved.");
                        recordAiLatency("discarded", startedAt); return;
                    }
                    if (!ReplyRefresh.replace(target, new NotebookStore.Reply(requested.mode, capturedInput, answer, "chatgpt", draft.anchor), replyPlacement)) {
                        restorePendingReply(); resetPaperContext(); paperStatus = "The reply could not be replaced. Your previous replies are preserved.";
                        recordAiDiagnostic(trace.failure(new java.util.concurrent.CancellationException())); showAiStatus(paperStatus);
                        recordAiLatency("discarded", startedAt); return;
                    }
                    localDraft = null; localTicket = null;
                    retryAccountId = null; retryModel = null;
                    resetEditHistory();
                    renderReplies(); dirty(); flushSave();
                    recordAiDiagnostic(trace.completed()); showAiStatus("Reply refreshed in place. Undo reply refresh restores the previous group. " + automaticStatus());
                    paperStatus = "ChatGPT replied in " + String.format(java.util.Locale.ROOT, "%.1f", (SystemClock.elapsedRealtime() - startedAt) / 1000.0)
                            + " s after the snapshot was ready. " + automaticStatus();
                    recordAiLatency("completed", startedAt);
                });
            } catch (Exception | OutOfMemoryError error) {
                final String failure = error instanceof SiwcInference.ProtocolException
                        ? error.getMessage() : "The reply stopped before completion.";
                final AiDiagnostic diagnostic = trace.failure(error);
                main.post(() -> {
                    if (destroyed || aiCancellation != cancellation) return;
                    aiTask = null; aiCancellation = null; aiTrace = null;
                    restorePendingReply();
                    boolean failedCurrent = paperSession.fail(ticket);
                    boolean recoverable = SelectionRetryPolicy.allows(error, diagnostic);
                    boolean retry = failedCurrent && paperSession.canRetry(ticket)
                            && recoverable;
                    if (retry) {
                        localDraft = draft; localTicket = ticket; retryAccountId = accountId; retryModel = model;
                    } else resetPaperContext();
                    if (!recoverable) blockReplySettings();
                    chatGptStatus = failure;
                    recordAiDiagnostic(diagnostic);
                    showAiStatus(failure + (retry ? " Tap to retry once." : " Tap for details.") + " " + automaticStatus());
                    paperStatus = failure + (retry ? " One explicit retry is available for this unchanged selection." : " Check connection details before trying again.")
                            + " " + automaticStatus() + " No automatic retry.";
                    recordAiLatency("failed", startedAt); refreshButtons();
                });
            }
        });
    }

    private static void recordAiLatency(String status, long startedAt) {
        android.util.Log.i("PliaAI", "status=" + status + " elapsed_ms=" + Math.max(0, SystemClock.elapsedRealtime() - startedAt));
    }

    private void showAiStatus(String message) {
        if (aiStatusView == null) return;
        aiStatusView.setText(message == null ? "" : message);
        aiStatusView.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void recordAiDiagnostic(AiDiagnostic diagnostic) {
        lastAiDiagnostic = diagnostic;
        getSharedPreferences("ai_diagnostics", MODE_PRIVATE).edit()
                .putString("stage", diagnostic.stage.name()).putString("code", diagnostic.code.name())
                .putInt("http_status", diagnostic.httpStatus).putLong("elapsed_ms", diagnostic.elapsedMs)
                .putString("content_type", diagnostic.contentType.name()).putString("body_shape", diagnostic.bodyShape.name()).apply();
        android.util.Log.i("PliaAI", diagnostic.logLine());
    }

    private String aiDiagnosticText() {
        return lastAiDiagnostic == null ? "No detailed reply diagnostic recorded yet."
                : "Last recorded reply event\n" + lastAiDiagnostic.userMessage();
    }

    private void cancelAiRequest() {
        ChatGptClient.Cancellation cancellation = aiCancellation;
        ChatGptClient.AnswerTrace trace = aiTrace;
        aiCancellation = null; aiTrace = null;
        restorePendingReply();
        if (cancellation != null) {
            cancellation.cancel(); recordAiLatency("cancelled", aiStartedAt);
            if (trace != null) recordAiDiagnostic(trace.failure(new java.util.concurrent.CancellationException()));
            showAiStatus("Reply stopped. Your ink is saved. " + automaticStatus());
        }
        // Do not interrupt a rotating-token refresh; the client checks cancellation before sending the image.
        if (aiTask != null) aiTask.cancel(false);
        boolean hadTask = aiTask != null;
        aiTask = null; if (hadTask || cancellation != null) refreshButtons();
    }

    private SiwcStore.Account selectedChatGptAccount() { return chatGptAccounts == null ? null : chatGptAccounts.selected(); }

    private boolean automaticReady() {
        return foreground && repliesEnabled && chatGptReady();
    }

    private boolean chatGptReady() {
        SiwcStore.Account account = selectedChatGptAccount();
        return accountReady && !accountBusy && account != null
                && account.hasScope("chatgpt.tokens.use.direct") && account.hasScope("resource.invoke")
                && handwritingModel != null && handwritingModel.imageInput
                && account.selectedModel.equals(handwritingModel.slug);
    }

    private String automaticStatus() {
        if (repliesEnabled && chatGptReady()) return "Select replies on. Account usage or credits may apply.";
        if (selectedChatGptAccount() == null) return "Not connected. Replies off.";
        if (replySettings.blocked && replySettings.enabled && handwritingModel != null
                && replySettings.matches(selectedChatGptAccount().id, handwritingModel.slug))
            return "Sending paused after a service error. See connection details.";
        return "Replies off.";
    }


    private boolean retryAvailable() {
        SiwcStore.Account account = selectedChatGptAccount();
        return chatGptReady() && aiCancellation == null && localDraft != null
                && localTicket != null && paperSession.canRetry(localTicket)
                && paperMatches(localTicket.pageId, paperSession.stamp())
                && account != null && account.id.equals(retryAccountId) && handwritingModel == retryModel;
    }

    private void confirmRetrySelection() {
        if (!retryAvailable()) return;
        final PaperDraft draft = localDraft;
        final PaperSession.Ticket failed = localTicket;
        new AlertDialog.Builder(this).setTitle("Retry this selection once?")
                .setMessage("Send the same selected handwriting and page context to " + retryModel.displayName
                        + " again. This is a new request; account usage or existing credits may apply even if the earlier reply failed.\n\nNo automatic retry. " + automaticStatus())
                .setNegativeButton("Cancel", null).setPositiveButton("Retry once", (dialog, which) -> {
                    if (!retryAvailable() || localDraft != draft || localTicket != failed) return;
                    PaperSession.Ticket retry = paperSession.retry(failed);
                    if (retry == null) return;
                    localTicket = retry; retryAccountId = null; retryModel = null;
                    submitPaperDraft(retry.pageId, paperSession.stamp(), draft, retry, true);
                }).show();
    }

    private void pauseAutomatic() {
        repliesEnabled = false;
        resetPaperContext();
        showAiStatus("Replies off · Tap to enable");
    }

    private void readReplySettings() {
        SharedPreferences settings = getSharedPreferences("reply_settings", MODE_PRIVATE);
        try {
            replySettings = ReplySettings.restore(settings.getBoolean("initialized", false), settings.getBoolean("enabled", false),
                    settings.getString("account_id", ""), settings.getString("model_slug", ""), settings.getBoolean("blocked", false));
        } catch (ClassCastException invalid) {
            replySettings = ReplySettings.restore(true, false, "", "", true);
        }
    }

    private boolean saveReplySettings(ReplySettings next) {
        boolean saved = getSharedPreferences("reply_settings", MODE_PRIVATE).edit()
                .putBoolean("initialized", next.initialized).putBoolean("enabled", next.enabled)
                .putString("account_id", next.accountId).putString("model_slug", next.modelSlug)
                .putBoolean("blocked", next.blocked).commit();
        replySettings = saved ? next : next.block();
        if (!saved) { repliesEnabled = false; showAiStatus("Could not save the reply setting. Sending is paused."); }
        return saved;
    }

    /** Restoring a setting never creates a ticket, restores a gesture, or starts a request. */
    private void restoreReplyAdmission() {
        if (!accountReady || accountBusy) { repliesEnabled = false; return; }
        SiwcStore.Account account = selectedChatGptAccount();
        String accountId = account == null ? "" : account.id;
        String modelSlug = handwritingModel == null ? "" : handwritingModel.slug;
        if (!replySettings.initialized) {
            // This upgrade explicitly enables the already-connected pair at Andrea's request.
            if (!saveReplySettings(replySettings.migrate(chatGptReady() ? accountId : "", chatGptReady() ? modelSlug : ""))) return;
        }
        repliesEnabled = chatGptReady() && replySettings.canSend(accountId, modelSlug);
        paperStatus = automaticStatus();
        if (aiCancellation == null) showAiStatus(automaticStatus() + " Tap for controls.");
    }

    private void disableReplies() {
        boolean saved = saveReplySettings(replySettings.disable());
        pauseAutomatic();
        if (!saved) showAiStatus("Replies off now, but the setting could not be saved.");
    }

    private void stopReply() {
        resetPaperContext();
        restoreReplyAdmission();
    }

    private void blockReplySettings() {
        saveReplySettings(replySettings.block());
        repliesEnabled = false;
    }

    private static String safeChatGptError(Throwable error) {
        // Only the protocol mapper has fixed, reviewed UI messages. Never show transport/token/parser details.
        if (error instanceof SiwcInference.ProtocolException) return error.getMessage();
        return "ChatGPT could not complete the operation. Your notebook is unchanged. Reconnect or check the connection and try again.";
    }

    private void loadChatGptAccounts() {
        if (accountBusy || destroyed) return;
        accountBusy = true; chatGptStatus = "Reading the saved ChatGPT connection.";
        accountTask = aiWorker.submit(() -> {
            if (destroyed) return;
            try {
                if (chatGpt == null) chatGpt = new ChatGptClient(getApplicationContext());
                SiwcStore.Snapshot snapshot = chatGpt.store.read();
                main.post(() -> {
                    if (destroyed) return;
                    accountBusy = false; accountTask = null; accountReady = true; chatGptAccounts = snapshot; restoreSelectedModel();
                    restoreReplyAdmission();
                    chatGptStatus = snapshot.selected() == null ? "ChatGPT not connected." : "Saved account selected.";
                    paperStatus = automaticStatus();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (destroyed) return;
                    accountBusy = false; accountTask = null; accountReady = false;
                    pauseAutomatic(); chatGptStatus = "Saved sign-in data could not be opened. Nothing was replaced.";
                });
            }
        });
    }

    /** Only closed enum values and bounded numeric metadata enter preferences or Logcat. */
    private void recordAuthDiagnostic(AuthDiagnostic diagnostic) {
        lastAuthDiagnostic = diagnostic;
        getSharedPreferences("auth_diagnostics", MODE_PRIVATE).edit()
                .putString("stage", diagnostic.stage.name()).putString("code", diagnostic.code.name())
                .putInt("http_status", diagnostic.httpStatus).putLong("elapsed_ms", diagnostic.elapsedMs).apply();
        android.util.Log.i("PliaAuth", diagnostic.logLine());
        if (authDiagnosticView != null) authDiagnosticView.setText(authDiagnosticText());
    }

    private String authDiagnosticText() {
        return lastAuthDiagnostic == null ? "No sign-in diagnostic recorded yet."
                : lastAuthDiagnostic.userMessage();
    }

    private void restoreSelectedModel() {
        SiwcStore.Account account = selectedChatGptAccount();
        handwritingModel = account == null ? null : SiwcInference.restoreSelectedModel(account.selectedModel);
    }

    private void showChatGptConnection() {
        if (destroyed || !foreground) return;
        SiwcStore.Account account = selectedChatGptAccount();
        String status = automaticStatus();
        if (account != null) status += "\nModel: " + (handwritingModel == null ? "choose model" : handwritingModel.displayName);
        if (accountBusy) status += "\nConnecting…";
        else if (!accountReady) status += "\nSaved connection unavailable. Open details to retry.";
        if (aiCancellation != null) status += "\nReading this selection.";
        else if (retryAvailable()) status += "\nLast reply failed. Retry the same selection once.";
        else if (lastAiDiagnostic != null && lastAiDiagnostic.code != AiDiagnostic.Code.COMPLETED)
            status += "\nLast reply did not complete. See details.";
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Think connection").setMessage(status)
                .setNegativeButton("Close", null).setNeutralButton("Details / settings", (choice, which) -> showConnectionDetails());
        if (!accountBusy && accountReady) {
            if (account == null) dialog.setPositiveButton("Connect", (choice, which) -> startChatGptSignIn(null));
            else if (!chatGptReady()) dialog.setPositiveButton("Choose model", (choice, which) -> loadChatGptModels());
            else if (aiCancellation != null) dialog.setPositiveButton("Stop reply", (choice, which) -> stopReply());
            else if (repliesEnabled) dialog.setPositiveButton("Pause replies", (choice, which) -> { disableReplies(); cancelNetwork(); });
            else if (retryAvailable()) dialog.setPositiveButton("Retry selection", (choice, which) -> confirmRetrySelection());
            else dialog.setPositiveButton("Enable replies", (choice, which) -> confirmAutomaticReplies());
        }
        dialog.show();
    }

    private void showConnectionDetails() {
        if (destroyed || !foreground) return;
        SiwcStore.Account account = selectedChatGptAccount();
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(20), dp(6), dp(20), dp(12));
        panel.addView(text(chatGptStatus + "\n\n" + automaticStatus(), 14));
        panel.addView(text(aiDiagnosticText(), 13));
        TextView diagnosticView = text(authDiagnosticText(), 14);
        diagnosticView.setTextIsSelectable(true); panel.addView(diagnosticView);
        authDiagnosticView = diagnosticView;
        if (lastAuthDiagnostic != null && (lastAuthDiagnostic.code == AuthDiagnostic.Code.BROWSER_DISABLED
                || lastAuthDiagnostic.code == AuthDiagnostic.Code.BROWSER_UNAVAILABLE
                || lastAuthDiagnostic.code == AuthDiagnostic.Code.BROWSER_BLOCKED)) {
            panel.addView(button("Open Firefox app settings", this::openFirefoxAppSettings));
        }
        if (account != null) panel.addView(text(account.label() + "\nModel: " + (handwritingModel == null ? "choose model" : handwritingModel.displayName), 13));
        panel.addView(text("Eligible Plus and Pro plans can connect. This sends your handwriting, this page's study context and up to two earlier Plia replies to OpenAI. It does not access your personal dot, other chats or memories. Sign-in is stored encrypted on this device.", 12));
        ScrollView scroll = new ScrollView(this); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("ChatGPT connection").setView(scroll).setNegativeButton("Close", null).create();
        dialog.setOnDismissListener(closed -> { if (authDiagnosticView == diagnosticView) authDiagnosticView = null; });
        if (!accountBusy && accountReady) {
            panel.addView(button("Continue with ChatGPT", () -> { dialog.dismiss(); startChatGptSignIn(account); }));
            if (!chatGptAccounts.accounts.isEmpty()) panel.addView(button("Choose saved account", () -> { dialog.dismiss(); chooseChatGptAccount(); }));
            if (account != null) {
                panel.addView(button("Add another account", () -> { dialog.dismiss(); confirmNewChatGptAccount(); }));
                panel.addView(button("Check and choose handwriting model", () -> { dialog.dismiss(); loadChatGptModels(); }));
                panel.addView(text("Only a new Select gesture sends handwriting. Showing or hiding replies never sends anything.", 13));
                panel.addView(button("Open ChatGPT usage settings", () -> { dialog.dismiss(); openUsageSettings(); }));
                panel.addView(button("Enable Select replies", () -> { dialog.dismiss(); confirmAutomaticReplies(); }));
                panel.addView(button("Pause Select replies", () -> { dialog.dismiss(); disableReplies(); }));
                panel.addView(button("Forget this account on this device", () -> { dialog.dismiss(); confirmForgetChatGptAccount(account); }));
            }
        } else if (!accountBusy) panel.addView(button("Retry saved connection", () -> { dialog.dismiss(); loadChatGptAccounts(); }));
        final ChatGptClient.SignIn pending = signInControl;
        if (pending != null) panel.addView(button("Cancel sign-in", () -> { dialog.dismiss(); pending.cancel(); }));
        dialog.show();
    }

    private void confirmNewChatGptAccount() {
        new AlertDialog.Builder(this).setTitle("Add ChatGPT account").setMessage("Use the official browser sign-in to add a separate account registration. Automatic replies remain paused.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue with ChatGPT", (dialog, which) -> startChatGptSignIn(null)).show();
    }

    private void openFirefoxAppSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + FIREFOX_PACKAGE))); }
        catch (ActivityNotFoundException | SecurityException unavailable) {
            announce("Open the BOOX app settings and enable or unfreeze Firefox, then return to Plia.");
        }
    }

    /** Inspect only the selected browser. A disabled app cannot receive an Android VIEW intent. */
    private void requireFirefoxEnabled() throws PackageManager.NameNotFoundException, AuthDiagnostic.Failure {
        PackageManager packages = getPackageManager();
        ApplicationInfo firefox = packages.getApplicationInfo(FIREFOX_PACKAGE, PackageManager.MATCH_DISABLED_COMPONENTS);
        int state = packages.getApplicationEnabledSetting(FIREFOX_PACKAGE);
        if (!firefox.enabled || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                || state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED)
            throw new AuthDiagnostic.Failure(AuthDiagnostic.Code.BROWSER_DISABLED);
    }

    private void startChatGptSignIn(SiwcStore.Account selected) {
        if (destroyed || accountBusy || !accountReady) return;
        pauseAutomatic(); handwritingModel = null; accountBusy = true;
        pendingSignInStatus = null; pendingSignInModels = false;
        chatGptStatus = "Finish sign-in in Firefox, then return to Plia. Automatic replies remain paused.";
        final String accountId = selected == null ? null : selected.id;
        final ChatGptClient.SignIn control = new ChatGptClient.SignIn(); signInControl = control;
        control.observe(diagnostic -> main.post(() -> {
            if (!destroyed && signInControl == control) recordAuthDiagnostic(diagnostic);
        }));
        accountTask = aiWorker.submit(() -> {
            if (destroyed) return;
            try {
                control.stage(AuthDiagnostic.Stage.READ_STORE);
                SiwcStore.Account fresh = null;
                if (accountId != null) {
                    for (SiwcStore.Account saved : chatGpt.store.read().accounts) if (saved.id.equals(accountId)) fresh = saved;
                    if (fresh == null) throw new IOException("Saved account unavailable.");
                }
                SiwcStore.Snapshot snapshot = chatGpt.signIn(fresh, control, url -> main.post(() -> {
                    if (destroyed || signInControl != control) return;
                    try {
                        Uri uri = Uri.parse(url);
                        if (!"https".equals(uri.getScheme()) || !"auth.openai.com".equals(uri.getHost())
                                || !"/api/accounts/authorize".equals(uri.getPath())) throw new IllegalArgumentException();
                        requireFirefoxEnabled();
                        startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(FIREFOX_PACKAGE));
                    } catch (AuthDiagnostic.Failure failure) { control.browserFailure(failure.code); }
                    catch (PackageManager.NameNotFoundException | ActivityNotFoundException unavailable) { control.browserUnavailable(); }
                    catch (SecurityException blocked) { control.browserFailure(AuthDiagnostic.Code.BROWSER_BLOCKED); }
                    catch (IllegalArgumentException invalid) { control.browserFailure(AuthDiagnostic.Code.INVALID_INPUT); }
                    catch (RuntimeException unavailable) { control.browserUnavailable(); }
                }));
                main.post(() -> {
                    if (destroyed || signInControl != control) return;
                    recordAuthDiagnostic(control.completed());
                    chatGptAccounts = snapshot; accountReady = true; accountBusy = false; accountTask = null; signInControl = null;
                    chatGptStatus = "ChatGPT sign-in verified. Automatic replies are paused.";
                    pendingSignInStatus = chatGptStatus + "\n\n" + authDiagnosticText(); pendingSignInModels = true; showPendingSignInStatus();
                });
            } catch (Exception error) {
                final AuthDiagnostic diagnostic = control.failure(error);
                main.post(() -> {
                    if (destroyed || signInControl != control) return;
                    recordAuthDiagnostic(diagnostic);
                    accountBusy = false; accountTask = null; signInControl = null; pauseAutomatic();
                    chatGptStatus = "Sign-in did not complete. Automatic replies remain paused.";
                    pendingSignInStatus = chatGptStatus + "\n\n" + authDiagnosticText(); pendingSignInModels = false; showPendingSignInStatus();
                });
            }
        });
    }

    private void showPendingSignInStatus() {
        if (destroyed || !foreground || pendingSignInStatus == null) return;
        String message = pendingSignInStatus; boolean chooseModel = pendingSignInModels;
        pendingSignInStatus = null; pendingSignInModels = false;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("ChatGPT connection").setMessage(message).setNegativeButton("Close", null);
        if (chooseModel) dialog.setPositiveButton("Choose model", (choice, which) -> loadChatGptModels());
        dialog.show();
    }

    private interface AccountChange { SiwcStore.Snapshot apply(SiwcStore store, SiwcStore.Snapshot before) throws Exception; }

    private void changeChatGptAccount(String status, AccountChange change, Runnable after) {
        if (accountBusy || !accountReady || destroyed) return;
        pauseAutomatic(); accountBusy = true; chatGptStatus = status;
        accountTask = aiWorker.submit(() -> {
            if (destroyed) return;
            try {
                SiwcStore.Snapshot snapshot = change.apply(chatGpt.store, chatGpt.store.read());
                main.post(() -> {
                    if (destroyed) return;
                    accountBusy = false; accountTask = null; chatGptAccounts = snapshot;
                    chatGptStatus = "ChatGPT connection updated. Automatic replies remain paused.";
                    if (after != null) after.run();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (destroyed) return;
                    accountBusy = false; accountTask = null; pauseAutomatic();
                    chatGptStatus = "The account change could not be saved. Automatic replies remain paused.";
                });
            }
        });
    }

    private void chooseChatGptAccount() {
        if (accountBusy || chatGptAccounts == null || !foreground) return;
        final List<SiwcStore.Account> accounts = chatGptAccounts.accounts;
        String[] labels = new String[accounts.size()]; int selected = -1;
        for (int i = 0; i < labels.length; i++) { labels[i] = accounts.get(i).label(); if (accounts.get(i).id.equals(chatGptAccounts.selectedId)) selected = i; }
        new AlertDialog.Builder(this).setTitle("Saved ChatGPT accounts").setSingleChoiceItems(labels, selected, (dialog, which) -> {
            dialog.dismiss(); String id = accounts.get(which).id; handwritingModel = null;
            changeChatGptAccount("Selecting ChatGPT account.", (store, before) -> store.select(id, before.generation), () -> { if (foreground) loadChatGptModels(); });
        }).setNegativeButton("Cancel", null).show();
    }

    private void confirmForgetChatGptAccount(SiwcStore.Account account) {
        new AlertDialog.Builder(this).setTitle("Forget " + account.label() + "?")
                .setMessage("This removes its saved credentials from Plia on this device. To revoke its permission at OpenAI, also disconnect Plia in ChatGPT Settings > Security and login > Sign in with ChatGPT. Your notebook stays here.")
                .setNegativeButton("Cancel", null).setPositiveButton("Forget locally", (dialog, which) -> {
                    handwritingModel = null;
                    changeChatGptAccount("Removing this saved ChatGPT account.", (store, before) -> store.forget(account.id, before.generation), null);
                }).show();
    }

    private void loadChatGptModels() {
        SiwcStore.Account selected = selectedChatGptAccount();
        if (destroyed || accountBusy || !accountReady || selected == null || !foreground) return;
        pauseAutomatic(); accountBusy = true; chatGptStatus = "Checking this account's current model catalog.";
        final String accountId = selected.id;
        final ChatGptClient.Cancellation cancellation = new ChatGptClient.Cancellation(); accountCancellation = cancellation;
        accountTask = aiWorker.submit(() -> {
            if (destroyed) return;
            try {
                List<SiwcInference.ModelInfo> models = chatGpt.models(accountId, cancellation);
                SiwcStore.Snapshot snapshot = chatGpt.store.read();
                final ArrayList<SiwcInference.ModelInfo> images = new ArrayList<>();
                for (SiwcInference.ModelInfo model : models) if (model.imageInput) images.add(model);
                main.post(() -> {
                    if (destroyed || accountCancellation != cancellation) return;
                    accountBusy = false; accountTask = null; accountCancellation = null; chatGptAccounts = snapshot;
                    if (cancellation.cancelled() || !foreground) { chatGptStatus = "Model check cancelled. Automatic replies remain paused."; return; }
                    if (snapshot.selected() == null || !snapshot.selectedId.equals(accountId)) return;
                    if (images.isEmpty()) { handwritingModel = null; chatGptStatus = "No model with verified handwriting-image support was returned for this account."; showChatGptConnection(); return; }
                    chatGptStatus = "Current model catalog checked. Availability can still change.";
                    String[] labels = new String[images.size()]; int checked = -1;
                    for (int i = 0; i < images.size(); i++) { labels[i] = images.get(i).displayName; if (images.get(i).slug.equals(snapshot.selected().selectedModel)) checked = i; }
                    new AlertDialog.Builder(this).setTitle("Handwriting model").setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        dialog.dismiss(); final SiwcInference.ModelInfo model = images.get(which);
                        changeChatGptAccount("Saving this account's handwriting model.", (store, before) -> {
                            if (!before.selectedId.equals(accountId)) throw new IOException("Account changed.");
                            return store.selectModel(accountId, model.slug, before.generation);
                        }, () -> {
                            SiwcStore.Account active = selectedChatGptAccount();
                            if (active != null && active.id.equals(accountId) && active.selectedModel.equals(model.slug)) handwritingModel = model;
                            restoreReplyAdmission(); chatGptStatus = "Model saved. " + automaticStatus();
                            if (foreground) showChatGptConnection();
                        });
                    }).setNegativeButton("Cancel", null).show();
                });
            } catch (Exception error) {
                final String failure = safeChatGptError(error);
                main.post(() -> {
                    if (destroyed || accountCancellation != cancellation) return;
                    accountBusy = false; accountTask = null; accountCancellation = null; pauseAutomatic();
                    chatGptStatus = cancellation.cancelled() ? "Model check cancelled. Automatic replies remain paused." : failure;
                    if (foreground) showChatGptConnection();
                });
            }
        });
    }

    private void openUsageSettings() {
        if (accountBusy || selectedChatGptAccount() == null) return;
        pauseAutomatic();
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage")).addCategory(Intent.CATEGORY_BROWSABLE).setPackage("org.mozilla.firefox")); }
        catch (RuntimeException unavailable) { chatGptStatus = "No browser could open ChatGPT usage settings. Automatic replies remain paused."; }
    }

    private void confirmAutomaticReplies() {
        SiwcStore.Account account = selectedChatGptAccount();
        if (!chatGptReady() || !foreground) { showChatGptConnection(); return; }
        final String accountId = account.id; final SiwcInference.ModelInfo model = handwritingModel;
        new AlertDialog.Builder(this).setTitle("Enable Select replies?")
                .setMessage("A completed Select gesture sends the enclosed handwriting, page context and up to two earlier Plia replies to OpenAI. Ordinary writing and showing or hiding replies do not send anything.\n\nYour account usage or existing credits may apply; plan-only use is not guaranteed.\n\nThis setting stays on for this account and model after switching apps or restarting Plia. Pause anytime.")
                .setNegativeButton("Cancel", null).setPositiveButton("Keep replies on", (dialog, which) -> {
                    if (!foreground || !chatGptReady() || !selectedChatGptAccount().id.equals(accountId) || handwritingModel != model) return;
                    if (!saveReplySettings(replySettings.enable(accountId, model.slug))) return;
                    repliesEnabled = true; resetPaperContext(); paperSession.startConnectedSession();
                    showAiStatus(automaticStatus() + " Tap to pause.");
                    chatGptStatus = "Select replies enabled for this account and model. Account usage or credits may apply.";
                }).show();
    }
    private void settings() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(22), dp(6), dp(22), dp(8));
        panel.addView(text("Companion server address", 14)); EditText address = field("http://PC-address:port", 300, 1); address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); address.setText(serverUrl); panel.addView(address);
        panel.addView(text("Server access token", 14)); EditText access = field("Token from your companion configuration", 256, 1); access.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); access.setSaveEnabled(false); access.setText(token); panel.addView(access);
        panel.addView(text("The token stays in memory for this app session. An OpenAI API key belongs only on the companion server. A ready bridge does not mean AIM is connected.", 12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Connection to AIM").setView(panel).setNegativeButton("Cancel", null).setPositiveButton("Save and check", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> { try { if (address.getText().toString().trim().isEmpty()) { address.setError("Enter this PC's current companion server address and port."); return; } String value = BridgeClient.normalize(address.getText().toString()); cancelNetwork(); serverUrl = value; token = access.getText().toString().trim(); getPreferences(MODE_PRIVATE).edit().putString("companionUrl", serverUrl).apply(); dialog.dismiss(); checkConnection(); } catch (Exception error) { address.setError(error.getMessage()); } })); dialog.show();
    }
    private String statusProvider(JSONObject status) throws IOException { String value = status.optString("provider"); if (!(value.equals("dot") || value.equals("openai") || value.equals("demo"))) throw new IOException("Unknown companion provider; no page was sent."); return value; }
    private void showStatus(JSONObject status) {
        provider = status.optString("provider", "dot");
        connection.setText(provider.equals("dot") ? "AIM bridge ready · AIM must be connected separately" : providerLabel(provider) + (status.optBoolean("configured") ? " provider selected" : " is not configured")); refreshButtons();
    }
    private void checkConnection() {
        if (busy || !hasCompanionAddress()) return; busy = true; refreshButtons(); BridgeClient request = new BridgeClient(serverUrl, token); client = request;
        networkTask = network.submit(() -> { try { JSONObject status = request.status(); statusProvider(status); main.post(() -> { if (!destroyed && client == request) { showStatus(status); announce(status.optBoolean("configured") ? "Companion reachable. Sending queues your page. AIM must be explicitly asked to read it and reply." : "Configure the selected provider on your companion server."); } }); } catch (Exception error) { main.post(() -> { if (!destroyed && client == request) { connection.setText("Companion unavailable"); announce(error.getMessage()); } }); } finally { completeNetwork(request); } });
    }
    private void ask(String action) {
        if (!loaded || busy || !hasCompanionAddress()) return; captureInk(); NotebookStore.Page page = current().copy();
        if (action.equals("problem") && page.goal.trim().isEmpty()) { goal.requestFocus(); announce("Add a learning goal first."); return; }
        if (action.equals("ask") && page.prompt.trim().isEmpty() && (!includeImage.isChecked() || !ink.hasInk())) { question.requestFocus(); announce("Type a question or write it on the page and include the ink image."); return; }
        if (action.equals("feedback") && page.problem.trim().isEmpty() && page.prompt.trim().isEmpty() && !ink.hasInk()) { announce("Add a problem or some work first."); return; }
        if (action.equals("problem") && notebook.pages.size() >= NotebookStore.MAX_PAGES || !action.equals("problem") && page.replies.size() >= 100) { announce("This notebook or page has reached its limit. Export a backup and use another page."); return; }
        String expectedProvider = provider; boolean image = !action.equals("problem") && includeImage.isChecked() && ink.hasInk();
        if (page.mode.equals("think")) { clearPaperWork(); paperSession.cancel(); }
        final long requestStamp = paperSession.stamp();
        busy = true; refreshButtons(); announce("Sending this page to " + providerLabel(expectedProvider) + "…");
        BridgeClient request = new BridgeClient(serverUrl, token); client = request;
        networkTask = network.submit(() -> {
            try {
                JSONObject status = request.status(); String actualProvider = statusProvider(status); main.post(() -> { if (!destroyed && client == request) showStatus(status); });
                if (!expectedProvider.equals(actualProvider)) throw new IOException("The companion is configured for " + providerLabel(actualProvider) + ". No page was sent. Press its labelled Send button if you want to use that provider.");
                if (!status.optBoolean("configured")) throw new IOException(providerLabel(actualProvider) + " is not configured on the companion.");
                JSONObject payload = new JSONObject().put("mode", page.mode).put("action", action).put("goal", page.goal).put("prompt", page.prompt).put("problem", page.problem);
                JSONArray history = new JSONArray();
                for (int i = Math.max(0, page.replies.size() - 2); i < page.replies.size(); i++) { NotebookStore.Reply reply = page.replies.get(i); if (!reply.input.isEmpty()) history.put(new JSONObject().put("role", "user").put("text", reply.input.substring(0, Math.min(2000, reply.input.length())))); history.put(new JSONObject().put("role", "assistant").put("text", reply.text.substring(0, Math.min(2000, reply.text.length())))); }
                payload.put("history", history);
                if (image) { Bitmap bitmap = InkView.bitmap(page.strokes, true); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)) throw new IOException("Could not capture handwriting."); } finally { bitmap.recycle(); } if (bytes.size() > 3 * 1024 * 1024) throw new IOException("The ink image is larger than 3 MB. Turn off Include my handwritten page or use a smaller page."); payload.put("imageDataUrl", "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)); }
                JSONObject result = request.assist(payload, () -> main.post(() -> { if (!destroyed && client == request) announce("Page queued. Waiting for AIM to read it and reply. You can keep writing or stop waiting."); }));
                String text = result.optString("text", "").trim(), source = result.optString("source", "");
                if (text.isEmpty() || text.length() > (action.equals("problem") ? 4000 : 16000) || !source.equals(actualProvider)) throw new IOException("The companion returned an invalid or oversized reply. Your page is unchanged.");
                main.post(() -> { if (!destroyed && client == request && (!page.mode.equals("think") || paperMatches(page.id, requestStamp))) receive(page, action, text, source); });
            } catch (Exception error) { main.post(() -> { if (!destroyed && client == request) announce(error.getMessage() == null ? "The request did not complete. Your writing is safe." : error.getMessage()); }); }
            finally { completeNetwork(request); }
        });
    }
    private void receive(NotebookStore.Page requested, String action, String text, String source) {
        NotebookStore.Page target = notebook.find(requested.id); if (target == null) return;
        if (action.equals("problem")) { if (notebook.pages.size() >= NotebookStore.MAX_PAGES) { announce("The new problem could not be added because the notebook reached 100 pages. Your existing work is safe."); return; } NotebookStore.Page p = new NotebookStore.Page(); p.title = requested.goal.substring(0, Math.min(60, requested.goal.length())); p.goal = requested.goal; p.problem = text; p.problemSource = providerLabel(source); notebook.pages.add(p); if (current().id.equals(requested.id)) { captureInk(); notebook.activeId = p.id; loadPage(); } else refreshButtons(); announce("A fresh practice problem is on page " + notebook.pages.size() + "."); }
        else { target.replies.add(new NotebookStore.Reply(requested.mode, requested.mode.equals("tutor") ? requested.problem : requested.prompt, text, source)); if (current().id.equals(target.id)) { resetEditHistory(); renderReplies(); } announce(providerLabel(source) + " replied on \"" + target.title + "\"."); }
        dirty(); flushSave();
    }
    private void completeNetwork(BridgeClient request) { main.post(() -> { if (!destroyed && client == request) { client = null; networkTask = null; busy = false; refreshButtons(); } }); }
    private void cancelNetwork() { if (client == null && networkTask == null && !busy) return; BridgeClient current = client; client = null; if (current != null) current.cancel(); if (networkTask != null) networkTask.cancel(true); networkTask = null; busy = false; refreshButtons(); }
    private boolean hasCompanionAddress() {
        if (serverUrl != null && !serverUrl.trim().isEmpty()) return true;
        announce("Set this PC's current companion server address and port in Connection settings first."); return false;
    }

    private static final class TextBlock {
        final StaticLayout layout;
        TextBlock(String value, float size, Typeface typeface) { TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG); paint.setColor(0xff20251e); paint.setTextSize(size); paint.setTypeface(typeface); layout = StaticLayout.Builder.obtain(value, 0, value.length(), paint, 900).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(6, 1.1f).build(); }
    }
    private byte[] pageImage(NotebookStore.Page page) throws IOException {
        ArrayList<TextBlock> top = new ArrayList<>(), bottom = new ArrayList<>();
        top.add(new TextBlock("PLIA / " + page.mode.toUpperCase(), 18, Typeface.DEFAULT)); top.add(new TextBlock(page.title.isEmpty() ? "Untitled page" : page.title, 36, Typeface.SERIF));
        if (page.mode.equals("tutor")) { if (!page.goal.isEmpty()) top.add(new TextBlock("Goal: " + page.goal, 22, Typeface.DEFAULT)); if (!page.problem.isEmpty()) top.add(new TextBlock(page.problem, 28, Typeface.SERIF)); }
        if (!page.prompt.isEmpty()) top.add(new TextBlock(page.prompt, 24, Typeface.DEFAULT));
        for (NotebookStore.Reply reply : page.replies) { bottom.add(new TextBlock(providerLabel(reply.source) + (reply.source.equals("demo") ? " / SAMPLE ONLY" : " / RESPONSE"), 18, Typeface.DEFAULT)); if (!reply.input.isEmpty()) bottom.add(new TextBlock(reply.input, 22, Typeface.DEFAULT)); bottom.add(new TextBlock(reply.text, 32, handwriting)); }
        long height = 80 + InkView.HEIGHT + 30; for (TextBlock block : top) height += block.layout.getHeight() + 22; for (TextBlock block : bottom) height += block.layout.getHeight() + 22;
        if (height > 8000) throw new IOException("This page is too long for one PNG. Export a notebook backup instead; no content has been truncated.");
        Bitmap image = Bitmap.createBitmap(InkView.WIDTH, (int)height, Bitmap.Config.ARGB_8888); Bitmap inkBitmap = null;
        try { Canvas canvas = new Canvas(image); canvas.drawColor(Color.WHITE); float y = 40;
            for (TextBlock block : top) { canvas.save(); canvas.translate(50, y); block.layout.draw(canvas); canvas.restore(); y += block.layout.getHeight() + 22; }
            if (page.mode.equals("tutor")) { Paint lines = new Paint(); lines.setColor(0xffe4e6df); for (int line = 36; line < InkView.HEIGHT; line += 36) canvas.drawLine(20, y + line, 980, y + line, lines); }
            inkBitmap = InkView.bitmap(page.strokes, false); canvas.drawBitmap(inkBitmap, 0, y, null); y += InkView.HEIGHT + 30;
            for (TextBlock block : bottom) { canvas.save(); canvas.translate(50, y); block.layout.draw(canvas); canvas.restore(); y += block.layout.getHeight() + 22; }
            ByteArrayOutputStream output = new ByteArrayOutputStream(); if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG export failed."); return output.toByteArray();
        } finally { image.recycle(); if (inkBitmap != null) inkBitmap.recycle(); }
    }
    private void exportImage() { if (!loaded || exportBusy) return; captureInk(); NotebookStore.Page page = current().copy(); prepareExport(() -> pageImage(page), "image/png", safeName(page.title) + ".png"); }
    private void exportBackup() {
        if (!loaded || exportBusy) return; captureInk(); exportNotebook = notebook.copy(); exportRecovery = readOnly; exportBusy = true; setEnabled(loaded);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, readOnly ? "plia-recovery.zip" : "plia-notebook.zip");
        try { startActivityForResult(intent, CREATE_DOCUMENT); } catch (RuntimeException error) { finishExport(); announce("No document picker is available on this device."); }
    }
    private interface Bytes { byte[] make() throws Exception; }
    private void prepareExport(Bytes operation, String mime, String name) {
        exportBusy = true; setEnabled(loaded); announce("Preparing your export…");
        NotebookStore.IO.execute(() -> { try { byte[] data = operation.make(); main.post(() -> { if (destroyed) return; exportBytes = data; Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name); try { startActivityForResult(intent, CREATE_DOCUMENT); } catch (RuntimeException error) { finishExport(); announce("No document picker is available on this device."); } }); } catch (Exception | OutOfMemoryError error) { main.post(() -> { if (!destroyed) { finishExport(); announce(error.getMessage() == null ? "The device could not prepare this export. Try a smaller page or a notebook backup." : error.getMessage()); } }); } });
    }
    private static String safeName(String title) { String value = title.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-"); return value.isEmpty() ? "plia-page" : value.substring(0, Math.min(70, value.length())); }
    private void finishExport() { exportBytes = null; exportNotebook = null; exportRecovery = false; exportBusy = false; if (!destroyed) setEnabled(loaded); }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_TUTOR) {
            if (resultCode == RESULT_OK) { returnToThinkRequested = true; restoreWorkspaceRoute(); }
            return;
        }
        if (requestCode == CREATE_DOCUMENT) {
            byte[] bytes = exportBytes; NotebookStore.Notebook snapshot = exportNotebook; boolean recovery = exportRecovery;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) { finishExport(); announce("Export cancelled. Your notebook is unchanged."); return; }
            if (bytes == null && snapshot == null) { finishExport(); announce("Please export again after the screen rotated. Your notebook is unchanged."); return; }
            Uri uri = data.getData();
            NotebookStore.IO.execute(() -> { try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) { if (output == null) throw new IOException("Could not open the chosen document."); if (recovery) storage.exportRaw(output); else if (snapshot != null) NotebookStore.writeBackup(snapshot, output); else output.write(bytes); main.post(() -> { if (!destroyed) announce("Export saved."); }); } catch (Exception | OutOfMemoryError error) { main.post(() -> { if (!destroyed) announce("Export failed: " + error.getMessage()); }); } finally { main.post(this::finishExport); } });
        } else if (requestCode == OPEN_BACKUP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            NotebookStore.IO.execute(() -> { try { NotebookStore.Notebook imported; try (InputStream input = getContentResolver().openInputStream(uri)) { if (input == null) throw new IOException("Could not open the backup."); imported = NotebookStore.readBackup(input); } main.post(() -> { if (destroyed) return; if (readOnly) new AlertDialog.Builder(this).setTitle("Restore this backup?").setMessage("This replaces the unreadable saved notebook. Export a recovery copy first if you need to keep its original file.").setNegativeButton("Cancel", null).setPositiveButton("Restore", (dialog, which) -> { cancelNetwork(); notebook = imported; inkPageId = null; readOnly = false; loadPage(); dirty(); flushSave(); announce("Backup restored."); }).show(); else { if (notebook.pages.size() + imported.pages.size() > NotebookStore.MAX_PAGES) { announce("Import would exceed the 100-page limit. Existing pages are unchanged."); return; } captureInk(); for (NotebookStore.Page page : imported.pages) page.id = java.util.UUID.randomUUID().toString(); notebook.pages.addAll(imported.pages); notebook.activeId = imported.pages.get(0).id; loadPage(); dirty(); flushSave(); announce("Backup pages imported. Existing pages were kept."); } }); } catch (Exception | OutOfMemoryError error) { main.post(() -> { if (!destroyed) announce("Could not import: " + error.getMessage()); }); } });
        }
    }
    @Override protected void onResume() { super.onResume(); foreground = true; if (loaded) { if (tutorRenderersReleased) { tutorRenderersReleased = false; renderReplies(); } paperSession.activate(current().id, current().mode.equals("think")); } showPendingSignInStatus(); restoreReplyAdmission(); restoreWorkspaceRoute(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("returnToThink", returnToThinkRequested); state.putBoolean("initialWorkspaceRoute", initialWorkspaceRoute); super.onSaveInstanceState(state); }
    @Override protected void onPause() { foreground = false; resetPaperContext(); paperSession.background(); if (accountCancellation != null) accountCancellation.cancel(); if (loaded && current().mode.equals("think")) cancelNetwork(); super.onPause(); }
    @Override protected void onStop() { if (loaded) { captureInk(); flushSave(); } if (busy) { cancelNetwork(); announce("Waiting stopped while Plia was in the background. Already queued requests may still be completed by AIM."); } super.onStop(); }
    @Override protected void onDestroy() {
        // Record this known cancellation before activity-bound callbacks are discarded.
        if (signInControl != null) {
            signInControl.cancel(); recordAuthDiagnostic(signInControl.failure(null));
        }
        destroyed = true; main.removeCallbacks(delayedSave); clearPaperWork(); paperSession.background(); cancelNetwork();
        if (paper != null) paper.disposeReplyViews();
        authDiagnosticView = null;
        if (accountCancellation != null) accountCancellation.cancel();
        if (accountTask != null) accountTask.cancel(false);
        // Let any token rotation finish atomically; queued tasks check destroyed before touching storage.
        aiWorker.shutdown(); network.shutdownNow(); draftWorker.shutdownNow(); super.onDestroy();
    }
}
