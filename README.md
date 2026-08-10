# Detective — v0.2 Controlled Validation Harness

Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21.

Detective is a client-only diagnostic mod that records evidence around render-thread freezes and ranks the non-vanilla mods observed in watchdog stack samples. A suspect score is evidence, not proof of causality.

## Production engine

- Captures installed mods and versions, then compares them with the previous launch.
- Keeps a 30-second Black Box of frame time, FPS, JVM memory, dimension, and player position.
- Samples the render thread every 20 ms from an independent low-priority watchdog.
- Detects freezes at `max(120 ms, 6 × rolling median frame time)` after a warm-up baseline.
- Resolves Java classes and JAR/package ownership through NeoForge metadata.
- Ranks up to five suspects by their share of watchdog samples.
- Processes incidents on a bounded worker queue and atomically stores JSON under `<game directory>/detective/incidents/`.
- Performs no network calls or telemetry and is not required on a server.

The internal Java package remains `fr.apocalypsebleu.moddetective` for v0.2 to avoid a high-risk package-only rename. The public mod id, artifact, display name, assets, logs, and data directory are all `detective`/`Detective`.

## Legacy data migration

At startup, Detective checks `<game directory>/moddetective`. If the new `detective` directory does not exist, the legacy directory is moved as a unit. If both exist, non-conflicting files are moved into `detective`; conflicting files are left untouched in the legacy directory.

## Development validation mod

`src/validation` contains the separate NeoForge mod `detective_testculprit`. NeoGradle adds it only to `runClient`; it is never part of `build/libs/detective-<version>.jar`.

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
/detective_validate metrics
```

`all` schedules the four primary durations and an 80 ms negative case. `burst` schedules four 150 ms stalls inside the debounce window. `double` schedules two 600 ms freezes farther apart than the two-second debounce.

Ground truth is written separately to `run/client/detective-validation/ground-truth.jsonl`. A validation worker reads newly produced incident JSON and logs `Expected`, `Detected #1`, rank, sample share, completeness, and `PASS`/`FAIL`. Ground truth is never passed to Detective's detection or attribution code.

## Build and test

The repository includes the Gradle 9.2.1 wrapper. A Java 21 JDK is required.

```powershell
.\gradlew.bat build
.\gradlew.bat test
.\gradlew.bat runClient
```

For unattended local validation against an existing world named `DetectiveValidation`:

```powershell
.\gradlew.bat runClient -PdetectiveValidationWorld=DetectiveValidation -PdetectiveValidationAutorun=all -PdetectiveValidationExit=true
```

The same autorun can connect to a local validation server with `-PdetectiveValidationServer=127.0.0.1` instead of the singleplayer world property. This development-only path waits for client loading to finish and then uses Minecraft's normal `ConnectScreen` API.

The build compiles the validation source set so harness API breakage is caught, but the public JAR task packages only `sourceSets.main`.

During a development run, inspect:

- `run/client/logs/latest.log` for `[Detective]` and `[Detective Validation]`;
- `run/client/detective/snapshots/last-session.json`;
- `run/client/detective/incidents/*.json`;
- `run/client/detective-validation/ground-truth.jsonl`.

## Scope

v0.2 validates the engine. It deliberately excludes the final dashboard, graphics work, cloud uploads, telemetry, server profiling, Fabric support, monetization, update services, and natural-language diagnosis.
