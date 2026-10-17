package com.plia.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import org.json.JSONObject;

/** Fixed synthetic notebook model only. Every backup stays in a byte array. */
final class MoveQaData {
    private MoveQaData() {}
    static NotebookStore.Notebook fresh() {
        NotebookStore.Notebook n = new NotebookStore.Notebook();
        NotebookStore.Page p = new NotebookStore.Page(); p.id = "move-qa-page"; p.title = "Synthetic Move QA"; p.mode = "think";
        p.goal = "Keep this synthetic goal"; p.prompt = "Keep this synthetic note"; p.problem = "x² + 3"; p.problemSource = "Fixed QA fixture";
        p.strokes.add(stroke(100, 240, 160, 300));
        p.strokes.add(stroke(170, 220, 178, 214, 190, 214, 194, 222, 172, 235, 195, 235));
        p.strokes.add(stroke(350, 600, 410, 660));
        String paired = "## To solve\n| Steps in math | Steps in words |\n| --- | --- |\n"
                + "| F'(x)=2^{3(x^4+x)-2}(4x^3+1)-2^{3x^4-2}(4x^3) | Keep each exponent with its base. |\n"
                + "| x^2+3 | The raised 2 belongs to x. |";
        p.replies.add(new NotebookStore.Reply("think", "Fixed synthetic input", paired, "chatgpt",
                new PaperAnchor(80, 190, 230, 340), new ReplyPlacement(600, 100, 300, true)));
        p.replies.add(new NotebookStore.Reply("think", "Second fixed input", "Stationary synthetic reply. Keep this whole block.", "chatgpt",
                new PaperAnchor(330, 570, 440, 700), new ReplyPlacement(650, 900, 300, true)));
        n.pages.add(p); n.activeId = p.id;
        NotebookStore.Page other = new NotebookStore.Page(); other.id = "unrelated-synthetic-page";
        other.title = "Unrelated synthetic page"; other.prompt = "This must remain byte-identical";
        other.strokes.add(stroke(44, 55, 66, 77)); n.pages.add(other);
        return n;
    }
    private static InkView.Stroke stroke(float... xy) {
        InkView.Stroke stroke = new InkView.Stroke(false, 3.5f);
        for (int i = 0; i < xy.length; i += 2) stroke.points.add(new InkView.Point(xy[i], xy[i + 1], .6f));
        return stroke;
    }
    static byte[] json(NotebookStore.Notebook notebook) throws Exception { return NotebookStore.encode(notebook); }
    static byte[] zip(NotebookStore.Notebook notebook) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); NotebookStore.writeBackup(notebook, out); return out.toByteArray();
    }
    static NotebookStore.Notebook fromZip(byte[] bytes) throws Exception { return NotebookStore.readBackup(new ByteArrayInputStream(bytes)); }
    static String hash(NotebookStore.Notebook notebook) throws Exception { return hash(json(notebook)); }
    static String hash(byte[] bytes) throws Exception {
        byte[] result = MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder text = new StringBuilder();
        for (byte b : result) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return text.toString();
    }
    static String protectedHash(NotebookStore.Notebook notebook) throws Exception {
        JSONObject root = new JSONObject(new String(json(notebook), StandardCharsets.UTF_8));
        JSONObject active = root.getJSONArray("pages").getJSONObject(0); active.remove("strokes"); active.remove("replies");
        // Includes all active-page typed metadata/history and the entire unrelated synthetic page.
        return hash(root.toString().getBytes(StandardCharsets.UTF_8));
    }
    static boolean translated(InkView.Stroke before, InkView.Stroke after, float dx, float dy) {
        if (before.erase != after.erase || before.width != after.width || before.points.size() != after.points.size()) return false;
        for (int i = 0; i < before.points.size(); i++) { InkView.Point a = before.points.get(i), b = after.points.get(i);
            if (Math.abs(a.x + dx - b.x) > .02f || Math.abs(a.y + dy - b.y) > .02f || a.pressure != b.pressure) return false; }
        return true;
    }
    static boolean sameReply(NotebookStore.Reply a, NotebookStore.Reply b) {
        return a.mode.equals(b.mode) && a.source.equals(b.source) && a.input.equals(b.input) && a.text.equals(b.text)
                && a.anchor.left == b.anchor.left && a.anchor.top == b.anchor.top && a.anchor.right == b.anchor.right && a.anchor.bottom == b.anchor.bottom;
    }
    static boolean at(NotebookStore.Reply reply, float x, float y, float width) {
        return reply.placement != null && Math.abs(reply.placement.left - x) < .02f && Math.abs(reply.placement.top - y) < .02f
                && Math.abs(reply.placement.width - width) < .02f;
    }
}
