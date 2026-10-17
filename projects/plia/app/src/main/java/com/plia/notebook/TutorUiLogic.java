package com.plia.notebook;

import java.util.Collection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small deterministic UI policy. No state, storage, network or handwriting assessment. */
public final class TutorUiLogic {
    private TutorUiLogic() {}
    public static boolean hasCurriculum(String curriculumId) { return "ieu-calculus".equals(curriculumId); }
    /** Typing is also useful for symbolic work even when it cannot be graded automatically. */
    public static boolean typedEntryEnabled(TutorPracticeEngine.AnswerKind kind) { return kind != null; }
    public static String answerHint(TutorPracticeEngine.AnswerKind kind) {
        return kind == TutorPracticeEngine.AnswerKind.NUMERIC
                ? "Type a fraction or decimal, e.g. 3/4"
                : "Type your answer and reasoning; compare with the solution";
    }
    public static String parentRoute(String route) {
        if ("practice".equals(route)) return "lesson";
        if ("lesson".equals(route)) return "lessons";
        if ("goal".equals(route)) return "library";
        return "goal";
    }
    public static String restoreRoute(String route, boolean curriculum, boolean attemptExists) {
        if ("materials".equals(route)) return "materials";
        if (!curriculum) return "goal";
        if ("practice".equals(route)) return attemptExists ? "practice" : "practice-list";
        if ("lesson".equals(route) || "lessons".equals(route) || "practice-list".equals(route)) return route;
        return "goal";
    }
    public static String attemptStatus(TutorStore.Attempt attempt) {
        if (!attempt.submitted) return "In progress";
        switch (attempt.outcome) {
            case CORRECT: return "Typed result matched";
            case INCORRECT: return "Typed result did not match";
            case INVALID: return "Number could not be read";
            case SELF_CHECKED: return "Self-check recorded";
            default: return "In progress";
        }
    }
    public static boolean canReveal(long savedVersion, long requiredVersion, long currentRoute, long requiredRoute,
            String currentAttempt, String requiredAttempt, boolean finishing) {
        return savedVersion >= requiredVersion && currentRoute == requiredRoute && requiredAttempt != null
                && requiredAttempt.equals(currentAttempt) && !finishing;
    }
    public static boolean importFitsGoal(TutorStore.Data imported, boolean hasCurriculum) {
        return hasCurriculum || imported.attempts.isEmpty() && imported.topics.isEmpty() && imported.lastTopicId.isEmpty();
    }
    public static TutorPracticeEngine.PracticeItem freshItem(String family, Collection<String> used) {
        String example = TutorPracticeEngine.canonical(family).itemKey;
        for (long seed = 1; seed <= 256; seed++) {
            TutorPracticeEngine.PracticeItem item = TutorPracticeEngine.generate(family, seed);
            if (!item.itemKey.equals(example) && !used.contains(item.itemKey)) return item;
        }
        return null;
    }
    public static String numericFeedback(TutorPracticeEngine.CheckResult result) {
        switch (result) {
            case CORRECT: return "Typed result matches. Handwriting and method have not been assessed.";
            case INCORRECT: return "Typed result does not match. Handwriting and method have not been assessed.";
            case INVALID_INPUT: return "Enter a finite number or a fraction such as 3/4. Handwriting and method have not been assessed.";
            default: return "This exercise needs a self-check against the worked solution. It is not automatically marked correct.";
        }
    }
    public static String promptDocumentJson(TutorPracticeEngine.PracticeItem item) {
        try {
            return new JSONObject().put("before", "").put("after", "").put("structured", true).put("showHeading", false).put("explanationFont", "default")
                    .put("rows", new JSONArray().put(new JSONObject().put("math", item.promptLatex).put("words", item.promptWords))).toString();
        } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
    }
    /** Validate the complete imported content/assessment bundle before asking to replace local data. */
    public static void validateContent(TutorStore.Data loaded) throws java.io.IOException {
        try {
            for (String id : loaded.topics.keySet()) TutorCatalog.topic(id);
            if (!loaded.lastTopicId.isEmpty()) TutorCatalog.topic(loaded.lastTopicId);
            for (TutorStore.Attempt attempt : loaded.attempts) {
                TutorPracticeEngine.PracticeItem item = TutorPracticeEngine.fromSnapshotJson(attempt.itemSnapshotJson);
                if (!item.topicId.equals(attempt.topicId) || !item.templateId.equals(attempt.templateId)
                        || !item.itemKey.equals(attempt.itemKey) || !item.generatorVersion.equals(attempt.generatorVersion)
                        || item.seed != attempt.seed) throw new IllegalArgumentException("Exercise identity mismatch");
                if (attempt.numericAssessment) {
                    TutorPracticeEngine.CheckResult result = TutorPracticeEngine.checkNumeric(item, attempt.checkedAnswer);
                    String expected = result == TutorPracticeEngine.CheckResult.INVALID_INPUT ? "INVALID" : result.name();
                    if (!expected.equals(attempt.outcome.name())) throw new IllegalArgumentException("Stored numeric result mismatch");
                }
            }
        } catch (RuntimeException invalid) { throw new java.io.IOException("Unsupported Tutor content", invalid); }
    }
}
