package com.plia.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fixed logical-paper coordinates, no caller-supplied text or events. */
final class FixedInputCases {
    static final int DOWN = 0, UP = 1, MOVE = 2, CANCEL = 3, HOVER = 7, INTERVAL_MS = 8;
    static final class Sample {
        final int action;
        final float x, y;
        Sample(int action, float x, float y) { this.action = action; this.x = x; this.y = y; }
    }
    static final class Case {
        final String name;
        final boolean select, manual;
        final int replies, expectedCompleted;
        final List<Sample> samples;
        Case(String name, boolean select, int replies, boolean manual, int expectedCompleted, List<Sample> samples) {
            this.name = name; this.select = select; this.replies = replies; this.manual = manual;
            this.expectedCompleted = expectedCompleted; this.samples = Collections.unmodifiableList(samples);
        }
    }
    static List<Case> all() {
        ArrayList<Case> result = new ArrayList<>();
        result.add(new Case("Pen / no replies", false, 0, false, 0, pen()));
        result.add(new Case("Pen / three replies", false, 3, false, 0, pen()));
        ArrayList<Sample> open = new ArrayList<>();
        open.add(new Sample(DOWN, 150, 180));
        line(open, 150, 180, 850, 180, 60);
        open.add(new Sample(UP, 850, 180)); open.add(new Sample(HOVER, 850, 180));
        result.add(new Case("Straight selection has no area", true, 0, false, 0, open));
        ArrayList<Sample> exact = oval(500, 320, 380, 80, 2 * Math.PI, UP);
        exact.add(new Sample(UP, 880, 320)); // Duplicate owner UP must never produce a second callback.
        result.add(new Case("Closed long oval / duplicate UP", true, 0, false, 1, exact));
        result.add(new Case("Closed oval with 20-degree overshoot", true, 0, false, 1,
                oval(500, 320, 380, 80, 2 * Math.PI + Math.PI / 9, UP)));
        result.add(new Case("Closed oval crossing a reply", true, 1, false, 1,
                oval(500, 360, 360, 200, 2 * Math.PI, UP)));
        result.add(new Case("Closed outline CANCEL / hover", true, 0, false, 0,
                oval(500, 320, 380, 80, 2 * Math.PI, CANCEL)));
        // A hand-built approximation of the reported outline, not recovered touch events.
        float[] photo = {220,390,320,386,440,397,495,400,520,430,540,465,530,510,500,560,
                430,600,350,626,250,642,200,640,160,615,135,580,120,520,120,460,140,420,
                175,400,210,391,240,393,280,397};
        ArrayList<Sample> overtrace = new ArrayList<>();
        overtrace.add(new Sample(DOWN, photo[0], photo[1]));
        for (int i = 2; i < photo.length; i += 2) overtrace.add(new Sample(MOVE, photo[i], photo[i + 1]));
        overtrace.add(new Sample(UP, 280, 397)); overtrace.add(new Sample(HOVER, 280, 397));
        result.add(new Case("Photo-inspired parallel overtrace", true, 0, false, 1, overtrace));
        result.add(new Case("Manual stylus / Pen / three replies", false, 3, true, -1, new ArrayList<>()));
        result.add(new Case("Manual stylus / AI Select / three replies", true, 3, true, -1, new ArrayList<>()));
        return Collections.unmodifiableList(result);
    }
    private static ArrayList<Sample> pen() {
        ArrayList<Sample> values = new ArrayList<>();
        for (int stroke = 0; stroke < 4; stroke++) {
            float y = 160 + stroke * 95;
            values.add(new Sample(DOWN, 80, y));
            for (int i = 1; i <= 90; i++) {
                float x = 80 + 760 * i / 90f;
                values.add(new Sample(MOVE, x, y + (float)Math.sin(i / 90.0 * Math.PI * 6) * 22));
            }
            values.add(new Sample(UP, 840, y));
        }
        return values;
    }
    private static ArrayList<Sample> oval(float cx, float cy, float rx, float ry, double end, int ending) {
        ArrayList<Sample> values = new ArrayList<>();
        values.add(new Sample(DOWN, cx + rx, cy));
        arc(values, cx, cy, rx, ry, 0, end, 120);
        Sample last = values.get(values.size() - 1);
        values.add(new Sample(ending, last.x, last.y));
        values.add(new Sample(HOVER, last.x, last.y));
        return values;
    }
    private static void arc(ArrayList<Sample> values, float cx, float cy, float rx, float ry, double start, double end, int count) {
        for (int i = 1; i <= count; i++) {
            double a = start + (end - start) * i / count;
            values.add(new Sample(MOVE, cx + (float)Math.cos(a) * rx, cy + (float)Math.sin(a) * ry));
        }
    }
    private static void line(ArrayList<Sample> values, float x1, float y1, float x2, float y2, int count) {
        for (int i = 1; i <= count; i++) values.add(new Sample(MOVE, x1 + (x2 - x1) * i / count, y1 + (y2 - y1) * i / count));
    }
}
