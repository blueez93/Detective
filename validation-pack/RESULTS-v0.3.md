# Detective v0.3 real-world validation results

## Environment and duration

- Minecraft 1.21.1, NeoForge 21.1.235, Java 21.
- Eleven pinned third-party mods from `modrinth-pack.json` plus three development-only culprit mods.
- One automated 30-minute client session against a local dedicated server, followed by a focused five-scenario attribution run.
- 369 overhead observations at five-second intervals. Percentile columns below are the median of the reported rolling 4,096-sample percentiles; they are not reconstructed whole-session raw percentiles.

## Watchdog measurements

| Phase | Samples/s | p50 us | p95 us | p99 us | Maximum us | Incidents |
|---|---:|---:|---:|---:|---:|---:|
| Stable gameplay | 48.00 | 101.3 | 212.5 | 357.5 | 32,371.4 | 0 |
| Rapid chunk generation | 47.96 | 114.2 | 300.7 | 662.1 | 43,529.8 | 0 |
| Large inventory/JEI menu | 47.94 | 102.1 | 275.8 | 700.6 | 43,529.8 | 0 |
| Dimension change | 47.96 | 97.1 | 220.4 | 409.6 | 53,177.2 | 1 |
| Resource reload | 47.96 | 95.8 | 208.6 | 340.3 | 446,081.8 | 0 |
| Explicit GC pressure | 47.98 | 95.8 | 211.6 | 338.0 | 446,081.8 | 0 |
| Whole session | 47.77 | 106.2 | 281.9 | 571.0 | 446,081.8 | 2 |

The cumulative maximum is intentionally sensitive to one-off OS/JVM scheduling delays. Resource reload caused the 446 ms capture outlier but no incident. The incident queue remained at 0/8 and dropped incidents remained zero throughout.

Whole-JVM used heap ranged from 281.5 MiB to 1,088.2 MiB and repeatedly returned after collection. The labelled Detective retained-memory estimate peaked at 2.93 MiB. This is a conservative shallow estimate, not a JVM instrumentation measurement.

## Attribution

| Scenario | Expected | Detected #1 | Expected rank | Share | Result |
|---|---|---|---:|---:|---|
| Direct A | `detective_testculprit_a` | A | 1 | 96.8% | PASS |
| Direct B | `detective_testculprit_b` | B | 1 | 100.0% | PASS |
| Scheduled standard-library C | `detective_testculprit_c` | C | 1 | 100.0% | PASS |
| Indirect A -> B | `detective_testculprit_b` | A | 2 | 96.6% | Top-3 PASS |
| Nested A -> B -> C | `detective_testculprit_c` | A | 3 | 96.6% | Top-3 PASS |

Top-1 accuracy was 3/5 (60%); Top-3 accuracy was 5/5 (100%). Equal sample shares in nested stacks are currently broken deterministically by mod id, so the deepest blocking frame is not automatically promoted. The ranking algorithm was not changed to favor the harness.

## False positives and evidence

Two false incidents occurred in the original 30-minute run:

1. A 154.5 ms dimension-transition frame contained only 2/7 Sodium samples (28.6%) but the old evidence rule called it attributed. The incident remains visible, while the revised confidence gate now requires at least three observations and 40% share before using `ATTRIBUTED`; the exact sparse case is covered by a regression test.
2. Restoring the iconified window produced a 478.4 ms frame with no mod suspect and `NATIVE_OR_DRIVER_STALL_POSSIBLE`. Sampling now skips the first resumed frame after pause/unfocus, preventing suspended time from being charged to gameplay. The incident category itself is not globally suppressed.

Stable gameplay, six long-distance teleports/chunk-generation loads, inventory/JEI, resource reload, pause, disconnect/reconnect, and final shutdown produced no incident. Explicit 64 MiB allocation plus `System.gc()` did not exceed the active threshold, so a GC incident classification could not be validated at runtime. Unit tests cover explicit GC markers, native/LWJGL markers, unknown Java stacks, and insufficient evidence.

The first long-session attribution phase ran while the window had lost focus after resource reload, so all five controlled stalls were correctly ignored by the public focus guard. The development harness now restores focus immediately before controlled attribution. The separate focused run above is the authoritative attribution result; the failed raw run remains archived under `run/client/detective-validation/realworld-30m-raw`.

## Proposed performance budget

The measurements support this development budget:

- average watchdog capture at or below 250 us during a stable session;
- stable-gameplay rolling p99 below 2 ms;
- Detective retained estimate below 4 MiB;
- fixed 4,096 latency samples, time-bounded Black Box, bounded watchdog stacks, and incident queue capacity 8;
- on saturation, drop and count the new incident rather than block the render thread or grow memory.

These limits leave measured headroom over the stable 101/213/358 us p50/p95/p99 and 2.93 MiB peak while remaining strict enough to catch a material regression. Transition-only cumulative maxima are reported but are not used as an average-overhead budget.
