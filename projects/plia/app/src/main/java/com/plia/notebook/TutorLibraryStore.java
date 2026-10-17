package com.plia.notebook;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Small goal index, separate from every goal's exercise/ink state. All I/O belongs off the UI thread. */
public final class TutorLibraryStore {
    /** Both Activities share this queue, including legacy title migration and scoped goal state. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static final String LEGACY_GOAL_ID = "ieu-calculus", CALCULUS_CURRICULUM = "ieu-calculus";
    public static final String FILE_NAME = "tutor-library.json";
    public static final int MAX_GOALS = 64, MAX_TITLE_CHARS = 200;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    // Different Activities may hold different store objects. Each mutation rereads under this lock.
    private static final Object LOCK = new Object();
    private final File directory;
    private final AtomicFile file;
    private boolean readFailed;

    public static final class Goal {
        public final String id, title, curriculumId;
        /** Zero means the legacy creation date was never recorded. */
        public final long createdAt;
        Goal(String id, String title, long createdAt, String curriculumId) {
            this.id = id; this.title = title; this.createdAt = createdAt;
            this.curriculumId = curriculumId;
        }
    }
    public static final class Data {
        public final List<Goal> goals;
        Data(List<Goal> goals) { this.goals = Collections.unmodifiableList(new ArrayList<>(goals)); }
        public Goal find(String id) {
            for (Goal goal : goals) if (goal.id.equals(id)) return goal;
            return null;
        }
    }
    public TutorLibraryStore(Context context) { this(context.getFilesDir()); }
    TutorLibraryStore(File directory) {
        this.directory = directory; file = new AtomicFile(new File(directory, FILE_NAME));
    }
    public static boolean validGoalId(String id) {
        return LEGACY_GOAL_ID.equals(id) || id != null && id.matches("goal-[0-9a-f]{32}");
    }
    static File goalStateFile(File directory, String id) {
        if (!validGoalId(id)) throw new IllegalArgumentException("Invalid Tutor goal identifier");
        return LEGACY_GOAL_ID.equals(id) ? new File(directory, TutorStore.FILE_NAME)
            : new File(new File(directory, "tutor-goals"), id + ".json");
    }
    public Data read() throws IOException { synchronized (LOCK) { return readLocked(); } }
    private Data readLocked() throws IOException {
        try (InputStream input = file.openRead()) {
            return decode(readBytes(input));
        } catch (FileNotFoundException absent) {
            File base = file.getBaseFile();
            if (!base.exists() && !new File(base + ".new").exists() && !new File(base + ".bak").exists())
                return initialLibrary();
            readFailed = true; throw absent;
        } catch (IOException | RuntimeException failure) {
            readFailed = true;
            throw new IOException("Your goal library could not be read; the original file is preserved", failure);
        }
    }
    private Data initialLibrary() {
        String title = "Calculus practice";
        // Only migrate an already valid legacy title. Never rewrite or reset the legacy state.
        try {
            String saved = new TutorStore(new File(directory, TutorStore.FILE_NAME)).read().goalTitle.trim();
            if (!saved.isEmpty()) title = saved;
        } catch (IOException ignored) { /* The goal still opens Tutor's existing recovery screen. */ }
        return new Data(Collections.singletonList(new Goal(LEGACY_GOAL_ID, title, 0, CALCULUS_CURRICULUM)));
    }
    public Goal createGoal(String title, long now) throws IOException {
        title = normalizedTitle(title);
        if (now <= 0) throw new IOException("Invalid goal creation date");
        synchronized (LOCK) {
            ensureWritable(); Data current = readLocked();
            if (current.goals.size() >= MAX_GOALS) throw new IOException("Your goal library has reached its 64-goal limit");
            Goal goal;
            do {
                goal = new Goal("goal-" + UUID.randomUUID().toString().replace("-", ""), title, now, "");
            } while (current.find(goal.id) != null || stateExists(goal.id));
            ArrayList<Goal> goals = new ArrayList<>(current.goals); goals.add(goal);
            // One transaction, no separate state file to orphan if creating the index fails.
            writeLocked(new Data(goals)); return goal;
        }
    }
    public Goal updateGoal(String id, String title) throws IOException {
        title = normalizedTitle(title);
        synchronized (LOCK) {
            ensureWritable(); Data current = readLocked(); Goal old = current.find(id);
            if (old == null) throw new IOException("This goal is no longer in the library");
            Goal updated = new Goal(old.id, title, old.createdAt, old.curriculumId);
            ArrayList<Goal> goals = new ArrayList<>(current.goals);
            goals.set(goals.indexOf(old), updated); writeLocked(new Data(goals)); return updated;
        }
    }
    private boolean stateExists(String id) {
        File path = goalStateFile(directory, id);
        return path.exists() || new File(path + ".new").exists() || new File(path + ".bak").exists();
    }
    private void ensureWritable() throws IOException {
        if (readFailed) throw new IOException("The library is write-locked after a load error; export the original file for recovery");
    }
    private void writeLocked(Data data) throws IOException {
        ensureWritable(); byte[] bytes = encode(data).getBytes(StandardCharsets.UTF_8);
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(bytes); output.getFD().sync(); file.finishWrite(output);
            File base = file.getBaseFile();
            if (new File(base + ".new").exists() || new File(base + ".bak").exists()
                    || !base.isFile() || base.length() != bytes.length)
                throw new IOException("Goal save could not be confirmed; keep this screen open and retry");
            output = null;
        } catch (IOException | RuntimeException failure) {
            if (output != null) file.failWrite(output);
            throw new IOException("Goal save could not be confirmed; the previous library is retained", failure);
        }
    }
    public void exportRaw(OutputStream output) throws IOException {
        synchronized (LOCK) {
            InputStream original;
            try { original = file.openRead(); }
            catch (FileNotFoundException absent) {
                File base = file.getBaseFile(), fresh = new File(base + ".new");
                if (base.exists() || new File(base + ".bak").exists() || !fresh.isFile()) throw absent;
                original = new FileInputStream(fresh);
            }
            try (InputStream input = original) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
        }
    }
    static String encode(Data data) throws IOException {
        validate(data);
        try {
            JSONArray goals = new JSONArray();
            for (Goal goal : data.goals) goals.put(new JSONObject().put("id", goal.id).put("title", goal.title)
                .put("createdAt", goal.createdAt).put("curriculumId", goal.curriculumId));
            String text = new JSONObject().put("app", "Plia Tutor library").put("version", 1).put("goals", goals).toString();
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Goal library is too large");
            return text;
        } catch (JSONException failure) { throw new IOException("Goal library could not be encoded", failure); }
    }
    static Data decode(String text) throws IOException {
        if (text == null || text.length() > MAX_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IOException("Goal library is too large");
        try {
            JSONTokener tokens = new JSONTokener(text); Object parsed = tokens.nextValue();
            if (!(parsed instanceof JSONObject) || tokens.nextClean() != 0) throw new IOException("Invalid goal library");
            JSONObject root = (JSONObject)parsed;
            if (!"Plia Tutor library".equals(string(root, "app")) || whole(root, "version") != 1)
                throw new IOException("Unsupported goal library");
            JSONArray array = root.getJSONArray("goals");
            if (array.length() > MAX_GOALS) throw new IOException("Goal limit exceeded");
            ArrayList<Goal> goals = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject raw = array.getJSONObject(i);
                goals.add(new Goal(string(raw, "id"), string(raw, "title"), whole(raw, "createdAt"),
                    string(raw, "curriculumId")));
            }
            Data data = new Data(goals); validate(data); return data;
        } catch (JSONException failure) { throw new IOException("Invalid goal library", failure); }
    }
    private static void validate(Data data) throws IOException {
        if (data == null || data.goals.isEmpty() || data.goals.size() > MAX_GOALS) throw new IOException("Invalid goal collection");
        HashSet<String> ids = new HashSet<>(); int legacyCount = 0;
        for (Goal goal : data.goals) {
            if (goal == null || !validGoalId(goal.id) || !ids.add(goal.id)) throw new IOException("Invalid or duplicate goal identifier");
            validateText(goal.title, MAX_TITLE_CHARS, "Goal title", true);
            if (goal.createdAt < 0) throw new IOException("Invalid goal date");
            if (LEGACY_GOAL_ID.equals(goal.id)) {
                legacyCount++; if (!CALCULUS_CURRICULUM.equals(goal.curriculumId)) throw new IOException("Invalid Calculus curriculum");
            } else if (!"".equals(goal.curriculumId) || goal.createdAt == 0) throw new IOException("Invalid personal goal metadata");
        }
        if (legacyCount != 1) throw new IOException("The existing Calculus goal is missing");
    }
    private static String normalizedTitle(String title) throws IOException {
        if (title != null) title = title.trim();
        validateText(title, MAX_TITLE_CHARS, "Goal title", true); return title;
    }
    private static void validateText(String text, int limit, String label, boolean required) throws IOException {
        if (text == null || text.length() > limit || required && text.trim().isEmpty()) throw new IOException(label + " is missing or too long");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IOException("Invalid Unicode in " + label);
            } else if (Character.isLowSurrogate(c) || c == 0) throw new IOException("Invalid Unicode in " + label);
        }
    }
    private static String readBytes(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_BYTES - bytes.size()) throw new IOException("Goal library is too large");
            bytes.write(buffer, 0, count);
        }
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
    }
    private static String string(JSONObject object, String key) throws JSONException, IOException {
        Object value = object.get(key); if (!(value instanceof String)) throw new IOException("Invalid library text"); return (String)value;
    }
    private static long whole(JSONObject object, String key) throws JSONException, IOException {
        Object value = object.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw new IOException("Invalid library integer");
        return ((Number)value).longValue();
    }
}
