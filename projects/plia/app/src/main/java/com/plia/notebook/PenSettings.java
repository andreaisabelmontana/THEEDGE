package com.plia.notebook;

import android.content.Context;
import android.content.SharedPreferences;

/** The current pen affects new strokes only; saved strokes carry their own width and color. */
public final class PenSettings {
    public static final int BLACK = 0xff171917, BLUE = 0xff1746a2, RED = 0xffa12626;
    public static final float THIN = 2f;
    private static final String PREFERENCES = "plia_pen", WIDTH = "width", COLOR = "color";

    private PenSettings() {}

    public static float width(Context context) {
        try {
            float value = preferences(context).getFloat(WIDTH, THIN);
            return validWidth(value) ? value : THIN;
        } catch (ClassCastException invalid) { return THIN; }
    }

    public static int color(Context context) {
        try {
            int value = preferences(context).getInt(COLOR, BLACK);
            return paletteColor(value) ? value : BLACK;
        } catch (ClassCastException invalid) { return BLACK; }
    }

    public static void save(Context context, float width, int color) {
        if (!validWidth(width) || !paletteColor(color)) return;
        preferences(context).edit().putFloat(WIDTH, width).putInt(COLOR, color).apply();
    }

    private static boolean validWidth(float width) { return Float.isFinite(width) && width >= .5f && width <= 12f; }
    private static boolean paletteColor(int color) { return color == BLACK || color == BLUE || color == RED; }
    private static SharedPreferences preferences(Context context) { return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE); }
}
