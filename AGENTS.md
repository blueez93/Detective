# Detective — Codex instructions

## Project
Detective is a client-side Minecraft diagnostic mod for Minecraft 1.21.1 using NeoForge and Java 21.

The product goal is to help ordinary players identify which mod is probably responsible for freezes or performance regressions without requiring profiler expertise.

## Current milestone
The current milestone is **v0.4 first usable client UI**. Preserve the validated v0.3.1 engine and keep the interface lightweight and vanilla-friendly.

Preserve and make functional these subsystems:
- mod/version snapshot and launch-to-launch diff
- 30-second Black Box metric history
- freeze detection
- render-thread watchdog sampling
- stack/class -> owning mod attribution
- ranked suspects for an incident
- JSON incident reports
- development-only controlled attribution harness
- client-only incident, detail, Black Box, and modpack-change screens

## Engineering rules
- Target Minecraft 1.21.1 and NeoForge 21.1.x.
- Use Java 21.
- Keep the initial mod client-side; do not require installation on a dedicated server.
- Prefer official NeoForge/Minecraft APIs over brittle reflection when practical.
- Do not silently delete a feature just to make the build pass.
- If an existing implementation is invalid for NeoForge 1.21.1, replace it with the closest robust implementation and document the change.
- Suspect scores are evidence, not proof. Avoid naming a mod as definitively guilty unless causality is actually established.
- Keep runtime overhead low. Profiling must not itself create visible stutter.
- Avoid network calls and telemetry in v0.4.
- Do not add third-party runtime dependencies unless clearly justified.
- Keep persistent output under the Detective data directory and avoid touching unrelated player files.
- Keep all controlled-freeze and validation code outside the public `main` source set.

## Validation before completing a task
When the local environment permits:
1. Run the Gradle build.
2. Fix compilation errors rather than working around them by disabling core code.
3. Run available tests/checks.
4. Attempt the client run configuration or equivalent sanity check.
5. Inspect logs for Detective startup errors and validation results.
6. Inspect the public JAR for development-only harness classes.

## Reporting
At the end of each substantial task, report:
- files changed
- build/test commands run and their result
- behavior verified
- remaining risks/TODOs
- any NeoForge API assumptions that still need runtime validation

## Scope guard for v0.4
Do not implement cloud services, automatic uploads, telemetry, monetization, Fabric support, public server profiling, 3D world localization, AI explanations, or a complex real-time overlay. Keep UI data adapters separate from the low-level engine.
