# Build and release

## Pinned inputs

| Input | Version / setting |
| --- | --- |
| Java | JDK 17 |
| Gradle wrapper | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Compile / target SDK | 35 / 35 |
| Minimum SDK | 26 |
| Android Build Tools | 35.0.0 recommended for this toolchain |
| App ID | `com.plia.notebook` |
| App version | 0.1.23, code 24 |
| Runtime Maven dependency | Gson 2.10.1 |
| Bundled math renderer | KaTeX 0.19.0 |

The wrapper scripts, JAR and properties are included. `distributionSha256Sum` pins the downloaded Gradle distribution to `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. Dependency repositories are Google Maven and Maven Central; plugin resolution also uses the Gradle Plugin Portal.

## Local setup

1. Install a JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0, accepting the SDK licenses as appropriate.
2. Open the repository root in Android Studio. Choose JDK 17 for Gradle.
3. Configure your own SDK location through Android Studio, `ANDROID_HOME`, or an untracked `local.properties` containing `sdk.dir=...`. No developer-machine SDK path is supplied.
4. Build using the included wrapper.

Windows:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

macOS/Linux:

```sh
sh ./gradlew --no-daemon :app:assembleDebug
```

After all dependencies are cached, `--offline` can be added. A fresh machine normally needs network access for Gradle, the Android plugin and dependencies. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

These pinned inputs support repeatable source builds; they are not a claim of byte-identical APKs across machines. Debug signing keys, tool installations and build metadata can differ. The snapshot's initial development build succeeded with installed tools and caches. A new offline Gradle check of the curated package on 17 October 2026 stopped before compilation because its restricted environment could not connect to the local Gradle daemon. All 46 curated Java files were separately compiled with JDK 17 against Android API 35 and Gson 2.10.1. Java compilation does not validate Android resource packaging or produce a verified APK; this is not a successful APK rebuild.

## Running and verifying

Use an emulator for basic UI checks and an actual tablet for input and e-ink behavior. Preserve an existing installation's data before trying another build. Installing an update requires a compatible signing key; do not uninstall a valuable notebook merely to resolve a signing mismatch. Android Studio's Run action also builds and installs, so avoid competing deployments from older project copies.

A useful manual pass covers writing/erasing/undo, save/reopen, goal isolation, typed numeric checking, a self-review exercise, sticky hint exposure, local math display and export/import. The debug manifest exposes `MathPreviewActivity`, `InputPreviewActivity` and `MovePreviewActivity` with synthetic fixtures. Those previews do not prove physical pen performance or live AI operation.

No complete historical host-test harness or continuous-integration pipeline is included. Do not report all development checks as newly rerun merely because the app builds.

## Curated snapshot provenance

The package was copied through an explicit whitelist: root Gradle configuration and wrapper, `gradle/`, `app/build.gradle`, and `app/src/`. It excludes caches, build outputs, IDE state, local SDK configuration, account data, user notebooks, device backups, assignment PDFs and operational logs. New public documentation was authored for this snapshot.

Publication cleanup affects four source files. `TutorCatalog.java` omits an inconsistent future provenance date and uses the neutral visible goal title “Calculus practice.” `TutorMaterials.java` omits that date, six private Library identifiers and original assignment filenames, replacing the latter with generic source labels. `TutorLibraryStore.java` and `TutorActivity.java` use the same neutral visible goal wording. Internal storage identifiers remain unchanged for compatibility. These metadata and wording changes do not alter lessons, exercise parameters or checking behavior. First-party source otherwise reflects the stable 0.1.23 implementation, rather than the separate experimental candidate.

## GitHub publication workflow

This document describes a release process; it does not claim publication has already occurred.

1. Review the whitelist, third-party notices, first-party licensing position and secret scan.
2. Build the curated snapshot and record the result. Keep build outputs and local configuration out of Git.
3. Initialize the intended repository, review the staged file list and commit the source plus documentation. Set an explicit repository visibility.
4. Push to the verified repository. Record the real repository URL and commit only after the push succeeds.
5. If distributing binaries, prepare a separate signed release, test it on the target hardware and document its exact version and limitations. Do not expose a development signing key.

The debug manifest permits cleartext traffic for local development and exports QA activities. The main manifest disallows cleartext. A successful debug build alone is not a security review or a production release.

## Licensing and assets

Retain all bundled third-party notices. This repository does not choose a blanket license for first-party code, custom artwork or the personalized handwriting font. See [third-party notices](../THIRD_PARTY_NOTICES.md). The custom font is a project asset and is not covered by the adjacent Caveat OFL notice or the KaTeX license merely because it shares an asset directory.
