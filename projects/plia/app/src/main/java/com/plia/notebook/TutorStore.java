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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Separate, bounded Tutor state. No notebook files, network or exercise engine access.
 * Call all instance I/O on one application-owned serial executor. Snapshot Data.copy()
 * on the UI thread before enqueueing saves. Notebook ZIP backups do not include Tutor.
 */
public final class TutorStore {
    public static final String FILE_NAME = "tutor-state.json";
    public static final int MAX_ATTEMPTS = 100, MAX_TOPICS = 64,
        MAX_TOTAL_INK_POINTS = 200000, MAX_TOTAL_STROKES = 12000,
        MAX_FILE_BYTES = 16 * 1024 * 1024, MAX_SNAPSHOT_CHARS = 32768,
        MAX_TYPED_ANSWER_CHARS = 4000, MAX_ID_CHARS = 120, MAX_ITEM_KEY_CHARS = 512,
        MAX_GOAL_TITLE_CHARS = 200, MAX_MATERIAL_NOTES_CHARS = 16000, MAX_HINT_COUNT = 10000;
    private static final int VERSION = 1, MAX_STROKE_POINTS = 8000;
    private final AtomicFile file;
    private boolean loaded, readFailed;

    public enum Outcome { UNCHECKED, CORRECT, INCORRECT, INVALID, SELF_CHECKED }

    public static final class Data {
        public String goalTitle = "", goalDate = "", lastTopicId = "", activeAttemptId = "", materialNotes = "";
        public long nextSeed;
        public ArrayList<Attempt> attempts = new ArrayList<>();
        public LinkedHashMap<String, TopicProgress> topics = new LinkedHashMap<>();
        public Attempt findAttempt(String id) {
            for (Attempt attempt : attempts) if (attempt.id.equals(id)) return attempt;
            return null;
        }
        public Data copy() {
            Data copy = new Data();
            copy.goalTitle = goalTitle; copy.goalDate = goalDate; copy.lastTopicId = lastTopicId;
            copy.activeAttemptId = activeAttemptId; copy.nextSeed = nextSeed; copy.materialNotes = materialNotes;
            for (Attempt attempt : attempts) copy.attempts.add(attempt.copy());
            for (Map.Entry<String, TopicProgress> entry : topics.entrySet())
                copy.topics.put(entry.getKey(), entry.getValue().copy());
            return copy;
        }
    }

    public static final class TopicProgress {
        public boolean lessonRead;
        public long lastOpenedAt;
        public TopicProgress copy() {
            TopicProgress copy = new TopicProgress();
            copy.lessonRead = lessonRead; copy.lastOpenedAt = lastOpenedAt; return copy;
        }
    }

    public static final class Attempt {
        public String id = UUID.randomUUID().toString(), topicId = "", templateId = "",
            itemKey = "", generatorVersion = "", itemSnapshotJson = "", typedAnswer = "",
            checkedAnswer = "";
        public long seed, createdAt, updatedAt, checkedAt;
        public List<InkView.Stroke> ink = new ArrayList<>();
        public int hintCount;
        public boolean solutionViewed, numericAssessment, submitted;
        public Outcome outcome = Outcome.UNCHECKED;

        public Attempt copy() {
            Attempt copy = new Attempt();
            copy.id = id; copy.topicId = topicId; copy.templateId = templateId; copy.itemKey = itemKey;
            copy.generatorVersion = generatorVersion; copy.itemSnapshotJson = itemSnapshotJson;
            copy.typedAnswer = typedAnswer; copy.checkedAnswer = checkedAnswer; copy.seed = seed;
            copy.createdAt = createdAt; copy.updatedAt = updatedAt; copy.checkedAt = checkedAt;
            ArrayList<InkView.Stroke> frozen = new ArrayList<>();
            for (InkView.Stroke stroke : ink) frozen.add(InkView.Stroke.immutableCopy(stroke));
            copy.ink = Collections.unmodifiableList(frozen);
            copy.hintCount = hintCount; copy.solutionViewed = solutionViewed;
            copy.numericAssessment = numericAssessment; copy.submitted = submitted; copy.outcome = outcome;
            return copy;
        }
        public void setTypedAnswer(String value, long now) {
            helperText(value, MAX_TYPED_ANSWER_CHARS); helperTime(now);
            if (value.equals(typedAnswer)) return;
            typedAnswer = value; outcome = Outcome.UNCHECKED; checkedAnswer = "";
            checkedAt = 0; numericAssessment = false; submitted = false; updatedAt = now;
        }
        public void recordNumericCheck(Outcome value, long now) {
            if (value != Outcome.CORRECT && value != Outcome.INCORRECT && value != Outcome.INVALID)
                throw new IllegalArgumentException("A numeric check needs CORRECT, INCORRECT or INVALID");
            helperText(typedAnswer, MAX_TYPED_ANSWER_CHARS); helperTime(now);
            outcome = value; checkedAnswer = typedAnswer; numericAssessment = true;
            submitted = true; checkedAt = now; updatedAt = now;
        }
        public void recordSelfCheck(long now) {
            helperText(typedAnswer, MAX_TYPED_ANSWER_CHARS); helperTime(now);
            outcome = Outcome.SELF_CHECKED; checkedAnswer = typedAnswer; numericAssessment = false;
            submitted = true; checkedAt = now; updatedAt = now;
        }
        public void recordHint(long now) {
            helperTime(now);
            if (hintCount < 0 || hintCount >= MAX_HINT_COUNT)
                throw new IllegalStateException("Tutor hint count limit reached");
            hintCount++; updatedAt = now;
        }
        public void recordSolutionView(long now) {
            helperTime(now); solutionViewed = true; updatedAt = now;
        }
    }

    /** Counts are evidence about final typed numeric answers, never handwriting or mastery. */
    public static final class TopicSummary {
        public final int submittedAttempts, checkedNumericAttempts, correctAttempts, incorrectAttempts,
            invalidAttempts, selfCheckedAttempts, independentCorrectItems, assistedCorrectItems;
        public final boolean lessonRead;
        public final long lastOpenedAt;
        public final String statusLabel;
        private TopicSummary(int submitted, int numeric, int correct, int incorrect, int invalid,
                int self, int independent, int assisted, boolean lesson, long opened) {
            submittedAttempts = submitted; checkedNumericAttempts = numeric; correctAttempts = correct;
            incorrectAttempts = incorrect; invalidAttempts = invalid; selfCheckedAttempts = self;
            independentCorrectItems = independent; assistedCorrectItems = assisted;
            lessonRead = lesson; lastOpenedAt = opened;
            statusLabel = independent >= 2 ? "Ready for review" : incorrect > 0 ? "Needs practice"
                : submitted > 0 ? "Practising" : lesson || opened > 0 ? "Learning" : "Not started";
        }
    }

    public static boolean hasPriorAssistance(Data data, String itemKey) {
        if (data == null || itemKey == null || itemKey.isEmpty()) return false;
        for (Attempt attempt : data.attempts)
            if (itemKey.equals(attempt.itemKey) && (attempt.hintCount > 0 || attempt.solutionViewed)) return true;
        return false;
    }

    public static TopicSummary summarize(Data data, String topicId) {
        int submitted = 0, numeric = 0, correct = 0, incorrect = 0, invalid = 0, self = 0;
        Set<String> ids = new HashSet<>(), independent = new HashSet<>(), assisted = new HashSet<>();
        for (Attempt attempt : data.attempts) {
            if (!topicId.equals(attempt.topicId) || !attempt.submitted || !ids.add(attempt.id)) continue;
            submitted++;
            // A caller bypassing setTypedAnswer cannot accidentally retain stale evidence.
            if (attempt.typedAnswer == null || !attempt.typedAnswer.equals(attempt.checkedAnswer)) continue;
            if (attempt.outcome == Outcome.SELF_CHECKED && !attempt.numericAssessment) { self++; continue; }
            if (!attempt.numericAssessment) continue;
            if (attempt.outcome != Outcome.CORRECT && attempt.outcome != Outcome.INCORRECT
                    && attempt.outcome != Outcome.INVALID) continue;
            numeric++;
            if (attempt.outcome == Outcome.CORRECT) {
                correct++;
                if (attempt.itemKey != null && !attempt.itemKey.isEmpty()) {
                    if (hasPriorAssistance(data, attempt.itemKey)) assisted.add(attempt.itemKey);
                    else independent.add(attempt.itemKey);
                }
            } else if (attempt.outcome == Outcome.INCORRECT) incorrect++;
            else invalid++;
        }
        TopicProgress progress = data.topics.get(topicId);
        return new TopicSummary(submitted, numeric, correct, incorrect, invalid, self,
            independent.size(), assisted.size(), progress != null && progress.lessonRead,
            progress == null ? 0 : progress.lastOpenedAt);
    }

    public TutorStore(Context context) { this(new File(context.getFilesDir(), FILE_NAME)); }
    /** The legacy goal keeps its exact existing file; other goals never share its state. */
    public TutorStore(Context context, String goalId) {
        this(TutorLibraryStore.goalStateFile(context.getFilesDir(), goalId));
    }
    // File constructor is package-private for offline atomic wiring tests.
    TutorStore(File path) { file = new AtomicFile(path); }

    public synchronized Data read() throws IOException {
        try (InputStream input = file.openRead()) {
            Data result = importJson(input); loaded = true; return result;
        } catch (FileNotFoundException absent) {
            File base = file.getBaseFile();
            if (!base.exists() && !new File(base + ".bak").exists() && !new File(base + ".new").exists()) {
                loaded = true; return new Data();
            }
            readFailed = true; throw absent;
        } catch (IOException | RuntimeException failure) {
            readFailed = true;
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Tutor state could not be read; the existing file was retained", failure);
        }
    }

    public synchronized void write(Data data) throws IOException {
        if (readFailed) throw new IOException("Tutor state is write-locked after a load error; export the original file for recovery");
        if (!loaded) read(); // A caller cannot overwrite an unread corrupt file.
        byte[] bytes = encode(data).getBytes(StandardCharsets.UTF_8);
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(bytes); output.getFD().sync();
            file.finishWrite(output);
            // Android AtomicFile logs some rename failures instead of throwing them.
            File base = file.getBaseFile();
            if (new File(base + ".new").exists() || new File(base + ".bak").exists()
                    || !base.isFile() || base.length() != bytes.length)
                throw invalid("Tutor save could not be confirmed; keep this session open and retry");
            output = null;
        } catch (IOException | RuntimeException failure) {
            if (output != null) file.failWrite(output);
            if (failure instanceof IOException) throw (IOException) failure;
            throw new IOException("Tutor state could not be saved; keep this session open and retry", failure);
        }
    }

    /** Raw recovery deliberately does not parse or impose the normal import size cap. */
    public synchronized void exportRaw(OutputStream output) throws IOException {
        InputStream recovery;
        try { recovery = file.openRead(); }
        catch (FileNotFoundException absent) {
            File base = file.getBaseFile(), fresh = new File(base + ".new");
            if (base.exists() || new File(base + ".bak").exists() || !fresh.isFile()) throw absent;
            recovery = new FileInputStream(fresh); // Interrupted first write: retain and export it verbatim.
        }
        try (InputStream input = recovery) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    public static void exportJson(Data data, OutputStream output) throws IOException {
        output.write(encode(data).getBytes(StandardCharsets.UTF_8));
    }

    /** Parses only. The caller confirms replacement and invokes write on a healthy store. */
    public static Data importJson(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) {
            if (count > MAX_FILE_BYTES - bytes.size()) throw invalid("Tutor JSON exceeds 16 MiB; nothing was imported");
            bytes.write(buffer, 0, count);
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            return decode(text);
        } catch (CharacterCodingException failure) { throw new IOException("Tutor JSON is not valid UTF-8", failure); }
    }

    public static String encode(Data data) throws IOException {
        validate(data);
        try {
            JSONArray attempts = new JSONArray();
            for (Attempt a : data.attempts) {
                JSONArray ink = new JSONArray();
                for (InkView.Stroke stroke : a.ink) ink.put(stroke.json());
                attempts.put(new JSONObject().put("id", a.id).put("topicId", a.topicId)
                    .put("templateId", a.templateId).put("itemKey", a.itemKey).put("seed", a.seed)
                    .put("generatorVersion", a.generatorVersion).put("itemSnapshotJson", a.itemSnapshotJson)
                    .put("typedAnswer", a.typedAnswer).put("ink", ink).put("hintCount", a.hintCount)
                    .put("solutionViewed", a.solutionViewed).put("outcome", a.outcome.name())
                    .put("numericAssessment", a.numericAssessment).put("checkedAnswer", a.checkedAnswer)
                    .put("submitted", a.submitted).put("createdAt", a.createdAt)
                    .put("updatedAt", a.updatedAt).put("checkedAt", a.checkedAt));
            }
            JSONObject topics = new JSONObject();
            for (Map.Entry<String, TopicProgress> entry : data.topics.entrySet())
                topics.put(entry.getKey(), new JSONObject().put("lessonRead", entry.getValue().lessonRead)
                    .put("lastOpenedAt", entry.getValue().lastOpenedAt));
            String text = new JSONObject().put("app", "Plia Tutor").put("version", VERSION)
                .put("goalTitle", data.goalTitle).put("goalDate", data.goalDate).put("materialNotes", data.materialNotes)
                .put("lastTopicId", data.lastTopicId).put("activeAttemptId", data.activeAttemptId)
                .put("nextSeed", data.nextSeed).put("topics", topics).put("attempts", attempts).toString();
            if (text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES)
                throw invalid("Tutor state exceeds 16 MiB; no attempts were removed");
            return text;
        } catch (JSONException failure) { throw new IOException("Tutor state could not be encoded", failure); }
    }

    public static Data decode(String text) throws IOException {
        requireText(text, MAX_FILE_BYTES, "Tutor JSON", false);
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw invalid("Tutor JSON exceeds 16 MiB");
        try {
            JSONTokener tokens = new JSONTokener(text); Object root = tokens.nextValue();
            if (!(root instanceof JSONObject) || tokens.nextClean() != 0) throw invalid("Invalid Tutor JSON document");
            JSONObject object = (JSONObject) root;
            if (!"Plia Tutor".equals(string(object, "app")) || whole(object, "version") != VERSION)
                throw invalid("Unsupported Tutor state format; the file was retained");
            Data data = new Data();
            data.goalTitle = string(object, "goalTitle"); data.goalDate = string(object, "goalDate");
            data.materialNotes = object.has("materialNotes") ? string(object, "materialNotes") : "";
            data.lastTopicId = string(object, "lastTopicId"); data.activeAttemptId = string(object, "activeAttemptId");
            data.nextSeed = whole(object, "nextSeed");
            JSONObject topics = object.getJSONObject("topics");
            if (topics.length() > MAX_TOPICS) throw invalid("Tutor topic limit exceeded");
            java.util.Iterator<String> keys = topics.keys();
            while (keys.hasNext()) {
                String key = keys.next(); JSONObject value = topics.getJSONObject(key);
                TopicProgress progress = new TopicProgress(); progress.lessonRead = bool(value, "lessonRead");
                progress.lastOpenedAt = whole(value, "lastOpenedAt"); data.topics.put(key, progress);
            }
            JSONArray attempts = object.getJSONArray("attempts");
            if (attempts.length() > MAX_ATTEMPTS) throw invalid("Tutor attempt limit is 100; no attempts were imported");
            int totalPoints = 0, totalStrokes = 0;
            for (int i = 0; i < attempts.length(); i++) {
                JSONObject value = attempts.getJSONObject(i); Attempt a = new Attempt();
                a.id = string(value, "id"); a.topicId = string(value, "topicId"); a.templateId = string(value, "templateId");
                a.itemKey = string(value, "itemKey"); a.generatorVersion = string(value, "generatorVersion");
                a.itemSnapshotJson = string(value, "itemSnapshotJson"); a.typedAnswer = string(value, "typedAnswer");
                a.checkedAnswer = string(value, "checkedAnswer"); a.seed = whole(value, "seed");
                long hints = whole(value, "hintCount");
                if (hints < 0 || hints > MAX_HINT_COUNT) throw invalid("Invalid hint count");
                a.hintCount = (int) hints; a.solutionViewed = bool(value, "solutionViewed");
                a.numericAssessment = bool(value, "numericAssessment"); a.submitted = bool(value, "submitted");
                try { a.outcome = Outcome.valueOf(string(value, "outcome")); }
                catch (IllegalArgumentException failure) { throw invalid("Unknown Tutor assessment outcome"); }
                a.createdAt = whole(value, "createdAt"); a.updatedAt = whole(value, "updatedAt");
                a.checkedAt = whole(value, "checkedAt");
                JSONArray ink = value.getJSONArray("ink");
                if (ink.length() > MAX_TOTAL_STROKES - totalStrokes) throw invalid("Tutor ink stroke limit exceeded");
                totalStrokes += ink.length(); ArrayList<InkView.Stroke> strokes = new ArrayList<>();
                for (int s = 0; s < ink.length(); s++) {
                    JSONObject raw = ink.getJSONObject(s); JSONArray points = raw.getJSONArray("points");
                    if (points.length() < 1 || points.length() > MAX_STROKE_POINTS
                            || points.length() > MAX_TOTAL_INK_POINTS - totalPoints) throw invalid("Tutor ink point limit exceeded");
                    totalPoints += points.length();
                    double width = number(raw.get("width"));
                    if (width < .5 || width > 40) throw invalid("Invalid Tutor ink width");
                    int color = 0xff171917; // Exact pre-color ink, not the current pen setting.
                    if (raw.has("color")) {
                        long encodedColor = whole(raw, "color");
                        if (encodedColor < Integer.MIN_VALUE || encodedColor > Integer.MAX_VALUE
                                || (((int)encodedColor) >>> 24) != 0xff) throw invalid("Invalid Tutor ink color");
                        color = (int)encodedColor;
                    }
                    InkView.Stroke stroke = new InkView.Stroke(bool(raw, "erase"), (float) width, color);
                    for (int p = 0; p < points.length(); p++) {
                        JSONArray point = points.getJSONArray(p);
                        if (point.length() != 3) throw invalid("Invalid Tutor ink point");
                        double x = number(point.get(0)), y = number(point.get(1)), pressure = number(point.get(2));
                        if (x < 0 || x > InkView.WIDTH || y < 0 || y > InkView.HEIGHT || pressure < 0 || pressure > 1)
                            throw invalid("Tutor ink is outside the page");
                        stroke.points.add(new InkView.Point((float)x, (float)y, (float)pressure));
                    }
                    strokes.add(InkView.Stroke.immutableCopy(stroke));
                }
                a.ink = Collections.unmodifiableList(strokes); data.attempts.add(a);
            }
            validate(data); return data;
        } catch (JSONException failure) { throw new IOException("Invalid Tutor JSON; nothing was imported", failure); }
    }

    private static void validate(Data data) throws IOException {
        if (data == null || data.attempts == null || data.topics == null) throw invalid("Missing Tutor data");
        requireText(data.goalTitle, MAX_GOAL_TITLE_CHARS, "Goal title", false);
        requireText(data.materialNotes, MAX_MATERIAL_NOTES_CHARS, "Material notes", false);
        requireText(data.goalDate, 10, "Goal date", false);
        if (!data.goalDate.isEmpty()) {
            if (!data.goalDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("Goal date must be YYYY-MM-DD or unset");
            try { if (LocalDate.parse(data.goalDate).getYear() < 1) throw invalid("Invalid goal date"); }
            catch (DateTimeParseException failure) { throw invalid("Invalid goal date"); }
        }
        requireText(data.lastTopicId, MAX_ID_CHARS, "Last topic", false);
        requireText(data.activeAttemptId, MAX_ID_CHARS, "Active attempt", false);
        if (data.nextSeed < 0) throw invalid("Invalid next exercise seed");
        if (data.attempts.size() > MAX_ATTEMPTS) throw invalid("Tutor attempt limit is 100; no attempts were removed");
        if (data.topics.size() > MAX_TOPICS) throw invalid("Tutor topic limit is 64");
        for (Map.Entry<String, TopicProgress> entry : data.topics.entrySet()) {
            requireText(entry.getKey(), MAX_ID_CHARS, "Topic ID", true);
            if (entry.getValue() == null || entry.getValue().lastOpenedAt < 0) throw invalid("Invalid topic progress");
        }
        Set<String> ids = new HashSet<>(); int pointCount = 0, strokeCount = 0;
        for (Attempt a : data.attempts) {
            if (a == null) throw invalid("Missing Tutor attempt");
            requireText(a.id, MAX_ID_CHARS, "Attempt ID", true);
            if (!ids.add(a.id)) throw invalid("Duplicate Tutor attempt ID");
            requireText(a.topicId, MAX_ID_CHARS, "Topic ID", true);
            requireText(a.templateId, MAX_ID_CHARS, "Template ID", true);
            requireText(a.itemKey, MAX_ITEM_KEY_CHARS, "Question identity", true);
            requireText(a.generatorVersion, MAX_ID_CHARS, "Generator version", true);
            requireText(a.itemSnapshotJson, MAX_SNAPSHOT_CHARS, "Exercise snapshot", true);
            requireText(a.typedAnswer, MAX_TYPED_ANSWER_CHARS, "Typed answer", false);
            requireText(a.checkedAnswer, MAX_TYPED_ANSWER_CHARS, "Checked answer", false);
            if (a.seed < 0 || a.createdAt < 0 || a.updatedAt < 0 || a.checkedAt < 0
                    || a.hintCount < 0 || a.hintCount > MAX_HINT_COUNT || a.outcome == null || a.ink == null)
                throw invalid("Invalid Tutor attempt metadata");
            if (a.outcome == Outcome.UNCHECKED) {
                if (a.submitted || a.numericAssessment || !a.checkedAnswer.isEmpty() || a.checkedAt != 0)
                    throw invalid("Unchecked attempt has an assessment");
            } else if (!a.submitted || !a.typedAnswer.equals(a.checkedAnswer)
                    || a.numericAssessment == (a.outcome == Outcome.SELF_CHECKED))
                throw invalid("Tutor assessment does not match the current typed answer");
            if (a.ink.size() > MAX_TOTAL_STROKES - strokeCount) throw invalid("Tutor ink stroke limit is 12000");
            strokeCount += a.ink.size();
            for (InkView.Stroke stroke : a.ink) {
                if (stroke == null || !Float.isFinite(stroke.width) || stroke.width < .5f || stroke.width > 40f
                        || (stroke.color >>> 24) != 0xff
                        || stroke.points == null || stroke.points.isEmpty() || stroke.points.size() > MAX_STROKE_POINTS)
                    throw invalid("Invalid Tutor ink stroke");
                if (stroke.points.size() > MAX_TOTAL_INK_POINTS - pointCount) throw invalid("Tutor ink point limit is 200000");
                pointCount += stroke.points.size();
                for (InkView.Point point : stroke.points)
                    if (point == null || !Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.pressure)
                            || point.x < 0 || point.x > InkView.WIDTH || point.y < 0 || point.y > InkView.HEIGHT
                            || point.pressure < 0 || point.pressure > 1) throw invalid("Invalid Tutor ink point");
            }
        }
        if (!data.activeAttemptId.isEmpty() && !ids.contains(data.activeAttemptId)) throw invalid("Active Tutor attempt is missing");
    }

    private static String string(JSONObject object, String key) throws JSONException, IOException {
        Object value = object.get(key);
        if (!(value instanceof String)) throw invalid("Expected text for " + key);
        return (String) value;
    }
    private static boolean bool(JSONObject object, String key) throws JSONException, IOException {
        Object value = object.get(key);
        if (!(value instanceof Boolean)) throw invalid("Expected true or false for " + key);
        return (Boolean) value;
    }
    private static long whole(JSONObject object, String key) throws JSONException, IOException {
        Object value = object.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) throw invalid("Expected an integer for " + key);
        return ((Number) value).longValue();
    }
    private static double number(Object value) throws IOException {
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue())) throw invalid("Invalid ink number");
        return ((Number) value).doubleValue();
    }
    private static void requireText(String value, int cap, String label, boolean required) throws IOException {
        if (value == null || value.length() > cap || (required && value.trim().isEmpty())) throw invalid(label + " is missing or exceeds its limit");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw invalid(label + " has invalid Unicode");
            } else if (Character.isLowSurrogate(c)) throw invalid(label + " has invalid Unicode");
        }
    }
    private static void helperText(String text, int cap) {
        try { requireText(text, cap, "Typed answer", false); }
        catch (IOException failure) { throw new IllegalArgumentException(failure.getMessage()); }
    }
    private static void helperTime(long now) { if (now < 0) throw new IllegalArgumentException("Negative timestamp"); }
    private static IOException invalid(String message) { return new IOException(message); }
}
