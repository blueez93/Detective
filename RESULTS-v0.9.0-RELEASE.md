# Detective 0.9.0 — Final Release Preparation Results

## Release source

- Source RC branch: `feature/0.9.0-investigation`
- Source RC commit: `a9acd004f4f5014a6a59734c006baad308c3cf46`
- Source RC result: **GO WITH KNOWN LIMITATIONS**
- Release branch: `release/0.9.0`
- Final version: `0.9.0`
- Minecraft: `1.21.1`
- NeoForge metadata minimum: `21.1.235`
- Tested NeoForge development runtime: `21.1.248`
- Java: 21

The worktree was clean before the release branch was created. The only build version source requiring a change was `mod_version` in `gradle.properties`, from `0.9.0-dev` to `0.9.0`. Historical RC references to the audited development artifact were retained unchanged.

## Validation result

- Command: `.\gradlew.bat cleanTest test build --no-daemon`
- Test suites: 47
- Tests: 273
- Failures: 0
- Errors: 0
- Skipped: 0
- Build result: **BUILD SUCCESSFUL**

The final count matches the hardened RC baseline. No test was removed, disabled, or skipped.

## Release documentation

- Added `RELEASE-NOTES-v0.9.0.md` with public Investigation, Technical Similarity, What Changed?, Launch History, UI, privacy, compatibility, upgrade, validation, and limitation guidance.
- Added the 0.9.0 section to `CHANGELOG.md`.
- Updated the focused public feature and installation information in `README.md` without rewriting its established positioning or philosophy.
- Updated `PRIVACY.md` to document the bounded local `launch-history.json` representation and explicit Support Report exclusions.
- Updated the bug-report template version example and privacy wording.

The public wording remains neutral: Technical Similarity and nearby changes describe captured evidence; they do not prove a shared cause or that a mod is defective. Temporal proximity does not establish causation.

## Final public JAR

| Property | Result |
|---|---|
| Path | `C:\Users\fpoug\Desktop\Projets\Detective\build\libs\detective-0.9.0.jar` |
| Filename | `detective-0.9.0.jar` |
| Exact size | 515,631 bytes |
| SHA-256 | `47327512b29e4506296ff2bdbb7209251417d91df160491219a982a19cba5976` |
| ZIP/JAR entries | 251 |
| `.class` files | 226 |
| Nested JARs | 0 |
| Embedded version | `0.9.0` |
| Embedded Minecraft range | exactly `[1.21.1]` |
| Embedded NeoForge range | `[21.1.235,)` |
| Client-only | yes |

Client-only status is expressed both by `@Mod(..., dist = Dist.CLIENT)` and by client-side NeoForge/Minecraft dependency metadata.

## Isolation and metadata audit

Aggressive entry-name and decoded-content scans confirmed that the public JAR contains no:

- tests, JUnit, or TestNG;
- validation harness or `detectivevalidation` classes/resources;
- culprit fixtures or validation mods;
- screenshots or validation captures;
- local Incident, Case, or launch histories;
- generated Support Reports;
- logs, GC logs, or run-directory files;
- third-party validation JARs;
- nested JARs;
- `0.9.0-dev` metadata;
- developer username or concrete local filesystem path;
- temporary debug resource.

The only non-class files are the manifest, NeoForge mod metadata, and the English/French language resources. Production Support Report implementation classes are expected code and are not generated report artifacts. The generic `/home/` and `/users/` strings in `ReportPrivacy` are intentional redaction patterns, not embedded developer paths.

## Privacy audit

- Detective remains client-side and local-only.
- No telemetry, analytics, networking, remote API, or automatic upload was introduced.
- `launch-history.json` remains local and bounded to 64 retained launch records by default.
- Launch History stores observed launch timestamps plus bounded change type, mod ID, display name, and applicable previous/new version values.
- It does not add player identity, server addresses, raw stack dumps, or personal filesystem paths.
- Support Reports remain explicit allow-list exports.
- Support Reports do not automatically include `launch-history.json`, the Case database/index, or `latest.log`.

## Compatibility and upgrade

- Existing 0.7/schema-v1 and 0.8/schema-v2 Incident histories remain readable.
- Legacy Incidents are not fabricated with enhanced evidence they never captured.
- Missing or malformed optional evidence continues to fail conservatively.
- Launch History is not retroactive and does not infer launches Detective did not observe.
- The public artifact remains a client-only Minecraft 1.21.1 / NeoForge 21.1.x mod.

## Production behavior

Release preparation changed only the build version and public documentation. There are no modified production Java files and no change to:

- freeze detection;
- render-thread watchdog sampling;
- attribution;
- Incident Comparison or Technical Similarity;
- Case clustering, identity, persistence, or reconciliation;
- Incident persistence or retention;
- Modpack Launch History behavior;
- Support Report serialization or export behavior;
- UI behavior or design.

## Known accepted limitations

- Incident, Case, and launch histories are deliberately bounded, so older evidence may no longer be available.
- Legacy Incidents may support less detailed comparison because enhanced evidence cannot be reconstructed retroactively.
- Launch History begins when a supporting Detective version observes it and is not retroactive.
- A small amount of local snapshot and Launch History I/O occurs once during client setup; it does not run per frame or from `render()`.
- Some stalls remain ambiguous, system-related, unknown, or too sparsely sampled to support comparison or a recurring Case.
- An intermittent missing-glyph artifact was observed only in the third-party validation/capture profile; clean-profile controls were stable, and no public-JAR product defect was confirmed.

## Release artifact copy

Release bundle directory: `C:\Users\fpoug\Desktop\Detective-Releases\0.9.0`

The bundle contains the final JAR, release notes, this result report, `LICENSE`, and `CHANGELOG.md`. The copied JAR SHA-256 was verified against the build artifact.

## Recommendation

Detective 0.9.0 is ready for later tagging and publication when explicitly authorized. No tag, GitHub Release, merge, CurseForge upload, or Modrinth upload was performed during this task.
