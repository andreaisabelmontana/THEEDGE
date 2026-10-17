package com.plia.notebook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Source locators only. Source exercises are never substituted for generated practice. */
public final class TutorMaterials {
    private TutorMaterials() {}
    public static final class Source {
        public final String id, title, libraryFileId, status;
        public final int pageCount;
        public final List<String> notes;
        private Source(String id, String title, int pageCount, String libraryFileId, List<String> notes) {
            this.id=id; this.title=title; this.pageCount=pageCount; this.libraryFileId=libraryFileId;
            this.status=SOURCE_STATUS; this.notes=notes;
        }
    }
    public static final class Reference {
        public final String id, sourceId, topicId, question, title, canonicalId, status;
        public final int page;
        public final List<String> flags;
        private Reference(String id, String sourceId, String topicId, int page, String question,
                String title, String canonicalId, List<String> flags) {
            this.id=id; this.sourceId=sourceId; this.topicId=topicId; this.page=page; this.question=question;
            this.title=title; this.canonicalId=canonicalId; this.flags=flags; this.status=SOURCE_STATUS;
        }
        public String locator() { return sourceId + " p" + page + " " + question; }
    }
    public static final String VERSION = "ieu-source-map-v1";
    public static final String VERIFIED_ON = ""; // Exact source-check date omitted from the public snapshot.
    public static final String SOURCE_STATUS = "Page/question reference verified from PDF text";
    public static final String VERIFICATION_SCOPE = "All 15 source PDF pages were read during prototype development. References and matching exercise stems checked in extracted text; graph heights and ambiguous formula typography are not visually verified. Source questions and PDFs are not bundled in the app. Private Library identifiers and original filenames are omitted from this public snapshot.";
    public static final String ORIGINAL_PRACTICE = "Original Plia practice, generated from bounded templates; not a copied PDF exercise. Checking a practice answer does not complete its related source exercise.";
    public static final String UNRESOLVED = "Graph heights, ambiguous formula typography and full exam coverage remain unverified. Source references are not source answer keys.";
    private static final List<Source> SOURCES = Collections.unmodifiableList(Arrays.asList(
        new Source("A1", "Source A1.pdf", 4, "", strings("Two quiz forms, pp1-2 and pp3-4. Graphs need the original page; no inferred heights.")),
        new Source("A2", "Source A2.pdf", 2, "", strings("The extra-credit polynomial/root task repeats A3 p2 extra credit.")),
        new Source("A3", "Source A3.pdf", 2, "", strings("The p1 MC1 negative-base power needs domain clarification. Extra credit repeats A2.")),
        new Source("A4", "Source A4.pdf", 4, "", strings("Extracted pp3-4 repeat pp1-2; mapped once. Six exercise stems also occur in A5, sometimes with reordered choices.")),
        new Source("A5", "Source A5.pdf", 2, "", strings("Shared exercise stems: A5 Q2=A4 Q5; Q3=Q6; Q4=Q7; Q5=Q2; Q6=Q3; Q8=Q8. Choice letters are not interchangeable.")),
        new Source("A6", "Source A6.pdf", 1, "", strings("Q1b asks for an integral value, not only convergence. Q2a expression needs visual typography check. Q3c asks only to state the constrained setup. Q5 triangle differs from Plia original practice."))
    ));
    private static final List<Reference> REFERENCES = Collections.unmodifiableList(Arrays.asList(
        new Reference("A1-p1-MC1", "A1", "limit-methods", 1, "MC1", "Limit with required L'Hopital method", "A1-p1-MC1", strings("The source explicitly requires L'Hopital; do not generalize this permission to other exercises.")),
        new Reference("A1-p1-MC2", "A1", "limits-continuity-graphs", 1, "MC2", "Rational-function asymptotes", "A1-p1-MC2", strings()),
        new Reference("A1-p1-MC3", "A1", "differentiation", 1, "MC3", "Implicit differentiation", "A1-p1-MC3", strings()),
        new Reference("A1-p1-MC4", "A1", "limit-methods", 1, "MC4", "Exponential limit", "A1-p1-MC4", strings()),
        new Reference("A1-p1-MC5", "A1", "maclaurin", 1, "MC5", "Degree-four cosine Maclaurin polynomial", "A1-p1-MC5", strings("Plia original Maclaurin family uses a degree-three exponential; it is related practice, not this question.")),
        new Reference("A1-p2-Q1", "A1", "limits-continuity-graphs", 2, "Q1", "Graph limits, values and discontinuities", "A1-p2-Q1", strings("Consult the original graph; numerical heights are not supplied by this index.")),
        new Reference("A1-p2-Q2", "A1", "limit-methods", 2, "Q2", "Epsilon-delta proof for a quadratic", "A1-p2-Q2", strings()),
        new Reference("A1-p2-Q3", "A1", "limits-continuity-graphs", 2, "Q3", "Piecewise-function continuity", "A1-p2-Q3", strings()),
        new Reference("A1-p3-MC1", "A1", "limits-continuity-graphs", 3, "MC1", "Piecewise-function asymptotes", "A1-p3-MC1", strings()),
        new Reference("A1-p3-MC2", "A1", "differentiation", 3, "MC2", "Variable-power logarithmic differentiation", "A1-p3-MC2", strings("Negative-base real-power ambiguity in the source; do not invent a real-valued domain.")),
        new Reference("A1-p3-MC3", "A1", "differentiation", 3, "MC3", "Implicit differentiation with trigonometric and exponential terms", "A1-p3-MC3", strings()),
        new Reference("A1-p3-MC4", "A1", "maclaurin", 3, "MC4", "Degree-three exponential Maclaurin polynomial", "A1-p3-MC4", strings()),
        new Reference("A1-p3-MC5", "A1", "differentiation", 3, "MC5", "Variable-power derivative", "A1-p3-MC5", strings("Establish the real domain before logarithmic differentiation.")),
        new Reference("A1-p4-Q1", "A1", "limits-continuity-graphs", 4, "Q1", "Graph limits, values and discontinuities", "A1-p4-Q1", strings("The supplied brief flags unlabeled heights; visual review of the original graph is still required.")),
        new Reference("A1-p4-Q2", "A1", "limit-methods", 4, "Q2", "Epsilon-delta proof for a square", "A1-p4-Q2", strings()),
        new Reference("A1-p4-Q3", "A1", "limits-continuity-graphs", 4, "Q3", "Piecewise-function continuity", "A1-p4-Q3", strings()),
        new Reference("A2-p1-MC1", "A2", "improper-integrals-series", 1, "MC1", "Improper integral at a singular endpoint", "A2-p1-MC1", strings()),
        new Reference("A2-p1-MC2", "A2", "limit-methods", 1, "MC2", "Power-form limit", "A2-p1-MC2", strings()),
        new Reference("A2-p2-Q1a", "A2", "integration-ftc", 2, "Q1a", "FTC with moving bounds", "A2-p2-Q1a", strings()),
        new Reference("A2-p2-Q1b", "A2", "differentiation", 2, "Q1b", "Tangent line using logarithmic differentiation", "A2-p2-Q1b", strings()),
        new Reference("A2-p2-Q2a-b", "A2", "limits-continuity-graphs", 2, "Q2a-b", "Asymptotes and discontinuities", "A2-p2-Q2a-b", strings()),
        new Reference("A2-p2-Q3a", "A2", "improper-integrals-series", 2, "Q3a", "Two series and convergence tests", "A2-p2-Q3a", strings("Begin with the divergence test as requested; a zero term limit alone does not establish convergence.")),
        new Reference("A2-p2-Q3b", "A2", "integration-ftc", 2, "Q3b", "Mixed substitution and integration by parts", "A2-p2-Q3b", strings()),
        new Reference("A2-p2-Q3c", "A2", "integration-ftc", 2, "Q3c", "Geometric area and sign changes", "A2-p2-Q3c", strings()),
        new Reference("A2-p2-Extra-credit", "A2", "limit-methods", 2, "Extra credit", "Existence and location of a real root", "shared-A2-A3-root", strings("Same polynomial and root-location task in A2/A3; count once. Uses continuity and the intermediate value theorem.")),
        new Reference("A3-p1-MC2", "A3", "improper-integrals-series", 1, "MC2", "Improper integral at a singular endpoint", "A3-p1-MC2", strings()),
        new Reference("A3-p1-MC1", "A3", "limit-methods", 1, "MC1", "Power-form limit", "A3-p1-MC1", strings("Negative-base variable-power expression needs real-domain clarification; no answer inferred.")),
        new Reference("A3-p2-Q1a", "A3", "integration-ftc", 2, "Q1a", "Tangent line of an integral-defined function", "A3-p2-Q1a", strings()),
        new Reference("A3-p2-Q1b", "A3", "differentiation", 2, "Q1b", "Derivative at a point", "A3-p2-Q1b", strings()),
        new Reference("A3-p2-Q2a-b", "A3", "limits-continuity-graphs", 2, "Q2a-b", "Asymptotes and discontinuities", "A3-p2-Q2a-b", strings()),
        new Reference("A3-p2-Q3a", "A3", "improper-integrals-series", 2, "Q3a", "Two series and convergence tests", "A3-p2-Q3a", strings("Begin with the divergence test as requested; a zero term limit alone does not establish convergence.")),
        new Reference("A3-p2-Q3b", "A3", "integration-ftc", 2, "Q3b", "Mixed substitution and integration by parts", "A3-p2-Q3b", strings()),
        new Reference("A3-p2-Q3c", "A3", "integration-ftc", 2, "Q3c", "Geometric area and sign changes", "A3-p2-Q3c", strings()),
        new Reference("A3-p2-Extra-credit", "A3", "limit-methods", 2, "Extra credit", "Existence and location of a real root", "shared-A2-A3-root", strings("Same polynomial and root-location task in A2/A3; count once. Uses continuity and the intermediate value theorem.")),
        new Reference("A4-p1-Q1", "A4", "multivariable", 1, "Q1", "Multivariable concepts: justified true or false", "A4-p1-Q1", strings("Includes a Newton-method statement; the 14 practice families do not separately cover every statement.")),
        new Reference("A4-p1-Q2", "A4", "multivariable", 1, "Q2", "Gradient in three variables", "shared-A4-A5-gradient", strings("Same exercise stem as A5 p2 Q5; answer choices are reordered.")),
        new Reference("A4-p1-Q3", "A4", "multivariable", 1, "Q3", "Normalize a direction and compute its derivative", "shared-A4-A5-direction", strings("Same exercise stem as A5 p2 Q6.")),
        new Reference("A4-p1-Q4", "A4", "multivariable", 1, "Q4", "Identify level sets", "A4-p1-Q4", strings("No dedicated level-set practice family is bundled yet.")),
        new Reference("A4-p2-Q5", "A4", "multivariable", 2, "Q5", "Tangent plane of a rational function", "shared-A4-A5-plane", strings("Same exercise stem as A5 p1 Q2; answer choices are reordered.")),
        new Reference("A4-p2-Q6", "A4", "multivariable", 2, "Q6", "Multivariable chain rule", "shared-A4-A5-chain", strings("Same exercise stem as A5 p1 Q3; answer choices are reordered. Preserve unsimplified chain factors as requested.")),
        new Reference("A4-p2-Q7", "A4", "optimization", 2, "Q7", "Find and classify critical points", "shared-A4-A5-critical", strings("Same exercise stem as A5 p1 Q4; answer choices are reordered. Plia quadratic practice is not a complete substitute for this cubic problem.")),
        new Reference("A4-p2-Q8-extra-credit", "A4", "multivariable", 2, "Q8 extra credit", "Constant-temperature and fastest-cooling directions", "shared-A4-A5-mosquito", strings("Same exercise stem as A5 p2 Q8.")),
        new Reference("A5-p1-Q1a-b", "A5", "double-integrals-mass", 1, "Q1a-b", "Sketch a region and reverse integration order", "A5-p1-Q1a-b", strings()),
        new Reference("A5-p1-Q2", "A5", "multivariable", 1, "Q2", "Tangent plane of a rational function", "shared-A4-A5-plane", strings("Same stem as A4 p2 Q5; choice letters differ.")),
        new Reference("A5-p1-Q3", "A5", "multivariable", 1, "Q3", "Multivariable chain rule", "shared-A4-A5-chain", strings("Same stem as A4 p2 Q6; preserve unsimplified chain factors.")),
        new Reference("A5-p1-Q4", "A5", "optimization", 1, "Q4", "Find and classify critical points", "shared-A4-A5-critical", strings("Same stem as A4 p2 Q7; choice letters differ.")),
        new Reference("A5-p2-Q5", "A5", "multivariable", 2, "Q5", "Gradient in three variables", "shared-A4-A5-gradient", strings("Same stem as A4 p1 Q2; choice letters differ.")),
        new Reference("A5-p2-Q6", "A5", "multivariable", 2, "Q6", "Normalize a direction and compute its derivative", "shared-A4-A5-direction", strings("Same stem as A4 p1 Q3.")),
        new Reference("A5-p2-Q7", "A5", "optimization", 2, "Q7", "Lagrange multipliers with a quartic constraint", "A5-p2-Q7", strings("Requires optimization and the multiplier at maxima; unlike the state-only A6 task, setup alone is insufficient.")),
        new Reference("A5-p2-Q8-extra-credit", "A5", "multivariable", 2, "Q8 extra credit", "Constant-temperature and fastest-cooling directions", "shared-A4-A5-mosquito", strings("Same stem as A4 p2 Q8.")),
        new Reference("A6-p1-Q1a", "A6", "differentiation", 1, "Q1a", "Implicit differentiation", "A6-p1-Q1a", strings()),
        new Reference("A6-p1-Q1b", "A6", "improper-integrals-series", 1, "Q1b", "Value of a non-elementary improper integral", "A6-p1-Q1b", strings("Source asks for its value. A convergence argument alone is incomplete; no source answer is supplied.")),
        new Reference("A6-p1-Q1c", "A6", "limits-continuity-graphs", 1, "Q1c", "Piecewise continuity and asymptotes", "A6-p1-Q1c", strings()),
        new Reference("A6-p1-Q2a", "A6", "multivariable", 1, "Q2a", "Tangent plane", "A6-p1-Q2a", strings("Extracted expression contains no y; the brief flags this too. Do not introduce y, and visually confirm exponent/fraction typography before solving the source question.")),
        new Reference("A6-p1-Q2b", "A6", "multivariable", 1, "Q2b", "Directional derivative from an angle", "A6-p1-Q2b", strings()),
        new Reference("A6-p1-Q3a", "A6", "double-integrals-mass", 1, "Q3a", "Sketch a region and evaluate a double integral", "A6-p1-Q3a", strings()),
        new Reference("A6-p1-Q3b", "A6", "optimization", 1, "Q3b", "Unconstrained maxima and minima", "A6-p1-Q3b", strings()),
        new Reference("A6-p1-Q3c", "A6", "optimization", 1, "Q3c", "State a constrained-extrema method", "A6-p1-Q3c", strings("STATE only: retain the constraint and Lagrange system without solving it.")),
        new Reference("A6-p1-Q4a-c", "A6", "vector-fields", 1, "Q4a-c", "Conservative fields, potential and line integral", "A6-p1-Q4a-c", strings()),
        new Reference("A6-p1-Q5-extra-credit", "A6", "double-integrals-mass", 1, "Q5 extra credit", "Triangle density and mean y-coordinate", "A6-p1-Q5-extra-credit", strings("Source vertices are (0,2), (1,0), (2,0). Plia original family uses a different triangle. Never relabel its generated answer as this source answer."))
    ));
    public static List<Source> sources() { return SOURCES; }
    public static Source source(String id) {
        for (Source s : SOURCES) if (s.id.equals(id)) return s;
        throw new IllegalArgumentException("Unknown source: " + id);
    }
    public static List<Reference> references() { return REFERENCES; }
    public static List<Reference> referencesForSource(String id) {
        source(id); ArrayList<Reference> result = new ArrayList<>();
        for (Reference r : REFERENCES) if (r.sourceId.equals(id)) result.add(r);
        return Collections.unmodifiableList(result);
    }
    public static List<Reference> referencesForTopic(String id) {
        TutorCatalog.topic(id); ArrayList<Reference> result = new ArrayList<>();
        for (Reference r : REFERENCES) if (r.topicId.equals(id)) result.add(r);
        return Collections.unmodifiableList(result);
    }
    /** Counts mapped question groups, not individual parts, attempts, or mastered exercises. */
    public static int uniqueReferenceCount() {
        HashSet<String> ids = new HashSet<>();
        for (Reference r : REFERENCES) ids.add(r.canonicalId);
        return ids.size();
    }
    private static List<String> strings(String... values) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
    }
}
