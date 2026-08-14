# Detective 0.8.0 — Final Release Preparation

Date: 2026-08-14
Branch: `feature/0.8.0-case-files`
Base commit tested: `b71e8a6f23e2528bde487db528fe1c8a614a0839` (`Add Case Files release hardening tests`)

The release candidate was built from that commit plus the uncommitted release-only version and documentation changes requested for 0.8.0. No commit, tag, publication, or merge was created by this preparation task.

## Release scope

- Public version changed from `0.8.0-dev` to `0.8.0` in `gradle.properties`.
- Added the public 0.8.0 changelog entry and release notes.
- Updated the README installation filename and Case Files explanation.
- Updated current privacy documentation and bug-report version guidance.
- Preserved historical 0.7.0 release notes, results, and historical project-state references.
- Changed zero production Java files. Detection, sampling, attribution, Case similarity, clustering, persistence behavior, and UI behavior are unchanged by this task.

## Validation result

Command:

```powershell
.\gradlew.bat cleanTest test build --no-daemon
```

Result: **BUILD SUCCESSFUL** in 37 seconds.

| Metric | Result |
|---|---:|
| Test suites | 35 |
| Tests | 134 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |

## Public JAR audit

Audited artifact: `build/libs/detective-0.8.0.jar`

| Property | Result |
|---|---|
| Filename | `detective-0.8.0.jar` |
| Exact size | 304,779 bytes |
| SHA-256 | `725a0519e804f8137e6432b310a94c50196677d55d563b45a49d0373b9a0c3b9` |
| Archive entries | 151 |
| File entries | 133 |
| Class files | 129 |
| Embedded mod ID | `detective` |
| Embedded version | `0.8.0` |
| Display name | `Detective` |

NeoForge metadata retains required `neoforge` and `minecraft` dependencies with `side="CLIENT"`. Detective remains a client-side mod with no required server installation.

The archive contains zero:

- `detectivevalidation` entries;
- validation harness classes or resources;
- culprit fixtures;
- test classes or test resources;
- JUnit classes or resources;
- generated reports or test-result reports;
- logs;
- third-party test mods or nested validation-pack JARs.

`build/libs` also contains an older local `detective-0.8.0-dev.jar` from development. It is not the release candidate and was not used for this audit. Only `detective-0.8.0.jar` should be considered for release.

## Compatibility

- Minecraft 1.21.1, Java 21, and NeoForge 21.1.235 or newer in the 21.1.x line remain the documented target.
- NeoForge 21.1.248 remains the recommended tested runtime.
- Existing 0.7.0/schema-v1 Incident files load without destructive rewriting.
- Legacy-only, mixed legacy/schema-v2, missing derived-evidence, and malformed optional derived-evidence histories are covered by tests.
- Legacy Incidents use supported hot-class and owner fallback evidence when available; enhanced evidence is never fabricated for old records.
- Missing Case indexes rebuild deterministically. Unsupported or corrupt Case indexes are preserved rather than overwritten automatically.
- Repeated restarts, Case identity growth, unrelated history, retention, Case disappearance/reformation, and complete-link bridge prevention are covered by the release-hardening suite.

## Privacy

- Case analysis and Case index persistence remain local-only.
- No telemetry, networking, remote API, automatic upload, account, or server-side requirement was added.
- Schema-v2 derived evidence persists bounded counts, bounded owner observations, and truncated SHA-256 signatures of normalized class, frame, and stack-path symbols.
- Case Files do not add full raw thread dumps, source filenames, line numbers, thread names, object values, player data, or arbitrary runtime text.
- Standard Support Report privacy behavior is unchanged; `latest.log` is not included automatically.

## Known limitations

- A recurring Case requires at least three sufficiently similar Incidents. Two matching Incidents do not establish a recurring Case.
- Legacy 0.7.0 history lacks the stronger schema-v2 derived stack signatures and may produce fewer or less detailed Cases.
- Retention can reduce a Case below three members, causing it to disappear. A later independently established pattern receives a new Case ID.
- If an unsupported or corrupt Case index cannot be read, persistent identity continuity cannot be guaranteed after founding Incidents leave retained history.
- Analysis is bounded to the newest 500 eligible Incidents.
- Recurring similarity does not establish causation. Attribution and Case Files represent captured evidence, not proof that a mod is defective or solely responsible.
- Long-duration Medium/Large/Stress modpack soaks remain incomplete. Physical Alt+Tab, full resolution/GUI-scale, keyboard-navigation, and `Open Folder` checks still require final human validation.

## Recommendation

The 0.8.0 release candidate passes the complete automated suite, contains the expected client-only version metadata, and passes the public-JAR isolation audit. No release-critical defect was found during final preparation.

Final SHA-256: `725a0519e804f8137e6432b310a94c50196677d55d563b45a49d0373b9a0c3b9`

READY TO RELEASE
