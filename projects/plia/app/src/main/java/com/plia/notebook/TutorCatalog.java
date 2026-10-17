package com.plia.notebook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Original offline lessons with PDF-text-verified source locators in TutorMaterials. */
public final class TutorCatalog {
    public static final String GOAL = "Calculus practice";
    public static final boolean SYLLABUS_CONFIRMED = false;
    public static final String EXAM_DATE = null;
    public static final String COVERAGE_STATEMENT =
            "Ten original topic lessons and fourteen practice families support calculus practice. "
            + "The syllabus and exam date are unconfirmed. This is a topic-level study aid, not verified exam coverage or a prediction of exam success.";
    public static final String PROVENANCE_STATEMENT =
            "A1-A6 page/question references were checked against all six source PDFs during prototype development. "
            + "Source questions and answer keys are not bundled or fully validated; graph and formula ambiguities remain. "
            + "Lessons and practice are original Plia content, separate from source exercises. External links are optional and were not fetched.";
    public static final String LEARNING_EVIDENCE_STATEMENT =
            "Record independent attempt, hint used, or solution viewed as activity evidence; these labels do not establish mastery.";

    public static final String SOURCE_UNVERIFIED = "Source exercise statements and answer keys are not fully validated or bundled. Original practice does not complete a PDF exercise.";
    public static final String SYLLABUS_UNCONFIRMED = "Course syllabus unconfirmed; no claim of complete exam coverage.";
    public static final String A4_DUPLICATE = "A4 pp3-4 repeat pp1-2 in the inspected PDF text; mapped once.";
    public static final String A4_A5_OVERLAP = "Six A4/A5 exercise stems match: A4 Q2/3/5/6/7/8 correspond to A5 Q5/6/2/3/4/8. Reordered choices are not interchangeable; references share canonical IDs.";
    public static final String NEGATIVE_BASE_AMBIGUITY = "A3 p1 MC1 and A1 p3 MC2: negative-base real-power ambiguity; do not invent a real-valued domain.";
    public static final String GRAPH_HEIGHTS_UNLABELED = "The supplied brief flags unlabeled heights in A1 p4 Q1. Graph pixels have not been visually verified in this mapping; infer no numerical heights.";
    public static final String NON_ELEMENTARY_VALUE = "A6 Q1b asks for the value of a non-elementary improper integral: convergence alone is insufficient.";
    public static final String NO_Y_DEPENDENCE = "A6 p1 Q2a has no y in the extracted expression or supplied brief; do not introduce y. Visually confirm exponent/fraction typography before solving the source question.";
    public static final String MOMENT_NOTATION =
            "Supplied mass seed calls the y first moment My=8/5; conventionally Mx=integral y*rho=8/5, "
            + "while My=integral x*rho=8/15. Use the explicit integral / 'first moment in y' and ybar=6/5.";

    public static final List<String> SOURCE_AMBIGUITY_WARNINGS = immutable(
            NEGATIVE_BASE_AMBIGUITY, GRAPH_HEIGHTS_UNLABELED, NON_ELEMENTARY_VALUE,
            NO_Y_DEPENDENCE, MOMENT_NOTATION);
    public static final List<String> DEDUPLICATION_WARNINGS = immutable(A4_DUPLICATE, A4_A5_OVERLAP);
    public static final List<String> SOURCE_WARNINGS = immutable(
            SOURCE_UNVERIFIED, SYLLABUS_UNCONFIRMED, A4_DUPLICATE, A4_A5_OVERLAP,
            NEGATIVE_BASE_AMBIGUITY, GRAPH_HEIGHTS_UNLABELED, NON_ELEMENTARY_VALUE,
            NO_Y_DEPENDENCE, MOMENT_NOTATION);

    private static final String CALC_I = "https://tutorial.math.lamar.edu/Classes/CalcI/CalcI.aspx";
    private static final String CALC_II = "https://tutorial.math.lamar.edu/Classes/CalcII/CalcII.aspx";
    private static final String CALC_III = "https://tutorial.math.lamar.edu/Classes/CalcIII/CalcIII.aspx";

    public static final class Topic {
        public final String id, title, objective, concept, method, trap, externalUrl;
        public final List<String> sourceReferences, sourceFlags, familyIds;

        private Topic(String id, String title, String objective, String concept, String method,
                String trap, String externalUrl, List<String> sourceReferences,
                List<String> sourceFlags, List<String> familyIds) {
            this.id = id;
            this.title = title;
            this.objective = objective;
            this.concept = concept;
            this.method = method;
            this.trap = trap;
            this.externalUrl = externalUrl;
            this.sourceReferences = Collections.unmodifiableList(new ArrayList<>(sourceReferences));
            this.sourceFlags = Collections.unmodifiableList(new ArrayList<>(sourceFlags));
            this.familyIds = Collections.unmodifiableList(new ArrayList<>(familyIds));
        }
    }

    private static final List<Topic> TOPICS = Collections.unmodifiableList(Arrays.asList(
        new Topic(
            "limits-continuity-graphs", "Limits, continuity and graphs",
            "Separate a function's domain, its limiting behavior and its value at a point.",
            "A limit describes nearby values. Continuity also requires the function's value to exist and match that limit. Simplifying a formula cannot restore an input excluded by its original domain.",
            "Record excluded inputs before simplifying. Compare the limits from each side, then check the function's value. Distinguish removable holes from unbounded behavior, and read graph limits from the indicated side.",
            "A missing point is not automatically an asymptote. Do not assign numerical heights to an unlabeled graph.",
            CALC_I,
            immutable("A1 p1 MC2; p2 Q1/Q3; p3 MC1; p4 Q1/Q3", "A2 p2 Q2a-b", "A3 p2 Q2a-b", "A6 p1 Q1c"),
            flags(GRAPH_HEIGHTS_UNLABELED), immutable("rational-hole")),
        new Topic(
            "limit-methods", "Choosing and justifying a limit method",
            "Choose a valid limit method and turn an epsilon target into an explicit delta bound.",
            "A limit proof controls every sufficiently nearby input. Algebra, comparison and continuity can often settle the question before a more advanced theorem is needed.",
            "Try substitution, then simplify or compare when justified. In an epsilon-delta proof, bound the remaining factor in a fixed neighborhood and choose a positive input tolerance from the requested output tolerance. Use L'Hopital only when permitted and all its hypotheses hold.",
            "Choose delta from epsilon, not from the later test input. An indeterminate form alone is not a proof. A negative base with a variable real exponent needs a valid real domain; do not guess one.",
            CALC_I,
            immutable("A1 p1 MC1/MC4; p2 Q2; p4 Q2", "A2 p1 MC2", "A3 p1 MC1", "A2/A3 p2 Extra credit (same task)"),
            flags(NEGATIVE_BASE_AMBIGUITY), immutable("epsilon-delta-square")),
        new Topic(
            "differentiation", "Differentiation with dependent variables",
            "Apply product, chain, implicit and logarithmic differentiation while preserving the requested form.",
            "An implicit relation connects dependent variables, so the chain rule must follow that dependence. Logarithmic differentiation can simplify valid products and powers before taking their derivatives.",
            "Differentiate both sides, apply product and chain rules, then collect the terms containing the required derivative. Establish the real domain before taking logarithms; logarithms of absolute values require a nonzero function on the interval. Keep product and chain factors visible when an unsimplified answer is requested.",
            "Do not treat a dependent variable as constant or lose the sign when differentiating cosine. Dividing to isolate a derivative requires a nonzero coefficient. Do not repair ambiguous negative-base powers by inventing a domain.",
            CALC_I,
            immutable("A1 p1 MC3; p3 MC2/MC3/MC5", "A2/A3 p2 Q1b", "A6 p1 Q1a"),
            flags(NEGATIVE_BASE_AMBIGUITY), immutable("implicit-polynomial-trig")),
        new Topic(
            "maclaurin", "Maclaurin polynomials",
            "Build a polynomial of the requested degree from derivatives at zero.",
            "A Maclaurin polynomial matches the function's derivatives at zero through the requested degree. Away from zero, the finite polynomial is generally an approximation.",
            "Evaluate successive derivatives at zero and divide each by its corresponding factorial. Pair each coefficient with the correct power, including the constant term. If an error bound is requested, justify a remainder estimate on the stated interval.",
            "Derivative values are not the final coefficients. Include every power through the requested degree, allowing zero coefficients, and do not treat an approximation as an exact identity.",
            CALC_II, immutable("A1 p1 MC5", "A1 p3 MC4"), flags(), immutable("cubic-exponential-maclaurin")),
        new Topic(
            "integration-ftc", "Integration and the Fundamental Theorem",
            "Differentiate moving-bound integrals, choose integration methods and distinguish signed integral from area.",
            "The Fundamental Theorem connects differentiation and integration. With a continuous integrand and differentiable moving bounds, each bound contributes through the chain rule. Geometric area counts every region positively.",
            "Evaluate the integrand at each moving bound and multiply by that bound's derivative; subtract the lower contribution. For antiderivatives, choose substitution or integration by parts to match the structure. For geometric area, split the interval wherever the function changes sign.",
            "Keep the lower-bound minus sign and both chain factors. Include the constant of integration for an indefinite integral. Regions below the axis cannot cancel positive geometric area.",
            CALC_II, immutable("A2/A3 p2 Q1a; Q3b; Q3c"), flags(),
            immutable("ftc-two-variable-bounds", "mixed-substitution-parts", "geometric-area-sign-split")),
        new Topic(
            "improper-integrals-series", "Improper integrals and series",
            "Establish convergence using the correct limit or comparison and distinguish convergence from evaluation.",
            "An improper integral uses limits at singular or infinite endpoints. A series uses the limit of its partial sums. Convergence requires the defining limits to exist and be finite.",
            "Replace each problematic endpoint by a cutoff, and require every resulting piece to converge separately. Reciprocal powers converge at infinity only when the exponent exceeds one; near zero, only when it is below one. For series, check the term limit before choosing a justified comparison or another suitable test.",
            "Terms approaching zero do not guarantee convergence. Symmetric cancellation does not establish ordinary improper convergence. If the value is requested, convergence alone is incomplete; a non-elementary value may need a special function or numerical evaluation with error control.",
            CALC_II, immutable("A2 p1 MC1; p2 Q3a", "A3 p1 MC2; p2 Q3a", "A6 p1 Q1b"),
            flags(NON_ELEMENTARY_VALUE), immutable("two-series-tests")),
        new Topic(
            "multivariable", "Gradients, directions and tangent planes",
            "Compute partial derivatives, normalize a direction and form a tangent plane at the specified point.",
            "A partial derivative varies one coordinate while the others stay fixed. For a differentiable function, the gradient describes rates of change and the tangent plane gives a local linear approximation.",
            "Check the domain and differentiability at the specified point. Evaluate the gradient there, normalize the nonzero direction, then take their dot product. For a tangent plane, combine the function's value and partial derivatives at the base point with displacements from that point.",
            "An unnormalized direction scales the result, and a zero vector cannot be normalized. Evaluate derivatives at the base point. Do not introduce a variable absent from the supplied expression.",
            CALC_III, immutable("A4 p1 Q1-Q4; p2 Q5/Q6/Q8", "A5 p1 Q2/Q3; p2 Q5/Q6/Q8", "A6 p1 Q2a-b"),
            flags(A4_DUPLICATE, A4_A5_OVERLAP, NO_Y_DEPENDENCE),
            immutable("normalized-directional-derivative", "tangent-plane-quadratic")),
        new Topic(
            "optimization", "Unconstrained and constrained optimization",
            "Justify extrema on the stated domain and set up only the constrained system requested.",
            "A stationary point is a candidate for an extremum, not a global conclusion. Completing squares can give a global bound. A regular equality constraint restricts which directions are allowed.",
            "Find and classify candidates on the stated domain, then justify any global conclusion. For a constraint with nonzero gradient, set up proportional objective and constraint gradients together with the constraint. Stop at this setup when asked only to state the method.",
            "Local derivative tests alone do not prove a global extremum. Check boundaries and singular constraint points separately. Avoid dividing away possible solutions, and respect a state-only instruction.",
            CALC_III, immutable("A4 p2 Q7", "A5 p1 Q4; p2 Q7", "A6 p1 Q3b-c"),
            flags(A4_DUPLICATE, A4_A5_OVERLAP), immutable("unconstrained-quadratic-and-state-only-constraint")),
        new Topic(
            "double-integrals-mass", "Double integrals and lamina mass",
            "Describe a region in either order and compute mass and a coordinate-weighted first moment consistently.",
            "Changing integration order keeps the same region and integrand but changes the bounds. With nonnegative density and positive finite mass, the mean height is the integral of density-weighted height divided by total mass.",
            "Sketch the region and describe it in both orders before choosing the easier integral. For a lamina, compute total mass and the first moment for the requested coordinate using the same region and density. Divide that moment by mass.",
            "Reversed bounds must describe the same region. Moment subscripts refer to axes and can be confusing; identify the coordinate inside the integral explicitly. Do not substitute a generated practice triangle for a different source triangle.",
            CALC_III, immutable("A5 p1 Q1a-b", "A6 p1 Q3a; Q5 extra credit"),
            flags(A4_A5_OVERLAP, MOMENT_NOTATION), immutable("reverse-double-integral", "triangular-lamina-mass")),
        new Topic(
            "vector-fields", "Conservative fields and line integrals",
            "Verify a potential on the domain and use endpoint values to evaluate a conservative line integral.",
            "A differentiable potential has the vector field as its gradient throughout the relevant domain. Along a piecewise smooth path in that domain, the field's line integral equals the potential's change from start to end.",
            "Integrate one component, retain the possible dependence on the remaining variables, then match the other components. Verify the full gradient and domain. Evaluate the resulting potential at the final endpoint and subtract its value at the initial endpoint.",
            "Zero curl alone is not sufficient on an arbitrary domain. A potential must match every component. Reversing the path changes the sign of the integral.",
            CALC_III, immutable("A6 p1 Q4a-c"), flags(), immutable("conservative-field-integral"))
    ));

    private TutorCatalog() {}

    public static List<Topic> topics() { return TOPICS; }

    public static Topic topic(String id) {
        for (Topic value : TOPICS) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("Unknown Tutor topic: " + id);
    }

    public static List<String> familiesForTopic(String id) { return topic(id).familyIds; }

    public static String titleForFamily(String familyId) {
        if (familyId == null) throw new IllegalArgumentException("Unknown Tutor family: null");
        switch (familyId) {
            case "rational-hole": return "A rational function with a hole";
            case "epsilon-delta-square": return "An epsilon-delta proof for a square";
            case "implicit-polynomial-trig": return "Implicit differentiation";
            case "ftc-two-variable-bounds": return "FTC with two moving bounds";
            case "mixed-substitution-parts": return "Substitution and integration by parts";
            case "geometric-area-sign-split": return "Area across a sign change";
            case "reverse-double-integral": return "Reverse the integration order";
            case "two-series-tests": return "Two series, two tests";
            case "cubic-exponential-maclaurin": return "A cubic Maclaurin polynomial";
            case "normalized-directional-derivative": return "A normalized directional derivative";
            case "tangent-plane-quadratic": return "A tangent plane";
            case "unconstrained-quadratic-and-state-only-constraint": return "Quadratic optimization and Lagrange equations";
            case "conservative-field-integral": return "A conservative line integral";
            case "triangular-lamina-mass": return "Mass and mean height of a triangle";
            default: throw new IllegalArgumentException("Unknown Tutor family: " + familyId);
        }
    }

    public static TutorPracticeEngine.PracticeItem example(String topicId) {
        return TutorPracticeEngine.canonical(topic(topicId).familyIds.get(0));
    }

    private static List<String> flags(String... extra) {
        ArrayList<String> flags = new ArrayList<>();
        flags.add(SOURCE_UNVERIFIED);
        flags.add(SYLLABUS_UNCONFIRMED);
        flags.addAll(Arrays.asList(extra));
        return Collections.unmodifiableList(flags);
    }

    private static List<String> immutable(String... values) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values)));
    }
}
