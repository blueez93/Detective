# Detective v0.6 — Manual test checklist

These checks require real human interaction or display configurations that the automated development harness cannot prove. They are intentionally left unchecked. A harness result is not a substitute for a physical input/focus test.

## Entry points and navigation

- [ ] **MANUAL TEST REQUIRED** — Detective button on the main menu opens the dashboard.
- [ ] **MANUAL TEST REQUIRED** — Detective button on the pause menu opens the dashboard and returns correctly.
- [ ] **MANUAL TEST REQUIRED** — Mouse navigation across dashboard, incident list/detail, Modpack Changes, Settings and export preview.
- [ ] **MANUAL TEST REQUIRED** — `Why this suspect?` expands/displays all cautious explanatory copy.
- [ ] **MANUAL TEST REQUIRED** — Technical Evidence is readable and scrollable.
- [ ] **MANUAL TEST REQUIRED** — Leaf ownership and Stack presence tooltips are reachable and readable.
- [ ] **MANUAL TEST REQUIRED** — Full and partial Black Box views render correctly.
- [ ] **MANUAL TEST REQUIRED** — Modpack Changes handles changes, no changes and first launch.
- [ ] **MANUAL TEST REQUIRED** — Settings values persist after a real restart.
- [ ] **MANUAL TEST REQUIRED** — Incident notifications toggle ON/OFF is respected in play.
- [ ] **MANUAL TEST REQUIRED** — Export Support Report privacy preview and success screen.
- [ ] **MANUAL TEST REQUIRED** — Click Open Folder on Windows and verify Explorer opens the exact report directory.
- [ ] **MANUAL TEST REQUIRED** — Clear Incident History confirmation, Cancel, then confirmed deletion.

## Physical focus and pause

- [ ] **MANUAL TEST REQUIRED** — Active game → Alt+Tab → wait 5 seconds → return; no incident caused only by lost focus.
- [ ] **MANUAL TEST REQUIRED** — Active game → Alt+Tab → wait 30 seconds → return; no incident caused only by lost focus.
- [ ] **MANUAL TEST REQUIRED** — Repeat several Alt+Tab cycles; no notification spam, false incident or stuck sampling.
- [ ] **MANUAL TEST REQUIRED** — Pause for 30 seconds and resume; no incident caused only by the pause interval.

## Resolution and resize

- [ ] **MANUAL TEST REQUIRED** — 854×480.
- [ ] **MANUAL TEST REQUIRED** — 1280×720.
- [ ] **MANUAL TEST REQUIRED** — 1920×1080.
- [ ] **MANUAL TEST REQUIRED** — 2560×1440.
- [ ] **MANUAL TEST REQUIRED** — Open Detective, resize the window repeatedly, and verify widgets/scroll remain recoverable.

At each resolution verify dashboard, incident list, long incident detail, Black Box, Settings, export preview and success screen.

## GUI scale and language

- [ ] **MANUAL TEST REQUIRED** — GUI Scale Auto.
- [ ] **MANUAL TEST REQUIRED** — GUI Scale 1.
- [ ] **MANUAL TEST REQUIRED** — GUI Scale 2.
- [ ] **MANUAL TEST REQUIRED** — GUI Scale 3.
- [ ] **MANUAL TEST REQUIRED** — GUI Scale 4.
- [ ] **MANUAL TEST REQUIRED** — English (`en_us`).
- [ ] **MANUAL TEST REQUIRED** — Français (`fr_fr`).

In both languages verify Ambiguous Attribution, Possible System Stall, Why this suspect?, Support Report, Privacy Preview, Settings and Clear History. Copy must remain cautious: “Detect. Measure. Explain. Never accuse.”

## Keyboard

- [ ] **MANUAL TEST REQUIRED** — Tab advances focus in a reasonable order.
- [ ] **MANUAL TEST REQUIRED** — Shift+Tab reverses focus.
- [ ] **MANUAL TEST REQUIRED** — Enter activates the focused control once.
- [ ] **MANUAL TEST REQUIRED** — Escape returns to the correct parent screen without losing state.

## Frame pacing and memory profiles

- [ ] **MANUAL TEST REQUIRED** — VSync ON.
- [ ] **MANUAL TEST REQUIRED** — VSync OFF.
- [ ] **MANUAL TEST REQUIRED** — 30 FPS limit.
- [ ] **MANUAL TEST REQUIRED** — 60 FPS limit.
- [ ] **MANUAL TEST REQUIRED** — 120 FPS or higher.
- [ ] **MANUAL TEST REQUIRED** — 2 GiB JVM heap where the chosen pack can start.
- [ ] **MANUAL TEST REQUIRED** — 4 GiB JVM heap.
- [ ] **MANUAL TEST REQUIRED** — 8 GiB JVM heap.
- [ ] **MANUAL TEST REQUIRED** — 12 GiB JVM heap if available.

## Gameplay configurations not reproducible automatically

- [ ] **MANUAL TEST REQUIRED** — Active Mekanism machine/block-entity area with several machines and interfaces.
- [ ] **MANUAL TEST REQUIRED** — Explicit Overworld → Nether → End → Overworld round trip; verify the dimension shown in each Detective incident/context. The automated v0.6 harness proved Overworld/Nether only.
- [ ] **MANUAL TEST REQUIRED** — Modded dimension round trips (the validated pack exposes modded worldgen but no dedicated modded dimension).
- [ ] **MANUAL TEST REQUIRED** — JEI search/list interaction under load.
- [ ] **MANUAL TEST REQUIRED** — External compatible server without exposing its address in artifacts.
- [ ] **MANUAL TEST REQUIRED** — New-world creation plus repeated entry/exit in addition to the automated existing-world/dedicated-server cycles.

## Release smoke test

- [ ] **MANUAL TEST REQUIRED** — Install only the public `detective-0.6.0-alpha.1.jar` in a clean client profile.
- [ ] **MANUAL TEST REQUIRED** — Join a server that does not install Detective and verify no handshake requirement.
- [ ] **MANUAL TEST REQUIRED** — Generate a report after multiplayer play and inspect its ZIP manually before sharing.
