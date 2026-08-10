# Mod Detective — v0.1 engine validation

Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21.

## Implemented engine

- Captures the loaded mod list + versions at startup.
- Compares the current list against the previous launch.
- Stores a SHA-256 fingerprint of the pack state.
- Keeps a 30-second rolling black box of frame time, approximate FPS, JVM memory, dimension and player position.
- Runs a lifecycle-managed, low-priority watchdog that samples the Minecraft render thread every 20 ms.
- Detects abnormal frames with an adaptive threshold: max(120 ms, 6x rolling median frame time).
- On a detected freeze, inspects watchdog samples from the exact frame interval.
- Attributes stack classes through NeoForge SecureJar package metadata, with a code-source fallback.
- Ranks up to five suspects by the percentage of freeze samples in which their code appeared.
- Analyzes and saves incidents away from the render thread.
- Atomically saves JSON reports under `<game directory>/moddetective/incidents/`.

## Build

The repository includes the standard Gradle 9.2.1 wrapper used by the current NeoForge 1.21.1 NeoGradle MDK. A Java 21 JDK is required.

On Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

On Linux/macOS:

```text
./gradlew build
./gradlew runClient
```

Focused JUnit tests cover adaptive freeze thresholds, snapshot comparisons, Black Box retention, and suspect ranking.

## First test

Launch a world and play for a minute. Mod Detective should create:

- `run/client/moddetective/snapshots/last-session.json` in a dev run.
- JSON reports in `run/client/moddetective/incidents/` when a long frame is detected.

In `run/client/logs/latest.log`, search for `[Mod Detective]`. On normal shutdown, the log should also report that the watchdog stopped.

## Design rule

A suspect score is **evidence**, not proof. A mod appearing in a sampled stack during a freeze is correlated with the freeze, but the root cause can be another mod, vanilla code, a driver, GC, disk I/O, or a dependency. The UI must always communicate this as a confidence/suspicion score rather than an accusation.

## Next milestone — v0.2 UI

1. F8 (or configurable key) opens the Mod Detective dashboard.
2. Timeline of the last 30 seconds.
3. Incident cards with duration, position, RAM and suspects.
4. “What changed?” screen showing added/removed/updated mods.
5. Export Support Report ZIP.
6. Better class-to-mod resolution for nested/Jar-in-Jar mods.
7. GC pause sampling and chunk/entity counters.
