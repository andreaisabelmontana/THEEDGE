package com.plia.notebook;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Packaged math only. No remote requests, file access, cookies, or JavaScript bridge. */
final class LocalMathView extends WebView {
    private static final String ORIGIN = "https://plia.local/";
    private final String document;
    private final Runnable resized;
    private final Runnable failed;
    private int contentHeight, generation;
    private boolean pageReady, disposed;
    private int lastWidth;
    private float touchX, touchY;

    LocalMathView(Context context, String document, Runnable resized, Runnable failed) {
        super(context);
        this.document = withExplanationFont(context, document); this.resized = resized; this.failed = failed;
        contentHeight = Math.round(240 * getResources().getDisplayMetrics().density);
        setBackgroundColor(Color.WHITE);
        setVerticalScrollBarEnabled(false); setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    touchX = event.getX(); touchY = event.getY(); break;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - touchX), dy = Math.abs(event.getY() - touchY);
                    if (dx > 8 * getResources().getDisplayMetrics().density && dx > dy)
                        requestDisallowInterceptTouchEvent(true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL: requestDisallowInterceptTouchEvent(false); break;
            }
            return false;
        });
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBlockNetworkLoads(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDomStorageEnabled(false); settings.setDatabaseEnabled(false);
        settings.setSaveFormData(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false); settings.setSupportZoom(false);
        settings.setTextZoom(100); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl(); String path = uri.getPath();
                if (!"https".equals(uri.getScheme()) || !"plia.local".equals(uri.getHost())
                        || path == null || !path.matches("/math/[A-Za-z0-9_./-]+") || path.contains("..")) return blocked();
                String mime = path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css"
                        : path.endsWith(".woff2") ? "font/woff2" : path.endsWith(".woff") ? "font/woff"
                        : path.endsWith(".ttf") ? "font/ttf" : "text/html";
                try { return new WebResourceResponse(mime, mime.startsWith("font/") ? null : "UTF-8",
                        getContext().getAssets().open(path.substring(1))); }
                catch (IOException missing) { return blocked(); }
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!disposed && (ORIGIN + "math/index.html").equals(url)) { pageReady = true; render(); }
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                post(() -> { if (!disposed && failed != null) failed.run(); });
                return true;
            }
        });
        loadUrl(ORIGIN + "math/index.html");
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", java.util.Collections.emptyMap(),
                new ByteArrayInputStream(new byte[0]));
    }

    /** Direct Tutor documents use the same local preference as SolutionView replies. */
    private static String withExplanationFont(Context context, String document) {
        try {
            JSONObject value = new JSONObject(document);
            Object requested = value.opt("explanationFont");
            String option = value.has("explanationFont") ? requested instanceof String ? (String)requested : "" : null;
            return value.put("explanationFont", ExplanationFont.documentOption(context, option)).toString();
        }
        catch (org.json.JSONException invalidDocument) { return document; }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), contentHeight);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && width != lastWidth) { generation++; lastWidth = width; post(this::render); }
    }

    private void render() {
        if (disposed || !pageReady || getWidth() <= 0) return;
        final int ticket = ++generation;
        final int width = getWidth();
        evaluateJavascript("window.renderSolution(" + document.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + ")", ignored -> pollHeight(ticket, width, 0));
    }

    private void pollHeight(int ticket, int expectedWidth, int attempt) {
        if (disposed || ticket != generation || getWidth() != expectedWidth) return;
        evaluateJavascript("JSON.stringify({ready:window.solutionReady===true,height:window.measureSolution?window.measureSolution():0,width:window.innerWidth})", value -> {
            if (disposed || ticket != generation || getWidth() != expectedWidth) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                JSONObject metrics = new JSONObject(decoded instanceof String ? (String)decoded : "{}");
                if (!metrics.optBoolean("ready") && attempt < 60) {
                    postDelayed(() -> pollHeight(ticket, expectedWidth, attempt + 1), 100); return;
                }
                if (!metrics.optBoolean("ready")) { if (failed != null) failed.run(); return; }
                double cssHeight = metrics.optDouble("height"), cssWidth = metrics.optDouble("width");
                if (!Double.isFinite(cssHeight) || !Double.isFinite(cssWidth) || cssHeight <= 0 || cssWidth <= 0) {
                    if (failed != null) failed.run(); return;
                }
                int pixels = (int)Math.ceil(cssHeight * getWidth() / cssWidth);
                int maximum = Math.round(24000 * getResources().getDisplayMetrics().density);
                if (pixels > maximum) { if (failed != null) failed.run(); return; }
                pixels = Math.max(1, pixels);
                if (Math.abs(contentHeight - pixels) > 1) {
                    contentHeight = pixels; requestLayout();
                    if (resized != null) resized.run();
                }
            } catch (Exception invalidMetrics) { if (failed != null) failed.run(); }
        });
    }

    void dispose() {
        if (disposed) return;
        disposed = true; generation++;
        try { stopLoading(); } catch (RuntimeException rendererGone) { /* Still release the dead view. */ }
        destroy();
    }
}
