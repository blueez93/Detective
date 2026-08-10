# Mod Detective project state

## Product promise
"Your modpack is lagging. Find the culprit."

Mod Detective should turn technical profiling evidence into a simple diagnosis for normal Minecraft players and modpack maintainers.

## v0.1 scope
Engine proof-of-concept only. No polished GUI yet.

### Subsystems
- Snapshot: pack state and version changes.
- Black Box: recent performance history.
- Watchdog: independent render-thread stack samples.
- Freeze Detector: adaptive long-frame detection.
- Suspect Analyzer: best-effort class -> mod attribution.
- Incident Store: JSON evidence bundle.

## Non-goals for v0.1
- Exact GPU profiling.
- Server TPS profiling.
- Automatic disabling of mods.
- Claiming causal certainty.
- Cloud uploads.

## Future differentiators
- Compare regressions between launches.
- Config-file change tracking.
- One-click support report.
- Location-aware block/entity suspects.
- Friendly explanations instead of raw flamegraphs.
- Optional server companion module later.
