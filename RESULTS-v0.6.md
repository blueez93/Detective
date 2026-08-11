# Detective v0.6.0-alpha.1 — Release hardening results

Validation date: 2026-08-11  
Baseline commit: `5f15886` / `v0.5.0-alpha.1`  
No commit or tag was created by this mission.

## 1. Scope and environment

- Minecraft: **1.21.1**
- Java: **Oracle Java 21.0.9 LTS**, 64-bit
- OS used for runtime validation: Windows 11 amd64
- NeoForge checkpoints: **21.1.235**, **21.1.238**, **21.1.248**
- Demonstrated minimum: **21.1.235**
- Recommended/runtime used for final validation: **21.1.248**
- Client-only: Detective has no public server feature, custom packet or handshake.

The official NeoForge Maven metadata resolved 21.1.248 as the newest available stable 21.1.x artifact on the validation date. The release dependency range is separated from the development runtime: `[21.1.235,)` in metadata, 21.1.248 for normal development/builds.

## 2. Bugs reproduced, explained and corrected

### 2.1 Sensitive network identifiers in Support Reports

**Reproduction:** a hostile installed-mod metadata fixture containing IPv6 and MAC values survived the final generated ZIP. The new final-ZIP test failed before the fix.  
**Cause:** defense-in-depth redaction covered labels, paths, IPv4 and UUIDs but not IPv6/MAC.  
**Fix:** redact IPv6 and colon/hyphen MAC forms, plus the actual local username/home-directory component.  
**Retest:** final ZIP tests pass for IPv4, IPv6, MAC, UUID, tokens, user/home, hostname labels and server-address labels.

### 2.2 False incident during initial world loading

**Reproduction:** 21.1.235 created a 386.0 ms `INSUFFICIENT_EVIDENCE` incident and 21.1.238 created a 272.2 ms incident during the first seconds of world/JEI initialization, before any controlled stall.  
**Cause:** focus continuity skipped suspended frames, but the detector accepted active frames immediately after a new `ClientLevel`; late join-time client initialization remained eligible.  
**Fix:** a five-second world/dimension stabilization gate suppresses incident creation only. Black Box capture and the watchdog continue; the detector baseline/debounce is reset for the new level. No freeze threshold or attribution rule changed.  
**Retest:** post-fix 21.1.238 and 21.1.248 checkpoints created exactly the controlled incident, retained Top-1 attribution, UI/export PASS and no join-time incident.

### 2.3 Open Folder failure could escape UI code

**Reproduction:** direct platform invocation had no local failure boundary.  
**Fix:** `LocalFolderOpener` handles missing directory, platform `RuntimeException` and linkage failure, reports a translated nonfatal UI error and is dependency-injectable for pure tests.  
**Retest:** existing, missing and platform-failure branches pass. Physical Explorer launch remains manual.

### 2.4 Persistent incident/snapshot schema was implicit

**Reproduction:** report/settings JSON carried schema version 1, but top-level incident and snapshot records did not.  
**Fix:** explicit `schemaVersion: 1` with constructors that normalize absent/zero legacy values and tolerate future numeric versions.  
**Retest:** serialized incident/snapshot tests assert version 1; legacy fixtures still load.

### 2.5 Corrupt snapshot validation gaps

**Reproduction:** duplicate mod ids and blank id/version entries could enter comparison.  
**Fix:** invalid/duplicate entries make the previous snapshot unavailable rather than corrupting Modpack Changes. Missing display name falls back safely.  
**Retest:** absent, empty, truncated, null, old/future schema, duplicate, missing-name and blank-version cases pass.

### 2.6 Validation Gradle ordering race

**Reproduction:** requesting `prepareValidationPack verifyValidationPack` together let verification race the JEI download.  
**Fix:** `verifyValidationPack.mustRunAfter(prepareValidationPack)` and updated validation User-Agent.  
**Retest:** combined command verifies all 11 pinned mods.

### 2.7 Dedicated run injected Detective implicitly

**Reproduction:** NeoGradle's source-set convention put `main` in `runServer`, so its mod list contained Detective even though `run/server/mods` did not.  
**Fix:** disable automatic source-set inclusion, explicitly attach main only to client/data/game-test runs, and give the dedicated run its own development-only marker source set.  
**Retest:** the dedicated server reaches `Done` with `detective_servervalidation`; `detective` is absent. The marker is excluded from the public JAR.

### 2.8 Missing worker p95/queue-high-water metrics

**Reproduction:** the v0.6 spec requested incident p95 and queue maximum, while diagnostics exposed only mean/max and current queue.  
**Fix:** development diagnostics reuse a bounded 4096-value latency window and an atomic queue high-water mark. Production mode does not allocate/use the latency window.  
**Retest:** compile/tests pass and runtime metrics log `incidentP95` and `workerQueueMax`.

### 2.9 Second restoration frame escaped continuity protection

**Reproduction:** an archived partial soak discarded an initial >10-second render interval after focus loss, then recorded the following 2,086.7 ms native restoration frame as an incident. Minecraft had rendered no inactive frame, so the continuity gate never observed `samplingAllowed=false`.  
**Fix:** discarding an interval above the existing 10-second useful-frame ceiling now explicitly marks a discontinuity and skips the next three active frames. No threshold, debounce, attribution or confidence rule changed.  
**Retest:** a new unit test protects the explicit path. The final 20-minute soak recorded zero incidents in pause, programmatic iconify/restore, reconnect and final stable phases; the five real unforced stalls occurred in menu, resource reload or GC pressure instead.

### 2.10 Lifecycle harness reconnected before Netty closed

**Reproduction:** the first 20-cycle run reached cycle 11 while a prior connection was still decoding a registry-backed equipment packet, producing `No value with id 828`. Detective still retained one watchdog and zero incidents, but the harness timing was not clean.  
**Fix:** the development-only lifecycle plan now waits for `Minecraft.getConnection() == null` and a three-second disconnected settle before reconnecting.  
**Retest:** 20/20 cycles, watchdog 1→1, zero incidents, no decoder exception, clean client shutdown.

## 3. NeoForge compatibility

See `COMPATIBILITY-v0.6.md` for the full matrix.

| Version | Build | World/client | Incident | UI | Support ZIP | Shutdown |
|---|---|---|---|---|---|---|
| 21.1.235 | PASS | PASS | PASS | PASS | PASS | PASS |
| 21.1.238 | PASS | PASS | PASS | PASS | PASS | PASS |
| 21.1.248 | PASS | PASS | PASS | PASS | PASS | PASS |

The modern validation pack uses JEI 19.44.0.401 on 21.1.238/248. The historical 21.1.235-compatible manifest is preserved separately with JEI 19.39.0.372; another mod's loader floor is not reported as a Detective incompatibility.

## 4. Validation packs and soak duration

| Profile | Requested size | Assembled/executed | Duration |
|---|---:|---|---:|
| Small | 20–30 | 21 loaded mod ids, 11 pinned external JARs; executed | 20 min protocol; 20.24 min metrics; 20 min 59.4 s process |
| Medium | 75–100 | Not assembled/executed | 0 min |
| Large | 150–200 | Not assembled/executed | 0 min |
| Stress | 200+ if coherent | Not assembled/executed | 0 min |

The current pack covers optimization, rendering, inventory/QoL, content, worldgen, machines/block entities, libraries and animation. It contains ModernFix, FerriteCore, Sodium, Mekanism, Farmer's Delight, Regions Unexplored, Lithostitched, JEI, Jade, Curios and GeckoLib. Names, versions, official URLs, licenses and SHA-512 hashes are in `validation-pack/modrinth-pack.json`.

| Mod id | Name | Version | License |
|---|---|---|---|
| `modernfix` | ModernFix | 5.27.20+mc1.21.1 | LGPL-3.0-only |
| `ferritecore` | FerriteCore | 7.0.3-neoforge | MIT |
| `sodium` | Sodium | 0.8.12+mc1.21.1 | PolyForm-Shield-1.0.0 |
| `mekanism` | Mekanism | 10.7.19.85 | MIT |
| `farmersdelight` | Farmer's Delight | 1.21.1-1.3.2 | MIT |
| `regions_unexplored` | Regions Unexplored | 0.6.2-neoforge-21.1 | MIT |
| `lithostitched` | Lithostitched | 1.7.13-neoforge-21.1 | MIT |
| `jei` | Just Enough Items | 19.44.0.401 | MIT |
| `jade` | Jade | 15.10.6+neoforge | CC-BY-NC-SA-4.0 |
| `curios` | Curios API | 9.5.1+1.21.1 | LGPL-3.0-or-later |
| `geckolib` | GeckoLib | 4.9.2 | MIT |

NeoForge additionally reports four Forgified Fabric API modules bundled as Jar-in-Jar dependencies by Sodium. Detective, Minecraft/NeoForge and the three development culprits bring the loaded-id total to 21; none of those development/third-party artifacts enters the public Detective JAR.

Medium/Large/Stress were not fabricated from duplicates or incompatible downloads. Therefore the requested cumulative ≥140 minutes is **not achieved**, independently of the Small result.

## 5. Performance

Final detailed values are in `PERFORMANCE-v0.6.md`.

- Session: 57,802 watchdog samples, 47.81 samples/s, 178.8 us mean, final rolling p50/p95/p99 109.7/311.3/464.9 us, cumulative max 227,345.4 us.
- Stable windows: means 161.5 and 147.3 us; rolling p99 609.8 and 469.3 us.
- Resource reload, reported separately: 427.9 us phase mean and 5,004.3 us rolling p99.
- Core retained estimate maximum: **3.03 MiB**.
- Black Box maximum: **3,556 entries**.
- Incident worker mean/p95/max: **41.2/160.5/160.5 ms**, off render thread.
- Incident queue max/capacity: **1/8**.
- Dropped incidents: **0**.
- JVM heap observed: **217.3–960.8 MiB**; this is whole-process heap, not Detective ownership.

Buffers remain structurally bounded: 30-second Black Box, 12-second stack retention, 4096 latency samples, incident queue 8 and support queue 8. The worker performs attribution/JSON outside the render thread.

Targeted tests additionally cover 10/25/50/100-incident indexing, 25 Support Reports, bounded 10,000-id notification stress, retention and settings.

Logging audit: the 20-minute run produced 48 non-validation Detective lines, limited to ready/watchdog lifecycle and the 14 actual incidents/suspect observations. There is no per-frame or per-sample production log. The 253 five-second overhead lines are explicitly development-only validation output.

## 6. Incidents, false positives and attribution

Final Small Pack counts:

- Total incidents: **14**.
- Controlled: **9**.
- Unforced real stalls: **5**.
- Attributed: **9**, all controlled.
- Ambiguous: **0**.
- Insufficient: **1**, a 1,833.5 ms resource-reload stall with JEI present in raw suspects but evidence deliberately too weak for attribution.
- JVM/GC explicit classifier: **0**.
- Native/driver possible: **4** (menu, resource reload and two GC-correlated pauses).
- Unknown: **0**.
- Confirmed false positives: **0**.

The two pre-fix join-time incidents are confirmed lifecycle false positives for the purpose of the hardening study and are retained in ignored validation archives. They are not hidden from the report.

Controlled matrix post-hardening:

- Top-1: **9/9 (100%)**.
- Top-3: **9/9 (100%)**.
- Direct A/B/C, A→B, A→B→C and four historical permutations are replayed by the final plan.
- No ground truth enters ranking or confidence.
- No production ranking, leaf-ownership, confidence, adaptive-threshold or engine-debounce rule changed in v0.6.

## 7. Indirect stacks and ModSourceResolver

Existing runtime scenarios cover direct independent source sets, indirect A→B, nested A→B→C and permutations. Unit analysis now also checks equal/shared frame ownership and unknown/platform-only samples. Leaf ownership remains the final ranking model; confidence remains a separate evidence decision.

Runtime coverage includes standard mod JARs, multiple development source sets, libraries and Jar-in-Jar mods present in the real pack. Multi-mod-file metadata is represented as shared ownership; package conflicts across distinct sources degrade to unknown instead of choosing arbitrarily. A synthetic SecureJar/Jar-in-Jar ownership matrix beyond the loaded real pack was not completed and remains a limitation, not a claimed PASS.

## 8. Worlds, dimensions, worldgen and GC

- Singleplayer: compatibility checkpoints PASS on the existing `DetectiveValidation` world.
- Dedicated server: NeoForge 21.1.248 reaches `Done` without `detective` installed/listed.
- Detective client → dedicated server: **PASS** for the final 20-minute soak, a separate multiplayer Support Report run, and a clean 20-cycle reconnect rerun.
- Overworld: compatibility/singleplayer runs record valid context. Nether: the Small soak and final persisted player NBT record `minecraft:the_nether` with coherent `(0, 90, 0)` incident positions. End: **not demonstrated**. Although the current harness commands were accepted, final incident JSON and playerdata show that the automated sequence remained in the Nether; this report does not claim a three-dimension round trip.
- Modded dimension: unavailable in this pack; Regions Unexplored supplies modded worldgen, not a separate dimension.
- Worldgen: six long-distance teleports into new areas plus rapid generation phase.
- Machines/block entities: Mekanism loads, but a deliberately constructed active machine network requires manual play and is not claimed.
- GC: three 512 MiB pressure passes ran only in the harness with unified G1/safepoint logging. Full G1 pauses of 194.910 ms and 128.165 ms align with Detective incidents of 206.0 and 134.0 ms. Each incident had one watchdog sample, no suspect, and `NATIVE_OR_DRIVER_STALL_POSSIBLE`; no mod received strong attribution. A third 146.719 ms Full GC fell within the existing incident debounce and created no additional record.

The final 20-cycle dedicated reconnection rerun reports `cycles=20 watchdogStart=1 watchdogEnd=1 newIncidents=0`. The original faster harness attempt exposed and documented a Netty close/reconnect race, then the corrected plan was rerun cleanly. New-world creation and physical manual traversal remain unchecked.

## 9. Notification, history and Support Report stress

- Notification cooldown: first incident shown, four rapid following incidents suppressed, next at 8 seconds allowed.
- Settings OFF: suppresses notification; the incident has already been persisted before notification handling.
- Duplicate incident id: never shown twice.
- 10,000 ids: tracking remains ≤256.
- History datasets: parsing/sorting tests include 100 incidents; runtime UI retains the configured 50 most recent.
- Support Reports: 25 unique ZIPs across HIGH, MODERATE, LOW, AMBIGUOUS, INSUFFICIENT, JVM/GC, NATIVE/DRIVER and UNKNOWN.
- Every exported JSON: valid object and `schemaVersion: 1`.
- Every stress ZIP: below 500 KiB, unique name, no overwrite and no `latest.log`.

## 10. Corruption, recovery and migration

### Settings

PASS: missing, empty, truncated/invalid JSON, unknown field, incorrect type, negative/huge integer clamping, old schema and future schema. Defaults remain safe and Minecraft-facing code receives a valid settings object.

### Incidents

PASS: empty/truncated file isolation, missing fields, no suspects, missing/partial Black Box, legacy/future fields and invalid value types. One bad incident cannot block valid list entries.

### Snapshots

PASS: absent, empty, malformed/truncated, old/future schema, missing name fallback, blank id/version rejection and duplicate rejection. Modpack Changes degrades to “no previous snapshot”.

### Atomic recovery

Incident/settings/snapshot writes use same-directory temp + atomic replace where supported. ZIP export writes a complete temp ZIP then atomically moves it. Tests prove replacement leaves no temp and that a stale crash-temp does not replace the committed file. Abrupt client terminations were followed by successful client restarts; valid prior data remained usable.

### Migrations

Legacy `moddetective` directory move/merge and collision preservation pass. Incident adapters cover pre-schema legacy fields, settings load missing fields as defaults, and snapshot schema zero normalizes to 1. Representative v0.3/v0.4/v0.5-compatible shapes are readable; downgrade is not promised.

## 11. Privacy and network audits

### Privacy

The final ZIP—not only its DTO—was scanned for user home/name, hostname labels, IPv4, parsed IPv6, MAC, UUID, tokens, server address labels, absolute personal path and `latest.log`. Automated malicious-metadata ZIP tests and a real generated ZIP report zero exposed target values. The post-multiplayer ZIP is 11,072 bytes, contains ten allow-listed entries and eight valid schema-v1 JSON documents, and exposes none of the audited values—including the local server address `127.0.0.1`.

### Network

Static runtime audit found no HTTP client, URL connection, socket, telemetry, analytics, update check, upload or remote API call. `java.net.URI/URL` in `ModSourceResolver` only converts local class code-source locations and never opens a connection. Validation-pack downloads exist only in a developer Gradle task, never in runtime code. Minecraft and third-party mod connections are out of scope.

## 12. UI hardening

- Pure adapters handle corrupt/missing files.
- Incident indexing and Support Report work are asynchronous/off-render-thread.
- Open Folder failures are nonfatal and translated.
- `en_us` and `fr_fr` each contain 165 matching keys; no cautious wording regression was found by prohibited-copy scan.
- Automated v0.4/v0.5 harness rerun: **PASS**, route completed with **35 screenshots** at 854×480. Visual inspection covered home/list, High/Moderate/Low, ambiguous, insufficient, possible system stall, unknown, complete/partial Black Box, technical evidence, modpack changes/no snapshot, export, settings, clear history, notification, English and French. Important text remained readable; scrolled content remained reachable in the captured states.
- Physical mouse/keyboard, real Explorer, resolution, GUI scale, resize and Alt+Tab checks are intentionally **MANUAL TEST REQUIRED** in `MANUAL-TEST-CHECKLIST-v0.6.md`.

## 13. Tests, build, runClient and public JAR

- Automated tests: **96**, 28 suites, 0 failures, 0 errors, 0 skipped.
- `clean build --offline --no-daemon`: **PASS** in 1 min 30 s.
- final `test --offline --no-daemon`: **PASS** in 27 s (`UP-TO-DATE` after the clean build test execution).
- `runClient`: **PASS** for NeoForge 21.1.235/238/248 compatibility, final Small soak, multiplayer export, corrected lifecycle20 and UI harness.
- `runServer` isolated: PASS to `Done`; third-party Regions Unexplored emits a client-screen mixin warning but no Detective server code is present.
- Public JAR: `detective-0.6.0-alpha.1.jar`, **208,349 bytes**, **111 entries**, **90 classes**, **0 nested JARs**, SHA-256 `456D91848533E443C5C225939543191EEFFE55A3C844887A688A353B13D206C7`.
- Forbidden public content scan: **PASS**; no harness, culprit, ground truth, server marker, validation pack, GC validation, fixture, generated report, `latest.log`, credentials or third-party JAR. Present code packages are the intended `fr.apocalypsebleu.moddetective` root plus `client`, `core`, `snapshot`, `storage` and `support`.

## 14. Known issues and remaining manual tests

1. Medium, Large and Stress profiles are not assembled; cumulative hardening soak is below 140 minutes.
2. VSync/FPS-cap, 2/4/8/12 GiB heap matrix was not executed.
3. Physical Alt+Tab, all resolutions/scales, keyboard traversal, resize and Open Folder require a human. The installed Windows-control helper was attempted, retried and reset, but failed with `failed to write kernel assets`.
4. The End transition was not demonstrated by the current command harness; only Overworld and Nether are proven. No dedicated modded dimension was available.
5. No physically constructed active Mekanism machine network was exercised.
6. Synthetic SecureJar/Jar-in-Jar/multi-mod ownership cases beyond loaded real mods are not exhaustive.
7. NeoGradle online refresh hit an environment/Mojang metadata download failure late in the run; cached/offline 21.1.248 builds work. A final online release build should be repeated when the endpoint is available.

## 15. Recommendation

**NO-GO toward v0.7/public release at this time.**

The core fixes and the executed matrix are suitable for continued alpha hardening, but the explicit release gate requires Medium/Large/Stress packs, ≥140 exploitable soak minutes and remaining physical UI/focus configurations. This NO-GO is due to incomplete demonstrated coverage, not a hidden engine failure. No v0.7 feature work should begin from this report.
