# Detective v0.6 — Performance report

Validation runtime: Minecraft 1.21.1, Java 21.0.9, NeoForge 21.1.248.  
Metrics are development instrumentation; classloading/resource-reload peaks are separated from stable gameplay. Detective performs no heavy incident analysis on the render thread.

## Reference budgets

| Metric | Budget |
|---|---:|
| Stable watchdog mean | ≤ 250 µs |
| Stable watchdog p99 | < 2 ms |
| Core retained sampling/Black Box estimate | < 4 MiB |
| Watchdog latency window | ≤ 4096 samples |
| Incident queue capacity | ≤ 8 |
| Dropped incidents in normal operation | 0 |

## Pack matrix

| Profile | Status | Mods | NeoForge | Exploitable soak | samples/s | mean | p50 | p95 | p99 | max | Detective retained | JVM heap | worker avg / p95 / max | queue max | dropped | incidents |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Small | PASS | 21 loaded ids / 11 pinned external JARs | 21.1.248 | 20 min protocol / 20.24 min metrics | 47.81 | 178.8 us | 109.7 us | 311.3 us | 464.9 us | 227,345.4 us | 3.03 MiB max | 217.3–960.8 MiB | 41.2 / 160.5 / 160.5 ms | 1/8 | 0 | 14 |
| Medium | NOT EXECUTED | 0 assembled | — | 0 / 30 min | — | — | — | — | — | — | — | — | — | — | — | — |
| Large | NOT EXECUTED | 0 assembled | — | 0 / 60 min | — | — | — | — | — | — | — | — | — | — | — | — |
| Stress | NOT EXECUTED | 0 assembled | — | 0 / 30 min | — | — | — | — | — | — | — | — | — | — | — | — |

Medium/Large/Stress were not fabricated from duplicate or deliberately incompatible mods. Their absence means the requested cumulative ≥140-minute release gate is not met; the final recommendation must remain NO-GO until those curated profiles are assembled and soaked.

The Small process lasted **1,259.4 seconds** including startup and shutdown. Its protocol itself lasted exactly 20 minutes; the metrics file spans 20.24 minutes because startup observations precede the first phase. Percentiles in the row are the final bounded rolling values, the mean is the lifetime mean, and the maximum is the honest cumulative session maximum.

## Small phase detail

Phase means are reconstructed from deltas of cumulative sample count and capture time. Percentiles are the final bounded rolling values observed in each phase.

| Phase | New samples | Mean | p50 | p95 | p99 | Max retained | Black Box max |
|---|---:|---:|---:|---:|---:|---:|---:|
| Stable gameplay | 11,249 | 161.5 us | 113.2 us | 341.0 us | 609.8 us | 2.98 MiB | 3,480 |
| Rapid chunk generation | 11,268 | 152.3 us | 114.5 us | 336.6 us | 642.4 us | 2.31 MiB | 2,232 |
| Large inventory/menu | 4,552 | 162.3 us | 118.0 us | 341.1 us | 606.4 us | 2.29 MiB | 2,230 |
| Dimension transitions | 5,501 | 177.4 us | 116.8 us | 360.6 us | 659.8 us | 2.42 MiB | 3,409 |
| Resource reload | 4,172 | 427.9 us | 115.0 us | 434.6 us | 5,004.3 us | 2.38 MiB | 2,566 |
| Explicit GC pressure | 3,107 | 144.6 us | 110.0 us | 321.6 us | 458.3 us | 2.45 MiB | 3,554 |
| Controlled attribution | 5,531 | 147.7 us | 114.4 us | 316.1 us | 480.8 us | 2.58 MiB | 3,556 |
| Pause menu | 2,406 | 132.7 us | 102.8 us | 286.9 us | 448.4 us | 2.61 MiB | 3,551 |
| Programmatic iconify/restore | 722 | 140.5 us | 101.8 us | 286.9 us | 421.2 us | 2.52 MiB | 3,550 |
| Disconnect/reconnect | 1,908 | 213.4 us | 110.4 us | 328.6 us | 714.0 us | 3.03 MiB | 2,794 |
| Final stable gameplay | 4,329 | 147.3 us | 109.8 us | 311.3 us | 469.3 us | 2.56 MiB | 3,546 |

The resource-reload mean and rolling p99 exceed the stable budget and are reported separately as an intentional classloading/resource-reload transition. Both stable gameplay windows remain inside the mean/p99 budgets. The cumulative maximum of 227.3 ms occurred outside a stable budget interpretation and is not hidden.

The 14 incidents comprise 9 controlled attributed incidents and 5 real unforced stalls: one menu native/system-possible, one resource-reload native/system-possible, one 1,833.5 ms resource-reload `INSUFFICIENT_EVIDENCE`, and two GC-correlated native/system-possible stalls. There were zero confirmed false positives.

## What the instrumentation measures

- Watchdog samples, lifetime mean/max and a bounded rolling p50/p95/p99 window of 4096 captures.
- Incident-worker lifetime mean/max plus a development-only bounded p95 window of 4096 incidents.
- Current and maximum observed incident queue occupancy, fixed capacity 8, and rejected/dropped count.
- Current retained stack/sample counts, Black Box entries, estimated Detective retention and JVM heap use every five seconds.
- Phase boundaries for stable gameplay, chunk generation, menus, dimensions, resource reload, GC pressure, controlled attribution, pause, focus, disconnect/reconnect and shutdown.

The retention estimate is intentionally conservative and is not a heap-dump attribution. JVM heap is whole-process usage, not Detective ownership.

## Targeted non-soak checks

- The pure UI repository indexed **10, 25, 50 and 100 incident JSON files**, each within the five-second release guardrail, and does not reparse all incidents every render frame.
- Support-report stress produced **25 unique ZIPs**, validated every JSON document, checked schema version 1, prohibited `latest.log`, and kept each ZIP below 500 KiB.
- Notification stress registered 10,000 unique ids while the duplicate/cooldown tracking set remained bounded to 256.
- The Black Box and stack retention are time-bounded; the latency statistics window is fixed at 4096; incident and support worker queues are fixed at 8.

An additional real multiplayer report measured **11,072 bytes**. Export duration was not isolated accurately enough to report as a performance number.

## v0.3.1 comparison

| Measure | v0.3.1 soak | v0.6 Small |
|---|---:|---:|
| Session samples/s | 47.22 | 47.81 |
| Session mean | 259.9 us | 178.8 us |
| Final p50 | 170.2 us | 109.7 us |
| Final p95 | 626.5 us | 311.3 us |
| Final p99 | 1,832.0 us | 464.9 us |
| Retained estimate max | 2.90 MiB | 3.03 MiB |
| Queue max | 0/8 | 1/8 |
| Dropped | 0 | 0 |

Stable means changed from 130.1/246.0 us in the two v0.3.1 windows to 161.5/147.3 us in v0.6; stable rolling p99 changed from 411.8/1,737.7 us to 609.8/469.3 us. These runs used different NeoForge/JEI checkpoints and are not a controlled microbenchmark, so the table demonstrates budget compliance and absence of a regression signal, not a claimed speedup.

## Interpretation rules

- Startup/classloading and resource reload maxima are reported, not hidden, but they are not called stable gameplay averages.
- A Minecraft/worldgen/third-party stall is not automatically a Detective false positive; a false positive requires Detective to claim an incident where no qualifying active gameplay stall occurred.
- Pack startup failure due to inadequate heap or another mod's loader floor is separated from Detective overhead.
