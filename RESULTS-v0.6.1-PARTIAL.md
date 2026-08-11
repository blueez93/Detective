# Detective v0.6.1 — Coverage Closure (partial)

Date: 2026-08-11  
Status: **PARTIAL — COVERAGE NOT VALIDATED**  
Runtime used for the new smoke runs: Minecraft 1.21.1, Java 21.0.9, NeoForge 21.1.248

This report records only work completed before the coverage campaign was stopped. Items that were not run are marked **NOT VALIDATED**, not failed. No v0.6 historical report was changed.

## Stop state

- No new Medium, Large or Stress soak was started after the stop request.
- The last Medium command had already reached its bounded timeout before the stop request.
- Orphaned validation Gradle processes from that timeout had already been stopped.
- A final process audit found `0` remaining Detective validation, Minecraft, Gradle or validation-server processes.
- No commit or tag was created.

## Existing v0.6 baseline — PASS (not rerun)

These results remain inherited from the immutable v0.6 reports. They were not re-executed during this partial v0.6.1 pass.

| Check | v0.6 result |
|---|---:|
| Unit tests | 96/96 PASS |
| NeoForge | 21.1.235 / 21.1.238 / 21.1.248 PASS |
| Controlled attribution Top-1 | 9/9 |
| Controlled attribution Top-3 | 9/9 |
| Confirmed false positives | 0 |
| Dropped incidents | 0 |
| Small Pack process duration | 20 min 59 s |
| Small Pack exploitable metrics window | 20.24 min |

### Small Pack stable performance baseline

| Metric | v0.6 baseline |
|---|---:|
| Samples/s | 47.81 |
| Mean capture | 178.8 µs |
| p50 | 109.7 µs |
| p95 | 311.3 µs |
| p99 | 464.9 µs |
| Maximum retained core state | 3.03 MiB |
| Maximum queue | 1 / 8 |
| Dropped incidents | 0 |
| Maximum Black Box entries | 3,556 |

## New v0.6.1 validation completed

### Large/Stress-size compatibility smoke — PASS

The development-only profile derived from **Technical Electrical: Striking Surprise 6.2.6** completed a real `runClient` smoke under NeoForge 21.1.248 and a 4 GiB maximum heap.

- Official pack input: 168 physical JARs from the Modrinth pack file.
- Development-runtime exclusions: 2 (documented below).
- Executed profile: 166 physical JARs.
- NeoForge mod IDs reported by Detective: **257 loaded mods**.
- World: `DetectiveValidation`, integrated singleplayer server.
- Successful command wall time: 6 min 34 s, including startup and shutdown.
- Detective reached `Ready`.
- Direct A 600 ms controlled incident: expected `detective_testculprit_a`, detected #1 `detective_testculprit_a`, Top-1 PASS, Top-3 PASS.
- Incident evidence: 651.6 ms duration, 165.4 ms threshold, 2 watchdog samples, Black Box present (246 samples), `minecraft:overworld`, coordinates present.
- Compatibility Support Report generation: PASS.
- Compatibility UI route with 3 incidents: PASS.
- Incident queue maximum: 1 / 8.
- Dropped incidents: 0.
- Watchdog shutdown marker present.
- Gradle `runClient` exit code: 0.

This was a compatibility smoke, **not** a 60-minute Large soak and **not** a 30-minute Stress soak. Although 257 mod IDs exceed the requested Stress size, no stable Stress-duration result may be inferred from this run.

## Pack inputs actually exercised

All third-party files remained under ignored `run/` validation directories and were never added to the Detective public source set or JAR.

| Candidate | Physical JARs | Result | Technical reason |
|---|---:|---|---|
| Create Ultimate 2.0.0 | 81 | Rejected before coverage | Create/Registrate reported unused register callbacks during registry initialization in the dev runtime. No Detective engine fault was identified. |
| Cobblemon Official Modpack 1.7.3 | 73 | Rejected before coverage | Connector Fabric discovery failed because `citresewn_neopatcher` required absent `citresewn`; NeoForge entered a broken mod state. |
| Farming Experience 26.08.07 | 98 | Rejected before coverage | Connector could not determine the clean Minecraft artifact path in the NeoGradle dev runtime, before Detective initialized. |
| Technical Electrical 6.2.6 | 168 original / 166 executed | Compatibility smoke PASS | Two dev-runtime-incompatible utilities were excluded declaratively; 257 mod IDs then loaded and the automated smoke completed. |
| Create: OneBlock 1.3 | 41 original / 39 attempted | Rejected before coverage | After excluding Connector and its sole Fabric mod, Create reported unused register callbacks and NeoForge entered a broken mod state. |

Rejected profiles and their logs/crash reports were retained under `run/validation-archives/` or their profile directory. They are evidence of pack/dev-runtime incompatibility, not Detective failures.

## Corrections and validation infrastructure

The following development/build validation changes were made. No production engine, ranking, threshold, watchdog, Black Box, persistence or UI implementation was changed.

- Version set to `0.6.1-alpha.1`.
- Isolated `run/coverage/<profile>-client` and `-server` working directories added for validation profiles.
- Optional validation heap, initial heap, window size, FPS, VSync and modded-dimension Gradle properties wired to development runs.
- A Modrinth `.mrpack` installer was added for validation-only inputs:
  - official pack URL and SHA-512 pinned;
  - every indexed file verified against its SHA-512;
  - shared cache and hard links used;
  - archive paths checked against traversal;
  - client/server environment flags respected;
  - third-party files installed only below ignored `run/` paths.
- The Technical Electrical count was corrected from 160 indexed files to 168 final JARs because its overrides add 8 JARs.
- `InventoryProfilesNext 2.2.5` was excluded from the Large dev profile after a reproducible null configuration delegate crash while rendering `LevelLoadingScreen` during quick-play. Crash report retained.
- `Neruina 3.3.3` was excluded after its intentional `test.ZombieMixin` repeatedly threw `RuntimeException` while ticking zombies and terminated the integrated server. Crash report retained.
- A native-NeoForge Medium subset script was drafted from the validated Large versions, but it was **not executed** after the stop request and has no validation result.

## Performance already measured

### v0.6 Small stable baseline

The stable v0.6 values are listed above and remain the only completed stable soak measurement.

### v0.6.1 Large startup smoke (transient only)

Sampling began while the 257-mod world and recipe/index systems were still loading. These are cumulative `phase=startup` observations, not stable gameplay measurements:

| Metric | Observed startup range / maximum |
|---|---:|
| Samples/s after sampling began | 23.12–41.5 |
| Mean capture | 1.46–10.50 ms |
| p50 | 250.1–302.3 µs |
| p95 | 3.86–50.88 ms |
| p99 | 27.43–236.70 ms |
| Maximum capture | 913.63 ms |
| Latency window max | 1,592 / 4,096 |
| Retained-state estimate peak observed | about 4.93 MiB (5,051.2 KiB) |
| Retained-state estimate at final sample | about 0.49 MiB |
| Black Box max observed | 491 |
| Queue max | 1 / 8 |
| Dropped incidents | 0 |
| JVM used heap during sampled startup | approximately 2.8–4.1 GB |

These startup values exceed the **stable** mean/p99 budget, but the stable budget cannot be evaluated from this run. Heavy world/recipe startup and the controlled incident are included. The result is therefore **NOT VALIDATED for stable Large performance**, not a stable-budget failure and not a PASS.

## Build and automated tests

- v0.6 baseline: build PASS, 96/96 tests PASS.
- v0.6.1 changes: **clean build NOT EXECUTED** after the validation-only edits.
- v0.6.1 unit tests: **NOT EXECUTED**.
- Final normal `runClient`: **NOT EXECUTED**.
- Public `detective-0.6.1-alpha.1.jar`: **NOT BUILT / NOT INSPECTED**.

## Coverage not executed — NOT VALIDATED

- Medium Pack 75–100 mods with 30 minutes exploitable soak.
- Large Pack 150–200 mods with 60 minutes exploitable soak.
- Stress Pack 200+ mods with 30 minutes exploitable soak.
- Total exploitable soak target of at least 140 minutes (only the inherited 20.24-minute Small baseline exists).
- Stable Medium/Large/Stress watchdog mean, p50, p95, p99 and maximum.
- Stable worker mean/p95/max, heap range and memory-leak comparison.
- Full v0.6.1 controlled attribution matrix (only one Direct A compatibility case ran; inherited v0.6 remains 9/9).
- Repeated Overworld → End → Overworld transitions.
- A real modded dimension and return to vanilla.
- Sustained real Mekanism machine/cable/energy/interface activity.
- Dedicated-server client-only multiplayer connect/disconnect/reconnect cycles.
- 8 GiB profile; 2 GiB and 12 GiB optional profiles.
- 30 / 60 / 120+ FPS caps and VSync ON/OFF.
- Runtime history with 100 incidents: open, scroll, detail, sort, settings, export, clear and restart.
- Multiple large-pack Support Reports, collision naming and full final-ZIP privacy scan.
- Automated UI matrix for small/large screens, English/French, long text, 100 incidents, large Black Box and every attribution state.
- Final NeoForge 21.1.235 / 21.1.238 / 21.1.248 smoke matrix after v0.6.1 changes.
- Crash recovery and data-corruption reruns specific to the v0.6.1 artifact.

## Manual validation still required

- Physical Alt+Tab: 5 seconds, 30 seconds, repeated cycles.
- Click `Open Folder` and verify the exact Explorer path.
- Mouse: every button, scrolling, tooltips, `Why this suspect?`, `View Technical Evidence`.
- Keyboard: Tab, Shift+Tab, Enter, Escape.
- GUI scale: Auto, 1, 2, 3, 4.
- Resolutions: 854×480, 1280×720, 1920×1080, 2560×1440.
- Windowed, fullscreen, and resizing while Detective is open.
- English and French visual/wording review.

## Files changed or created so far

- `build.gradle` — development validation profile/run parameters.
- `gradle.properties` — target version `0.6.1-alpha.1`.
- `validation-pack/coverage-packs.json` — pinned validation pack manifest and documented exclusions.
- `validation-pack/Install-CoveragePack.ps1` — validation-only verified installer.
- `validation-pack/New-CoverageSubset.ps1` — drafted, not executed or validated.
- `RESULTS-v0.6.1-PARTIAL.md` — this report.

## Current conclusion

The v0.6 engine baseline remains valid, and one 257-mod-ID compatibility smoke completed without a Detective crash, queue saturation or dropped incident. This is useful evidence, but it does not close the requested coverage.

**v0.6.1 coverage status: NOT VALIDATED (campaign intentionally stopped).**

No GO/NO-GO toward v0.7 is asserted from this partial run.
