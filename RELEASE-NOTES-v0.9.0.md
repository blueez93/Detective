# Detective 0.9.0 — Investigation

**Modpack diagnostics, without the guesswork.**

Detective 0.9.0 makes retained evidence easier to investigate. You can search and filter Incidents, compare two captures side by side, review how a recurring Case evolved, and see recorded modpack changes near its first retained occurrence.

**Detect. Measure. Explain. Never accuse.**

## Incident Investigation

The new Investigation tools help you work through a larger local history without changing how Detective detects or attributes an Incident.

- Search retained Incidents using supported technical and contextual fields.
- Filter the history to narrow the evidence under review.
- Select two Incidents and compare them side by side.
- Move between related Incidents, Cases, comparisons, and Case Evolution views.

Empty, legacy, and insufficient-evidence states are shown explicitly rather than filled with invented conclusions.

## Technical Similarity

Incident Comparison calculates a deterministic normalized similarity from the technical evidence both Incidents can support. Strong captured stack, frame, class, leaf-owner, and stack-presence overlap matters more than contextual measurements such as stall duration. A previous Primary Suspect is not used as a shortcut for declaring two Incidents similar.

Technical Similarity describes overlap in captured evidence. **Similarity does not prove a shared cause or establish that a mod is defective.** Two Incidents attributed differently may still share strong technical evidence, while two Incidents naming the same suspect may remain technically different.

Legacy Incidents are compared only with the evidence they actually retained. Detective does not manufacture newer evidence signatures for older files.

## What Changed?

Case Evolution summarizes how a recurring Case appears across retained Incident history. It can show occurrence timing, supported changes in the captured pattern, and recorded modpack changes near the first retained occurrence.

“First recorded occurrence” means the earliest supported occurrence still available in Detective's local retained history. It does not mean the first occurrence ever, and retention may limit how far back Detective can look.

Nearby changes are context for investigation, not a verdict. **Temporal proximity does not establish causation.** An added, updated, or removed mod appearing near an Incident does not prove that the change caused the stall.

## Modpack Launch History

Detective now keeps a bounded local history of observed modpack launch boundaries and the added, updated, or removed mod versions detected between snapshots. The default history retains up to 64 launch records and records when earlier coverage is unavailable.

This history begins only after installing a version that supports it. It is not retroactive and cannot reconstruct launches or changes that Detective never observed.

## UI Improvements

Long Investigation screens now support precise wheel scrolling and a draggable vertical scrollbar. Scrolling remains clamped to the available content, including after resizing, and long technical or mod names are kept inside their panels with clear ellipses where space is limited.

The 0.9 Investigation, Comparison, Search, Filter, and What Changed? interfaces are available in English and French.

## Privacy

Detective remains client-side and local:

- no telemetry or analytics;
- no networking or remote API calls;
- no automatic uploads;
- no server-side installation requirement.

`launch-history.json`, Incident history, and the Case index remain under the Minecraft instance's local `detective` data directory. Launch History stores timestamps and compact added/updated/removed mod metadata; it does not add raw stack dumps, player identity, server addresses, or personal filesystem paths.

Support Reports remain explicit allow-list exports. They do not automatically include `launch-history.json`, the Case database/index, or `latest.log`. Always review a diagnostic file before sharing it.

## Compatibility / Upgrade

Detective 0.9.0 targets:

- Minecraft **1.21.1**;
- Java **21**;
- NeoForge **21.1.235 or newer in the 21.1.x line**;
- client-side installation only.

To upgrade from 0.7.0 or 0.8.0, replace the previous Detective JAR with `detective-0.9.0.jar`. Existing Incident and Case history remains usable and is not destructively rewritten merely by reading it.

Older Incidents may contain less comparable technical evidence because their schema did not retain the newer derived signatures. They remain reviewable, and comparison fails conservatively when the common evidence is insufficient.

## Validation summary

- **273 tests passed** with 0 failures, 0 errors, and 0 skipped.
- **43 isolated NeoForge client startups** completed successfully.
- The final public JAR was audited for client-only metadata and isolation from tests, validation harnesses, local histories, logs, and development fixtures.
- Release preparation changes only the version and public documentation; it does not alter detection, sampling, attribution, similarity, clustering, persistence, or UI behavior.

## Known Limitations

- Incident, Case, and launch histories are deliberately bounded. Older retained data can be removed according to the applicable limits.
- Legacy Incidents cannot provide enhanced evidence that was never captured, so some comparisons are less detailed or explicitly insufficient.
- Case Evolution can only describe launches and changes Detective observed after Launch History became available; it is not retroactive.
- A small amount of local snapshot and Launch History I/O occurs once during client setup. It does not run per frame or from screen rendering.
- Some stalls remain ambiguous, system-related, unknown, or too sparsely sampled to support a comparison or recurring Case.

## Installation

1. Use Minecraft **1.21.1** with Java **21**.
2. Install NeoForge **21.1.235 or newer in the 21.1.x line**.
3. Place `detective-0.9.0.jar` in the client instance's `mods` folder.

NeoForge 21.1.248 is the recommended tested runtime. Review every diagnostic file before sharing it.
