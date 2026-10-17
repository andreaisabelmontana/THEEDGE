package com.plia.notebook;

import java.util.Locale;

/** Closed structural categories only; neither classifier establishes response validity or retains input. */
public final class ResponseMetadata {
    public static final int MAX_PREFIX_BYTES = 512;
    public enum ContentType { NOT_OBSERVED, MISSING, EVENT_STREAM, JSON, HTML, OTHER }
    public enum BodyShape { NOT_INSPECTED, EMPTY, EVENT_STREAM_PREFIX, JSON_PREFIX, HTML_PREFIX,
        OTHER_PREFIX, UNREADABLE }

    private ResponseMetadata() {}

    /** Ignore MIME parameters, but require a complete ASCII type/subtype instead of matching a prefix. */
    public static ContentType classifyContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) return ContentType.MISSING;
        int parameters = contentType.indexOf(';');
        String base = (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim();
        int slash = base.indexOf('/');
        if (slash <= 0 || slash == base.length() - 1 || base.indexOf('/', slash + 1) >= 0) return ContentType.OTHER;
        for (int i = 0; i < base.length(); i++) {
            if (i != slash && !mimeToken(base.charAt(i))) return ContentType.OTHER;
        }
        base = base.toLowerCase(Locale.ROOT);
        if (base.equals("text/event-stream")) return ContentType.EVENT_STREAM;
        if (base.equals("application/json") || base.equals("text/json")) return ContentType.JSON;
        if (base.startsWith("application/") && base.endsWith("+json")
                && base.length() > "application/+json".length()) return ContentType.JSON;
        if (base.equals("text/html") || base.equals("application/xhtml+xml")) return ContentType.HTML;
        return ContentType.OTHER;
    }

    /**
     * Inspect at most the first 512 supplied bytes without decoding or copying response text.
     * A UTF-8 BOM and leading ASCII whitespace are ignored. EMPTY means zero supplied bytes;
     * nonzero blank or truncated prefixes are inconclusive. The caller owns EOF and read errors.
     */
    public static BodyShape classifyPrefix(byte[] bytes, int length) {
        if (bytes == null || length < 0 || length > bytes.length) return BodyShape.UNREADABLE;
        if (length == 0) return BodyShape.EMPTY;
        int limit = Math.min(length, MAX_PREFIX_BYTES);
        int offset = 0;
        if (limit >= 3 && (bytes[0] & 255) == 239 && (bytes[1] & 255) == 187 && (bytes[2] & 255) == 191) offset = 3;
        while (offset < limit && whitespace(bytes[offset] & 255)) offset++;
        if (offset == limit) return BodyShape.OTHER_PREFIX;
        int first = bytes[offset] & 255;
        if (first == ':' || matches(bytes, offset, limit, "event:", false)
                || matches(bytes, offset, limit, "data:", false)) return BodyShape.EVENT_STREAM_PREFIX;
        if (first == '{' || first == '[') return BodyShape.JSON_PREFIX;
        if (htmlStart(bytes, offset, limit, "<!doctype html") || htmlStart(bytes, offset, limit, "<html")
                || htmlStart(bytes, offset, limit, "<head") || htmlStart(bytes, offset, limit, "<body")) {
            return BodyShape.HTML_PREFIX;
        }
        return BodyShape.OTHER_PREFIX;
    }

    private static boolean htmlStart(byte[] bytes, int offset, int limit, String marker) {
        if (!matches(bytes, offset, limit, marker, true)) return false;
        int next = offset + marker.length();
        return next < limit && (bytes[next] == '>' || whitespace(bytes[next] & 255));
    }

    private static boolean matches(byte[] bytes, int offset, int limit, String marker, boolean ignoreCase) {
        if (marker.length() > limit - offset) return false;
        for (int i = 0; i < marker.length(); i++) {
            int value = bytes[offset + i] & 255;
            if (ignoreCase && value >= 'A' && value <= 'Z') value += 'a' - 'A';
            if (value != marker.charAt(i)) return false;
        }
        return true;
    }

    private static boolean whitespace(int value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n' || value == '\f';
    }

    private static boolean mimeToken(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9') || "!#$%&'*+-.^_`|~".indexOf(value) >= 0;
    }
}
