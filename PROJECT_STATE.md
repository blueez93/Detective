# Detective project state

## Product promise
"Your modpack is lagging. Find the culprit."

Detective should turn technical profiling evidence into a simple diagnosis for normal Minecraft players and modpack maintainers.

## v0.3 scope — Real World Validation
Engine validation in a representative NeoForge modpack. No polished GUI yet.

### Production subsystems
- Snapshot: pack state and version changes.
- Black Box: recent performance history.
- Watchdog: independent render-thread stack samples.
- Freeze Detector: adaptive long-frame detection and bounded incident worker.
- Suspect Analyzer: best-effort class -> mod attribution.
- Incident Store: atomic JSON evidence bundle under `<game directory>/detective`.
- Attribution Evidence: explicit attributed/insufficient/GC/native-or-driver/unknown state without a fabricated culprit.

### Development-only validation
- Three source sets loaded as separate `detective_testculprit_a`, `_b`, and `_c` mods by `runClient` only.
- Controlled 150/300/600/1200 ms render-thread stalls, burst, debounce-separated double freeze, and below-threshold case.
- Ground truth stored separately in `detective-validation/ground-truth.jsonl`.
- Post-hoc comparison against Detective incident JSON; ground truth never enters the detector.
- Lightweight watchdog and incident-worker overhead logs.
- Bounded 4,096-sample latency window with average, p50, p95, p99, and cumulative maximum.
- Direct A/B/C, indirect A -> B, and nested A -> B -> C attribution scenarios with Top-1/Top-3 aggregation.
- 30-minute phased soak against an 11-mod pinned realistic pack, including chunk generation, menus, dimensions, resource reload, GC pressure, pause, alt-tab, and reconnect.
- The public JAR contains only `sourceSets.main`.

### v0.3 measured result
- 30-minute, 11-mod local-server session completed with a clean client shutdown.
- Watchdog: 47.77 samples/s average; median rolling p50/p95/p99 106.2/281.9/571.0 us; queue 0/8; zero drops.
- Detective retained-memory estimate peaked at 2.93 MiB.
- Focused multi-culprit run: Top-1 3/5 (60%), Top-3 5/5 (100%). Direct A/B/C rank first; indirect/nested stacks expose deterministic tie limitations.
- Two real false positives exposed and retained in the raw evidence: dimension transition and focus restoration. Confidence gating and resume continuity were corrected from those observations.
- Detailed measurements and limitations are in `validation-pack/RESULTS-v0.3.md`.

## Compatibility note
The public mod id and data directory changed from `moddetective` to `detective` in v0.2. Existing data is moved when possible; conflicts are retained in the legacy directory instead of being overwritten. The Java package remains `fr.apocalypsebleu.moddetective` to avoid a risky package-wide migration during engine validation.

## Non-goals for v0.3
- Exact GPU profiling.
- Server TPS profiling.
- Automatic disabling of mods.
- Claiming causal certainty.
- Cloud uploads or telemetry.
- Final dashboard UI.

## Future differentiators
- Compare regressions between launches.
- Config-file change tracking.
- One-click support report.
- Location-aware block/entity suspects.
- Friendly explanations instead of raw flamegraphs.
- Optional server companion module later.
