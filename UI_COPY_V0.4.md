# Detective v0.4 — Canonical UI Copy

This file is the canonical wording specification for Detective v0.4.

## Product voice

Detective must sound:
- calm
- technical
- credible
- cautious
- evidence-based
- never accusatory

Core rule:

> **Detect. Measure. Explain. Never accuse.**

Never use wording such as:
- culprit
- guilty
- caused by
- definitely caused
- 97% guilty
- 96% confidence

Prefer:
- incident
- primary suspect
- evidence
- attribution
- possible
- ambiguous
- insufficient evidence
- technical evidence
- black box

Canonical marketing line:

> **Detective finds the evidence. You make the call.**

Canonical subtitle:

> **Modpack diagnostics, without the guesswork.**

---

# 1. Main screen

## Header

**DETECTIVE**

**Modpack diagnostics, without the guesswork.**

## Normal state

**Watching for performance issues**

No incidents detected this session.  
Detective is monitoring Minecraft in the background.

## Incident state

**Performance issues detected**

3 incidents recorded this session.  
1 incident has strong attribution evidence.

## Session summary labels

**SESSION**

- Incidents
- Last freeze
- Last suspect
- Status

Recommended status values:
- Stable
- Needs attention
- Recent incidents

## Main buttons

- **View Incidents**
- **Latest Incident**
- **Modpack Changes**

---

# 2. Incidents screen

## Header

**INCIDENTS**

Performance stalls recorded by Detective.

## High-evidence incident card

**621 ms**

**HIGH EVIDENCE**

Create  
Primary suspect

Overworld · X -22  Y 60  Z -55  
2 minutes ago

## Ambiguous incident card

**284 ms**

**AMBIGUOUS**

Multiple suspects  
No clear primary suspect

## Possible system stall card

**478 ms**

**POSSIBLE SYSTEM STALL**

No mod attribution  
Native or driver activity may be involved

## Insufficient evidence card

**154 ms**

**INSUFFICIENT EVIDENCE**

No reliable suspect  
The stall was real, but the evidence is too weak

## Empty state

**No incidents yet**

Detective has not recorded any performance stalls.  
Keep playing normally — monitoring is automatic.

---

# 3. Incident details

## Header example

**INCIDENT #0042**

**621 ms freeze**

**High evidence**

## Incident metadata labels

**Occurred**  
Today at 21:42:16

**Location**  
Overworld  
X -22 · Y 60 · Z -55

**Detection threshold**  
120 ms

**Samples captured**  
30

---

# 4. Primary suspect

## Section title

**PRIMARY SUSPECT**

## Example

Create

**Evidence strength**  
HIGH

## Explanation line

Create owned the active mod execution frame in 29 of 30 captured samples.

## Expandable action

**Why this suspect?**

## Expanded explanation

Detective samples the render thread while a freeze is happening.

Create repeatedly appeared closest to the active point of execution during this incident. This makes it the strongest suspect in the captured evidence.

This does not prove that the mod is defective. The issue may depend on configuration, another mod, or the situation occurring in-game.

---

# 5. Evidence levels

## HIGH EVIDENCE

**Strong attribution evidence**

One suspect repeatedly owned the active mod execution point during the stall.

## MODERATE EVIDENCE

**Moderate attribution evidence**

One suspect stands out, but the captured evidence is not conclusive.

## LOW EVIDENCE

**Weak attribution evidence**

A possible suspect was found, but the available evidence is limited.

---

# 6. Ambiguous attribution

## Header

**AMBIGUOUS ATTRIBUTION**

## Body

Multiple plausible suspects were found.

The captured evidence does not reliably distinguish one mod from the others. Detective will not choose a primary suspect without stronger evidence.

## Suspect list title

**Possible suspects**

Example:
- Create
- Flywheel
- Supplementaries

---

# 7. Insufficient evidence

## Header

**INSUFFICIENT EVIDENCE**

## Body

Detective detected a real performance stall, but the captured evidence is too weak to reliably attribute it to a mod.

No primary suspect has been assigned.

## Action

**View Technical Evidence**

---

# 8. Possible system stall

## Header

**POSSIBLE SYSTEM STALL**

## Body

This incident may have occurred outside identifiable mod code.

Possible sources include the JVM, garbage collection, graphics drivers, native code, or operating system scheduling.

No mod has enough evidence to be named as the primary suspect.

---

# 9. Unknown source

## Header

**UNKNOWN SOURCE**

## Body

Detective recorded the stall but could not determine a likely source from the available samples.

---

# 10. Other suspects

## Header

**OTHER SUSPECTS**

Example:

Flywheel  
Secondary evidence

Minecraft  
Background stack presence

Do not show share-of-samples percentages as if they were guilt/confidence percentages in the primary UI.

Detailed metrics belong in **Technical Evidence**.

---

# 11. Technical Evidence

## Header

**TECHNICAL EVIDENCE**

## Labels

**Samples captured**  
30

**Leaf ownership**  
29 / 30

**Stack presence**  
30 / 30

**Average first-frame depth**  
2.1

**Classification**  
HIGH_EVIDENCE

## Tooltip: Leaf ownership

Leaf ownership measures how often this mod owned the closest identifiable mod frame to the active execution point.

## Tooltip: Stack presence

Stack presence measures how often this mod appeared anywhere in the captured call stacks.

---

# 12. Black Box

## Header

**BLACK BOX**

## Subtitle

Performance history captured around this incident.

## Statistics labels

**Frame time before**  
14.8 ms

**Incident frame**  
621.2 ms

**Memory**  
2.84 GB

**Samples**  
30

## Partial-data state

**Partial Black Box data**

Some performance history was unavailable for this incident.

---

# 13. Modpack Changes

## Header

**MODPACK CHANGES**

## Subtitle

Changes detected since the previous launch.

## Categories

**ADDED**
**UPDATED**
**REMOVED**

## No changes

**No modpack changes detected**

Your installed mods and versions match the previous recorded launch.

## First launch

**No previous snapshot**

Detective needs at least two launches to compare your modpack.

---

# 14. Incident notifications / toast copy

## High evidence

**Detective recorded a 621 ms freeze**

Primary suspect: Create  
High evidence

**View Incident**

## Ambiguous

**Detective recorded a 284 ms freeze**

Multiple suspects found  
Attribution is ambiguous

**View Incident**

## No reliable attribution

**Detective recorded a 478 ms freeze**

No reliable mod attribution

**View Incident**

Never show alarmist copy such as:
- CREATE IS CAUSING LAG
- MOD X BROKE YOUR GAME
- culprit found

---

# 15. Minecraft menu entry

Use exactly:

**Detective**

for both:
- main menu
- pause menu

Do not use verbose variants such as:
- Open Detective Diagnostics

---

# 16. Localization rule

English (`en_us`) is the canonical product copy.

If `fr_fr` is maintained, translations must preserve the same cautious meaning and must never strengthen the claim.

Do not translate "Primary suspect" into wording equivalent to "culprit" or "responsible mod".

---

# 17. Implementation rule for Codex

These strings are product copy, not placeholder text.

Do not rewrite, simplify, intensify, or replace them unless:
1. a technical UI constraint makes the exact wording impossible, or
2. the product owner explicitly approves a wording change.

If a string does not fit:
- prefer wrapping,
- resizing the component,
- or moving secondary explanation to a tooltip/detail section

before changing the wording.

UI architecture should reference translation/localization keys rather than hardcoding user-facing strings directly in screen classes wherever practical.
