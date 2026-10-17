package com.plia.notebook;

import android.content.Context;
import android.graphics.Typeface;

/** Optional bundled handwriting for explanation prose only, never mathematical glyphs or UI. */
public final class ExplanationFont {
    public static final String ASSET_PATH = "math/fonts/AndreaHandwriting.ttf";
    private static final String PREFERENCES = "plia_explanation_font", ENABLED = "enabled";
    private static boolean checked;
    private static Typeface cached;

    private ExplanationFont() {}

    /** Assets are immutable for the lifetime of this app process, including missing assets. */
    public static synchronized boolean available(Context context) {
        if (!checked) {
            checked = true;
            try { cached = Typeface.createFromAsset(context.getAssets(), ASSET_PATH); }
            catch (RuntimeException missingOrInvalid) { cached = null; }
        }
        return cached != null;
    }

    /** An explicit off preference survives updates; the default is on when the real asset loads. */
    public static boolean enabled(Context context) {
        if (!available(context)) return false;
        try { return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, true); }
        catch (ClassCastException invalidPreference) { return false; }
    }

    /** UI calls this only after a deliberate settings action, then recreates explanation views. */
    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply();
    }

    public static Typeface proseTypeface(Context context) {
        return enabled(context) ? cached : Typeface.DEFAULT;
    }

    /** Closed JSON option allowlist; no caller-supplied URL, family or CSS is accepted. */
    public static String documentOption(Context context) { return enabled(context) ? "andrea" : "default"; }

    /** Null means no caller preference. An explicit default/unknown option must not be overridden. */
    static String documentOption(Context context, String requested) {
        return requested == null || "andrea".equals(requested) ? documentOption(context) : "default";
    }

    /** Conservative native fallback: ambiguous raw notation keeps its original readable face. */
    static boolean canStyleUnstructured(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        for (int at = 0; at < text.length(); at++) {
            char c = text.charAt(at);
            if (Character.isDigit(c) || Character.getType(c) == Character.MATH_SYMBOL
                    || Character.getType(c) == Character.OTHER_NUMBER
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.GREEK
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.GREEK_EXTENDED
                    || "\\$=^_+-*/<>|{}[]()`~".indexOf(c) >= 0 || (c >= '\u2032' && c <= '\u2037')) return false;
        }
        return !text.toLowerCase(java.util.Locale.ROOT).matches("(?s).*\\b(?:sin|cos|tan|cot|sec|csc|log|ln|lim|sqrt|exp)\\b.*");
    }
}
