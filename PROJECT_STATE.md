# Detective project state

## Product promise
"Your modpack is lagging. Find the culprit."

Detective should turn technical profiling evidence into a simple diagnosis for normal Minecraft players and modpack maintainers.

## v0.3.1 scope — Revalidation and stack evidence study
Engine revalidation in a representative NeoForge modpack, with leaf/depth evidence for nested stacks. No polished GUI yet.

### Production subsystems
- Snapshot: pack state and version changes.
- Black Box: recent performance history.
- Watchdog: independent render-thread stack samples.
- Freeze Detector: adaptive long-frame detection and bounded incident worker.
- Suspect Analyzer: best-effort class -> mod attribution with presence, leaf ownership, depth, repetition, caller-only, and stack-diversity evidence.
- Incident Store: atomic JSON evidence bundle under `<game directory>/detective`.
- Attribution Evidence: explicit attributed/ambiguous/insufficient/GC/native-or-driver/unknown state without a fabricated culprit.

### Development-only validation
- Three source sets loaded as separate `detective_testculprit_a`, `_b`, and `_c` mods by `runClient` only.
- Controlled 150/300/600/1200 ms render-thread stalls, burst, debounce-separated double freeze, and below-threshold case.
- Ground truth stored separately in `detective-validation/ground-truth.jsonl`.
- Post-hoc comparison against Detective incident JSON; ground truth never enters the detector.
- Lightweight watchdog and incident-worker overhead logs.
- Bounded 4,096-sample latency window with average, p50, p95, p99, and cumulative maximum.
- Direct A/B/C, indirect A -> B, nested A -> B -> C, and four non-alphabetical permutations with Top-1/Top-3 aggregation.
- 30-minute phased soak against an 11-mod pinned realistic pack, including chunk generation, menus, dimensions, resource reload, GC pressure, pause, alt-tab, and reconnect.
- Development-only GC pressure runs off the render thread and emits monotonic/epoch markers for correlation with optional unified JVM GC logs.
- Phase incident JSON is analyzed on validation workers, not the render thread.
- The public JAR contains only `sourceSets.main`.

### v0.3.1 measured result
- The immutable v0.3 baseline remains in `validation-pack/RESULTS-v0.3.md`.
- Leaf ownership was selected after an experiment: presence alone scored 4/8 Top-1 while leaf ownership, presence-then-leaf, and depth scored 8/8. In the nine-case replay, leaf ownership scored 9/9 and remained 9/9 in the valid long soak; the combined presence-first model fell to 7/9 under one-sample noise.
- Historical five-scenario Top-1 improved from 3/5 (60%) to 5/5 (100%); Top-3 remains 100%. Four additional permutations also rank the active leaf owner first.
- Valid 30-minute soak: stable means 130.1 us initially and 246.0 us after reconnect, rolling stable p99 411.8/1,737.7 us, retained estimate 2.90 MiB, queue 0/8, and zero drops.
- Three controlled Full G1 pauses exceeded the 120 ms threshold. Detective never accused a mod; external GC logs exposed the current limitation that stop-the-world pauses often provide no `System.gc` frame to the watchdog.
- The soak exposed a harness phase-analysis stall and a focus-restore spillover. JSON phase analysis now runs on workers and three active frames are skipped after suspension. A focused post-fix runtime replay produced zero incidents across iconify, restore, recovery, and stable phases.
- Detailed measurements, exclusions, and the UI recommendation are in `validation-pack/RESULTS-v0.3.1.md`.

## Compatibility note
The public mod id and data directory changed from `moddetective` to `detective` in v0.2. Existing data is moved when possible; conflicts are retained in the legacy directory instead of being overwritten. The Java package remains `fr.apocalypsebleu.moddetective` to avoid a risky package-wide migration during engine validation.

## Non-goals for v0.3.1
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
