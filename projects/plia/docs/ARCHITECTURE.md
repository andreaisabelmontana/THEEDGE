# Architecture

This describes the curated 0.1.23 source snapshot. It is a native Java/Android Views application with a local WebView used for mathematical rendering. It is not a website wrapped in a WebView.

## Components

| Area | Main files | Responsibility |
| --- | --- | --- |
| Notebook shell | `MainActivity.java`, `PageEditor.java` | Modes, page navigation, actions and save boundaries |
| Ink | `InkView.java`, `PenSettings.java`, `SelectionGesture.java`, `SelectionOutline.java` | Stylus input, pressure, strokes, tool settings and selection |
| Paper and replies | `PaperSurface.java`, `PaperAnchor.java`, `ReplyPlacement.java`, `ReplyRefresh.java` | Place reply objects around ink; preserve refresh history |
| Notebook persistence | `NotebookStore.java`, `PaperDraft.java` | Local saved pages, drafts and notebook import/export |
| Tutor navigation | `TutorLibraryActivity.java`, `TutorActivity.java` | Goal library, lessons, practice and materials |
| Tutor content | `TutorCatalog.java`, `TutorMaterials.java`, `TutorPracticeEngine.java` | Original lessons, source locators, reproducible practice and bounded checking |
| Tutor evidence | `TutorStore.java`, `TutorLibraryStore.java`, `TutorUiLogic.java` | Goal isolation, attempts, assistance, progress labels and safe persistence |
| Math and typography | `SolutionDocument.java`, `SolutionView.java`, `LocalMathView.java`, `ExplanationFont.java` | Paired math/prose, local KaTeX and optional handwriting-style prose |
| Optional assistance | `BridgeClient.java`, `ChatGptClient.java`, `SiwcInference.java`, `SiwcProtocol.java`, `SiwcStore.java` | Separate companion and signed-in request flows |
| Request lifecycle | `PaperSession.java`, `SelectionRetryPolicy.java`, `ResponseMetadata.java` | Request admission, retry, completion and stale-result handling |

Java files are under `app/src/main/java/com/plia/notebook/`. The debug source set contains isolated math, input and move preview activities plus synthetic fixtures; they are not a complete portable regression suite.

## Native writing and rendering

Input is Android `MotionEvent` data drawn into a canvas/bitmap and invalidated as a view. The logical sheet preserves existing ink coordinates across display sizes. Stored ink and eraser operations retain their order; selection works on visible ink. Generated replies are separate from the learner's strokes and do not become new handwritten input.

“Native” describes the Android implementation. This snapshot does not use the Onyx raw-drawing SDK or establish a physical stylus-latency improvement. Desktop event simulation is not evidence about pen sampling, display scanout or e-ink refresh time.

KaTeX JavaScript, styles and font files are local assets. Explanatory prose may use the custom handwriting font while mathematical glyphs keep the renderer's own fonts and layout. A handwriting font is not a symbolic math renderer and does not animate strokes. Local math display and PNG export have different paths; do not assume an exported PNG reproduces all rich mathematical rendering exactly.

## The local learning loop

1. A goal selects a bundled topic or stores personal notes in its own scope.
2. An original lesson presents a concept, method, common trap and worked example.
3. The practice engine generates a bounded variant from a template, seed and version.
4. A learner enters a typed answer and may use optional scratch paper or hints.
5. A numeric family checks only the final scalar value. Other families support explicit solution comparison and self-checking.
6. The attempt retains the question snapshot, answer, outcome and assistance history.

No automatic external model is required for this loop. New personal goals start empty; creating a title does not create an AI course or invented progress.

## Evidence semantics

`TutorStore` separates `UNCHECKED`, `CORRECT`, `INCORRECT`, `INVALID` and `SELF_CHECKED`. Editing an answer clears its current assessment. Submitted attempts are counted once per attempt ID, while successful questions are deduplicated by canonical item identity. Hint or solution exposure for that item remains assistance history across attempts.

Only current, submitted, independently correct numeric items count toward the local “Ready for review” threshold of two distinct items. Symbolic self-checks do not become automatic numeric evidence. “Learning,” “Practising,” “Needs practice” and “Ready for review” are transparent activity rules. They are neither psychometric measurements nor mastery or exam predictions.

These records are local and editable through import. They are not cryptographically attested learning evidence. The UI validates imported snapshots and numeric outcomes against the local engine; this does not authenticate a person's identity or prove they worked independently.

## Persistence and recovery

Notebook and Tutor data are distinct. Tutor's goal index and goal-specific state use Android `AtomicFile` and a shared serial I/O queue. The legacy bundled goal retains its established state location; new goals have validated independent IDs and files. Corrupt/unreadable data locks writes and supports recovery rather than silently replacing it with an empty state.

Snapshots retain drafts, ink, assistance and question identity. Save errors must remain visible: a write that cannot be confirmed must not be represented as successfully saved. Notebook ZIP exports and Tutor exports are separate flows; users must not assume one includes all data from the other.

App-private storage remains local unless the learner explicitly exports or requests assistance. Uninstalling or clearing app data removes local work. Android automatic backup is disabled in the manifest.

## Assistance boundaries

The earlier companion bridge queues an explicit request for a separately operated local service. That service is not included in this source snapshot. The Android client does not imply that an AI agent is watching, connected, awake or carrying memories. Synthetic bridge tests from development did not establish a live dot roundtrip.

The separate signed-in model path uses explicit account/model/session setup and sends request content through its own client. It does not inherit a dot's identity or memories. Account usage or credits may apply. Credential storage code is included, but no account state, keys or tokens are included. AI text remains fallible; a transport-complete response is not evidence of correct mathematics.

The release manifest disallows cleartext traffic. The debug manifest permits it for local development and exposes debug preview activities. A debug build is therefore not a hardened public release.

## Content provenance

The calculus pack contains original lessons and generated examples. Source metadata maps six supplied assignment PDFs by page/question group; the PDFs, full questions and answer keys are absent. Overlapping locators are deduplicated, and ambiguous graphs/formulas retain warnings. Completing original practice does not complete a source assignment. The syllabus and exam date are unconfirmed.
