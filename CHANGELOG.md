# Changelog

## 0.9.0

### Incident Investigation

- Added an Incident Investigation workflow for searching, filtering, selecting, and comparing retained Incidents.
- Added deterministic Incident Search & Filters across supported Incident metadata and captured evidence, with explicit empty and insufficient-evidence states.
- Added side-by-side Incident Comparison with normalized Technical Similarity derived from captured evidence. A similar score does not prove a shared cause or that a mod is defective.
- Added **What Changed? / Case Evolution** to show how a recurring Case developed across retained Incidents and which recorded modpack changes occurred nearby.
- “First recorded occurrence” means the earliest supported occurrence in Detective's retained local history, not the first time the pattern ever happened.
- Nearby modpack changes are presented as temporal context only. Temporal proximity does not establish causation.

### Local history and interface

- Added a bounded, local Modpack Launch History that records observed launch boundaries and added, updated, or removed mod versions for future Case Evolution analysis. It is not retroactive.
- Added precise wheel scrolling, visible scroll tracks, and draggable vertical scrollbars for long Investigation, Comparison, and Case Evolution screens.
- Preserved deterministic Case identity, complete-link clustering safety, existing attribution semantics, freeze detection, render-thread sampling, and Support Report behavior.
- Kept legacy 0.7/0.8 Incident histories usable; older Incidents may provide less comparable technical evidence because information that was never captured is not fabricated.

### Privacy and compatibility

- Investigation and launch-history processing remain client-side and local, with no telemetry, networking, or automatic uploads.
- `launch-history.json` is bounded to 64 retained launch records by default and stores timestamps plus compact mod change metadata. It does not add raw stack dumps, player identity, server addresses, or personal file paths.
- Support Reports remain explicit allow-list exports and do not automatically include `launch-history.json`, the Case database/index, or `latest.log`.
- Final release-candidate validation: 273 tests passed with 0 failures, 0 errors, and 0 skipped; 43 isolated NeoForge client startups completed successfully.

Technical Similarity, attribution, recurring patterns, and nearby changes describe captured evidence. They do not establish causation or prove that a mod is defective.

## 0.8.0

### Case Files

- Added **Case Files**, a local history view for recurring technical patterns across recorded Incidents.
- Added deterministic recurring-pattern detection based on Technical Similarity between captured stack, frame, class, leaf-owner, and stack-presence evidence. Primary Suspect attribution is not used as the main clustering criterion.
- A recurring Case requires at least three sufficiently similar Incidents and uses conservative complete-link clustering to avoid joining incompatible patterns through a borderline Incident.
- Added stable Case identity, so a matching Incident can join an existing Case without automatically renaming it.
- Added local Case persistence and deterministic reconciliation across restarts, growth, retention, and unrelated history changes.
- Case details show repeated technical and mod-owner evidence only when supported, together with occurrence count, first/last seen dates, stall-duration aggregates, pattern consistency, evidence strength, and navigation to retained related Incidents.
- Added English and French Case Files list, detail, empty-state, home-summary, and safety wording.

### Evidence persistence and compatibility

- New schema-v2 Incidents persist compact, bounded, privacy-conscious derived evidence signatures for normalized classes, frames, and stack paths, plus bounded owner observations and counts.
- Technical symbols are stored as truncated SHA-256 signatures; full raw thread dumps, source files, line numbers, thread names, object values, player data, and arbitrary runtime text are not added for Case Files.
- Existing 0.7.0/schema-v1 Incident history remains readable and is not destructively rewritten. Missing or malformed optional derived evidence falls back safely to supported legacy hot-class and owner evidence.
- Case analysis remains local-only, is bounded to the newest 500 eligible Incidents, and runs outside the Minecraft render thread.

### Release hardening

- Added compatibility coverage for legacy-only and mixed histories, absent/malformed derived evidence, missing/corrupt Case indexes, repeated restarts, retention, Case disappearance/reformation, and non-destructive Incident reads.
- Added deterministic Case lifecycle and complete-link bridge regression coverage.
- Final release-preparation baseline: 134 tests passed with 0 failures, 0 errors, and 0 skipped.

Attribution and recurring patterns describe captured evidence. They do not prove that a mod is defective or solely responsible. Recurring similarity does not establish causation.

## 0.7.0

First public release for Minecraft 1.21.1 and NeoForge.

### Included

- Automatic render-thread freeze detection with an adaptive threshold.
- A 30-second Black Box of nearby performance and world context.
- Evidence-based Primary Suspect ranking with explicit ambiguous, insufficient, possible-system, and unknown states.
- Incident dashboard, newest-first history, detail view, Technical Evidence, and Black Box graph.
- Modpack Changes between recorded launches.
- Local Support Report ZIP export with privacy preview and schema-versioned JSON.
- Optional Incident notifications, bounded history retention, and essential settings.
- Client-only operation with no required server installation.
- No Detective telemetry, analytics, remote API calls, or automatic uploads.
- Adopted the **Detective Proprietary License 1.0**; see [LICENSE](LICENSE) for the complete terms.

### Release hardening

- Validated Minecraft 1.21.1 on NeoForge 21.1.235, 21.1.238, and 21.1.248.
- Preserved 9/9 controlled Top-1 and 9/9 Top-3 attribution results from the v0.6 hardening baseline.
- Added corruption recovery, migration, bounded-queue/history, Support Report privacy, and client/server-isolation coverage.
- Completed a short 4 GiB compatibility smoke with 166 physical JARs and 257 loaded mod IDs under NeoForge 21.1.248.

### Known limitations

- Long-duration Medium/Large/Stress modpack soaks are not complete.
- Physical focus, resolution, GUI-scale, keyboard-navigation, and folder-opening checks still require final human validation.
- Attribution expresses captured evidence, not proof that a mod is defective.
