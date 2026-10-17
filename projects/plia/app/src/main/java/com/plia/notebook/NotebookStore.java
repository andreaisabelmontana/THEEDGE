package com.plia.notebook;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.FilterOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One serialized I/O queue also orders saves before an Activity recreation loads. */
public final class NotebookStore {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static final int MAX_PAGES = 100, MAX_FILE_BYTES = 64 * 1024 * 1024;
    private final AtomicFile file;
    public static final class Reply {
        public final String mode, input, text, source;
        public final PaperAnchor anchor;
        public final ReplyPlacement placement;
        public Reply(String mode, String input, String text, String source) { this(mode, input, text, source, null); }
        public Reply(String mode, String input, String text, String source, PaperAnchor anchor) { this(mode, input, text, source, anchor, null); }
        public Reply(String mode, String input, String text, String source, PaperAnchor anchor, ReplyPlacement placement) { this.mode = mode; this.input = input; this.text = text; this.source = source; this.anchor = anchor; this.placement = placement; }
        public Reply withPlacement(ReplyPlacement value) { return new Reply(mode, input, text, source, anchor, value); }
        JSONObject json() throws JSONException {
            JSONObject value = new JSONObject().put("mode", mode).put("input", input).put("text", text).put("source", source);
            if (anchor != null) value.put("anchor", new JSONObject().put("left", anchor.left).put("top", anchor.top).put("right", anchor.right).put("bottom", anchor.bottom));
            if (placement != null) {
                JSONObject position = new JSONObject().put("left", placement.left).put("top", placement.top).put("width", placement.width);
                value.put("placement", position);
                if (placement.userPositioned) value.put("userPositioned", true);
            }
            return value;
        }
    }
    public static final class Page {
        public String id = UUID.randomUUID().toString(), title = "Untitled page", mode = "tutor", goal = "", prompt = "", problem = "", problemSource = "";
        public ArrayList<InkView.Stroke> strokes = new ArrayList<>();
        public ArrayList<Reply> replies = new ArrayList<>();
        public ArrayList<ArrayList<Reply>> replyHistory = new ArrayList<>(), replyRedo = new ArrayList<>();
        public Page copy() { Page p = new Page(); p.id = id; p.title = title; p.mode = mode; p.goal = goal; p.prompt = prompt; p.problem = problem; p.problemSource = problemSource; p.strokes = new ArrayList<>(strokes); p.replies = copyReplies(replies); p.replyHistory = copyReplyBatches(replyHistory); p.replyRedo = copyReplyBatches(replyRedo); return p; }
        JSONObject json() throws JSONException {
            if (!ReplyRefresh.validPage(this)) throw new JSONException("Invalid or oversized reply history");
            JSONArray ink = new JSONArray(), responses = new JSONArray();
            for (InkView.Stroke stroke : strokes) ink.put(stroke.json()); for (Reply reply : replies) responses.put(reply.json());
            JSONObject value = new JSONObject().put("id", id).put("title", title).put("mode", mode).put("goal", goal).put("prompt", prompt).put("problem", problem).put("problemSource", problemSource).put("strokes", ink).put("replies", responses);
            if (!replyHistory.isEmpty()) value.put("replyHistory", replyBatchesJson(replyHistory));
            if (!replyRedo.isEmpty()) value.put("replyRedo", replyBatchesJson(replyRedo));
            return value;
        }
    }
    public static final class Notebook {
        public String activeId;
        public ArrayList<Page> pages = new ArrayList<>();
        public Page active() { for (Page page : pages) if (page.id.equals(activeId)) return page; return pages.get(0); }
        public Page find(String id) { for (Page page : pages) if (page.id.equals(id)) return page; return null; }
        public Notebook copy() { Notebook n = new Notebook(); n.activeId = activeId; for (Page p : pages) n.pages.add(p.copy()); return n; }
        public static Notebook fresh() { Notebook n = new Notebook(); Page p = new Page(); p.title = "First principles"; p.goal = "Understand derivatives, one step at a time"; p.problem = "Differentiate f(x) = x² + 3x. Show your steps and explain what f′(2) tells you."; p.problemSource = "Starter"; n.pages.add(p); n.activeId = p.id; return n; }
    }
    public NotebookStore(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "notebook.zip")); }
    public Notebook read() throws IOException, JSONException {
        try (InputStream input = file.openRead()) { return readBackup(input); }
        catch (FileNotFoundException absent) { if (file.getBaseFile().exists() || new File(file.getBaseFile().getPath() + ".bak").exists()) throw absent; return Notebook.fresh(); }
    }
    public void exportRaw(OutputStream output) throws IOException { try (InputStream input = file.openRead()) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); } }
    private static byte[] limited(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) { if (output.size() + count > maximum) throw new IOException("A backup page exceeds the 64 MB safety limit"); output.write(buffer, 0, count); }
        return output.toByteArray();
    }
    public static byte[] encode(Notebook notebook) throws JSONException, IOException {
        JSONArray pages = new JSONArray(); for (Page page : notebook.pages) pages.put(page.json());
        byte[] bytes = new JSONObject().put("app", "Plia Android").put("version", 1).put("activeId", notebook.activeId).put("pages", pages).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("This JSON document exceeds the 64 MB safety limit");
        return bytes;
    }
    public void write(Notebook notebook) throws IOException, JSONException {
        FileOutputStream output = null;
        try { output = file.startWrite(); writeBackup(notebook, output); file.finishWrite(output); }
        catch (IOException | JSONException | RuntimeException | OutOfMemoryError error) { if (output != null) file.failWrite(output); throw error; }
    }
    /** Stream bounded page JSON documents; no aggregate notebook byte-array limit. */
    public static void writeBackup(Notebook notebook, OutputStream output) throws IOException, JSONException {
        // Closing ZIP releases its compressor without closing AtomicFile's stream.
        try (ZipOutputStream zip = new ZipOutputStream(new FilterOutputStream(output) { @Override public void close() throws IOException { flush(); } })) {
        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(new JSONObject().put("app", "Plia Android backup").put("version", 1).put("activeId", notebook.activeId).put("pages", notebook.pages.size()).toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        for (int i = 0; i < notebook.pages.size(); i++) { Notebook one = new Notebook(); one.pages.add(notebook.pages.get(i)); one.activeId = one.pages.get(0).id; zip.putNextEntry(new ZipEntry(String.format(java.util.Locale.ROOT, "page-%03d.json", i + 1))); zip.write(encode(one)); zip.closeEntry(); }
        zip.finish(); zip.flush();
        }
    }
    public static Notebook readBackup(InputStream source) throws IOException, JSONException {
        BufferedInputStream input = new BufferedInputStream(source); input.mark(4); int first = input.read(), second = input.read(); input.reset();
        if (first != 'P' || second != 'K') return decode(limited(input, MAX_FILE_BYTES));
        try (ZipInputStream zip = new ZipInputStream(input)) { ZipEntry entry = zip.getNextEntry();
        if (entry == null || !entry.getName().equals("manifest.json")) throw new IOException("Invalid Plia backup archive");
        JSONObject manifest = new JSONObject(new String(limited(zip, 4096), StandardCharsets.UTF_8)); zip.closeEntry();
        int count = manifest.getInt("pages");
        if (!manifest.getString("app").equals("Plia Android backup") || manifest.getInt("version") != 1 || count < 1 || count > MAX_PAGES) throw new IOException("Unsupported Plia backup archive");
        Notebook result = new Notebook(); HashSet<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) { entry = zip.getNextEntry(); if (entry == null || !entry.getName().equals(String.format(java.util.Locale.ROOT, "page-%03d.json", i + 1))) throw new IOException("Missing backup page"); Notebook one = decode(limited(zip, MAX_FILE_BYTES)); zip.closeEntry(); if (one.pages.size() != 1 || !ids.add(one.pages.get(0).id)) throw new IOException("Invalid or duplicate backup page"); result.pages.add(one.pages.get(0)); }
        if (zip.getNextEntry() != null) throw new IOException("Unexpected extra backup entries");
        result.activeId = manifest.optString("activeId"); if (result.find(result.activeId) == null) result.activeId = result.pages.get(0).id; return result;
        }
    }
    public static Notebook decode(byte[] bytes) throws JSONException {
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (root.getInt("version") != 1 || !"Plia Android".equals(root.getString("app"))) throw new JSONException("Unsupported notebook backup");
        JSONArray pages = root.getJSONArray("pages");
        if (pages.length() < 1 || pages.length() > MAX_PAGES) throw new JSONException("Invalid page count");
        Notebook n = new Notebook();
        for (int i = 0; i < pages.length(); i++) {
            JSONObject value = pages.getJSONObject(i); Page p = new Page();
            p.id = text(value, "id", 120); p.title = text(value, "title", 100); p.mode = text(value, "mode", 10);
            if (p.id.isEmpty() || n.find(p.id) != null || !(p.mode.equals("tutor") || p.mode.equals("think"))) throw new JSONException("Invalid page identity");
            p.goal = text(value, "goal", 1500); p.prompt = text(value, "prompt", 4000); p.problem = text(value, "problem", 4000); p.problemSource = text(value, "problemSource", 40);
            JSONArray ink = value.getJSONArray("strokes"); int points = 0;
            if (ink.length() > 12000) throw new JSONException("Too many ink strokes");
            for (int j = 0; j < ink.length(); j++) { InkView.Stroke stroke = InkView.Stroke.fromJson(ink.getJSONObject(j)); points += stroke.points.size(); if (points > InkView.MAX_POINTS) throw new JSONException("Too many ink points"); p.strokes.add(stroke); }
            p.replies = replies(value.getJSONArray("replies"));
            if (value.has("replyHistory")) p.replyHistory = replyBatches(value.getJSONArray("replyHistory"));
            if (value.has("replyRedo")) p.replyRedo = replyBatches(value.getJSONArray("replyRedo"));
            if (!ReplyRefresh.validPage(p)) throw new JSONException("Invalid or oversized reply history");
            n.pages.add(p);
        }
        n.activeId = text(root, "activeId", 120); if (n.find(n.activeId) == null) n.activeId = n.pages.get(0).id; return n;
    }
    private static String text(JSONObject value, String key, int limit) throws JSONException { Object raw = value.get(key); if (!(raw instanceof String) || ((String) raw).length() > limit) throw new JSONException("Invalid " + key); return (String) raw; }
    static ArrayList<Reply> copyReplies(java.util.List<Reply> values) {
        ArrayList<Reply> result = new ArrayList<>();
        for (Reply reply : values) result.add(new Reply(reply.mode, reply.input, reply.text, reply.source, reply.anchor, reply.placement));
        return result;
    }
    static ArrayList<ArrayList<Reply>> copyReplyBatches(java.util.List<? extends java.util.List<Reply>> values) {
        ArrayList<ArrayList<Reply>> result = new ArrayList<>();
        for (java.util.List<Reply> group : values) result.add(copyReplies(group));
        return result;
    }
    private static JSONArray replyBatchesJson(java.util.List<? extends java.util.List<Reply>> values) throws JSONException {
        JSONArray result = new JSONArray();
        for (java.util.List<Reply> group : values) { JSONArray batch = new JSONArray(); for (Reply reply : group) batch.put(reply.json()); result.put(batch); }
        return result;
    }
    private static ArrayList<Reply> replies(JSONArray values) throws JSONException {
        if (values.length() > ReplyRefresh.MAX_VISIBLE_REPLIES) throw new JSONException("Too many replies");
        ArrayList<Reply> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject r = values.getJSONObject(i);
            Object marker = r.has("userPositioned") ? r.get("userPositioned") : Boolean.FALSE;
            if (!(marker instanceof Boolean)) throw new JSONException("Invalid reply position marker");
            ReplyPlacement fixed = r.has("placement") ? placement(r.getJSONObject("placement")) : null;
            if ((Boolean)marker) {
                if (fixed == null) throw new JSONException("User position requires a placement");
                fixed = new ReplyPlacement(fixed.left, fixed.top, fixed.width, true);
            }
            Reply reply = new Reply(text(r, "mode", 10), text(r, "input", 4000), text(r, "text", 16000), text(r, "source", 20),
                    r.has("anchor") ? anchor(r.getJSONObject("anchor")) : null,
                    fixed);
            if (!ReplyRefresh.validReply(reply)) throw new JSONException("Invalid reply");
            result.add(reply);
        }
        return result;
    }
    private static ArrayList<ArrayList<Reply>> replyBatches(JSONArray values) throws JSONException {
        if (values.length() > ReplyRefresh.MAX_BATCHES) throw new JSONException("Too many reply history groups");
        ArrayList<ArrayList<Reply>> result = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < values.length(); i++) {
            ArrayList<Reply> group = replies(values.getJSONArray(i)); count += group.size();
            if (count > ReplyRefresh.MAX_RETAINED_REPLIES) throw new JSONException("Too many retained replies");
            result.add(group);
        }
        return result;
    }
    private static PaperAnchor anchor(JSONObject value) throws JSONException {
        if (value.length() != 4) throw new JSONException("Invalid reply anchor");
        try {
            return new PaperAnchor(coordinate(value, "left", 1000), coordinate(value, "top", 1600),
                    coordinate(value, "right", 1000), coordinate(value, "bottom", 1600));
        } catch (IllegalArgumentException invalid) { throw new JSONException("Invalid reply anchor"); }
    }
    private static float coordinate(JSONObject value, String key, int maximum) throws JSONException {
        Object raw = value.get(key);
        if (!(raw instanceof Number)) throw new JSONException("Invalid reply anchor");
        double number = ((Number)raw).doubleValue();
        if (!Double.isFinite(number) || number < 0 || number > maximum) throw new JSONException("Invalid reply anchor");
        return (float)number;
    }
    private static ReplyPlacement placement(JSONObject value) throws JSONException {
        if (value.length() != 3) throw new JSONException("Invalid reply placement");
        try {
            Object l = value.get("left"), t = value.get("top"), w = value.get("width");
            if (!(l instanceof Number) || !(t instanceof Number) || !(w instanceof Number)) throw new IllegalArgumentException();
            double left = ((Number) l).doubleValue(), top = ((Number) t).doubleValue(), width = ((Number) w).doubleValue();
            if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(width)
                    || left < 0 || top < 0 || top > ReplyPlacement.MAX_Y || width <= 0 || left + width > 1000) throw new IllegalArgumentException();
            return new ReplyPlacement((float) left, (float) top, (float) width);
        } catch (IllegalArgumentException invalid) { throw new JSONException("Invalid reply placement"); }
    }
}
