# Detective 0.9.0-dev — Release-candidate hardening results

## 1. Tested commit

- Commit: `9385d9fffd92ac4f7e5e7a7b6f7eb4edf40832fc`
- Commit date: 2026-08-14 18:00:04 +02:00
- Commit subject: `Add What Changed UI and precise scrolling`
- The complete production delta from `release/0.8.0` was reviewed: 64 changed files, including comparison, query, evolution, launch-history persistence, UI integration, localization, and validation fixtures.
- The initial worktree was clean. The final worktree contains only this report and ten release-hardening tests; no production Java file changed.

## 2. Branch/version

- Branch: `feature/0.9.0-investigation`
- Version: `0.9.0-dev`
- No commit, tag, push, publication, merge, or version bump was performed.

## 3. Environment

- OS: Windows 10 Home 25H2, build `26200.9168`, AMD64
- Java: Oracle Java 21.0.9 LTS, HotSpot 64-bit, build `21.0.9+7-LTS-338`
- Minecraft: `1.21.1`
- NeoForge development version: `21.1.248`; declared minimum: `21.1.235`
- Main visual profile: 1280×720, GUI scale 2, with the local third-party validation pack
- Clean visual control: 960×540, GUI scale 2, isolated game directory without third-party validation mods

## 4. Test/build results

Initial baseline before hardening changes:

- Command: `.\gradlew.bat cleanTest test build --no-daemon`
- Tests: 263
- Failures: 0
- Errors: 0
- Skipped: 0
- Result: `BUILD SUCCESSFUL`

Final regression:

- Command: `.\gradlew.bat cleanTest test build --no-daemon`
- Test suites: 47
- Tests: 273
- Failures: 0
- Errors: 0
- Skipped: 0
- Result: `BUILD SUCCESSFUL`

The increase from 263 to 273 is exactly ten hardening tests:

- six scrollbar/input cases: exact fit, fast-wheel clamps, drag top/bottom clamps, track clicks in both directions, unrelated-click rejection, and repeated resize/drag reset;
- three launch-history cases: empty valid history, excessive hostile record count, and excessive changes in one launch, including byte-preservation assertions;
- one measured pairwise comparison benchmark.

Targeted runs also passed: 42/42 launch-history plus scrollbar tests, and 13/13 comparison tests.

## 5. 0.7/0.8 regression audit

- The 0.9 production diff does not modify `FreezeDetector`, `RenderThreadWatchdog`, `SuspectAnalyzer`, `ClientPerformanceEvents`, Black Box recording, notifications, incident retention, or Support Report serialization.
- The two Case Files core edits expose the existing weighted-Jaccard helper and single-fingerprint reader for reuse. Existing scoring, weights, thresholds, complete-link clustering, and Case identity logic are unchanged.
- Freeze detection, sampling, attribution, suspect ranking, and the 0.8 Case Files tests all pass unchanged.
- The 0.7 home, incident, ambiguous, Black Box, modpack changes, and support-report routes were rendered successfully.

## 6. Upgrade compatibility

The automated suite covers fresh/missing stores, schema-v1 incidents, schema-v2 incidents, mixed legacy/enhanced histories, malformed/missing `derivedEvidence`, and repeated reloads.

- 0.7/schema-v1 incidents remain readable and use only captured legacy class/owner fallback evidence.
- Legacy incidents never receive fabricated frame or path signatures.
- Mixed legacy/enhanced comparison uses only the evidence categories genuinely shared by both records.
- Valid Case indexes retain deterministic IDs and membership across reloads.
- Missing and corrupt Case indexes recover deterministically; unsupported future Case indexes are preserved and not overwritten.
- Missing `launch-history.json` is not created by a read. An empty valid history loads as empty without fabrication or rewrite.
- Corrupt, malformed-known-field, structurally oversized, and future-schema launch histories are preserved byte-for-byte when a current launch is observed; the current launch remains memory-only.
- Unknown extra launch-history fields are ignored on read.
- Legacy `last-session.json` can contribute only the current observed launch boundary/diff. It is not rewritten by launch-history loading.
- Repeated restarts and duplicate launch persistence produce identical data without duplicate records or unnecessary rewrites.
- Existing incident files are not destructively migrated or rewritten by Case/comparison/history loading.

## 7. Incident Comparison results

- Enhanced/enhanced, legacy/legacy, enhanced/legacy, sparse, empty, malformed optional evidence, and unavailable context paths pass.
- Attribution state and displayed suspect do not contribute to Technical Similarity.
- Same attribution with unrelated technical evidence remains low similarity; different attribution with matching technical evidence remains eligible for high similarity.
- Technical Similarity is symmetric. Reversing A/B swaps only the directional evidence lists.
- Repeated comparison is deterministic and does not mutate either incident.
- `Not captured`, `Unavailable`, and `Insufficient comparable evidence` remain distinct view states.
- Duration, memory, dimension, player position, and attribution context do not alter the technical score.
- The public selection state cannot select the same incident twice because selection is keyed by incident identity.
- Long A/B attribution text is tested for both cards and uses a bounded two-line label/value presentation with a clear ellipsis where required.

## 8. Search & Filtering results

- Histories of 0, 1, 50, 250, and 500 records are covered.
- Blank, partial, exact, case-insensitive, locale-stable, whitespace-normalized, underscore-normalized, and hyphen-normalized text behavior is covered.
- Search is restricted to incident ID, evidence state, captured owner/mod IDs, and mod display names. It does not index raw stacks, paths, dimension text, or filesystem directory segments.
- Evidence-state alternatives use OR semantics. Different filter dimensions use AND semantics.
- Minimum/maximum durations and date ranges are inclusive.
- Any Case, no Case, and a specific Case use persisted membership, never owner identity.
- Missing/corrupt Case membership returns an explicit unavailable Case-filter result instead of guessing.
- Newest, oldest, longest, and shortest sorting is deterministic. Missing durations remain in the documented available-first/missing-after position.
- Query changes clear no-longer-visible A/B selections; stale query generations cannot replace newer results.

## 9. Case Evolution results

- ADDED, UPDATED, REMOVED, before, after, equal timestamp, same recorded launch, no nearby change, multiple nearby changes, and changes outside the configured window pass.
- Nearby changes are temporally ordered with deterministic tie-breaking and no severity/guilt/risk ranking.
- First recorded occurrence is the earliest available retained Case occurrence, never “first ever occurrence.”
- COMPLETE, LIMITED_BEFORE, LIMITED_AFTER, LIMITED_BOTH, INSUFFICIENT, and unavailable coverage paths are modeled conservatively.
- Missing timestamps, missing versions, missing members, evicted launches, legacy missing duration, and unavailable launch history do not fabricate evidence.
- Owner identity does not alter temporal correlation. A change from an unrelated owner may appear as nearby context, with no causal claim.
- The UI bounds excessive nearby changes and ellipsizes long mod IDs, display names, and versions.
- All ten requested Evolution routes were rendered at the actual `What Changed?` section. The visible copy includes: `Temporal proximity does not establish causation.`

## 10. Launch History results

Persisted file: `detective/snapshots/launch-history.json`.

- Schema: version 1, atomic UTF-8 JSON.
- Allow-listed launch fields: launch timestamp, optional previous-launch timestamp, and a sorted list of compact mod changes.
- Allow-listed change fields: type, mod ID, display name, optional previous version, and optional new version.
- No file paths, Minecraft/Java version, pack fingerprint, player data, raw object values, or stack content are persisted in this file.
- Retained launch records: 64 by default.
- Maximum accepted hostile records: 1,024.
- Maximum accepted changes per launch: 4,096.
- The 65th and later launch evicts the oldest record, increments `omittedEarlierRecords`, protects the newest/current record, and propagates partial coverage.
- Zero-change launches and consecutive zero-change launches remain explicit boundaries.
- Atomic replacement and stale temporary-file behavior are covered by the shared atomic-file tests.
- Failure to persist `last-session.json` prevents a potentially misleading launch-history write; current data remains memory-only.
- Support Reports do not include `launch-history.json`.

## 11. Case Files regression

- Three sufficiently similar incidents create a recurring Case; two do not.
- Complete-link compatibility remains required, so a borderline bridge cannot join mutually incompatible groups.
- Adding matching incidents retains the stable founder-derived Case ID.
- Identical reloads preserve IDs, membership, and Case-index bytes.
- Unrelated incidents do not mutate an existing Case.
- Retention can shrink a Case below minimum membership, remove it, and allow a later pattern to form without stale-index reuse.
- Merge, split, disappearance, and reappearance behavior remains deterministic and membership-based.
- Owner/suspect identity is neither the clustering criterion nor the Case identity mechanism.

## 12. Retention interactions

- A removed comparison source fails as unavailable; it cannot resolve to another incident because the loader validates the normalized local path and reads that exact file.
- Query refresh removes stale selected A/B entries rather than silently reassigning selection.
- Removed related Case incidents are displayed as unavailable and reduce coverage confidence.
- A Case that falls below three retained compatible members disappears after reconciliation.
- Missing retained Case members and evicted launch records produce limited/insufficient coverage.
- Incident retention touches only incident records; it does not delete launch history.
- No tested retention path crashes, fabricates evidence, or reuses a stale ID for a different incident.

## 13. Async/threading audit

- Incident history loading, search, pairwise incident loading/comparison, Case Evolution analysis, and UI projection run on the single daemon `Detective-UiData` executor.
- Case history loading, clustering, reconciliation, index persistence, retention, settings, notifications, and Support Report creation run on the single bounded `Detective-Support` executor (queue capacity 8).
- Both executors are shut down from `GameShuttingDownEvent`; no unbounded executor or thread creation was found.
- Search uses a generation token; Case Evolution uses a generation plus Case-ID token. Stale responses after replacement/navigation are ignored.
- Screen state mutations from futures are marshalled through `Minecraft.execute` onto the client thread.
- No JSON parsing, disk loading, Case clustering, or persistence occurs inside a screen `render()` method.
- One-time mod snapshot plus launch-history load/save still runs during client setup on the render thread, as the pre-0.9 snapshot path already did. It does not run per frame or from `render()`; its measured ordinary bound is listed below.

## 14. Performance measurements

All values are medians from the final Gradle test process on the environment in section 3. Production thread placement is from the audited call paths.

| Operation | Data size | Median | Production thread |
|---|---:|---:|---|
| Query: filter | 50 / 250 / 500 incidents | 0.007 / 0.027 / 0.027 ms | `Detective-UiData` |
| Query: text | 50 / 250 / 500 incidents | 0.302 / 1.367 / 1.789 ms | `Detective-UiData` |
| Query: combined | 50 / 250 / 500 incidents | 0.257 / 1.106 / 1.192 ms | `Detective-UiData` |
| Query: sorting | 50 / 250 / 500 incidents | 0.011 / 0.054 / 0.031 ms | `Detective-UiData` |
| Case Evolution | 50 / 250 / 500 incidents | 0.094 / 0.174 / 0.202 ms | `Detective-UiData` |
| Incident Comparison | two enhanced incidents; batches of 500 pairs | 0.017116 ms per pair | `Detective-UiData` |
| Case incident-file load | 50 / 250 / 500 incidents | 33.642 / 128.757 / 238.257 ms | `Detective-Support` |
| Case clustering | 50 / 250 / 500 incidents | 4.543 / 35.722 / 177.381 ms | `Detective-Support` |
| Case reconciliation + persistence | 50 / 250 / 500 incidents | 3.302 / 2.938 / 3.352 ms | `Detective-Support` |
| Complete Case refresh | 50 / 250 / 500 incidents | 40.465 / 169.370 / 424.996 ms | `Detective-Support` |
| Launch-history load | 64 launches, 1,536 changes | 3.704 ms | client setup (one time) |
| Launch-history save | 64 launches, 1,536 changes | 6.397 ms | client setup (one time) |

The 500-incident bound prevents unbounded Case/query work in normal local history. No quadratic history operation runs during `render()`.

## 15. Scroll/input validation

- `DetectiveScrollState` now has 21 focused tests, covering smaller, exact-fit, tiny-overflow, very tall, top/bottom clamps, precise and fast wheel input, thumb mapping, drag top/middle/bottom, track clicks above/below, release, resize, repeated resize, fixed footer exclusion, unrelated-click rejection, and determinism.
- Wheel movement remains 12 px per scroll unit.
- The scrollbar is hidden without overflow and retains a minimum 18 px thumb with overflow.
- Real route logs reported `clicked=true dragged=true released=true` for Case detail, Incident Comparison, and all Case Evolution screens.
- Content links and fixed footer controls remain outside the scrollbar hit target.
- Visual checks passed at 1280×720/GUI 2 and at 960×540/GUI 2.

## 16. UI smoke routes actually executed

All listed routes started a real NeoForge client, reached `[Detective] Ready`, rendered, captured a screenshot, and exited without a crash. Footer controls remained accessible. Long-content routes displayed a scrollbar where expected. “Clean” paths are from the isolated 960×540 control profile; other paths are 1280×720.

An intermittent missing-glyph artifact occurred in some screenshots from the third-party validation-pack profile. It crossed unrelated Detective and vanilla widgets. It did not reproduce on the three clean-profile controls without Sodium; those clean screenshots are used below where applicable.

| Requested route | Result / visual inspection | Screenshot |
|---|---|---|
| home | PASS; no overflow | `run/client/screenshots/detective-v070-public-demo-home.png` |
| incident | PASS; scrollbar and footer accessible | `run/client/screenshots/detective-v070-public-demo-incident.png` |
| ambiguous | PASS; cautious attribution copy visible | `run/client/screenshots/detective-v070-public-demo-ambiguous.png` |
| blackbox | PASS; clean 960 control, no clipping | `run/coverage/rc-clean-client/screenshots/detective-v070-public-demo-blackbox.png` |
| changes | PASS; added/updated/removed groups visible | `run/client/screenshots/detective-v070-public-demo-modpack-changes.png` |
| support-report | PASS; local-only wording and footer visible | `run/client/screenshots/detective-v070-public-demo-support-report.png` |
| case-files-empty | PASS; minimum-three explanation visible | `run/client/screenshots/detective-v090-case-files-empty.png` |
| case-files-multiple | PASS; both Cases and navigation rendered | `run/client/screenshots/detective-v090-case-files-multiple.png` |
| case-file-detail | PASS; links, drag, scrollbar, footer | `run/client/screenshots/detective-v090-case-file-detail.png` |
| incident-search | PASS; all three results and controls | `run/client/screenshots/detective-v090-incident-search.png` |
| incident-search-empty | PASS; conservative empty state | `run/client/screenshots/detective-v090-incident-search-empty.png` |
| incident-filters | PASS; clean 960 control, buttons fit | `run/coverage/rc-clean-client/screenshots/detective-v090-incident-filters.png` |
| incident-compare-select | PASS; A/B selection and cancel footer | `run/client/screenshots/detective-v090-incident-compare-select.png` |
| incident-comparison-high | PASS; two scroll positions, clean 960 control | `run/coverage/rc-clean-client/screenshots/detective-v090-incident-comparison-high.png` |
| incident-comparison-low | PASS; low score and two-line A/B context | `run/client/screenshots/detective-v090-incident-comparison-low.png` |
| incident-comparison-legacy | PASS; `Not captured` states visible | `run/client/screenshots/detective-v090-incident-comparison-legacy.png` |
| incident-comparison-insufficient | PASS; insufficient states distinct | `run/client/screenshots/detective-v090-incident-comparison-insufficient.png` |
| case-evolution-update-before | PASS; UPDATED, before offset, coverage, caution | `run/client/screenshots/detective-v090-case-evolution-update-before.png` |
| case-evolution-added-before | PASS; ADDED state rendered | `run/client/screenshots/detective-v090-case-evolution-added-before.png` |
| case-evolution-removed-before | PASS; REMOVED and previous version rendered | `run/client/screenshots/detective-v090-case-evolution-removed-before.png` |
| case-evolution-change-after | PASS; positive after offset rendered | `run/client/screenshots/detective-v090-case-evolution-change-after.png` |
| case-evolution-multiple | PASS; multiple deterministic cards and scrollbar | `run/client/screenshots/detective-v090-case-evolution-multiple.png` |
| case-evolution-none | PASS; no-nearby-change state rendered | `run/client/screenshots/detective-v090-case-evolution-none.png` |
| case-evolution-limited-before | PASS; earlier-history-limited copy rendered | `run/client/screenshots/detective-v090-case-evolution-limited-before.png` |
| case-evolution-insufficient | PASS; unavailable occurrence/history rendered | `run/client/screenshots/detective-v090-case-evolution-insufficient.png` |
| case-evolution-same-launch | PASS; same-launch wording rendered | `run/client/screenshots/detective-v090-case-evolution-same-launch.png` |
| case-evolution-long-mod-name | PASS; long ID/version use ellipses inside the card | `run/client/screenshots/detective-v090-case-evolution-long-mod-name.png` |

## 17. NeoForge startup stability

- Isolated client starts attempted: 43
- Successful starts: 43
- Failed starts: 0
- Every start obtained the requested OpenGL context, initialized Detective, rendered its requested route, captured, and shut down cleanly.
- The previously observed NeoForge `EARLYDISPLAY` crash was not reproduced.
- Normal `EARLYDISPLAY` informational lines appeared while creating the GL context; no failure occurred in that phase.
- The main profile included the local third-party validation pack. Three additional clean-profile starts excluded those third-party mods and also passed.
- Current evidence does not attribute the earlier intermittent display crash to Detective.

## 18. Localization

- `en_us.json` and `fr_fr.json` both parse successfully.
- Key counts: 325 English and 325 French.
- Missing keys in either direction: 0.
- Localization tests validate important 0.9 safety copy and terminology.
- Case Files, First recorded occurrence, Technical similarity, What Changed?, Nearby Modpack Changes, History coverage, Not captured, Unavailable, and Insufficient comparable evidence are present consistently.
- No untranslated localization key or debug-only text was observed on the executed routes.

## 19. Non-accusatory-language audit

- Production source/resources were searched for `culprit`, `guilty`, `proof`, `proven`, `confirmed cause`, `responsible mod`, `broken mod`, `bad mod`, `fault`, and `blame`.
- The only relevant public use of “proof/prove” is explicitly negating proof: a suspect or recurring pattern does not prove that a mod is defective or solely responsible.
- Development `detective_testculprit_*` identifiers remain confined to validation source sets and are absent from the public JAR.
- Attribution and temporal proximity remain evidence/context, not findings of guilt or causation.

## 20. Privacy/security audit

- No telemetry, analytics, network client, remote API, automatic upload, or server-side requirement was added.
- The only `URL` use in production resolves a loaded class's local `file:` code source for attribution; non-file schemes are rejected.
- Comparison/query/evolution models contain captured technical metadata only and do not add username, UUID, chat, server-message, raw object, exception-message, or source-path persistence.
- Derived evidence remains bounded 128-bit truncated SHA-256 signatures of normalized class/frame/path symbols plus bounded owner observations. It excludes raw stack text, source files, line numbers, thread names, object values, and player data.
- Case index and launch history remain local.
- Standard Support Reports are generated from an explicit ten-entry allow-list. They include neither `latest.log`, launch history, Case index/database, raw stack dumps, nor arbitrary Detective history.

## 21. Malformed-input handling

Conservative degradation is tested for:

- truncated/empty/malformed incident JSON;
- missing, malformed, corrupt, and unsupported derived evidence;
- malformed, missing, corrupt, and future Case indexes;
- missing, empty, truncated, malformed-field, partially malformed-record, structurally oversized, and future launch histories;
- malformed and future settings;
- missing Support Report source items and sources outside the Detective incident root;
- stale atomic temporary files.

Unsupported/corrupt persistent inputs are not overwritten merely to recover. Invalid optional evidence becomes unavailable/fallback evidence; it is never invented.

## 22. Determinism

- Repeated Technical Similarity returns identical scores/components.
- Search ordering uses explicit timestamp, duration, incident-ID, and stable-ID tie-breakers.
- Case clustering uses deterministic sorted inputs and complete-link comparisons.
- Stable Case identity remains founder-derived and does not change when membership grows.
- Nearby changes and launch records use explicit stable ordering.
- Case Evolution status/counts, scrollbar geometry, and launch-history eviction are deterministic for identical inputs.
- Tests assert source models are not mutated by comparison/query/evolution operations.

## 23. Public JAR audit

- Path: `C:\Users\fpoug\Desktop\Projets\Detective\build\libs\detective-0.9.0-dev.jar`
- Filename: `detective-0.9.0-dev.jar`
- Exact size: 515,636 bytes
- SHA-256: `df5ee139e501cdb2ac64eed7868f13ab603283c065ad88764dabff12fbd8f890`
- ZIP/JAR entries: 251
- `.class` files: 226
- Nested JARs: 0
- Embedded version: `0.9.0-dev`
- Embedded Minecraft range: exactly `1.21.1`
- Embedded NeoForge minimum: `21.1.235`
- Client-only status: `@Mod(..., dist = Dist.CLIENT)` plus client-side dependency metadata remains present.

Aggressive entry-name and decoded-content scans found zero contamination from tests, JUnit, TestNG, validation harnesses, `detectivevalidation`, culprit fixtures/mods, screenshots, generated reports, validation JSON, local histories, run directories, logs/GC logs, launch test data, or third-party validation JARs. Production Support Report exporter classes are intentionally present; no generated report artifact is packaged.

## 24. Defects found

- BLOCKER: none.
- MAJOR: none.
- Product MINOR: none confirmed.
- Validation-environment observation: intermittent missing glyphs appeared in some screenshots from the third-party pack profile. It affected unrelated Detective text and vanilla button labels, changed between consecutive processes, and disappeared in all three clean-profile controls without Sodium. It did not correlate with a Detective route or code path and is not classified as a Detective defect.

## 25. Fixes made

- Production fixes: none; no confirmed release-critical product defect required a code change.
- Added ten release-hardening tests described in section 4.
- A validation-only scroll value was changed temporarily to capture the actual Evolution section, then restored. No validation harness diff remains.

## 26. Known limitations

- Legacy incidents cannot provide enhanced frame/path signatures that were never captured; comparisons correctly use their smaller common evidence set.
- Case and Evolution statements are bounded by retained local history. “First recorded occurrence” is not “first ever occurrence.”
- Case similarity and nearby changes describe captured technical/temporal evidence; neither establishes causation.
- Incident/Case analysis is bounded to 500 incidents and launch history to 64 retained launches, so older coverage may be explicitly limited.
- Launch-history JSON has structural count bounds but no separate whole-file byte-size cap. It is local-only, and hostile oversized structures are rejected without overwrite.
- One-time snapshot and launch-history I/O occurs during client setup on the render thread. The realistic 64-launch/1,536-change median was approximately 3.704 ms load plus 6.397 ms save; it never runs from `render()` or per frame.
- The third-party validation-pack profile can intermittently produce incomplete font-atlas screenshots. Clean-profile 960×540 controls were stable; this remains an environment/validation limitation rather than a confirmed public-JAR defect.

## 27. Final release recommendation

No known release-critical Detective defect remains. Core 0.7/0.8 behavior is preserved, all important 0.9 backend/UI paths are validated, the public JAR is clean, and the documented limitations degrade conservatively.

**GO WITH KNOWN LIMITATIONS**
