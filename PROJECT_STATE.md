# Detective project state

## Product promise
"Your modpack is lagging. Find the culprit."

Detective should turn technical profiling evidence into a simple diagnosis for normal Minecraft players and modpack maintainers.

## v0.2 scope — Controlled Validation Harness
Engine validation only. No polished GUI yet.

### Production subsystems
- Snapshot: pack state and version changes.
- Black Box: recent performance history.
- Watchdog: independent render-thread stack samples.
- Freeze Detector: adaptive long-frame detection and bounded incident worker.
- Suspect Analyzer: best-effort class -> mod attribution.
- Incident Store: atomic JSON evidence bundle under `<game directory>/detective`.

### Development-only validation
- `validation` source set loaded as the separate `detective_testculprit` mod by `runClient` only.
- Controlled 150/300/600/1200 ms render-thread stalls, burst, debounce-separated double freeze, and below-threshold case.
- Ground truth stored separately in `detective-validation/ground-truth.jsonl`.
- Post-hoc comparison against Detective incident JSON; ground truth never enters the detector.
- Lightweight watchdog and incident-worker overhead logs.
- The public JAR contains only `sourceSets.main`.

## Compatibility note
The public mod id and data directory changed from `moddetective` to `detective` in v0.2. Existing data is moved when possible; conflicts are retained in the legacy directory instead of being overwritten. The Java package remains `fr.apocalypsebleu.moddetective` to avoid a risky package-wide migration during engine validation.

## Non-goals for v0.2
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
