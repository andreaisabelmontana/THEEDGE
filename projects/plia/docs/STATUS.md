# Status and evidence

Snapshot: **0.1.23 / version code 24**, documented **17 October 2026**.

## Implemented

| Area | Present behavior | Boundary |
| --- | --- | --- |
| Think notebook | Native ink, pages, selection, editing, reply placement and persistence | No Onyx raw-drawing integration or proven pen-latency claim |
| Goal library | Bundled calculus goal plus separate personal goals | Personal goals begin without generated course content |
| Lessons | Ten original calculus topic lessons | Syllabus and exam date unconfirmed |
| Practice | Fourteen bounded, reproducible families | Five final numeric checks; nine require self-review |
| Evidence | Saved attempts and assistance history | Activity records, not mastery certification |
| Typography | Local math rendering and custom handwriting-style prose | No animated handwritten responses |
| Offline use | Lessons, practice, scratch paper and local storage | Optional AI requests still require their own connection |
| Recovery | Goal isolation, exports and corrupt-data write protection | User backups remain necessary; notebook and Tutor exports differ |

## Planned or being explored

- Editable Cornell units with cues, notes, summary and conceal/retrieve/reveal interaction.
- Flashcards, a local review queue and a versioned spaced-repetition scheduler.
- Adaptive next-step suggestions grounded in observed work and review history.
- Supported reuse of NotebookLM exports and other authorized materials.
- More robust annotation identity across edited/reflowed lesson content.
- Wider accessibility and hardware testing, including actual pen behavior.

These are not shipped features of this snapshot. A separate 0.1.24 development candidate was under integration when this documentation was prepared.

## What the development evidence establishes

The original stable snapshot completed an offline Android build. A recorded 17 October 2026 in-place update verified the installed 0.1.23 APK, the goal library, and an offline lesson showing the custom prose font alongside correctly rendered mathematical notation. Local notebook bytes were preserved during that update. Private device records and notebook data are intentionally excluded from this repository.

Development handoffs recorded focused host checks for storage/recovery, goal isolation, navigation, request state, deterministic practice, numerical results and math rendering. For scale, the content work exercised 1,806 generated practice cases and rendered 984 mathematical expressions. These were engineering checks, not a trial with learners. Counts from different handoffs overlap and should not be added into a unique-test total.

The curated repository includes synthetic debug preview activities. The external host harnesses used during development are not all present in this source snapshot; there is no claim that running a single test command here reproduces every historical result. Build verification for the curated package is recorded in [build and release](BUILD-AND-RELEASE.md).

## What remains unverified

- A complete successful live dot/agent roundtrip with inherited memories.
- Broad physical pen latency, e-ink refresh and Android lifecycle coverage across devices.
- Automatic grading of handwriting, reasoning or symbolic equivalence.
- Full exercise-by-exercise assignment ingestion or complete exam coverage.
- Learning gains, long-term retention, general adaptability or resilience attributable to Plia.
- A hardened release, public beta distribution, ongoing maintenance policy or production support.

Source metadata contains 60 locator groups from six assignment PDFs, 53 after deduplication. These are provenance groups, not exercise-completion counts. Full PDFs, questions and answer keys are absent; formula and graph uncertainties remain flagged. Original practice does not complete a source assignment.

## Development sequence

| Version milestone | Focus |
| --- | --- |
| Early v0.1 | Native paper, local pages and manual assistance bridge concept |
| 0.1.12–0.1.14 | Paper layout, selection/eraser handling, response completion and retry rules |
| 0.1.17 | Persistent reply refresh with undo/redo |
| 0.1.18 | Local mathematical typesetting, device verification |
| 0.1.19–0.1.22 | Lasso closure, readability, move/remove, Tutor foundation, handwriting and compact pen settings |
| 0.1.23 | Goal notebooks and structured Tutor, offline build and device verification |

This sequence summarizes development records; it is not a Git commit history. No earlier GitHub publication is asserted. Publication of this source is a separate milestone.

## Useful next evaluation

Test a complete topic unit with matched study content and time. Record prior knowledge, hint use and confidence separately. Measure delayed unassisted retrieval and new application questions, alongside accessibility and device comfort. Report uncertainty and null results. Completion, streaks and satisfaction alone are insufficient evidence of learning.
