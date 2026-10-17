package com.plia.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Bounded, lossless-source parsing of supplied math/words pairs. Never invents a step. */
public final class SolutionDocument {
    private static final int MAX_CHARS = 16000, MAX_ROWS = 100, MAX_ROW_LINES = 64;
    public final String original, before, after;
    public final List<Row> rows;
    public final boolean structured, showHeading;

    public static final class Row {
        /** Source TeX is preserved. Normalize only at the rendering boundary. */
        public final String math, words;
        private Row(String math, String words) { this.math = math; this.words = words; }
    }

    private SolutionDocument(String original, String before, List<Row> rows, String after,
                             boolean structured, boolean showHeading) {
        this.original = original;
        this.before = before;
        this.after = after;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        this.structured = structured;
        this.showHeading = showHeading;
    }

    private static SolutionDocument plain(String text) {
        return new SolutionDocument(text, text, Collections.emptyList(), "", false, false);
    }

    public static SolutionDocument parse(String supplied) {
        String original = supplied == null ? "" : supplied;
        if (original.length() > MAX_CHARS) return plain(original);
        String text = original.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = text.split("\n", -1);
        int[] offsets = new int[lines.length];
        for (int i = 1; i < lines.length; i++) offsets[i] = offsets[i - 1] + lines[i - 1].length() + 1;
        Fence fence = new Fence();
        for (int start = 0; start + 2 < lines.length; start++) {
            if (fence.consume(lines[start])) continue;
            Cells header = cells(lines[start]);
            if (!header.valid() || !header.parts.get(0).equalsIgnoreCase("steps in math")
                    || !header.parts.get(1).equalsIgnoreCase("steps in words")) continue;
            Cells separator = cells(lines[start + 1]);
            if (!separator.valid() || !isSeparator(separator.parts.get(0))
                    || !isSeparator(separator.parts.get(1))) continue;
            ArrayList<Row> rows = new ArrayList<>();
            int end = start + 2;
            while (end < lines.length) {
                StringBuilder rowText = new StringBuilder(lines[end]);
                Cells row = cells(rowText.toString());
                int next = end + 1, count = 1;
                while (row.incomplete && next < lines.length && count < MAX_ROW_LINES) {
                    rowText.append('\n').append(lines[next++]);
                    count++;
                    row = cells(rowText.toString());
                }
                if (row.incomplete) return plain(original);
                if (!row.valid()) {
                    // A broken table row is not silently dropped or partially displayed.
                    if (lines[end].trim().startsWith("|")) return plain(original);
                    break;
                }
                if (row.parts.get(0).isEmpty() || row.parts.get(1).isEmpty()) return plain(original);
                rows.add(new Row(row.parts.get(0), unescapeProsePipe(row.parts.get(1))));
                if (rows.size() > MAX_ROWS) return plain(original);
                end = next;
            }
            if (!rows.isEmpty()) {
                String before = compactHeadings(text.substring(0, offsets[start]));
                String after = compactHeadings(end < lines.length ? text.substring(offsets[end]) : "");
                return new SolutionDocument(original, before, rows, after, true, !hasLead(before));
            }
        }
        return plain(original);
    }

    private static boolean isSeparator(String s) { return s.matches(":?-{3,}:?"); }

    private static String unescapeProsePipe(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '|'
                    && !escaped(s, i)) continue;
            out.append(s.charAt(i));
        }
        return out.toString();
    }

    private static final class Cells {
        final List<String> parts;
        final boolean incomplete;
        Cells(List<String> parts, boolean incomplete) { this.parts = parts; this.incomplete = incomplete; }
        boolean valid() { return !incomplete && parts.size() == 2; }
    }

    /** Splits only actual Markdown boundaries; TeX commands and row separators stay byte-for-byte. */
    private static Cells cells(String line) {
        String value = line.trim();
        ArrayList<String> parts = new ArrayList<>();
        if (value.isEmpty()) return new Cells(parts, false);
        if (value.startsWith("|")) value = value.substring(1);
        if (value.endsWith("|") && !escaped(value, value.length() - 1)) value = value.substring(0, value.length() - 1);
        int from = 0, braces = 0, environments = 0;
        String delimiter = null;
        boolean pipe = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && !escaped(value, i)) {
                if (value.startsWith("\\begin{", i)) environments++;
                else if (value.startsWith("\\end{", i) && environments > 0) environments--;
                if (value.startsWith("\\(", i) && delimiter == null) { delimiter = "\\)"; i++; continue; }
                if (value.startsWith("\\[", i) && delimiter == null) { delimiter = "\\]"; i++; continue; }
                if (delimiter != null && value.startsWith(delimiter, i)) { i += delimiter.length() - 1; delimiter = null; continue; }
            }
            if (ch == '$' && !escaped(value, i)) {
                String mark = value.startsWith("$$", i) ? "$$" : "$";
                if (delimiter == null) delimiter = mark;
                else if (delimiter.equals(mark)) delimiter = null;
                i += mark.length() - 1;
                continue;
            }
            if (!escaped(value, i)) {
                if (ch == '{') braces++;
                else if (ch == '}' && braces > 0) braces--;
                if (ch == '|' && braces == 0 && environments == 0 && delimiter == null) {
                    parts.add(value.substring(from, i).trim());
                    from = i + 1;
                    pipe = true;
                }
            }
        }
        if (pipe) parts.add(value.substring(from).trim());
        return new Cells(parts, braces > 0 || environments > 0 || delimiter != null);
    }

    private static boolean escaped(String s, int at) {
        int n = 0;
        while (at > 0 && s.charAt(--at) == '\\') n++;
        return n % 2 != 0;
    }

    private static String compactHeadings(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        Fence fence = new Fence();
        for (int i = 0; i < lines.length; i++) {
            if (!fence.consume(lines[i]) && headingText(lines[i]).equalsIgnoreCase("to solve")) {
                if (i + 1 < lines.length && lines[i + 1].trim().matches("(?:={3,}|-{3,})")) i++;
                continue;
            }
            if (out.length() > 0) out.append('\n');
            out.append(lines[i]);
        }
        return out.toString().trim();
    }

    private static boolean hasLead(String before) {
        for (String line : before.split("\n")) {
            String s = headingText(line).toLowerCase(Locale.ROOT);
            if (s.matches("to\\s+solve(?:\\s*[,:]|\\s+)\\s*\\S.*")) return true;
        }
        return false;
    }

    private static String headingText(String source) {
        String s = source.trim().replaceFirst("^#{1,6}\\s+", "").replaceFirst("\\s+#+$", "").trim();
        for (int i = 0; i < 3; i++) {
            boolean removed = false;
            for (String mark : new String[] { "***", "___", "**", "__", "*", "_" }) {
                if (s.length() > mark.length() * 2 && s.startsWith(mark) && s.endsWith(mark)) {
                    s = s.substring(mark.length(), s.length() - mark.length()).trim(); removed = true; break;
                }
            }
            if (!removed) break;
        }
        return s;
    }

    private static final class Fence {
        char marker;
        int length;
        boolean consume(String line) {
            String s = line.trim();
            if (s.startsWith("```") || s.startsWith("~~~")) {
                char c = s.charAt(0);
                int n = 0;
                while (n < s.length() && s.charAt(n) == c) n++;
                if (marker == 0) { marker = c; length = n; }
                else if (marker == c && n >= length && s.substring(n).trim().isEmpty()) marker = 0;
                return true;
            }
            return marker != 0;
        }
    }
}
