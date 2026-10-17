package com.plia.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native notebook collection. No canvas, pen tools, network requests or invented progress. */
public final class TutorLibraryActivity extends Activity {
    public static final String EXTRA_GOAL_ID = "plia.tutor.goal_id";
    public static final String EXTRA_GOAL_TITLE = "plia.tutor.goal_title";
    public static final String EXTRA_CURRICULUM_ID = "plia.tutor.curriculum_id";
    private static final int EXPORT_ORIGINAL = 611;
    private static final ExecutorService IO = TutorLibraryStore.IO;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TutorLibraryStore store;
    private TutorLibraryStore.Data data;
    private LinearLayout content;
    private Button create;
    private boolean destroyed, mutating, corrupt, foreground;
    private long generation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root = column(); root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(24), dp(12), dp(24), dp(16));
        LinearLayout navigation = row(); navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.addView(button("Think", () -> { setResult(RESULT_OK); finish(); }), new LinearLayout.LayoutParams(dp(88), dp(48)));
        TextView title = text("Tutor", 25, true); title.setPadding(dp(16), 0, 0, 0);
        navigation.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        create = button("+ Create", this::createGoal);
        navigation.addView(create, new LinearLayout.LayoutParams(dp(116), dp(48))); root.addView(navigation);
        TextView heading = text("Your goals", 21, true); heading.setPadding(0, dp(24), 0, dp(20)); root.addView(heading);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        content = column(); scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); store = new TutorLibraryStore(getApplicationContext());
    }
    @Override public void onResume() { super.onResume(); foreground = true; if (!mutating) load(); }
    @Override public void onPause() { foreground = false; super.onPause(); }
    private void load() {
        final long request = ++generation; create.setEnabled(false);
        if (data == null) { content.removeAllViews(); content.addView(text("Opening goals…", 17, false)); }
        TutorLibraryStore reading = new TutorLibraryStore(getApplicationContext()); store = reading;
        IO.execute(() -> {
            try {
                TutorLibraryStore.Data result = reading.read();
                main.post(() -> { if (destroyed || request != generation) return;
                    data = result; corrupt = false; create.setEnabled(true); showGoals(); });
            } catch (Exception failure) {
                main.post(() -> { if (destroyed || request != generation) return;
                    data = null; corrupt = true; create.setEnabled(false); showRecovery(); });
            }
        });
    }
    /** Shared layout policy is also exercised by offline navigation fixtures. */
    static int columnCount(int widthDp) { return widthDp >= 960 ? 3 : widthDp >= 600 ? 2 : 1; }
    private void showGoals() {
        content.removeAllViews();
        int columns = columnCount(getResources().getConfiguration().screenWidthDp);
        LinearLayout line = null; int total = data.goals.size() + 1;
        for (int position = 0; position < total; position++) {
            if (position % columns == 0) { line = row(); line.setGravity(Gravity.TOP); content.addView(line); }
            TutorLibraryStore.Goal goal = position < data.goals.size() ? data.goals.get(position) : null;
            LinearLayout card = goalCard(goal);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(0, 0, position % columns < columns - 1 ? dp(18) : 0, dp(20)); line.addView(card, params);
        }
        int rest = total % columns;
        if (rest != 0) for (int empty = rest; empty < columns; empty++) {
            View spacer = new View(this); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 1, 1);
            params.setMargins(0, 0, empty < columns - 1 ? dp(18) : 0, 0); line.addView(spacer, params);
        }
    }
    private LinearLayout goalCard(TutorLibraryStore.Goal goal) {
        boolean isNew = goal == null, calculus = !isNew && TutorLibraryStore.CALCULUS_CURRICULUM.equals(goal.curriculumId);
        LinearLayout card = row(); card.setGravity(Gravity.TOP); card.setPadding(dp(12), dp(18), dp(12), dp(18));
        card.setMinimumHeight(dp(154)); card.setBackground(border(Color.WHITE, 0xffc8cbc5, 4));
        card.setClickable(true); card.setFocusable(true);
        card.addView(new NotebookCover(this, isNew, calculus), new LinearLayout.LayoutParams(dp(54), dp(76)));
        LinearLayout words = column(); words.setPadding(dp(15), 0, 0, 0);
        TextView name = text(isNew ? "Create a goal" : goal.title, 19, true);
        words.addView(name);
        TextView details = text(isNew ? "Something you want to develop"
            : calculus ? TutorCatalog.topics().size() + " topics · Calculus pack" : "Personal goal", 14, false);
        details.setPadding(0, dp(10), 0, 0); words.addView(details);
        if (!isNew && goal.createdAt > 0) {
            TextView date = text("Created " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(goal.createdAt)), 13, false);
            date.setPadding(0, dp(5), 0, 0); words.addView(date);
        }
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        card.setContentDescription(isNew ? "Create a new personal goal" : "Open goal " + goal.title + ". " + details.getText());
        card.setOnClickListener(v -> { if (!mutating && !corrupt) { if (isNew) createGoal(); else open(goal); } });
        return card;
    }
    private void open(TutorLibraryStore.Goal goal) {
        if (!foreground || destroyed || isFinishing()) return;
        startActivity(new Intent(this, TutorActivity.class).putExtra(EXTRA_GOAL_ID, goal.id)
            .putExtra(EXTRA_GOAL_TITLE, goal.title).putExtra(EXTRA_CURRICULUM_ID, goal.curriculumId));
    }
    private void createGoal() {
        if (mutating || data == null || corrupt) return;
        EditText input = new EditText(this); input.setSingleLine(true); input.setTextSize(20);
        input.setHint("Goal name"); input.setContentDescription("Name of your new goal");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TutorLibraryStore.MAX_TITLE_CHARS)});
        LinearLayout fields = column(); fields.setPadding(dp(24), dp(10), dp(24), dp(8)); fields.addView(input);
        TextView detail = text("Start an empty notebook for your goal.", 16, false); detail.setPadding(0, dp(10), 0, 0); fields.addView(detail);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("New goal").setView(fields)
            .setNegativeButton("Cancel", null).setPositiveButton("Create", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = input.getText().toString().trim();
            if (title.isEmpty()) { input.setError("Give your goal a name"); return; }
            mutating = true; create.setEnabled(false); dialog.setCancelable(false);
            input.setEnabled(false); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            TutorLibraryStore writing = store;
            final long request = generation;
            IO.execute(() -> {
                try {
                    TutorLibraryStore.Goal goal = writing.createGoal(title, System.currentTimeMillis());
                    main.post(() -> { if (destroyed || isFinishing()) return; mutating = false;
                        create.setEnabled(true); dialog.dismiss();
                        if (foreground && request == generation) { open(goal); }
                        else if (foreground) { load(); } });
                } catch (Exception failure) {
                    main.post(() -> { if (destroyed) return; mutating = false; create.setEnabled(true);
                        dialog.setCancelable(true); input.setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                        input.setError("Save could not be confirmed. Your other goals are unchanged."); });
                }
            });
        }));
        dialog.show(); input.requestFocus();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void showRecovery() {
        content.removeAllViews(); content.addView(text("Your goal library is preserved", 21, true));
        TextView detail = text("The saved library could not be opened. Retry, or export the original index for recovery. Your goal notes have not been changed.", 17, false);
        detail.setPadding(0, dp(16), 0, dp(16)); content.addView(detail);
        content.addView(button("Retry opening", this::load));
        content.addView(button("Export original index", () -> {
            Intent export = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "plia-tutor-library-recovery.json");
            try { startActivityForResult(export, EXPORT_ORIGINAL); }
            catch (RuntimeException failure) { message("Export unavailable", "No document provider is available. The original file remains on this device."); }
        }));
    }
    @Override protected void onActivityResult(int request, int result, Intent returned) {
        super.onActivityResult(request, result, returned);
        if (request != EXPORT_ORIGINAL || result != RESULT_OK || returned == null || returned.getData() == null) return;
        android.net.Uri target = returned.getData(); TutorLibraryStore original = store;
        IO.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(target)) {
                if (output == null) throw new java.io.IOException("No output stream"); original.exportRaw(output);
                main.post(() -> { if (!destroyed) message("Exported", "The original library index was exported. Each goal's notes are stored separately."); });
            } catch (Exception failure) {
                main.post(() -> { if (!destroyed) message("Export failed", "The original library file is still on this device."); });
            }
        });
    }
    @Override public void onDestroy() { destroyed = true; generation++; super.onDestroy(); }
    private void message(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL); return view; }
    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(0xff171917);
        text.setIncludeFontPadding(false); text.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) text.setTypeface(null, Typeface.BOLD); return text;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(0xff171917); button.setMinHeight(dp(48)); button.setOnClickListener(v -> action.run()); return button;
    }
    private GradientDrawable border(int fill, int stroke, int radius) {
        GradientDrawable background = new GradientDrawable(); background.setColor(fill);
        background.setStroke(dp(1), stroke); background.setCornerRadius(dp(radius)); return background;
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class NotebookCover extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean create, calculus;
        NotebookCover(Context context, boolean create, boolean calculus) {
            super(context); this.create = create; this.calculus = calculus;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth(), height = getHeight();
            paint.setStyle(Paint.Style.FILL); paint.setColor(create ? Color.WHITE : 0xffeff0e9);
            canvas.drawRect(1, 1, width - 1, height - 1, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0xff343a32);
            canvas.drawRect(1, 1, width - 1, height - 1, paint);
            canvas.drawLine(width * .14f, 1, width * .14f, height - 1, paint);
            if (create) {
                canvas.drawLine(width * .36f, height * .5f, width * .75f, height * .5f, paint);
                canvas.drawLine(width * .55f, height * .35f, width * .55f, height * .65f, paint);
            } else if (calculus) {
                paint.setStyle(Paint.Style.FILL); paint.setTypeface(Typeface.SERIF); paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(height * .52f); canvas.drawText("∫", width * .57f, height * .68f, paint);
            } else {
                for (int line = 0; line < 3; line++) canvas.drawLine(width * .34f, height * (.4f + line * .12f), width * .77f, height * (.4f + line * .12f), paint);
            }
        }
    }
}
