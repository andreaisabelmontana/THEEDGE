# Brainstorming and design evolution

This is a **retrospective reconstruction** from saved prototype plans, source code and development handoffs, organized on 17 October 2026. It is not a verbatim brainstorming transcript. It does not invent interviews, user studies, historical quotations or a chronology of thoughts that was never recorded.

## The starting problem

The early project records describe a small, pen-first notebook with two modes: **Think**, for writing and asking about an idea, and **Tutor**, for a learning goal, a problem, an attempted solution and feedback. Calculus study supplied a concrete first use case.

The practical problem was fragmentation: handwritten reasoning, explanatory text, practice problems and saved feedback needed to live together. A useful answer should remain attached to the work that prompted it. The learner's original ink should survive assistance, revisions and app restarts.

The current product thesis extends this foundation: reimagine learning, notes, memorization and practice as one loop, organized around evidence over affirmation. This thesis is a design commitment; it is not evidence that the entire system has already been implemented or evaluated.

## Decisions that shaped the prototype

| Question | Decision reflected in the implementation | Why it matters |
| --- | --- | --- |
| What is the primary surface? | Native Android paper and views, designed first for a BOOX e-ink tablet | Keeps writing and reading central; actual pen behavior still needs device testing |
| When should help appear? | Explicit selection/request controls with lifecycle and duplicate protection | Makes the target work and request state visible |
| Where should an answer live? | A separate reply object near the selected ink, with fallback below | Preserves the learner's reasoning and allows refresh/undo |
| How should mathematics read? | Local typeset math paired with a verbal explanation | Gives symbols and reasoning distinct, readable roles |
| What does progress mean? | Saved attempts, independent versus assisted work, and transparent activity labels | Avoids rewarding repeated checking or solution exposure as independent understanding |
| How should practice vary? | Bounded, versioned original templates with reproducible parameters | Makes generated items inspectable and supports meaningful checks |
| What about a new personal goal? | Create a separate empty notebook | Avoids fabricating lessons or progress from a title |
| What must remain usable offline? | Writing, local lessons, deterministic practice and saved work | Prevents core study from depending on a successful model request |

## From a blank page to a structured study loop

The first layer was reliable writing and local storage. Selection and reply work then addressed rough loops, erased-and-rewritten regions, duplicate requests, retry behavior, stale results and preserving ink during refreshes. Rich mathematical rendering followed the early plain-text approach.

Tutor added structure: a goal, an original lesson, similar practice, saved attempts and resume. The design records explicitly separate reading from attempting. Opening a lesson can update a resume location; it cannot produce evidence of an independent correct answer. Hints and revealed solutions stay in the history even when a learner retries.

These details embody the central belief more concretely than a motivational slogan. The prototype can report what was attempted and what help was used. It cannot yet infer broad understanding from those records.

## The next direction

The saved next-iteration plan proposes ordered topic units that read like a textbook. Each unit would connect:

1. Editable Cornell-style cues/questions, main notes and a learner summary.
2. A retrieval step: conceal the notes, produce an answer, then reveal and reflect.
3. Worked examples and topic practice.
4. Cards for suitable memorization material and a review history.
5. Suggested next steps based on actual attempts, assistance and later retrieval.

This is roadmap work for 0.1.23. A later isolated candidate explores some of it, but its presence in another development folder is not evidence that it shipped here. Supported NotebookLM export/import and an FSRS-style scheduler are research/integration directions, not current integrations.

## Evidence over affirmation

The aim is to replace vague reassurance with specific information: what was recalled, which step failed, what evidence supports the correction and whether the next attempt succeeds without help. Confidence and correctness are separate signals. Self-reported understanding and an automated numeric check are also separate signals.

AI can explain, suggest and help a learner recover from an error. The intended role is to support thinking while preserving the opportunity to think independently. A polished model answer should not become a shortcut around every retrieval attempt. [The research ledger](RESEARCH.md) contains both positive and negative findings about AI-supported learning.

## Hebbian inspiration and its limits

The idea of repeatedly activating connected concepts resonates with the familiar Hebbian phrase “neurons that fire together wire together.” It is a useful inspiration for making connections through practice. Actual synaptic plasticity is more complicated, including timing-dependent strengthening and weakening. Plia does not measure neural activity, implement a validated biological scheduler or claim to “rewire the brain.” Retrieval, spacing and transfer studies provide more direct evidence for interface decisions.

## Why e-ink for now

A stable page, low-motion interface, readable mathematics and room to write fit the intended study ritual. E-ink's constraints also force the design to treat reading and action deliberately. That is a product rationale, not a universal educational advantage. Hardware refresh, ghosting and pen latency can obstruct the experience; contrast, font size and typing alternatives remain necessary.

The direction is therefore **e-ink first**, with a learning model that should remain portable. It should survive a change of screen technology rather than depend on a claim that one display teaches better.

## Adaptability and resilience

Adaptability means using an idea when the context or problem changes. Practice should eventually include varied examples and choices of method, not only repetition of one answer. Resilience means making revision and return to work possible: preserve drafts, retain evidence, offer a manageable next step and make errors specific enough to act on.

These are intended qualities of the experience and hypotheses about learner benefit. No Plia study has measured either trait.

## Open questions

- Does the complete loop improve delayed, unassisted recall and transfer compared with realistic alternatives?
- When should help be withheld, offered as a hint or expanded into a worked example?
- How should confidence, time, errors and assistance affect recommendations without inventing a mastery score?
- How can rich editable notes and ink remain stable across layout changes?
- Which e-ink and alternative-device users can comfortably read, type and write?
- Which content and import formats can be reused with clear provenance and rights?
- How should a transparent review scheduler recover from changed cards, imports and interrupted sessions?

The next useful milestone is a complete, testable topic unit—not a claim that all these questions are settled.
