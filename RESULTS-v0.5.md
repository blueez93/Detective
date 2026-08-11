# Detective v0.5.0-alpha.1 — Support & Daily Use

Validation date: 2026-08-11  
Runtime: Minecraft 1.21.1, NeoForge 21.1.235, Java 21  
Product rule: **Detect. Measure. Explain. Never accuse.**

## Result

| Criterion | Result |
| --- | --- |
| Clean build | PASS |
| Tests | PASS — 71 tests, 0 failures |
| `runClient` | PASS |
| Incident notification | PASS |
| UX notification cooldown | PASS |
| Local support ZIP | PASS |
| Human-readable report | PASS |
| Schema-versioned JSON | PASS |
| No Detective network code | PASS |
| Sensitive-data audit | PASS |
| Settings persistence | PASS |
| Count/age retention | PASS |
| HIGH / AMBIGUOUS / INSUFFICIENT / SYSTEM / UNKNOWN export | PASS |
| Public JAR isolation | PASS |

The production attribution algorithm, threshold, engine debounce, watchdog sampling, Black Box recording, confidence classification, and `ModSourceResolver` were not changed.

## Files created

Production support services:

- `src/main/java/fr/apocalypsebleu/moddetective/client/support/DetectiveSupportService.java`
- `src/main/java/fr/apocalypsebleu/moddetective/client/support/IncidentToastNotifier.java`
- `src/main/java/fr/apocalypsebleu/moddetective/support/DetectiveSettings.java`
- `src/main/java/fr/apocalypsebleu/moddetective/support/DetectiveSettingsStore.java`
- `src/main/java/fr/apocalypsebleu/moddetective/support/IncidentHistoryRetention.java`
- `src/main/java/fr/apocalypsebleu/moddetective/support/IncidentNotificationCooldown.java`
- `src/main/java/fr/apocalypsebleu/moddetective/support/report/ReportPrivacy.java`
- `src/main/java/fr/apocalypsebleu/moddetective/support/report/SupportReportData.java`
- `src/main/java/fr/apocalypsebleu/moddetective/support/report/SupportReportExporter.java`

UI:

- `src/main/java/fr/apocalypsebleu/moddetective/client/ui/ExportSupportReportScreen.java`
- `src/main/java/fr/apocalypsebleu/moddetective/client/ui/SupportReportCreatedScreen.java`
- `src/main/java/fr/apocalypsebleu/moddetective/client/ui/DetectiveSettingsScreen.java`
- `src/main/java/fr/apocalypsebleu/moddetective/client/ui/ClearIncidentHistoryScreen.java`

Tests and report:

- `src/test/java/fr/apocalypsebleu/moddetective/support/DetectiveSettingsStoreTest.java`
- `src/test/java/fr/apocalypsebleu/moddetective/support/IncidentNotificationCooldownTest.java`
- `src/test/java/fr/apocalypsebleu/moddetective/support/IncidentHistoryRetentionTest.java`
- `src/test/java/fr/apocalypsebleu/moddetective/support/report/SupportReportFixtures.java`
- `src/test/java/fr/apocalypsebleu/moddetective/support/report/SupportReportExporterTest.java`
- `RESULTS-v0.5.md`

## Files modified

- Project/version documentation: `AGENTS.md`, `PROJECT_STATE.md`, `README.md`, `gradle.properties`.
- Client lifecycle/integration: `ModDetective.java`, `ClientPerformanceEvents.java`, `FreezeDetector.java`.
- Storage/data: `ModDetectivePaths.java`, `DetectiveUiService.java`, `IncidentJsonAdapter.java`, `IncidentDetailViewModel.java`.
- Existing UI: `DetectiveHomeScreen.java`, `IncidentDetailScreen.java`.
- Localization/copy: `assets/detective/lang/en_us.json`, `fr_fr.json`, `LocalizationCopyTest.java`.
- Development-only validation: `UiValidationPlan.java`, `ValidationCommands.java`, `ValidationHarness.java`.

The `FreezeDetector` change is limited to an optional post-save callback invoked after `IncidentStore.save` succeeds. Existing constructors retain a no-op callback. `ClientPerformanceEvents` adds a shutdown guard so a late render event cannot submit work after the incident worker closes. Neither change affects detection or attribution.

## Support architecture

### Post-save notification

The incident worker still performs analysis and atomic JSON persistence. Only after a successful save does it call the client support service. The support service uses its own single low-priority worker with a bounded queue of 8 tasks. It performs retention and cache invalidation off the render thread, then schedules only the vanilla toast creation on Minecraft's client executor.

The UX cooldown is independent of the two-second engine debounce:

- default notification cooldown: 8 seconds;
- each incident path is registered once;
- up to 256 recent identifiers are retained, preventing an unbounded seen-set;
- incidents inside the UX cooldown remain stored but do not create another toast;
- disabled notifications register the event without showing it later.

The toast wording is cautious:

```text
Detective recorded a 621.0 ms freeze
Primary suspect: Create — HIGH EVIDENCE
```

For non-attributable incidents:

```text
Detective recorded a 478.0 ms freeze
No reliable mod attribution
```

The v0.5 toast is intentionally non-interactive. Opening the latest incident remains available from the dashboard.

### Settings

Settings are stored atomically as UTF-8 in `<game directory>/detective/settings.json`:

```json
{
  "schemaVersion": 1,
  "incidentNotifications": true,
  "incidentHistoryLimit": 50,
  "dataRetentionDays": 30,
  "showTechnicalEvidenceByDefault": false
}
```

Unknown fields are ignored. A malformed whole file falls back to defaults; an invalid individual field falls back independently while valid fields remain usable. Supported UI presets are 25/50/100 incidents and 7/30/90 days. No freeze-threshold control was added.

`Show technical evidence by default` opens a regular incident detail at its technical section after the detail has been loaded and laid out. Parsing remains off the render thread.

### Retention and clearing

Retention considers only files named `freeze-*.json` under the normalized Detective incidents directory. It reads `detectedAtEpochMs`, falling back to file time for unreadable legacy records. A record is deleted when it exceeds either the configured age or count limit, so the more restrictive rule wins.

The default runtime state was verified at 50 retained incident files. Unit tests verify newest-first count pruning and 30-day age pruning. The clear-history test verifies that incident records are removed while `notes.txt` and `snapshots/last-session.json` remain untouched.

## Support Report format

Reports are written atomically under `<game directory>/detective/reports` using the local filename format:

```text
detective-report-2026-08-11_21-42-16.zip
```

The standard ZIP contains exactly ten allow-listed UTF-8 entries:

```text
detective-report/README.txt
detective-report/summary.txt
detective-report/manifest.json
detective-report/detective/version.json
detective-report/detective/incidents.json
detective-report/detective/selected-incident.json
detective-report/detective/settings-summary.json
detective-report/modpack/mods.json
detective-report/modpack/changes.json
detective-report/system/environment.json
```

Every JSON file has `schemaVersion: 1`. The manifest also has `reportFormat: 1`, exact Detective/Minecraft/NeoForge versions, ISO generation time, and incident count. The dashboard exports the latest incident; an incident detail exports that current incident. No all-history mode was added.

Installed-mod entries contain only `modId`, display `name`, `version`, `loader`, and the source JAR basename. No JAR is copied. Modpack changes are split into `added`, `updated`, and `removed`; a missing snapshot is represented by `available: false`.

System information is allow-listed to OS description, architecture, Java/Minecraft/NeoForge/Detective versions, approximate used/max JVM memory, logical processor count, and `gpu: unavailable`. GPU probing was not added because no clean dependency-free production API was available.

The human-readable files repeat that a suspect is not proof and that Detective does not upload reports automatically. `latest.log` is never inspected or added.

### State examples

| State | Export behavior |
| --- | --- |
| `HIGH_EVIDENCE` | Primary suspect object included; test fixture reports Create with leaf ownership 29/30 and stack presence 29/30. |
| `MODERATE_EVIDENCE` / `LOW_EVIDENCE` | Primary suspect remains a ranking result; raw sampling evidence stays separate from the evidence-strength label. |
| `AMBIGUOUS_ATTRIBUTION` | `primarySuspect: null`; plausible suspects remain in the ranked evidence list. |
| `INSUFFICIENT_EVIDENCE` | `primarySuspect: null`; no mod is invented when the suspect list is empty. |
| `JVM_GC_SUSPECTED` / `NATIVE_OR_DRIVER_STALL_POSSIBLE` | Human state is `POSSIBLE_SYSTEM_STALL`; raw engine state is retained separately; primary suspect is null. |
| `UNKNOWN` | Primary suspect is null and the report still contains timing/context/Black Box availability. |

Missing coordinates are explicit JSON nulls. Partial Black Box data is exported with `partial: true`, original sample count, and whatever safe points remain.

## Privacy and security validation

The exporter never receives arbitrary logs or environment maps. Its input is an explicit record of allowed fields. Mod-provided strings pass through defense-in-depth sanitization for personal user paths, sensitive labels/assignments, IPv4 addresses, UUIDs, control characters, and excessive length. Source file metadata is reduced to a basename.

The integration test opens the final ZIP and searches its names and contents for:

- `accessToken`;
- OS username / `username`;
- `user.home` and its current value;
- `hostname`;
- server IP values;
- session id markers;
- player-style UUIDs;
- `latest.log`.

Result: **0 matches**. It also parses every JSON entry from the final ZIP and verifies the schema version.

The runtime harness generated `detective-report-2026-08-11_06-09-00.zip`:

- 4,747 bytes;
- 10 entries;
- 8 valid JSON documents;
- `latest.log` absent;
- audited sensitive marker matches: 0.

A source audit found no HTTP client, URL connection, socket, telemetry, analytics, or `latest.log` reference in `src/main`. The build script's explicit development-only validation-pack downloader remains outside the runtime mod and is not included in the public JAR.

## Tests

Final command:

```text
.\gradlew.bat test --no-daemon
```

Result: **BUILD SUCCESSFUL**, 22 suites, **71 tests, 0 failures**. All 53 v0.4.1 tests remain green; 18 tests were added:

- manifest, schema and deterministic ZIP name;
- human summary and cautious HIGH evidence output;
- ambiguous, insufficient, system, unknown/no-suspect exports;
- absent snapshot and changes;
- strict file allow-list and size budget;
- final-ZIP privacy/redaction and `latest.log` exclusion;
- settings serialization, partial corruption and malformed JSON;
- history count, age, and safe clearing;
- duplicate/burst/disabled notification cooldown.

## Build

Final command:

```text
.\gradlew.bat clean build --no-daemon
```

Result: **BUILD SUCCESSFUL** in 41 seconds. The build compiled main, all three development validation mods, and tests. Artifact:

```text
build/libs/detective-0.5.0-alpha.1.jar
```

No commit or tag was created.

## `runClient`

Final command:

```text
.\gradlew.bat runClient --no-daemon -PdetectiveValidationWorld=DetectiveValidation -PdetectiveValidationAutorun=ui -PdetectiveValidationExit=true
```

Result: **BUILD SUCCESSFUL** in 2 minutes 6 seconds.

- Detective 0.5.0-alpha.1 loaded with 21 mods on NeoForge 21.1.235.
- The `DetectiveValidation` world opened and the watchdog attached to the render thread.
- 35 screenshots covered the v0.4.1 states plus export preview, export success, settings, clear-history confirmation, notification, and long French layouts.
- The runtime support report logged `SUPPORT_REPORT result=PASS`.
- A four-event post-save notification burst logged one expected visible toast; the final capture shows one fully visible cautious toast.
- The route logged `UI screenshot route completed`, stopped the watchdog and support/UI workers, and shut down the integrated server cleanly.
- No Detective startup, UI, export, worker, queue, or shutdown warning/error remained in the final log.

The final short UI route measured approximately 48.5 watchdog samples/s, 108.5 µs cumulative mean capture time, rolling p99 359.5 µs, approximately 2.0 MiB retained estimate, queue 0/8, and zero dropped incidents. These remain within the v0.3.1 budget; v0.5 did not alter watchdog sampling.

The constrained external Windows GUI controller could not initialize (`EPERM` on the Codex application directory), so physical mouse clicks and the operating-system folder opener were not claimed as manually tested. Screen construction/routing and visual fit were validated through the existing development harness and inspected captures. `Open Folder` uses Minecraft's local `Util.getPlatform().openFile` path, but still needs one manual click pass outside this controller.

## Public JAR inspection

Artifact: `build/libs/detective-0.5.0-alpha.1.jar`

- Size: 202,317 bytes.
- SHA-256: `E9781766347AF8092EB2EB324BE21AA0BB9FAF1A1B34D2FC46E0A64F81A97C50`.
- 108 entries, including 87 classes.
- Roots: `META-INF`, `assets`, `fr`.
- All classes use the `fr` root; no third-party class namespace is bundled.
- Embedded metadata: `modId=detective`, `version=0.5.0-alpha.1`, client-side Minecraft 1.21.1 / NeoForge 21.1.235 dependencies.
- Both `en_us` and `fr_fr` resources are present.
- No validation harness, test culprit, ground truth, validation pack/result, GC validation file, third-party mod, nested JAR, or `latest.log` entry was found.

## Remaining limitations and v0.5.1 recommendation

- The toast is deliberately simple: suppressed events are not grouped later and the toast is not clickable. v0.5.1 may add a cautious grouped count or direct-open action only if vanilla APIs support it cleanly.
- Standard reports export one current/latest incident. Multi-incident selection and optional explicitly warned log attachment remain future work.
- GPU is reported unavailable rather than using brittle reflection or a new runtime dependency.
- Human report text is English for broad support interoperability; only the in-game UI is localized in English/French.
- Perform a manual physical-input pass for every new button, settings cycling, clear confirmation/cancel, successful export, failure state, and `Open Folder` on Windows.
- Consider adding export progress/cancellation only if real-world reports become materially larger; the validated report is currently 4.7 KiB and the worker queue is bounded.

Recommendation for v0.5.1: **proceed with focused manual interaction/accessibility testing and release packaging, not new engine or cloud features**. The v0.5 support workflow is technically ready for alpha use.
