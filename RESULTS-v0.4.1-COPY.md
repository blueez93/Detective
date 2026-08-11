# Detective v0.4.1 — Product Copy and UI Polish

This report preserves the pre-change copy audit before implementation. Validation results are completed after build and runtime checks.

## 1. Text that differed from the specification

| Area | v0.4 text or behavior | Canonical gap found before changes |
| --- | --- | --- |
| Home header | `Detective` / `Freeze evidence from this Minecraft installation` | The product header was not `DETECTIVE` and the canonical subtitle `Modpack diagnostics, without the guesswork.` was absent. |
| Home status | `Monitoring is active` | The canonical status `Watching for performance issues` and the separate `Performance issues detected` state were absent. |
| Home empty state | `No incidents have been recorded yet.` | The two-part explanation about the current session and background monitoring was missing. |
| Home summary | `Summary`, recent 24-hour count, combined evidence count | The canonical session labels and careful strong-attribution sentence were not used. |
| Home navigation | `Incidents`, `Last incident`, `Modpack changes` | The canonical labels are `View Incidents`, `Latest Incident`, and `Modpack Changes`. |
| Product footer | No product statement | `Detective finds the evidence. You make the call.` was missing. |
| Incident list | `Detective incidents` / `Most recent incidents first` | The canonical `INCIDENTS` header and `Performance stalls recorded by Detective.` subtitle were absent. |
| Incident-list empty state | `No incidents to display.` | The canonical title plus two explanatory lines were missing. |
| Incident overview | `Incident details`, `Context`, `Duration`, `Active threshold`, raw classification | The canonical incident heading and labels (`Occurred`, `Location`, `Detection threshold`, `Samples captured`, `Classification`) were not consistently presented. |
| Primary suspect | Badge followed by a compact generic description | `PRIMARY SUSPECT`, `Evidence strength`, the captured-sample ownership sentence, and `Why this suspect?` with its canonical caution text were missing. |
| Evidence levels | Short generic descriptions | The canonical HIGH/MODERATE/LOW evidence titles and explanations were not used verbatim. |
| Ambiguous attribution | Generic evidence description inside the primary panel | The canonical special-state presentation, `Possible suspects`, and the rule not to display a primary suspect were missing. |
| Insufficient evidence | Generic evidence description | The canonical three-part explanation and `View Technical Evidence` wording were missing. |
| JVM/GC/native/driver | Separate short labels and stronger GC wording | The UI did not use the shared cautious `POSSIBLE SYSTEM STALL` presentation and canonical possible-source explanation. |
| Unknown | `Unknown` with a generic cause sentence | The canonical `UNKNOWN SOURCE` heading and careful explanation were absent. |
| Other suspects | Ranked names only | The canonical distinction between secondary evidence and background stack presence was absent. |
| Technical evidence | `Watchdog samples`, abbreviated `Leaf` / `Presence`, combined percentage rows | Canonical metric names and the leaf-ownership / stack-presence tooltips were absent. |
| Black Box | `Black Box — frame time`, stored-sample count, `Partial history` | The canonical heading, description, and two-line partial-data warning were absent. |
| Modpack Changes | Current mod count subtitle; one-line empty and unavailable states | The canonical subtitle and two-line empty / first-launch states were absent. Category labels existed only as row tags rather than clear grouped sections. |
| French localization | Faithful overall, but abbreviated and occasionally stronger than the canonical caution level | The same missing canonical structure applied; JVM/GC wording in particular could imply a stronger diagnosis than the evidence supports. |
| Text fitting | Several rows used `plainSubstrByWidth` | Important copy could be silently truncated instead of wrapped, expanded, or exposed through a tooltip. |

No forbidden accusatory wording was found in the production screen classes. Development-only controlled-validation identifiers and logs still use `culprit`; they are not user-facing and are excluded from the public JAR.

## 2. Text changed

- The home screen now uses the canonical product header, subtitle, monitoring/detected states, session copy, navigation labels, and product footer.
- Incident list headings, cards, special-state summaries, empty-state explanation, and narration use cautious localized copy.
- Attributed incidents now separate rank (`PRIMARY SUSPECT`), evidence strength (`HIGH`, `MODERATE`, `LOW`), and raw evidence. They show the canonical ownership count and complete `Why this suspect?` explanation.
- Ambiguous, insufficient, possible-system, and unknown states now have dedicated presentations. None assigns or displays a primary suspect.
- JVM/GC and native/driver engine states retain their distinct raw classification while sharing the cautious user-facing `POSSIBLE SYSTEM STALL` wording.
- Other suspects distinguish leaf-owned secondary evidence from background stack presence.
- Technical evidence uses the canonical metric names and localized leaf-ownership / stack-presence tooltips. Sampling shares remain confined to this technical section and are never labeled as guilt or confidence.
- Black Box copy now includes the canonical description, frame/memory/sample labels, and complete partial-data warning.
- Modpack Changes now groups entries under `ADDED`, `UPDATED`, and `REMOVED`, with canonical empty and first-launch states.
- The public NeoForge mod description and project promise were aligned with the same non-accusatory product voice.

## 3. Text intentionally left different

- French copy is meaning-equivalent rather than word-for-word so that it remains natural and cautious; `Primary suspect` is consistently `suspect principal`, never `coupable` or `mod responsable`.
- Exact persisted timestamps remain visible instead of the relative-time examples in the copy document. Exact time was already part of the v0.4 incident model and is more useful for correlating logs.
- Technical `Classification` retains the raw engine state (for example `JVM_GC_SUSPECTED`) below the cautious user-facing state. This preserves useful technical distinction without presenting it as proven causality.
- Legacy incident JSON without leaf-ownership data uses an explicit stack-presence sentence instead of falsely claiming ownership of the active frame.
- Vanilla `Back` / `Retour` continues to come from `CommonComponents.GUI_BACK` rather than a Detective-specific translation key.

## 4. `en_us` keys added or changed

`en_us` and `fr_fr` now contain the same 123 keys. English added 79 keys and changed 23 existing values.

Added keys, grouped by exact prefix:

- Global: `detective.ui.none`, `detective.ui.tagline`.
- Home: `home.{empty.body,empty.headline,issues_detected,metric,metric.evidence,metric.incidents,metric.last_freeze,metric.last_suspect,metric.recent,metric.status,session,session_incidents.one,session_incidents.many,status.monitoring,strong_attribution.one,strong_attribution.many}`.
- Incident list: `incidents.{card.ambiguous,card.insufficient,card.system,card.unknown,context,empty.body,empty.hint,empty.title,freeze,narration}`.
- Incident detail: `incident.{ambiguous.body,ambiguous.lead,background_presence,black_box.description,black_box.frames,black_box.metadata,black_box.partial.body,black_box.partial.title,classification,evidence_strength,insufficient.body,insufficient.closing,location,occurred,possible_suspects,primary_ownership,primary_presence,secondary_evidence,system.closing,system.lead,system.sources,technical.classification,technical.depth,technical.extra,technical.leaf,technical.leaf.tooltip,technical.presence,technical.presence.tooltip,technical.samples,unknown.body,view_technical,why.1,why.2,why.3,why.title}`.
- Modpack: `modpack.{empty.body,empty.title,narration.added,narration.removed,narration.updated,unavailable.body,unavailable.title}`.
- Evidence: `evidence.{strength.high,strength.low,strength.moderate,system,system.description}`.

Changed existing values include the home subtitle/navigation/monitoring copy, incident and list titles, duration/threshold/sample labels, Black Box heading, empty technical copy, Modpack subtitle, all evidence descriptions, and `UNKNOWN SOURCE`.

Nineteen obsolete single-line or diagnosis-specific keys were retired, including `evidence.gc*`, `evidence.native*`, the old one-line home/list/modpack empty keys, and the abbreviated `leaf_evidence` / `depth_evidence` keys.

## 5. `fr_fr` keys added or changed

French added the same 79 keys as English and changed 29 existing values. In addition to the shared structural changes, the following existing French values were specifically revised for fit or caution: the HIGH/MODERATE/LOW labels, home navigation, Black Box empty copy, category labels, technical labels, and unknown-state wording. A test rejects `coupable`, `mod responsable`, and `cause certaine` from the localized UI resources.

## 6. Screens affected

- `DetectiveHomeScreen`: canonical status/session copy, fixed navigation, responsive scroll area, deterministic preloaded validation state.
- `IncidentListScreen`: canonical header and empty state, larger wrapping cards, localized narration.
- `IncidentDetailScreen`: attributed explanation, all special states, technical tooltips, partial Black Box messaging, dynamic panel heights, and a working `View Technical Evidence` action.
- `ModpackChangesScreen`: canonical header/empty states, grouped categories, localized narration, deterministic validation state.
- `DetectiveUiRenderer`: canonical footer and reusable centered/wrapped-text sizing helpers.
- Title and pause entry buttons remain unchanged and continue to use the canonical name `Detective`.

Created during this mission: `RESULTS-v0.4.1-COPY.md` and `LocalizationCopyTest.java`. The supplied canonical `UI_COPY_V0.4.md` was read and left unchanged; it remains an untracked worktree file until the milestone is committed.

## 7. Layout changes required by the copy

- Footer height increased to provide a dedicated product-statement line above the vanilla Back button.
- Home content became vertically scrollable inside a scissored viewport. This fixed overlap at the actual 427×240 logical GUI size while keeping navigation fixed and immediately reachable.
- Incident cards increased from 48 to 64 logical pixels and wrap primary/special-state copy onto two lines.
- Detail panels calculate height from wrapped translated text. The canonical `Why this suspect?` explanation no longer depends on a fixed 72-pixel panel.
- Black Box and technical-evidence sections gained separate metadata lines; the detail screen's existing scrolling handles their extra height.
- Modpack category headings are dedicated scroll-list entries, with dividers and category colors.
- Long English and French special-state paragraphs were verified through scrolled harness captures rather than shortened.

## 8. Special-state results

| State | Result |
| --- | --- |
| HIGH / MODERATE / LOW | Rank, evidence strength, ownership count, `Why this suspect?`, and caution paragraph render separately. |
| AMBIGUOUS_ATTRIBUTION | PASS — no primary suspect; canonical explanation and `Possible suspects` list shown. |
| INSUFFICIENT_EVIDENCE | PASS — no primary suspect; canonical explanation and technical-evidence action shown. |
| JVM_GC_SUSPECTED | PASS by mapping/unit test — raw classification retained; public copy is `POSSIBLE SYSTEM STALL`. |
| NATIVE_OR_DRIVER_STALL_POSSIBLE | PASS in runtime capture — no mod named; canonical possible-source explanation shown. |
| UNKNOWN | PASS — `UNKNOWN SOURCE`, no primary suspect, and cautious explanation shown. |
| Complete Black Box | PASS — graph, threshold, frame time, memory, and sample count visible across scrolled captures. |
| Partial Black Box | PASS — `Partial Black Box data` plus the full unavailable-history explanation shown. |

## 9. Build result

`./gradlew.bat clean build --no-daemon` — **BUILD SUCCESSFUL** in 53 seconds. Minecraft 1.21.1, NeoForge 21.1.235, and Java 21 remained unchanged.

## 10. Test count

`./gradlew.bat test --no-daemon` — **53 tests, 0 failures** across 18 suites (49 preserved + 4 new copy/state tests).

New coverage checks canonical English values, English/French key parity, forbidden accusatory wording, special-state wording mappings, and system-stall mapping. Memory formatting assertions were added to the existing formatter test.

## 11. `runClient` result

Final command:

```text
.\gradlew.bat runClient --no-daemon -PdetectiveValidationWorld=DetectiveValidation -PdetectiveValidationAutorun=ui -PdetectiveValidationExit=true
```

**BUILD SUCCESSFUL** in 1 minute 39 seconds. The realistic 21-mod development environment and `DetectiveValidation` world loaded, Detective reported `Ready`, the watchdog attached to the render thread, the complete UI route finished, and the watchdog stopped cleanly. No Detective startup/UI exception was found in `run/client/logs/latest.log`.

## 12. Visual validation

The development-only harness generated and the audit inspected 28 screenshots at the runtime's 854×480 framebuffer / 427×240 logical GUI size. Coverage includes:

- pause and title entry buttons;
- home with incidents, scrolled session summary, and empty home;
- multiple incident cards and empty list;
- HIGH, MODERATE, LOW, ambiguous, insufficient, possible-system, and unknown details;
- full `Why this suspect?` and caution text across scrolled views;
- complete and partial Black Box views;
- technical evidence plus the leaf-ownership tooltip;
- Modpack Changes with ADDED/UPDATED/REMOVED, no changes, and no previous snapshot;
- French ambiguous and no-snapshot layouts.

No important text was shortened to alternate wording. Long detail content is exposed by the existing scroll behavior, and the home screen now scrolls instead of drawing under fixed navigation. The external Windows GUI controller could not initialize because the Codex application denied access to its local app path (`EPERM`), so no claim is made that physical mouse clicks or alt-window interaction were manually exercised. Screen routing and captures were performed by the existing development harness as requested.

## 13. Public JAR contents

Artifact: `build/libs/detective-0.4.1-alpha.1.jar`

- SHA-256: `7C95F0B89393AA5852CA57D4CBBB5E3EA817D8F8BC26E02483618163DC60D24D`
- 87 entries, including 69 class files.
- Every class is under `fr/apocalypsebleu/moddetective`.
- Only the expected top-level paths are present: `META-INF`, `assets`, and `fr`.
- Both `assets/detective/lang/en_us.json` and `fr_fr.json` are present.
- No `detectivevalidation`, test culprit, validation harness, ground truth, validation results/assets, GC validation configuration, or third-party class namespace was found.

## 14. Engine changes

None. Ranking, evidence classification, watchdog, Black Box recording, incident detection, snapshots, and `ModSourceResolver` were not modified. `EvidenceBadge` remains a UI-only mapping over the existing raw engine state; JVM/GC and native/driver remain distinguishable in technical classification.

## 15. Remaining issues before v0.4.1 final

- Perform one manual interactive pass outside the constrained Codex controller: click every navigation entry, the `View Technical Evidence` action, both technical tooltips, and resize/change GUI scale while scrolling.
- The publication artifact and embedded NeoForge metadata are aligned on `0.4.1-alpha.1`.
- The list context line still uses exact time/dimension/coordinates on one line. It fits the validated scale, but exceptionally long custom dimension names can still be width-trimmed.
- Only `en_us` and `fr_fr` are provided at this milestone.
