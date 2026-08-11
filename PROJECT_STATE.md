# Detective project state

## Product promise
"Detect. Measure. Explain. Never accuse."

Detective should turn technical profiling evidence into a clear, cautious investigation for normal Minecraft players and modpack maintainers. Detective finds the evidence; the player makes the call.

## v0.5 scope — Support & Daily Use
A local-only support workflow over the validated v0.3.1 engine and v0.4.1 investigation UI. A recorded incident can produce a privacy-previewed, versioned support ZIP; essential settings control notifications, bounded history retention, and the default evidence view. The production attribution algorithm remains unchanged. Product copy follows: “Detect. Measure. Explain. Never accuse.”

### Production subsystems
- Snapshot: pack state and version changes.
- Black Box: recent performance history.
- Watchdog: independent render-thread stack samples.
- Freeze Detector: adaptive long-frame detection and bounded incident worker.
- Suspect Analyzer: best-effort class -> mod attribution with presence, leaf ownership, depth, repetition, caller-only, and stack-diversity evidence.
- Incident Store: atomic JSON evidence bundle under `<game directory>/detective`.
- Attribution Evidence: explicit attributed/ambiguous/insufficient/GC/native-or-driver/unknown state without a fabricated culprit.
- UI data layer: tolerant incident/modpack adapters, immutable view models, newest-first index, lazy detail loading, and a single low-priority bounded-lifecycle data worker.
- Client UI: entry buttons on the title and pause screens, session summary, scrollable incident list, incident detail with a simple 2D Black Box graph, and modpack changes.
- Daily-use support: cooldown-protected vanilla incident notifications, local support ZIP export, atomic settings, and count/age history retention.
- Support report: UTF-8 human summaries plus schema-versioned allow-listed JSON. It excludes `latest.log`, personal paths, account/session data, server addresses, JARs, and all automatic upload behavior.

### v0.3.1 engine baseline
- The immutable v0.3 and v0.3.1 measurements remain in `validation-pack/RESULTS-v0.3.md` and `validation-pack/RESULTS-v0.3.1.md`.
- Production ranking remains leaf-ownership-first with presence as preserved evidence/fallback. v0.4 added display tiers and v0.5 adds support workflows only; neither tunes detection, attribution, engine debounce, or confidence states.

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
- The UI screenshot route covers the v0.4.1 investigation states plus v0.5 export preview/success, settings, clear-history confirmation, and notification cooldown. Accessibility onboarding and the experimental-world confirmation are handled only in this development route.

### v0.3.1 measured result
- The immutable v0.3 baseline remains in `validation-pack/RESULTS-v0.3.md`.
- Leaf ownership was selected after an experiment: presence alone scored 4/8 Top-1 while leaf ownership, presence-then-leaf, and depth scored 8/8. In the nine-case replay, leaf ownership scored 9/9 and remained 9/9 in the valid long soak; the combined presence-first model fell to 7/9 under one-sample noise.
- Historical five-scenario Top-1 improved from 3/5 (60%) to 5/5 (100%); Top-3 remains 100%. Four additional permutations also rank the active leaf owner first.
- Valid 30-minute soak: stable means 130.1 us initially and 246.0 us after reconnect, rolling stable p99 411.8/1,737.7 us, retained estimate 2.90 MiB, queue 0/8, and zero drops.
- Three controlled Full G1 pauses exceeded the 120 ms threshold. Detective never accused a mod; external GC logs exposed the current limitation that stop-the-world pauses often provide no `System.gc` frame to the watchdog.
- The soak exposed a harness phase-analysis stall and a focus-restore spillover. JSON phase analysis now runs on workers and three active frames are skipped after suspension. A focused post-fix runtime replay produced zero incidents across iconify, restore, recovery, and stable phases.
- Detailed measurements, exclusions, and the UI recommendation are in `validation-pack/RESULTS-v0.3.1.md`.

### v0.4 validation result
- Minecraft 1.21.1 / NeoForge 21.1.235 loaded Detective 0.4.0-alpha.1 with the pinned realistic validation pack.
- Both client-only menu entry buttons and all four screens rendered in an 854 × 480 client using persisted real incidents; the no-incident path was also rendered without mutating stored data.
- Incident summaries are streamed without materializing their large Black Box arrays. Full JSON and peak-preserving graph downsampling are loaded once, off the render thread, only for the selected detail.
- The final runtime route produced eight screenshots, including an attributed detail and its scrolled Black Box, and stopped the watchdog and integrated server cleanly. A chunk/world-entry stall before the route produced `INSUFFICIENT_EVIDENCE`; it is retained as an honest runtime observation rather than filtered for UI validation.
- Detailed results are in `validation-pack/RESULTS-v0.4.md`.

### v0.5 validation result
- `clean build` and the separate test run pass with 71 tests (53 preserved and 18 new support/privacy tests).
- The final realistic `runClient` loaded 21 mods on Minecraft 1.21.1 / NeoForge 21.1.235, captured all investigation/support screens in English and the long v0.5 layouts in French, produced a real 4.7 KiB support ZIP, and shut down cleanly.
- A four-incident development burst displayed one cautious vanilla toast. Notification suppression is UX-only and does not modify detection or persistence.
- The runtime ZIP contained ten allow-listed entries and eight valid schema-v1 JSON documents; it contained no `latest.log` or audited account/session/path/network identifiers.
- The public `detective-0.5.0-alpha.1.jar` contains only `META-INF`, Detective assets, and `fr.apocalypsebleu.moddetective` classes. Detailed results are in `RESULTS-v0.5.md`.

## Compatibility note
The public mod id and data directory changed from `moddetective` to `detective` in v0.2. Existing data is moved when possible; conflicts are retained in the legacy directory instead of being overwritten. The Java package remains `fr.apocalypsebleu.moddetective` to avoid a risky package-wide migration during engine validation.

## Non-goals for v0.5
- Exact GPU profiling.
- Server TPS profiling.
- Automatic disabling of mods.
- Claiming causal certainty.
- Cloud uploads, accounts, telemetry, analytics, or update checks.
- Automatic `latest.log` inclusion, JVM dumps, screenshots, JARs, saves, or large diagnostics.
- Final visual polish, complex real-time overlay, advanced export, automatic mod/config changes, or AI diagnosis.

## Future differentiators
- Compare regressions between launches.
- Config-file change tracking.
- Optional, explicitly warned Minecraft-log attachment.
- Location-aware block/entity suspects.
- Friendly explanations instead of raw flamegraphs.
- Optional server companion module later.
