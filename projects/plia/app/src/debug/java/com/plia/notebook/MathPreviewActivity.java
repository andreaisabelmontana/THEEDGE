package com.plia.notebook;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Debug-only, fixed offline fixtures. No notebook, credentials, arbitrary text or network input. */
public final class MathPreviewActivity extends Activity {
    private static final int FIXTURE_COUNT = 9, MAX_ASSET_BYTES = 65536;
    private final ArrayList<Fixture> fixtures = new ArrayList<>();
    private LinearLayout preview;
    private ScrollView scroll;
    private TextView label;
    private Button widthButton;
    private SolutionView solution;
    private int index;
    private boolean narrow;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        readFixedFixtures();
        index = bounded(saved == null ? intentIndex(getIntent()) : saved.getInt("fixtureIndex", 0));
        narrow = saved != null && saved.getBoolean("narrow", false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        label = new TextView(this);
        label.setTextColor(Color.BLACK); label.setTextSize(16);
        label.setPadding(dp(12), dp(8), dp(12), dp(4));
        root.addView(label, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        Button next = new Button(this); next.setText("Next fixture");
        next.setOnClickListener(v -> { index = (index + 1) % fixtures.size(); showFixture(); });
        controls.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
        widthButton = new Button(this);
        widthButton.setOnClickListener(v -> { narrow = !narrow; applyWidth(); });
        controls.addView(widthButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this);
        preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(preview, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        showFixture();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        index = bounded(intentIndex(intent));
        showFixture();
    }

    private static int intentIndex(Intent intent) {
        try { return intent == null ? 0 : intent.getIntExtra("index", 0); }
        catch (RuntimeException invalidType) { return 0; }
    }

    private int bounded(int value) { return Math.max(0, Math.min(fixtures.size() - 1, value)); }

    private void showFixture() {
        releaseSolution();
        Fixture fixture = fixtures.get(index);
        solution = new SolutionView(this, fixture.text, Typeface.DEFAULT);
        preview.addView(solution, new LinearLayout.LayoutParams(narrow ? dp(320) : -1, -2));
        solution.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (v == solution && r - l != oldR - oldL) updateLabel();
        });
        applyWidth();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void applyWidth() {
        if (solution == null) return;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)solution.getLayoutParams();
        params.width = narrow ? dp(320) : LinearLayout.LayoutParams.MATCH_PARENT;
        solution.setLayoutParams(params);
        widthButton.setText(narrow ? "Use full width" : "Use 320dp width");
        updateLabel();
    }

    private void updateLabel() {
        Fixture fixture = fixtures.get(index);
        int pixels = solution == null ? 0 : solution.getWidth();
        String text = "Offline math QA " + index + "/" + (fixtures.size() - 1) + " — " + fixture.title
                + "\n" + (narrow ? "Fixed 320dp" : "Full width") + " (" + pixels + " px). Fixed display samples only.";
        if (!text.contentEquals(label.getText())) label.setText(text);
    }

    private void releaseSolution() {
        if (solution == null) return;
        SolutionView old = solution; solution = null;
        preview.removeView(old);
        old.dispose();
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putInt("fixtureIndex", index); saved.putBoolean("narrow", narrow);
        super.onSaveInstanceState(saved);
    }

    @Override protected void onDestroy() {
        releaseSolution();
        super.onDestroy();
    }

    private void readFixedFixtures() {
        try (InputStream input = getAssets().open("mathpreview/fixtures.json");
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[4096]; int read;
            while ((read = input.read(bytes)) != -1) {
                if (output.size() + read > MAX_ASSET_BYTES) throw new IllegalStateException("Fixture asset too large");
                output.write(bytes, 0, read);
            }
            JSONArray array = new JSONObject(output.toString(StandardCharsets.UTF_8.name())).getJSONArray("fixtures");
            if (array.length() != FIXTURE_COUNT) throw new IllegalStateException("Fixture count changed");
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.getJSONObject(i);
                if (value.getInt("index") != i) throw new IllegalStateException("Fixture index changed");
                String title = value.getString("title"), text = value.getString("text");
                if (title.length() > 120 || text.length() > 16000) throw new IllegalStateException("Invalid fixture size");
                fixtures.add(new Fixture(title, text));
            }
        } catch (Exception unavailable) {
            fixtures.clear();
            fixtures.add(new Fixture("Fixture asset unavailable", "The bundled debug fixture could not be loaded."));
        }
    }

    private int dp(int value) { return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density)); }
    private static final class Fixture {
        final String title, text;
        Fixture(String title, String text) { this.title = title; this.text = text; }
    }
}
