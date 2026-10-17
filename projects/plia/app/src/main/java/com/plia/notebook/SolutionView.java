package com.plia.notebook;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Displays supplied reply text; it never generates or infers solution steps. */
public final class SolutionView extends LinearLayout {
    private final Typeface proseFont;
    private LocalMathView mathView;
    private static final int MAX_LIVE_RENDERERS = 8;
    private static int liveRenderers;
    private String originalReply;

    public SolutionView(Context context, String text, Typeface handwriting) {
        super(context);
        this.proseFont = ExplanationFont.proseTypeface(context);
        setOrientation(VERTICAL);
        String reply = text == null ? "" : text;
        originalReply = reply;
        SolutionDocument document = SolutionDocument.parse(reply);
        if (!document.structured) {
            boolean originalNotation = reply.toLowerCase(java.util.Locale.ROOT).contains("steps in math")
                    && reply.toLowerCase(java.util.Locale.ROOT).contains("steps in words");
            if (originalNotation)
                addView(label("Math formatting could not be read. Original reply:", 16), fullWidth());
            addView(originalNotation ? label(reply, 18) : proseLabel(reply, 18), fullWidth()); return;
        }
        if (liveRenderers >= MAX_LIVE_RENDERERS) {
            addView(label("This page has many old replies. Original notation is shown here; refresh this problem to typeset it.", 16), fullWidth());
            addView(label(reply, 18), fullWidth()); return;
        }
        try {
            org.json.JSONArray rows = new org.json.JSONArray();
            for (SolutionDocument.Row row : document.rows) rows.put(new org.json.JSONObject()
                    .put("math", MathNotation.normalize(row.math)).put("words", row.words));
            String payload = new org.json.JSONObject().put("before", document.before).put("after", document.after)
                    .put("rows", rows).put("structured", true).put("showHeading", document.showHeading)
                    .put("explanationFont", ExplanationFont.documentOption(context)).toString();
            mathView = new LocalMathView(context, payload, this::requestLayout, this::showOriginalReply);
            liveRenderers++;
            addView(mathView, fullWidth());
        } catch (RuntimeException | org.json.JSONException unavailable) {
            addView(label("Math display unavailable. Original reply:", 16), fullWidth());
            addView(label(reply, 18), fullWidth());
        }
    }

    private void showOriginalReply() {
        dispose(); removeAllViews();
        addView(label("Math display unavailable. Original reply:", 16), fullWidth());
        addView(label(originalReply, 18), fullWidth());
    }

    public void dispose() {
        if (mathView != null) {
            LocalMathView old = mathView; mathView = null;
            removeView(old); old.dispose(); liveRenderers = Math.max(0, liveRenderers - 1);
        }
    }

    private void addRow(String math, String words, boolean header) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        TextView left = label(math, header ? 18 : 20);
        TextView right = label(words, header ? 18 : 20);
        left.setPadding(dp(12), dp(8), dp(12), dp(8));
        right.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.addView(left, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        View divider = new View(getContext());
        divider.setBackgroundColor(0xff9b9d96);
        row.addView(divider, new LayoutParams(dp(1), LayoutParams.MATCH_PARENT));
        row.addView(right, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        addView(row, fullWidth());
    }

    private TextView label(String text, int size) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextColor(0xff171917);
        label.setTextSize(size);
        label.setTypeface(Typeface.DEFAULT);
        label.setGravity(Gravity.TOP);
        label.setTextIsSelectable(true);
        return label;
    }

    private TextView proseLabel(String text, int size) {
        TextView label = label(text, size);
        // A native source fallback has no rich code parser; keep any code-containing reply readable.
        if (text.indexOf('`') >= 0 || text.matches("(?s).*?(?:^|\\n) {0,3}~{3,}.*"))
            label.setTypeface(Typeface.MONOSPACE);
        else if (proseFont != Typeface.DEFAULT && ExplanationFont.canStyleUnstructured(text)) {
            label.setTypeface(proseFont);
            label.setTextSize(size * 1.35f);
            label.setLineSpacing(dp(2), 1.15f);
        }
        return label;
    }

    private LayoutParams fullWidth() { return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.max(1, Math.round(value * getResources().getDisplayMetrics().density)); }

    /** Pure parser, separated from Android rendering so format handling can be checked on a JVM. */
    static final class ParsedReply {
        final String before, after;
        final List<String[]> rows;
        final boolean beforeHasToSolveHeading;

        private ParsedReply(String before, List<String[]> rows, String after) {
            this.before = before;
            this.rows = Collections.unmodifiableList(rows);
            this.after = after;
            this.beforeHasToSolveHeading = endsWithToSolveHeading(before);
        }

        /** Recognizes a supplied final heading without removing or rewriting any supplied prose. */
        private static boolean endsWithToSolveHeading(String before) {
            String[] lines = before.split("\n", -1);
            int last = lines.length - 1;
            while (last >= 0 && lines[last].trim().isEmpty()) last--;
            if (last < 0) return false;
            String heading = lines[last].trim();
            // Setext headings use the immediately preceding line, not earlier prose.
            if (heading.matches("(?:={3,}|-{3,})") && last > 0) heading = lines[last - 1].trim();
            if (heading.matches("#{1,6}[ \\t]+.*")) {
                heading = heading.replaceFirst("^#{1,6}[ \\t]+", "")
                        .replaceFirst("[ \\t]+#+$", "").trim();
            }
            // Common emphasis wrappers may surround a heading; unmatched markers are prose.
            for (int i = 0; i < 3; i++) {
                String marker = null;
                for (String candidate : new String[] { "***", "___", "**", "__", "*", "_" }) {
                    if (heading.length() > candidate.length() * 2
                            && heading.startsWith(candidate) && heading.endsWith(candidate)) {
                        marker = candidate;
                        break;
                    }
                }
                if (marker == null) break;
                heading = heading.substring(marker.length(), heading.length() - marker.length()).trim();
            }
            return heading.matches("(?i)to[ \\t]+solve");
        }

        static ParsedReply parse(String text) {
            String[] lines = text.split("\n", -1);
            int[] offsets = new int[lines.length];
            for (int i = 1; i < lines.length; i++) offsets[i] = offsets[i - 1] + lines[i - 1].length() + 1;
            char fence = 0;
            int fenceLength = 0;
            for (int i = 0; i + 2 < lines.length; i++) {
                String trimmed = lines[i].trim();
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    char marker = trimmed.charAt(0);
                    int count = 0;
                    while (count < trimmed.length() && trimmed.charAt(count) == marker) count++;
                    if (fence == 0) { fence = marker; fenceLength = count; }
                    else if (fence == marker && count >= fenceLength && trimmed.substring(count).trim().isEmpty()) fence = 0;
                    continue;
                }
                if (fence != 0) continue;
                String[] headers = cells(lines[i]);
                if (headers == null || !headers[0].equalsIgnoreCase("steps in math") || !headers[1].equalsIgnoreCase("steps in words")) continue;
                String[] separators = cells(lines[i + 1]);
                if (separators == null || !separator(separators[0]) || !separator(separators[1])) continue;
                ArrayList<String[]> rows = new ArrayList<>();
                int end = i + 2;
                while (end < lines.length) {
                    String[] row = cells(lines[end]);
                    if (row == null) break;
                    rows.add(row);
                    // Keep pathological tables as complete plain text instead of creating thousands of views.
                    if (rows.size() > 100) return null;
                    end++;
                }
                if (!rows.isEmpty()) return new ParsedReply(text.substring(0, offsets[i]), rows, end < lines.length ? text.substring(offsets[end]) : "");
            }
            return null;
        }

        private static boolean separator(String cell) { return cell.matches(":?-{3,}:?"); }

        /** Requires exactly two cells; escaped pipes remain cell text. Optional outer pipes are allowed. */
        private static String[] cells(String line) {
            String value = line.trim();
            if (value.isEmpty()) return null;
            ArrayList<String> parts = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean pipe = false;
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch == '\\' && i + 1 < value.length() && (value.charAt(i + 1) == '|' || value.charAt(i + 1) == '\\')) {
                    cell.append(value.charAt(++i));
                } else if (ch == '|') {
                    parts.add(cell.toString().trim());
                    cell.setLength(0);
                    pipe = true;
                } else cell.append(ch);
            }
            if (!pipe) return null;
            parts.add(cell.toString().trim());
            if (value.charAt(0) == '|') parts.remove(0);
            // An escaped final pipe is content, not an optional table boundary.
            int slashes = 0;
            for (int i = value.length() - 2; i >= 0 && value.charAt(i) == '\\'; i--) slashes++;
            if (value.charAt(value.length() - 1) == '|' && slashes % 2 == 0) parts.remove(parts.size() - 1);
            return parts.size() == 2 ? parts.toArray(new String[0]) : null;
        }
    }
}
