package com.plia.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;

/** Dedicated offline Tutor workspace. It never reads or changes MainActivity's notebook. */
public final class TutorActivity extends Activity {
    // Shared across Activity instances: a reopened screen cannot read ahead of queued writes.
    private static final ExecutorService IO = TutorLibraryStore.IO;
    private static final int EXPORT = 601, IMPORT = 602;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable delayedSave = this::flushSave;
    private TutorStore store;
    private TutorLibraryStore library;
    private TutorLibraryStore.Goal goal;
    private String goalId;
    private TutorStore.Data data;
    private long revision, savedRevision, routeGeneration;
    private boolean destroyed, loading, corrupt, replacing, leaveRequested;
    private String route = "goal", topicId, familyId, boundAttemptId;
    private LinearLayout root, toolbar, tabs;
    private FrameLayout body;
    private TextView saveStatus, practiceEvidence, goalHeading;
    private Button retrySave, hint, solution;
    private ToolButton pen, eraser, undo, redo;
    private final ArrayList<ToolButton> colorTools = new ArrayList<>();
    private boolean paperVisible;
    private int currentColor;
    private float currentWidth;
    private String restoredRoute = "goal", restoredTopic, restoredFamily, restoredAttempt;
    private boolean restoredPaper;
    private InkView ink;
    private PaperSurface paper;
    private LocalMathView promptMath;
    private final ArrayList<SolutionView> solutionViews = new ArrayList<>();
    private AlertDialog mathDialog;
    private PendingAction pendingAction;
    private TutorStore.Data exportSnapshot;
    private boolean exportRaw;
    private String lastSaveFailure;
    private static final class PendingAction {
        final long revision, route;
        final String attempt;
        final Runnable action;
        PendingAction(long revision, long route, String attempt, Runnable action) {
            this.revision = revision; this.route = route; this.attempt = attempt; this.action = action;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        root = column(); root.setBackgroundColor(Color.WHITE); root.setPadding(dp(24), dp(8), dp(24), dp(8));
        goalId = getIntent().getStringExtra(TutorLibraryActivity.EXTRA_GOAL_ID);
        if (goalId == null) goalId = TutorLibraryStore.LEGACY_GOAL_ID;
        if (state != null) {
            restoredRoute = state.getString("route", "goal"); restoredTopic = state.getString("topic");
            restoredFamily = state.getString("family"); restoredAttempt = state.getString("attempt"); restoredPaper = state.getBoolean("paper");
        }
        toolbar = row(); toolbar.addView(button("Goals", this::leave), new LinearLayout.LayoutParams(dp(80), dp(48)));
        goalHeading = label("Tutor", 20); goalHeading.setSingleLine(true); goalHeading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        toolbar.addView(goalHeading, new LinearLayout.LayoutParams(0, dp(48), 1));
        toolbar.addView(new ToolButton(this, "Goal options", ToolButton.Icon.MORE, this::showOptions), new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbar);
        tabs = row(); root.addView(tabs);
        LinearLayout statusRow = row(); saveStatus = label("Opening Tutor.", 14); saveStatus.setSingleLine(true);
        saveStatus.setOnClickListener(v -> { if (lastSaveFailure != null) message("Save not confirmed", lastSaveFailure); });
        statusRow.addView(saveStatus, new LinearLayout.LayoutParams(0, -2, 1));
        retrySave = button("Retry", () -> { if (corrupt) load(); else flushSave(); }); retrySave.setVisibility(View.GONE);
        statusRow.addView(retrySave, new LinearLayout.LayoutParams(dp(80), dp(48))); root.addView(statusRow);
        body = new FrameLayout(this); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        load();
    }
    private boolean ready() { return data != null && !corrupt && !loading && !replacing; }
    private void load() {
        if (loading || replacing) return;
        loading = true; retrySave.setVisibility(View.GONE); saveStatus.setVisibility(View.VISIBLE); saveStatus.setText("Opening goal...");
        library = new TutorLibraryStore(getApplicationContext());
        IO.execute(() -> {
            try {
                TutorLibraryStore.Goal selected = library.read().find(goalId);
                if (selected == null) throw new java.io.IOException("Goal is missing from the library");
                TutorStore reading = new TutorStore(getApplicationContext(), selected.id); store = reading;
                TutorStore.Data decoded = reading.read(); TutorUiLogic.validateContent(decoded);
                TutorStore.Data loaded = decoded.copy();
                main.post(() -> { if (destroyed || store != reading) return; data = loaded; corrupt = false; loading = false;
                    goal = selected; goalHeading.setText(goal.title); revision = savedRevision = 0; saved(); restoreScreen(); });
            } catch (Exception failure) {
                main.post(() -> { if (destroyed) return; loading = false; corrupt = true; data = null;
                    saveStatus.setVisibility(View.VISIBLE); saveStatus.setText("Goal could not be opened"); retrySave.setVisibility(View.VISIBLE); showRecovery(); });
            }
        });
    }
    private boolean hasCurriculum() { return goal != null && TutorUiLogic.hasCurriculum(goal.curriculumId); }
    private void restoreScreen() {
        String target = TutorUiLogic.restoreRoute(restoredRoute, hasCurriculum(), data.findAttempt(restoredAttempt) != null);
        switch (target) {
            case "materials": showMaterials(); break;
            case "lessons": showLessons(); break;
            case "practice-list": showPracticeList(); break;
            case "lesson": showLesson(restoredTopic, restoredFamily); break;
            case "practice": showPractice(restoredAttempt, restoredPaper); break;
            default: showGoal();
        }
        restoredRoute = "goal"; restoredAttempt = null; restoredPaper = false;
    }
    private void showTabs(String selected) {
        tabs.removeAllViews();
        String[] labels = {"Overview", "Lessons", "Practice", "Materials"};
        Runnable[] actions = {this::showGoal, this::showLessons, this::showPracticeList, this::showMaterials};
        for (int i = 0; i < labels.length; i++) {
            Button tab = button(labels[i], actions[i]); tab.setTextSize(15); tab.setSelected(labels[i].equals(selected));
            tab.setTypeface(null, labels[i].equals(selected) ? Typeface.BOLD : Typeface.NORMAL);
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
    }
    private void changed() {
        if (!ready()) return;
        revision++; saveStatus.setVisibility(View.VISIBLE); saveStatus.setText("Saving..."); retrySave.setVisibility(View.GONE);
        main.removeCallbacks(delayedSave); main.postDelayed(delayedSave, 400);
    }
    private void saved() { lastSaveFailure = null; saveStatus.setText("Saved on this device"); saveStatus.setVisibility(View.GONE); retrySave.setVisibility(View.GONE); }
    private void flushSave() {
        main.removeCallbacks(delayedSave);
        if (!ready() || revision == savedRevision && pendingAction == null) return;
        final TutorStore.Data snapshot;
        try { snapshot = data.copy(); } catch (RuntimeException failure) { saveFailed(failure); return; }
        final long version = revision; final TutorStore writing = store;
        IO.execute(() -> {
            try { writing.write(snapshot); main.post(() -> {
                if (destroyed || replacing || store != writing) return;
                savedRevision = Math.max(savedRevision, version); if (revision == savedRevision) saved();
                if (leaveRequested) {
                    if (savedRevision == revision) completeLeave(); else flushSave();
                    return;
                }
                PendingAction pending = pendingAction;
                if (pending != null && version >= pending.revision) {
                    pendingAction = null; updateExposureButtons();
                    if (TutorUiLogic.canReveal(version, pending.revision, routeGeneration, pending.route, boundAttemptId, pending.attempt, isFinishing())) pending.action.run();
                }
            }); } catch (Exception failure) { main.post(() -> { if (!destroyed && !replacing && store == writing) saveFailed(failure); }); }
        });
    }
    private void saveFailed(Throwable failure) {
        leaveRequested = false; body.setVisibility(View.VISIBLE);
        lastSaveFailure = "Saving could not be confirmed. Keep this session open, export Tutor, and retry.";
        for (int depth = 0; failure != null && depth < 8; depth++, failure = failure.getCause()) {
            String detail = failure.getMessage();
            if (detail != null && (detail.contains("16 MiB") || detail.startsWith("Tutor ink ")
                    || detail.startsWith("Tutor attempt limit") || detail.startsWith("Tutor topic limit"))) {
                lastSaveFailure = "Tutor's storage limit was reached. Export Tutor before explicitly resetting it. Retrying without reducing the saved data may fail again.";
                break;
            }
        }
        saveStatus.setVisibility(View.VISIBLE); saveStatus.setText("Save not confirmed - tap for details"); retrySave.setVisibility(View.VISIBLE);
    }
    private void persistBeforeReveal(Runnable reveal) {
        changed(); pendingAction = new PendingAction(revision, routeGeneration, boundAttemptId, reveal);
        updateExposureButtons(); flushSave();
    }
    private void updateExposureButtons() {
        boolean enabled = pendingAction == null && ready();
        if (hint != null) hint.setEnabled(enabled); if (solution != null) solution.setEnabled(enabled);
    }
    private void captureInk() {
        if (ink == null || data == null || corrupt || replacing) return;
        ink.finish(); TutorStore.Attempt attempt = data.findAttempt(boundAttemptId);
        if (attempt != null) attempt.ink = ink.snapshot();
    }
    private void releaseScreen() {
        captureInk();
        if (ink != null) ink.setListener(null);
        if (paper != null) paper.disposeReplyViews();
        if (promptMath != null) promptMath.dispose();
        promptMath = null;
        for (SolutionView view : solutionViews) view.dispose(); solutionViews.clear();
        if (mathDialog != null) { mathDialog.dismiss(); mathDialog = null; }
        ink = null; paper = null; boundAttemptId = null; practiceEvidence = null;
        pen = eraser = undo = redo = null; hint = solution = null; colorTools.clear(); paperVisible = false;
        pendingAction = null; routeGeneration++; body.removeAllViews();
    }
    private void showRecovery() {
        releaseScreen(); LinearLayout page = scrollPage();
        page.addView(title("Your Tutor file is preserved"));
        page.addView(label("The saved data could not be read. Nothing will be reset or overwritten. Retry opening it, or export the original file for recovery.", 18));
        page.addView(button("Retry opening", this::load));
        if (store != null) page.addView(button("Export original goal file", this::exportTutor));
        page.addView(label("Think pages are stored separately and are unchanged.", 18));
    }
    private void showGoal() {
        if (!ready()) return;
        releaseScreen(); route = "goal"; showTabs("Overview"); LinearLayout page = scrollPage();
        page.addView(title(goal.title));
        page.addView(button(data.goalDate.isEmpty() ? "Set a target date" : "Target: " + data.goalDate, this::editDate));
        if (!hasCurriculum()) {
            page.addView(label("A notebook for what you want to develop.", 19));
            if (!data.materialNotes.isEmpty()) page.addView(label(data.materialNotes, 18));
            else page.addView(label("Add your focus and study materials to begin planning this goal.", 18));
            page.addView(button(data.materialNotes.isEmpty() ? "Add goal notes" : "Edit goal notes", this::editMaterialNotes));
            page.addView(label("No lessons or practice are attached yet.", 16));
            return;
        }
        page.addView(label("Calculus preparation, one topic at a time.", 19));
        LinearLayout actions = row();
        actions.addView(button(data.lastTopicId.isEmpty() ? "Start learning" : "Continue lesson", () -> showLesson(validTopic(data.lastTopicId), null)), new LinearLayout.LayoutParams(0, dp(48), 1));
        TutorStore.Attempt active = data.findAttempt(data.activeAttemptId);
        if (active != null) actions.addView(button("Resume practice", () -> showPractice(active.id)), new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(actions, spaced(-1, -2));
        int read = 0, submitted = 0;
        for (TutorStore.TopicProgress progress : data.topics.values()) if (progress.lessonRead) read++;
        for (TutorStore.Attempt attempt : data.attempts) if (attempt.submitted) submitted++;
        page.addView(section("In this notebook"));
        page.addView(label("10 topic lessons · 14 original practice families", 18));
        page.addView(label(read + " lessons marked read · " + submitted + " submitted attempts", 16));
        page.addView(label("Activity is saved locally. These counts do not measure exam readiness.", 15));
        if (!data.lastTopicId.isEmpty()) {
            page.addView(section("Last opened"));
            addTopicRow(page, TutorCatalog.topic(validTopic(data.lastTopicId)), false);
        }
        page.addView(button("Browse topics", this::showLessons));
    }
    private void showLessons() {
        if (!ready()) return;
        releaseScreen(); route = "lessons"; showTabs("Lessons"); LinearLayout page = scrollPage(); page.addView(title("Lessons"));
        if (!hasCurriculum()) { emptyCurriculum(page); return; }
        page.addView(label("A short concept, a worked example, then your turn.", 17));
        for (TutorCatalog.Topic topic : TutorCatalog.topics()) addTopicRow(page, topic, false);
    }
    private void addTopicRow(LinearLayout page, TutorCatalog.Topic topic, boolean practice) {
        TutorStore.TopicSummary summary = TutorStore.summarize(data, topic.id);
        LinearLayout line = column(); line.setPadding(dp(8), dp(12), dp(8), dp(12));
        TextView heading = label(topic.title, 20); heading.setTypeface(null, Typeface.BOLD); line.addView(heading);
        line.addView(label(practice ? topic.familyIds.size() + " practice " + (topic.familyIds.size() == 1 ? "family" : "families")
                : summary.statusLabel, 15));
        line.setContentDescription(topic.title + ". " + (practice ? "Choose practice" : summary.statusLabel));
        line.setFocusable(true); line.setBackground(selectable());
        line.setOnClickListener(v -> { if (!ready()) return; if (practice) choosePracticeFamily(topic); else showLesson(topic.id, null); });
        page.addView(line, new LinearLayout.LayoutParams(-1, -2)); divider(page);
    }
    private void showPracticeList() {
        if (!ready()) return;
        releaseScreen(); route = "practice-list"; showTabs("Practice"); LinearLayout page = scrollPage(); page.addView(title("Practice"));
        if (!hasCurriculum()) { emptyCurriculum(page); return; }
        TutorStore.Attempt active = data.findAttempt(data.activeAttemptId);
        if (active != null) page.addView(button("Resume " + TutorCatalog.titleForFamily(active.templateId), () -> showPractice(active.id)));
        if (!data.attempts.isEmpty()) page.addView(button("Saved attempts (" + data.attempts.size() + ")", () -> chooseAttempt(null)));
        page.addView(label("Original generated questions. Numeric results are checked; symbolic work uses a self-check.", 17));
        for (TutorCatalog.Topic topic : TutorCatalog.topics()) addTopicRow(page, topic, true);
    }
    private void choosePracticeFamily(TutorCatalog.Topic topic) {
        if (topic.familyIds.size() == 1) { newPractice(topic.familyIds.get(0)); return; }
        String[] labels = new String[topic.familyIds.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = TutorCatalog.titleForFamily(topic.familyIds.get(i));
        new AlertDialog.Builder(this).setTitle(topic.title).setItems(labels, (dialog, which) -> newPractice(topic.familyIds.get(which))).setNegativeButton("Close", null).show();
    }
    private void emptyCurriculum(LinearLayout page) {
        page.addView(label("No learning content is attached to this goal yet.", 19));
        page.addView(label("Use Materials to record what you want to learn and the sources you want to use.", 17));
        page.addView(button("Open materials", this::showMaterials));
    }
    private void showOptions() {
        if (!ready()) return;
        new AlertDialog.Builder(this).setTitle("Goal options").setItems(new String[]{"Target date", "Explanation font", "How progress works", "Export this goal's study data", "Import study data into this goal", "Reset this goal's study data"}, (dialog, which) -> {
            switch (which) {
                case 0: editDate(); break; case 1: chooseExplanationFont(); break;
                case 2: message("Local progress", "Activity counts record your work, not exam readiness. Two different independent typed numeric results matched means ready for review, not mastery. Hints and viewed solutions remain recorded. Symbolic answers use an explicit self-check; handwriting and reasoning are not automatically assessed."); break;
                case 3: exportTutor(); break; case 4: importTutor(); break; case 5: confirmReset(); break;
            }
        }).setNegativeButton("Close", null).show();
    }
    private void showMaterials() {
        if (!ready()) return;
        releaseScreen(); route = "materials"; showTabs("Materials"); LinearLayout page = scrollPage(); page.addView(title("Materials"));
        page.addView(label(data.materialNotes.isEmpty() ? "Keep your focus, source names and links together." : data.materialNotes, 18));
        page.addView(button(data.materialNotes.isEmpty() ? "Add notes" : "Edit notes", this::editMaterialNotes));
        if (!hasCurriculum()) return;
        page.addView(section("Course references"));
        page.addView(label(TutorMaterials.SOURCE_STATUS, 16));
        for (TutorMaterials.Source source : TutorMaterials.sources()) {
            page.addView(button(source.id + " · " + source.title, () -> openSourceMap(source)));
        }
        page.addView(section("Practice provenance")); page.addView(label(TutorMaterials.ORIGINAL_PRACTICE, 16));
        page.addView(button("Reference links by topic", this::showReferenceLinks));
        page.addView(label("Exam syllabus and date have not been confirmed.", 15));
    }
    private void openSourceMap(TutorMaterials.Source source) {
        StringBuilder text = new StringBuilder(source.status).append("\n\n");
        for (TutorMaterials.Reference ref : TutorMaterials.referencesForSource(source.id)) {
            text.append(ref.locator()).append(" · ").append(ref.title).append("\n");
            for (String flag : ref.flags) text.append("  ").append(flag).append("\n");
            text.append("\n");
        }
        for (String note : source.notes) text.append(note).append("\n\n");
        message(source.id + " · " + source.title, text.toString().trim());
    }
    private void showReferenceLinks() {
        String[] names = new String[TutorCatalog.topics().size()];
        for (int i = 0; i < names.length; i++) names[i] = TutorCatalog.topics().get(i).title;
        new AlertDialog.Builder(this).setTitle("Paul's Online Math Notes references").setItems(names, (dialog, which) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(TutorCatalog.topics().get(which).externalUrl))); }
            catch (RuntimeException unavailable) { message("Reference link", TutorCatalog.topics().get(which).externalUrl); }
        }).setNegativeButton("Close", null).show();
    }
    private void editMaterialNotes() {
        if (!ready()) return;
        EditText notes = new EditText(this); notes.setTextSize(18); notes.setMinLines(5); notes.setMaxLines(12);
        notes.setGravity(Gravity.TOP); notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TutorStore.MAX_MATERIAL_NOTES_CHARS)}); notes.setText(data.materialNotes);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Goal notes and materials").setView(notes)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!ready()) return;
            data.materialNotes = notes.getText().toString(); changed(); flushSave(); dialog.dismiss();
            if ("goal".equals(route)) showGoal(); else showMaterials();
        })); dialog.show();
    }
    private String validTopic(String id) {
        for (TutorCatalog.Topic topic : TutorCatalog.topics()) if (topic.id.equals(id)) return id;
        return TutorCatalog.topics().get(0).id;
    }
    private void chooseExplanationFont() {
        if (!ready()) return;
        if (!ExplanationFont.available(this)) {
            message("Explanation font", "Your handwriting font is unavailable in this build. Readable default is active."); return;
        }
        new AlertDialog.Builder(this).setTitle("Explanation font").setSingleChoiceItems(
                new String[]{"Andrea handwriting", "Readable default"}, ExplanationFont.enabled(this) ? 0 : 1,
                (dialog, which) -> { ExplanationFont.setEnabled(this, which == 0); dialog.dismiss(); showGoal(); })
                .setNegativeButton("Close", null).show();
    }
    private void chooseAttempt(String topic) {
        ArrayList<TutorStore.Attempt> attempts = new ArrayList<>();
        for (TutorStore.Attempt a : data.attempts) if (topic == null || a.topicId.equals(topic)) attempts.add(a);
        String[] labels = new String[attempts.size()];
        for (int i = 0; i < labels.length; i++) { TutorStore.Attempt a = attempts.get(i);
            labels[i] = (i + 1) + ". " + TutorCatalog.titleForFamily(a.templateId) + " · "
                    + (a.solutionViewed ? "Solution viewed" : a.hintCount > 0 ? "Hints used" : "No hints")
                    + " · " + (a.submitted ? "Submitted" : "In progress"); }
        new AlertDialog.Builder(this).setTitle("Saved practice").setItems(labels, (dialog, index) -> showPractice(attempts.get(index).id)).setNegativeButton("Close", null).show();
    }
    private void editDate() {
        LocalDate date = LocalDate.now();
        try { if (!data.goalDate.isEmpty()) date = LocalDate.parse(data.goalDate); } catch (RuntimeException ignored) {}
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            data.goalDate = LocalDate.of(year, month + 1, day).toString(); changed(); showGoal();
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Clear date", (d, which) -> { data.goalDate = ""; changed(); showGoal(); }); dialog.show();
    }
    private void showLesson(String requestedTopic, String requestedFamily) {
        if (!ready() || !hasCurriculum()) return;
        releaseScreen(); route = "lesson"; showTabs("Lessons"); topicId = validTopic(requestedTopic);
        TutorCatalog.Topic topic = TutorCatalog.topic(topicId);
        familyId = topic.familyIds.contains(requestedFamily) ? requestedFamily : topic.familyIds.get(0);
        data.lastTopicId = topicId;
        TutorStore.TopicProgress progress = data.topics.get(topicId);
        if (progress == null) { progress = new TutorStore.TopicProgress(); data.topics.put(topicId, progress); }
        progress.lastOpenedAt = System.currentTimeMillis(); changed();
        LinearLayout page = scrollPage(); page.addView(title(topic.title));
        page.addView(label(topic.objective, 18));
        int savedCount = 0;
        for (TutorStore.Attempt attempt : data.attempts) if (attempt.topicId.equals(topic.id)) savedCount++;
        if (savedCount > 0) page.addView(button("Saved attempts (" + savedCount + ")", () -> chooseAttempt(topic.id)));
        addSection(page, "Concept", topic.concept); addSection(page, "Method", topic.method);
        page.addView(button("Watch for", () -> message("Watch for", topic.trap)));
        page.addView(section("Worked example"));
        page.addView(label("Original example", 14));
        if (topic.familyIds.size() > 1) page.addView(button(TutorCatalog.titleForFamily(familyId) + " · Change", () -> chooseFamily(topic)));
        SolutionView worked = new SolutionView(this, TutorPracticeEngine.canonical(familyId).toPairedMarkdown(), Typeface.SERIF);
        solutionViews.add(worked); page.addView(worked, spaced(-1, -2));
        page.addView(button("Mark lesson read", () -> { data.topics.get(topicId).lessonRead = true; changed(); message("Lesson recorded", "Reading this lesson does not count as an independent attempt."); }));
        page.addView(button("Try similar", () -> newPractice(familyId)));
    }
    private void chooseFamily(TutorCatalog.Topic topic) {
        String[] names = new String[topic.familyIds.size()]; int selected = 0;
        for (int i = 0; i < names.length; i++) { names[i] = TutorCatalog.titleForFamily(topic.familyIds.get(i)); if (topic.familyIds.get(i).equals(familyId)) selected = i; }
        new AlertDialog.Builder(this).setTitle("Practice family").setSingleChoiceItems(names, selected, (dialog, which) -> {
            dialog.dismiss(); showLesson(topic.id, topic.familyIds.get(which));
        }).setNegativeButton("Close", null).show();
    }
    private void newPractice(String family) {
        if (!ready() || !hasCurriculum()) return; captureInk();
        if (data.attempts.size() >= TutorStore.MAX_ATTEMPTS) { message("Tutor is full", "The 100-attempt limit has been reached. Export Tutor before explicitly resetting it. Existing work is kept."); return; }
        HashSet<String> used = new HashSet<>(); for (TutorStore.Attempt a : data.attempts) used.add(a.itemKey);
        TutorPracticeEngine.PracticeItem item = TutorUiLogic.freshItem(family, used);
        if (item == null) { message("No fresh variant available", "All available variants for this family have been used. Choose another family. Previous work is kept."); return; }
        TutorStore.Attempt attempt = new TutorStore.Attempt();
        attempt.topicId = item.topicId; attempt.templateId = item.templateId; attempt.itemKey = item.itemKey;
        attempt.seed = item.seed; attempt.generatorVersion = item.generatorVersion; attempt.itemSnapshotJson = item.toSnapshotJson();
        attempt.createdAt = attempt.updatedAt = System.currentTimeMillis(); data.attempts.add(attempt);
        data.activeAttemptId = attempt.id; data.lastTopicId = item.topicId; data.nextSeed = Math.max(data.nextSeed, item.seed + 1);
        TutorStore.TopicProgress progress = data.topics.get(item.topicId);
        if (progress == null) { progress = new TutorStore.TopicProgress(); data.topics.put(item.topicId, progress); }
        progress.lastOpenedAt = System.currentTimeMillis(); changed(); showPractice(attempt.id); flushSave();
    }
    private void showPractice(String id) { showPractice(id, false); }
    private void showPractice(String id, boolean revealPaper) {
        if (!ready() || !hasCurriculum()) return;
        TutorStore.Attempt attempt = data.findAttempt(id); if (attempt == null) { showGoal(); return; }
        final TutorPracticeEngine.PracticeItem item;
        try { item = TutorPracticeEngine.fromSnapshotJson(attempt.itemSnapshotJson); }
        catch (RuntimeException unavailable) { message("Exercise unavailable", "The saved exercise version could not be opened. Its ink and progress are preserved; export this goal for recovery."); return; }
        releaseScreen(); route = "practice"; showTabs("Practice"); boundAttemptId = id; topicId = item.topicId; familyId = item.templateId;
        if (!id.equals(data.activeAttemptId) || !item.topicId.equals(data.lastTopicId)) { data.activeAttemptId = id; data.lastTopicId = item.topicId; changed(); }
        LinearLayout page = scrollPage(); page.addView(title(TutorCatalog.titleForFamily(item.templateId)));
        page.addView(label("Original practice · " + (item.answerKind == TutorPracticeEngine.AnswerKind.NUMERIC ? "Numeric final-result check" : "Symbolic self-check"), 14));
        LinearLayout prompt = column(); page.addView(prompt);
        try {
            promptMath = new LocalMathView(this, TutorUiLogic.promptDocumentJson(item), prompt::requestLayout,
                    () -> { if (!destroyed && id.equals(boundAttemptId)) showPlainPrompt(prompt, item); });
            prompt.addView(promptMath, new LinearLayout.LayoutParams(-1, -2));
        } catch (RuntimeException unavailable) { showPlainPrompt(prompt, item); }
        page.addView(section("Your answer"));
        EditText answer = new EditText(this); answer.setTextSize(19);
        answer.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        answer.setGravity(Gravity.TOP); answer.setMinLines(item.answerKind == TutorPracticeEngine.AnswerKind.NUMERIC ? 2 : 4); answer.setMaxLines(8);
        answer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TutorStore.MAX_TYPED_ANSWER_CHARS)});
        answer.setText(attempt.typedAnswer); answer.setHint(TutorUiLogic.answerHint(item.answerKind));
        answer.setEnabled(TutorUiLogic.typedEntryEnabled(item.answerKind));
        page.addView(answer, new LinearLayout.LayoutParams(-1, -2));
        answer.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) { if (!ready() || !id.equals(boundAttemptId)) return;
                attempt.setTypedAnswer(value.toString(), System.currentTimeMillis()); changed(); updatePracticeEvidence(); }
            public void afterTextChanged(Editable value) {}
        });
        LinearLayout actions = row(); hint = button("Hint", () -> showHint(attempt, item));
        Button check = button(item.answerKind == TutorPracticeEngine.AnswerKind.NUMERIC ? "Check result" : "Compare", () -> {
            if (item.answerKind == TutorPracticeEngine.AnswerKind.NUMERIC) checkAttempt(attempt, item); else showSolution(attempt, item);
        });
        solution = button("Solution", () -> showSolution(attempt, item));
        for (Button action : new Button[]{hint, check, solution}) { action.setTextSize(16); actions.addView(action, new LinearLayout.LayoutParams(0, dp(48), 1)); }
        page.addView(actions); practiceEvidence = label("", 14); page.addView(practiceEvidence);
        LinearLayout links = row();
        links.addView(button("Lesson", () -> showLesson(item.topicId, item.templateId)), new LinearLayout.LayoutParams(0, dp(48), 1));
        links.addView(button("Try another", () -> newPractice(item.templateId)), new LinearLayout.LayoutParams(0, dp(48), 1)); page.addView(links);
        LinearLayout paperHost = column();
        Button paperToggle = button("Show paper" + (attempt.ink.isEmpty() ? "" : " (saved writing)"), () -> {});
        paperToggle.setOnClickListener(v -> { if (!ready() || !id.equals(boundAttemptId)) return;
            if (ink == null) createPracticePaper(paperHost, attempt, id);
            if (paperVisible) captureInk(); paperVisible = !paperVisible; paperHost.setVisibility(paperVisible ? View.VISIBLE : View.GONE);
            paperToggle.setText(paperVisible ? "Hide paper" : "Show paper" + (attempt.ink.isEmpty() ? "" : " (saved writing)"));
        });
        page.addView(paperToggle); page.addView(paperHost); paperHost.setVisibility(View.GONE);
        if (revealPaper) { createPracticePaper(paperHost, attempt, id); paperVisible = true; paperHost.setVisibility(View.VISIBLE); paperToggle.setText("Hide paper"); }
        updatePracticeEvidence(); updateExposureButtons();
    }
    private void createPracticePaper(LinearLayout host, TutorStore.Attempt attempt, String id) {
        currentColor = PenSettings.color(this); currentWidth = PenSettings.width(this);
        ink = new InkView(this); ink.setPlainPaper(false); ink.setFingerDrawing(false);
        ink.setPenWidth(currentWidth); ink.setPenColor(currentColor); ink.load(attempt.ink);
        HorizontalScrollView strip = new HorizontalScrollView(this); strip.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = row(); strip.addView(tools); host.addView(strip, new LinearLayout.LayoutParams(-1, dp(48)));
        pen = new ToolButton(this, "Pen", ToolButton.Icon.PEN, () -> { if (ink != null) { ink.setEraser(false); updatePen(false); } });
        eraser = new ToolButton(this, "Eraser", ToolButton.Icon.ERASER, () -> { if (ink != null) { ink.setEraser(true); updatePen(true); } });
        addTool(tools, pen); addTool(tools, eraser);
        int[] colors = {PenSettings.BLACK, PenSettings.BLUE, PenSettings.RED}; String[] names = {"Black ink", "Blue ink", "Red ink"};
        for (int i = 0; i < colors.length; i++) {
            final int color = colors[i]; ToolButton tool = new ToolButton(this, names[i], ToolButton.Icon.COLOR, () -> {
                if (ink == null) return; currentColor = color; ink.setEraser(false); ink.setPenColor(color);
                PenSettings.save(this, currentWidth, currentColor); updatePen(false);
            }); tool.setInkColor(color); colorTools.add(tool); addTool(tools, tool);
        }
        undo = new ToolButton(this, "Undo", ToolButton.Icon.UNDO, () -> { if (ink != null) ink.undo(); });
        redo = new ToolButton(this, "Redo", ToolButton.Icon.REDO, () -> { if (ink != null) ink.redo(); });
        addTool(tools, undo); addTool(tools, redo);
        addTool(tools, new ToolButton(this, "Pen settings", ToolButton.Icon.MORE, this::choosePenSettings));
        paper = new PaperSurface(this, ink, Typeface.SERIF);
        ScrollView paperScroll = new ScrollView(this); paperScroll.setFillViewport(false); paperScroll.setSmoothScrollingEnabled(false);
        paperScroll.addView(paper, new ScrollView.LayoutParams(-1, -2)); host.addView(paperScroll, new LinearLayout.LayoutParams(-1, dp(420)));
        ink.setListener(new InkView.Listener() {
            public void changed() { if (!ready() || !id.equals(boundAttemptId)) return;
                ArrayList<InkView.Stroke> candidate = ink.snapshot();
                if (!fitsInkBudget(attempt, candidate)) { ink.load(attempt.ink);
                    message("Tutor ink limit", "The latest gesture exceeds this goal's storage limit and could not be kept. Earlier work is unchanged. Export this goal before resetting it."); return; }
                attempt.ink = candidate; attempt.updatedAt = System.currentTimeMillis(); changedDataOnly(); }
            public void state(boolean canUndo, boolean canRedo, boolean hasInk) { if (undo != null) undo.setEnabled(canUndo); if (redo != null) redo.setEnabled(canRedo); }
            public void warning(String text) { message("Writing", text); }
        });
        updatePen(false);
    }
    private void addTool(LinearLayout tools, ToolButton tool) { tools.addView(tool, new LinearLayout.LayoutParams(dp(48), dp(48))); }
    private void choosePenSettings() {
        if (ink == null) return; final InkView current = ink;
        float[] widths = {PenSettings.THIN, 3.5f, 6f}; int selected = currentWidth == 6f ? 2 : currentWidth == 3.5f ? 1 : 0;
        new AlertDialog.Builder(this).setTitle("Pen settings").setSingleChoiceItems(new String[]{"Thin", "Medium", "Bold"}, selected, (dialog, which) -> {
            if (ink != current) { dialog.dismiss(); return; }
            currentWidth = widths[which]; ink.setEraser(false); ink.setPenWidth(currentWidth);
            PenSettings.save(this, currentWidth, currentColor); updatePen(false); dialog.dismiss();
        }).setNegativeButton("Close", null).show();
    }
    private void changedDataOnly() { changed(); } // Pen commits update data/status only, never rebuild a view or renderer.
    private void updatePen(boolean erase) {
        if (pen != null) { pen.setSelected(!erase); pen.setPenWidth(currentWidth); }
        if (eraser != null) eraser.setSelected(erase);
        int[] colors = {PenSettings.BLACK, PenSettings.BLUE, PenSettings.RED};
        for (int i = 0; i < colorTools.size(); i++) colorTools.get(i).setSelected(!erase && currentColor == colors[i]);
    }
    private void showPlainPrompt(LinearLayout panel, TutorPracticeEngine.PracticeItem item) {
        if (promptMath != null) { panel.removeView(promptMath); promptMath.dispose(); promptMath = null; }
        panel.addView(label(item.promptWords + "\n" + item.promptLatex, 18));
    }
    private void updatePracticeEvidence() {
        if (practiceEvidence == null || data == null) return;
        TutorStore.Attempt attempt = data.findAttempt(boundAttemptId); if (attempt == null) return;
        String exposure = attempt.solutionViewed ? "Solution viewed" : attempt.hintCount > 0 ? attempt.hintCount + " hints used" : "No hints used";
        practiceEvidence.setText(exposure + " · " + TutorUiLogic.attemptStatus(attempt) + ". Handwriting and method are not assessed.");
    }
    private void showHint(TutorStore.Attempt attempt, TutorPracticeEngine.PracticeItem item) {
        if (!ready() || pendingAction != null) return; captureInk();
        if (attempt.hintCount < item.hints.size()) attempt.recordHint(System.currentTimeMillis());
        int count = Math.min(attempt.hintCount, item.hints.size()); updatePracticeEvidence();
        persistBeforeReveal(() -> { StringBuilder text = new StringBuilder();
            for (int i = 0; i < count; i++) text.append(i + 1).append(". ").append(item.hints.get(i)).append("\n\n");
            message("Hint " + count + " of " + item.hints.size(), text.toString().trim()); });
    }
    private void showSolution(TutorStore.Attempt attempt, TutorPracticeEngine.PracticeItem item) {
        if (!ready() || pendingAction != null) return; captureInk();
        attempt.recordSolutionView(System.currentTimeMillis()); updatePracticeEvidence();
        persistBeforeReveal(() -> openSolution(attempt, item));
    }
    private void openSolution(TutorStore.Attempt attempt, TutorPracticeEngine.PracticeItem item) {
        ScrollView scroll = new ScrollView(this); SolutionView view = new SolutionView(this, item.toPairedMarkdown(), Typeface.SERIF);
        scroll.addView(view); AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Worked solution").setView(scroll).setNegativeButton("Close", null);
        if (item.answerKind == TutorPracticeEngine.AnswerKind.REVIEW_REQUIRED) builder.setPositiveButton("I compared my work", (dialog, which) -> {
            attempt.recordSelfCheck(System.currentTimeMillis()); changed(); flushSave(); updatePracticeEvidence();
            message("Self-check recorded", "This is your self-check, not an automatic correctness assessment.");
        });
        mathDialog = builder.create(); mathDialog.setOnDismissListener(dialog -> { view.dispose(); mathDialog = null; }); mathDialog.show();
    }
    private void checkAttempt(TutorStore.Attempt attempt, TutorPracticeEngine.PracticeItem item) {
        if (!ready() || pendingAction != null) return; captureInk();
        TutorPracticeEngine.CheckResult result = TutorPracticeEngine.checkNumeric(item, attempt.typedAnswer);
        if (result == TutorPracticeEngine.CheckResult.REVIEW_REQUIRED) { message("Self-check required", "This symbolic exercise is not automatically graded. Use Solution to compare your steps and explicitly record a self-check. Your handwriting is not assessed."); return; }
        TutorStore.Outcome outcome = result == TutorPracticeEngine.CheckResult.CORRECT ? TutorStore.Outcome.CORRECT
                : result == TutorPracticeEngine.CheckResult.INCORRECT ? TutorStore.Outcome.INCORRECT : TutorStore.Outcome.INVALID;
        attempt.recordNumericCheck(outcome, System.currentTimeMillis()); updatePracticeEvidence(); final String checked = attempt.typedAnswer;
        persistBeforeReveal(() -> { if (attempt.typedAnswer.equals(checked)) message("Typed result", TutorUiLogic.numericFeedback(result)); });
    }
    private void exportTutor() {
        if (loading || replacing || store == null) return;
        captureInk(); exportRaw = corrupt; exportSnapshot = corrupt ? null : data.copy();
        if (exportSnapshot != null && goal != null) exportSnapshot.goalTitle = goal.title;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, corrupt ? "plia-goal-recovery.json" : "plia-" + goalId + ".json");
        startActivityForResult(intent, EXPORT);
    }
    private void importTutor() {
        if (!ready()) return;
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), IMPORT);
    }
    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) return;
        Uri uri = intent.getData();
        if (request == EXPORT) {
            // SAF may recreate this Activity while its picker is open. Never truncate a destination for a lost snapshot.
            if (exportSnapshot == null && !exportRaw) {
                if (!ready()) { message("Export again", "Tutor was reopened while choosing the destination. Wait for it to load, then choose Export Tutor again."); return; }
                captureInk(); exportSnapshot = data.copy(); if (goal != null) exportSnapshot.goalTitle = goal.title;
            }
            final TutorStore.Data snapshot = exportSnapshot; final boolean raw = exportRaw; final TutorStore reading = store;
            IO.execute(() -> { try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new java.io.IOException("Could not open destination");
                if (raw) reading.exportRaw(out); else TutorStore.exportJson(snapshot, out);
                main.post(() -> { if (!destroyed) message("Goal exported", "This goal's notes, attempts, ink and progress were saved. Other goals and Think pages were not included."); });
            } catch (Exception failure) { main.post(() -> { if (!destroyed) message("Export failed", "Tutor data is unchanged. Choose another destination and retry."); }); } });
        } else if (request == IMPORT) {
            if (!ready()) {
                message("Import again", "Tutor was reopened while choosing the file. Wait for it to load, then choose Import Tutor again.");
                return;
            }
            IO.execute(() -> { try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException("Could not open source"); TutorStore.Data decoded = TutorStore.importJson(in); TutorUiLogic.validateContent(decoded);
                TutorStore.Data imported = decoded.copy();
                main.post(() -> { if (destroyed || !ready()) return;
                    if (!TutorUiLogic.importFitsGoal(imported, hasCurriculum())) {
                        message("Different learning content", "This file contains Calculus practice. Import it from the Calculus practice goal; this goal's work is unchanged."); return;
                    }
                    new AlertDialog.Builder(this).setTitle("Replace this goal's study data?")
                        .setMessage("Replace notes, attempts, ink and progress in " + goal.title + " with this validated file (" + imported.attempts.size() + " attempts)? Export this goal first if needed. Other goals and Think pages are unchanged.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Replace data", (dialog, which) -> replaceData(imported)).show();
                });
            } catch (Exception failure) { main.post(() -> { if (!destroyed) message("Import could not be read", "No Tutor data was changed. The file may be damaged or use an unsupported content version."); }); } });
        }
    }
    private void confirmReset() {
        if (!ready()) return;
        new AlertDialog.Builder(this).setTitle("Reset this goal's study data?").setMessage("Delete notes, attempts, ink and progress in " + goal.title + "? Export this goal first to keep a copy. The goal itself, other goals and Think pages are unchanged.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reset data", (dialog, which) -> replaceData(new TutorStore.Data())).show();
    }
    private boolean fitsInkBudget(TutorStore.Attempt changed, java.util.List<InkView.Stroke> candidate) {
        long points = 0, strokes = 0;
        for (TutorStore.Attempt attempt : data.attempts) {
            java.util.List<InkView.Stroke> values = attempt == changed ? candidate : attempt.ink;
            strokes += values.size(); for (InkView.Stroke stroke : values) points += stroke.points.size();
        }
        return points <= TutorStore.MAX_TOTAL_INK_POINTS && strokes <= TutorStore.MAX_TOTAL_STROKES;
    }
    private void replaceData(TutorStore.Data replacement) {
        if (!ready()) return; captureInk(); main.removeCallbacks(delayedSave); pendingAction = null;
        final TutorStore.Data snapshot = replacement.copy(); replacing = true; body.setVisibility(View.INVISIBLE);
        saveStatus.setText("Saving replacement."); retrySave.setVisibility(View.GONE); final TutorStore writing = store;
        IO.execute(() -> { try { writing.write(snapshot); main.post(() -> {
            if (destroyed) return; replacing = false;
            // Release old views before binding the new data; never copy old ink into imported attempts.
            if (ink != null) ink.setListener(null); ink = null;
            data = replacement; revision = savedRevision = 0; body.setVisibility(View.VISIBLE); saved(); showGoal();
        }); } catch (Exception failure) { main.post(() -> { if (destroyed) return; replacing = false; body.setVisibility(View.VISIBLE);
            saveFailed(failure); message("Replacement not confirmed", "Could not confirm replacement; current work remains open. Export it before retrying."); }); } });
    }
    private void leave() {
        if (leaveRequested || replacing) return;
        if (!ready()) { completeLeave(); return; }
        captureInk(); pendingAction = null;
        if (revision == savedRevision) { completeLeave(); return; }
        leaveRequested = true; body.setVisibility(View.INVISIBLE); saveStatus.setVisibility(View.VISIBLE); saveStatus.setText("Saving before returning to Goals..."); flushSave();
    }
    private void completeLeave() { leaveRequested = false; releaseScreen(); finish(); }
    @Override public void onBackPressed() {
        if (!ready() || leaveRequested || replacing) { if (!leaveRequested && !replacing) leave(); return; }
        switch (TutorUiLogic.parentRoute(route)) {
            case "lesson": showLesson(topicId, familyId); break;
            case "lessons": showLessons(); break;
            case "library": leave(); break;
            default: showGoal();
        }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        captureInk(); flushSave(); state.putString("route", route); state.putString("topic", topicId);
        state.putString("family", familyId); state.putString("attempt", boundAttemptId); state.putBoolean("paper", paperVisible);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStop() { captureInk(); flushSave(); super.onStop(); }
    @Override protected void onDestroy() {
        captureInk(); flushSave(); destroyed = true; main.removeCallbacks(delayedSave); releaseScreen(); super.onDestroy();
    }
    private LinearLayout scrollPage() {
        ScrollView scroll = new ScrollView(this); LinearLayout page = column(); page.setPadding(0, 0, 0, dp(24)); scroll.addView(page);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1)); return page;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView label(String text, int size) { TextView view = new TextView(this); view.setText(text); view.setTextColor(Color.rgb(30, 30, 27)); view.setTextSize(size); view.setPadding(0, dp(4), 0, dp(4)); view.setLineSpacing(dp(3), 1); return view; }
    private TextView title(String text) { TextView view = label(text, 32); view.setTypeface(null, Typeface.BOLD); return view; }
    private TextView section(String text) { TextView view = label(text, 24); view.setTypeface(null, Typeface.BOLD); view.setPadding(0, dp(8), 0, dp(8)); return view; }
    private void addSection(LinearLayout page, String heading, String text) { page.addView(section(heading)); page.addView(label(text, 18)); }
    private Button button(String text, Runnable action) { Button view = new Button(this); view.setText(text); view.setAllCaps(false); view.setTextSize(18); view.setTextColor(Color.BLACK); view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48)); view.setPadding(dp(8), 0, dp(8), 0); view.setOnClickListener(v -> { if (!destroyed && !replacing && !leaveRequested) action.run(); }); return view; }
    private android.graphics.drawable.Drawable selectable() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true); return getDrawable(value.resourceId);
    }
    private void divider(LinearLayout page) {
        View line = new View(this); line.setBackgroundColor(Color.rgb(185, 185, 180)); page.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private LinearLayout.LayoutParams spaced(int width, int height) { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height); params.topMargin = dp(8); return params; }
    private void message(String title, String text) { if (!destroyed && !isFinishing()) new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
