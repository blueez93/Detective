# Detective 0.8.0

**Modpack diagnostics, without the guesswork.**

Detective 0.8.0 adds Case Files: a local way to recognize recurring technical patterns across previously recorded performance Incidents.

**Detect. Measure. Explain. Never accuse.**

## What's new

- **Case Files** group recurring, technically similar Incidents into a reviewable local history.
- Each Case shows a stable Case ID, occurrence count, first and last seen dates, average and longest stall duration, pattern consistency, and aggregate evidence strength.
- Case details present repeated technical signatures and mod-owner observations only when the stored evidence supports them.
- Retained related Incidents can be opened directly from a Case for their existing attribution, Technical Evidence, and Black Box details.
- Case Files are available in English and French and include explicit non-causation wording.

## How Case Files work

A recurring Case requires at least three sufficiently similar Incidents. Detective compares captured technical evidence and applies deterministic, conservative complete-link clustering: an Incident must be sufficiently similar to every existing member before it can join a Case. A borderline Incident cannot be used merely as a bridge between otherwise incompatible groups.

Case identity remains stable as matching Incidents join. Detective stores a small local Case index and reconciles recomputed membership with the previous index across restarts. Unrelated Incidents do not change an established Case. If history retention reduces a Case below three members, that Case disappears; a later independently established pattern receives a new identity.

## Technical Similarity

Technical Similarity gives greater weight to captured stack, frame, class, leaf-owner, and stack-presence overlap than to contextual measurements such as stall duration. The previous Primary Suspect is not used as the main grouping criterion, so Case Files do not merely confirm earlier attribution.

Cases may therefore form when underlying technical evidence is strongly similar even if prior attribution states differ. Conversely, Incidents with the same attributed suspect do not automatically form a Case when their technical fingerprints differ substantially.

Attribution and recurring patterns represent captured evidence. They do not prove that a mod is defective or solely responsible. **Recurring similarity does not establish causation.**

## Privacy and local processing

Case analysis is local-only. Detective adds no telemetry, networking, automatic upload, account, or server-side requirement.

New schema-v2 Incidents can store a compact, bounded derived evidence block containing observation counts, bounded mod-owner observations, and truncated SHA-256 signatures of normalized class names, class/method frames, and stack paths. Hidden-class runtime suffixes are normalized before hashing.

Case Files do not add full raw thread dumps, source filenames, line numbers, thread names, object values, player data, or arbitrary runtime text. The Case index and Incident history remain under the local `detective` data directory. Standard Support Report privacy behavior is unchanged.

## Compatibility and upgrading from 0.7.0

Detective 0.8.0 targets:

- Minecraft **1.21.1**;
- Java **21**;
- NeoForge **21.1.235 or newer in the 21.1.x line**;
- client-side installation only.

To upgrade, replace the previous Detective JAR with `detective-0.8.0.jar`. No server installation is required.

Existing 0.7.0/schema-v1 Incident files remain readable and are not destructively migrated or rewritten. Legacy Incidents without derived evidence use the supported hot-class and owner fallback where sufficient evidence exists. Detective never fabricates enhanced signatures for old history, so Case Files may initially be empty or less detailed until enough supported evidence has accumulated.

Missing or malformed optional derived evidence fails safely. A missing Case index is rebuilt from eligible local history; an unsupported or corrupt existing index is preserved rather than overwritten automatically.

## Validation summary

- **134 tests passed**.
- **0 failures, 0 errors, 0 skipped**.
- Covered legacy-only and mixed histories, missing/malformed derived evidence, missing/corrupt Case indexes, repeated restarts, stable identity growth, unrelated Incidents, retention, Case disappearance and reformation, complete-link bridge prevention, deterministic UI derivation, privacy, and public-JAR isolation.
- Case processing remains bounded to the newest 500 eligible Incidents and runs outside the Minecraft render thread.

## Known limitations

- A recurring Case requires at least three sufficiently similar Incidents; two matching Incidents are intentionally not presented as an established recurring pattern.
- Older 0.7.0 Incidents do not contain the stronger schema-v2 derived stack signatures, so legacy-only Cases depend on the evidence those files already retained.
- Retention can remove Case members. A Case below three members disappears, and a pattern established again later receives a new Case ID.
- An unsupported or corrupt Case index is not overwritten automatically. Current Cases can still be recomputed, but identity continuity cannot be guaranteed if the previous index cannot be read and founding Incidents later leave retained history.
- Analysis is bounded to the newest 500 eligible Incidents.
- Long-duration Medium/Large/Stress modpack soaks are not complete. Physical Alt+Tab, the full resolution/GUI-scale matrix, keyboard navigation, and `Open Folder` behavior still require final human validation.
- Some stalls remain ambiguous, system-related, unknown, or too sparsely sampled to establish a Case.

## Installation

1. Use Minecraft **1.21.1** with Java **21**.
2. Install NeoForge **21.1.235 or newer in the 21.1.x line**.
3. Place `detective-0.8.0.jar` in the client instance's `mods` folder.

NeoForge 21.1.248 is the recommended tested runtime. Review every diagnostic file before sharing it.
