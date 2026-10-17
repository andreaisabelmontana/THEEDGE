# Plia

**A learning notebook built around evidence over affirmation.**

Plia is an early native Android prototype for learning, handwritten thinking and practice, designed first around an e-ink tablet. Its longer-term aim is to connect reading, notes, recall, explanation, practice and review in one learning loop. The guiding question is what a learner can demonstrate independently: a reassuring answer, a familiar page or a completed session is not enough evidence of understanding.

This repository contains a curated source snapshot of **0.1.23 (Android version code 24)**, documented on **17 October 2026**. It is a working prototype, not a validated learning intervention or a production release. The snapshot preserves the stable implementation; later experimental features are described separately in the roadmap.

## What works in this snapshot

- **Think:** a native pen canvas with local pages, pressure-aware strokes, erasing, undo/redo, selection, move/remove operations and separate reply objects.
- **Tutor:** a goal notebook library, a bundled calculus study pack and creation of separate empty personal goals. Each goal has Overview, Lessons, Practice and Materials sections.
- **Original learning content:** ten short calculus lessons and fourteen deterministic practice families, with mathematical steps and verbal explanations.
- **Honest checking:** five families check a typed final numeric result; nine require explicit self-comparison with a worked solution. Handwriting, reasoning and symbolic equivalence are not automatically graded.
- **Visible evidence:** saved attempts distinguish independent work, hints and solution exposure. Repeated checks do not manufacture additional independent questions.
- **Local use:** lessons, generated practice, scratch paper, notes and persistence work without an AI request. Mathematical typesetting and fonts are bundled.
- **Optional assistance paths:** experimental signed-in model support and a client for the earlier manual companion bridge. Successful end-to-end live dot integration is not established by this snapshot. The companion server is not included here.

## The learning idea

The intended loop is **read → explain in your own words → retrieve → apply → check → revisit**. Only parts of this full loop are implemented today. Cornell notes, flashcards, spaced repetition and adaptive recommendations remain roadmap work for this snapshot.

“Evidence over affirmation” is a practical design rule. Tutor stores the difference between an independent answer and an answer produced after a hint. It does not label a lesson mastered simply because it was opened, or treat a self-check as an automatic grade. Its activity label “Ready for review” means two distinct independently correct numeric questions under a transparent local rule; it is not an estimate of mastery or exam readiness.

Hebbian learning—often summarized as “neurons that fire together wire together”—is an inspiration for connecting and repeatedly using ideas. It is not a claim that Plia measures synapses, implements a biological model or has demonstrated improved memory. Human learning studies on retrieval, spacing, feedback and transfer provide more direct design guidance. [Read the research and its limits](docs/RESEARCH.md).

## Why e-ink first

E-ink offers a useful initial constraint: stable pages, legible text, space to write and little motion. Plia explores a deliberate reading-and-practice experience on that surface. This is a design choice, not evidence that e-ink itself improves learning. Refresh behavior, ghosting, input latency, accessibility and comfort must be assessed on actual hardware. The learning model should remain useful on other displays, and typing remains important alongside handwriting.

AI makes answers easier to obtain. Plia's response is to preserve the learner's effort and distinguish assisted performance from later independent performance. Adaptability means applying an idea to a changed problem; resilience is an ambition to make correction, retrying and returning to work easier. Neither has been measured as a Plia outcome.

## Documentation

| Document | Contents |
| --- | --- |
| [Brainstorming and design evolution](docs/BRAINSTORMING.md) | Reconstructed decisions, motivations, tradeoffs and open questions |
| [Research](docs/RESEARCH.md) | Primary evidence, design implications and limits |
| [Architecture](docs/ARCHITECTURE.md) | Native UI, ink, learning evidence, persistence and assistance boundaries |
| [Status](docs/STATUS.md) | Implemented features, roadmap, validation and known limitations |
| [Build and release](docs/BUILD-AND-RELEASE.md) | Pinned tools, build steps, snapshot provenance and publication workflow |
| [Third-party notices](THIRD_PARTY_NOTICES.md) | KaTeX, Caveat, Gradle and Gson notices |

## Build

Open the repository root in Android Studio. Use **JDK 17**, **Android SDK Platform 35** and **Build Tools 35.0.0**. The included wrapper pins **Gradle 8.11.1**; the build pins **Android Gradle Plugin 8.9.2**.

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS/Linux, run `sh ./gradlew :app:assembleDebug`. The first build requires network access for tools and dependencies unless already cached. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [build and release](docs/BUILD-AND-RELEASE.md) before installation or publication.

No credentials, local SDK paths, user notebooks, device backups, assignment PDFs or prebuilt APKs are included. This snapshot does not declare a project-wide open-source license; third-party components retain their own licenses. Public source availability should not be confused with an unrestricted license for the first-party code, artwork or custom handwriting font.
