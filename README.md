# Detective — v0.4 First Usable UI

Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21.

Detective is a client-only diagnostic mod that records evidence around render-thread freezes and ranks the non-vanilla mods observed in watchdog stack samples. A suspect score is evidence, not proof of causality.

Its product-copy rule is: **Detect. Measure. Explain. Never accuse.**

## Production engine

- Captures installed mods and versions, then compares them with the previous launch.
- Keeps a 30-second Black Box of frame time, FPS, JVM memory, dimension, and player position.
- Samples the render thread every 20 ms from an independent low-priority watchdog.
- Detects freezes at `max(120 ms, 6 × rolling median frame time)` after a warm-up baseline.
- Resolves Java classes and JAR/package ownership through NeoForge metadata.
- Collects presence, leaf ownership, first-frame depth, repeated leaf ownership, caller-only observations, and stack diversity for up to five suspects.
- Ranks probable suspects by ownership of the first modded frame nearest the active execution point. Presence share remains preserved as evidence and as a deterministic fallback, but no ground truth enters the ranker.
- Stores an explicit attribution-evidence state (`ATTRIBUTED`, `AMBIGUOUS_ATTRIBUTION`, `INSUFFICIENT_EVIDENCE`, `JVM_GC_SUSPECTED`, `NATIVE_OR_DRIVER_STALL_POSSIBLE`, or `UNKNOWN`) instead of inventing a suspect when stacks are not attributable. Ranking and confidence are independent.
- Processes incidents on a bounded worker queue and atomically stores JSON under `<game directory>/detective/incidents/`.
- Performs no network calls or telemetry and is not required on a server.

The internal Java package remains `fr.apocalypsebleu.moddetective` for v0.4 to avoid a high-risk package-only rename. The public mod id, artifact, display name, assets, logs, and data directory are all `detective`/`Detective`.

## Legacy data migration

At startup, Detective checks `<game directory>/moddetective`. If the new `detective` directory does not exist, the legacy directory is moved as a unit. If both exist, non-conflicting files are moved into `detective`; conflicting files are left untouched in the legacy directory.

## User interface

Detective adds a client-only button to the top-right of both the Minecraft title screen and pause menu. The first alpha UI contains:

- a home screen with monitoring status, current-session/recent incident counts, and the latest primary suspect or cautious special state;
- a newest-first scrollable incident list with duration, evidence badge, date, dimension, and compact coordinates;
- a scrollable incident detail with duration/threshold/context, primary and other suspects, raw evidence metrics, and a peak-preserving 2D Black Box frametime graph;
- a modpack changes screen for added, removed, and updated mods since the previous launch;
- explicit empty and degraded states for missing files, partial Black Box data, no suspect, ambiguous attribution, insufficient evidence, JVM/GC, native/driver, and unknown cases.

Screens consume immutable UI view models rather than engine records. Incident indexing and JSON parsing run once on a low-priority client data worker when the UI opens; summaries skip Black Box arrays while streaming, and a full incident is loaded lazily only for its detail screen. No parsing is performed every render frame.

## Development validation harness

The harness is split into three real NeoForge development mods with separate source sets, packages, metadata, and mod ids:

- `src/validation`: controller plus `detective_testculprit_a`;
- `src/validationB`: `detective_testculprit_b`, used as an indirect library;
- `src/validationC`: `detective_testculprit_c`, used for scheduled/standard-library and nested stacks.

NeoGradle adds them only to `runClient`; none is part of `build/libs/detective-<version>.jar`.

With a world loaded, these local client commands are available:

```text
/detective_validate 150
/detective_validate 300
/detective_validate 600
/detective_validate 1200
/detective_validate below
/detective_validate burst
/detective_validate double
/detective_validate all
/detective_validate direct_b
/detective_validate scheduled_c
/detective_validate indirect_b
/detective_validate nested_c
/detective_validate evidence
/detective_validate focus
/detective_validate metrics
```

`all` schedules the four primary durations and an 80 ms negative case. `burst` schedules four 150 ms stalls inside the debounce window. `double` schedules two 600 ms freezes farther apart than the two-second debounce.

Ground truth is written separately to `run/client/detective-validation/ground-truth.jsonl`. A validation worker reads newly produced incident JSON and logs `Expected`, `Detected #1`, rank, sample share, Top-1/Top-3, completeness, and `PASS`/`FAIL`. Ground truth is never passed to Detective's detection or attribution code.

Development metrics are appended every five seconds to `overhead-metrics.jsonl`, including samples/second, average/p50/p95/p99/max capture cost, bounded-buffer sizes, worker queue state, dropped incidents, JVM heap, and a labelled retained-memory estimate. Stack evidence is stored in `stack-evidence-results.jsonl`. Phase results distinguish negative-phase incident candidates from manually confirmed false positives.

## Realistic validation pack

`validation-pack/modrinth-pack.json` pins 11 NeoForge 1.21.1 mods by official Modrinth URL and SHA-512. The pack covers optimization, rendering, content, world generation, machines/block entities, libraries, and inventory/QoL. Third-party JARs are downloaded only into ignored `run/client/mods` and `run/server/mods` directories and are never packaged or redistributed with Detective.

```powershell
.\gradlew.bat prepareValidationPack
.\gradlew.bat verifyValidationPack
```

See `validation-pack/README.md` for exact versions, licenses, selection rationale, and the 30-minute validation procedure.
The immutable v0.3 baseline is in `validation-pack/RESULTS-v0.3.md`; v0.3.1 comparisons, GC correlation, stack evidence, and limitations are in `validation-pack/RESULTS-v0.3.1.md`.

## Build and test

The repository includes the Gradle 9.2.1 wrapper. A Java 21 JDK is required.

```powershell
.\gradlew.bat clean build --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat runClient
```

For unattended local validation against an existing world named `DetectiveValidation`:

```powershell
.\gradlew.bat runClient -PdetectiveValidationWorld=DetectiveValidation -PdetectiveValidationAutorun=all -PdetectiveValidationExit=true
```

The same autorun can connect to a local validation server with `-PdetectiveValidationServer=127.0.0.1` instead of the singleplayer world property. This development-only path waits for client loading to finish and then uses Minecraft's normal `ConnectScreen` API.

The real-world phase plan is launched with:

```powershell
.\gradlew.bat runClient -PdetectiveValidationPack=true -PdetectiveValidationServer=127.0.0.1 -PdetectiveValidationAutorun=realworld -PdetectiveValidationSoakMinutes=30 -PdetectiveValidationExit=true
```

It waits for a loaded world and at least 60 Black Box frames, then covers stable gameplay, rapid chunk generation, an inventory/JEI screen, dimension changes, resource reload, explicit GC pressure, direct and indirect attribution, pause, alt-tab, disconnect/reconnect, final stability, and shutdown.

The focused multi-culprit matrix can be repeated independently with `-PdetectiveValidationAutorun=attribution` and `-PdetectiveValidationExit=true`.

The nine-shape stack study uses `-PdetectiveValidationAutorun=evidence`; the isolated focus continuity replay uses `-PdetectiveValidationAutorun=focus`. Add `-PdetectiveValidationGcLogging=true` to a GC or real-world run to create a local unified JVM log, and optionally tune `detectiveValidationGcPressureMiB` and `detectiveValidationGcPasses`. These settings and all pressure code remain development-only.

The development-only v0.4 screen route uses an existing world and writes local screenshots under `run/client/screenshots`:

```powershell
.\gradlew.bat runClient --no-daemon -PdetectiveValidationWorld=DetectiveValidation -PdetectiveValidationAutorun=ui -PdetectiveValidationExit=true
```

The build compiles the validation source set so harness API breakage is caught, but the public JAR task packages only `sourceSets.main`.

During a development run, inspect:

- `run/client/logs/latest.log` for `[Detective]` and `[Detective Validation]`;
- `run/client/detective/snapshots/last-session.json`;
- `run/client/detective/incidents/*.json`;
- `run/client/detective-validation/ground-truth.jsonl`.
- `run/client/detective-validation/overhead-metrics.jsonl`;
- `run/client/detective-validation/phase-results.jsonl`;
- `run/client/detective-validation/stack-evidence-results.jsonl`;
- `run/client/detective-validation/gc-markers.jsonl` and `run/client/gc-validation.log` when GC logging is enabled.

## Scope

v0.4 provides the first stable, usable UI over the validated engine. It deliberately excludes final visual polish, complex live overlays, advanced exports, cloud uploads, telemetry, server profiling, Fabric support, 3D world localization, monetization, update services, and natural-language diagnosis.
