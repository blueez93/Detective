# Detective v0.7.0-alpha.1 — Release Preparation

Preparation date: 2026-08-11  
Minecraft: **1.21.1**  
Java: **21**  
Loader: **NeoForge 21.1.x**  
Scope: public Alpha metadata, documentation, build, tests, and JAR audit only

No soak, validation pack, `runClient`, product feature, commit, tag, or publication was created by this mission.

## 1. Files created

- `PRIVACY.md` — local-data, Support Report, exclusion, and no-network policy.
- `CHANGELOG.md` — public `0.7.0-alpha.1` entry.
- `RELEASE-NOTES-v0.7.0-alpha.1.md` — CurseForge/Modrinth release-note base.
- `.github/ISSUE_TEMPLATE/bug_report.yml` — structured privacy-aware bug report form.
- `RESULTS-v0.7-RELEASE-PREP.md` — this report.

## 2. Files modified

- `gradle.properties` — public version changed to `0.7.0-alpha.1`.
- `src/main/resources/META-INF/neoforge.mods.toml` — concise public description; existing loader and client-side constraints retained.
- `README.md` — replaced the internal validation-oriented landing page with a public Early Alpha README.
- `.gitignore` — expanded local IDE, temporary, dump, downloaded pack, report, mod, world, and validation-JAR exclusions.
- `PROJECT_STATE.md` — current scope updated to v0.7 Release Preparation while retaining historical validation evidence.

No Java production or validation source was changed.

## 3. Final public metadata

The built JAR embeds:

| Field | Final value |
|---|---|
| Artifact | `detective-0.7.0-alpha.1.jar` |
| Mod id | `detective` |
| Display name | `Detective` |
| Version | `0.7.0-alpha.1` |
| Author | `Blue` |
| Minecraft | exactly `1.21.1` |
| NeoForge minimum | `21.1.235` (`[21.1.235,)`) |
| Development/recommended runtime | `21.1.248` |
| Java | 21 |
| Loader | `javafml`, `[1,)` |
| Environment | client-only |
| Current license metadata | `All Rights Reserved` |

Client-only behavior is represented in two independent places:

- the built entrypoint annotation is `@Mod(value = "detective", dist = Dist.CLIENT)`;
- Minecraft and NeoForge dependency declarations have `side="CLIENT"`.

There is no server dependency, custom handshake, packet, or public server feature.

Final embedded description:

> Client-side modpack diagnostics that detect performance stalls, preserve nearby performance history, and present cautious evidence about probable mod suspects.

## 4. README

The public README now begins with:

> DETECTIVE  
> Modpack diagnostics, without the guesswork.

It explains Automatic freeze detection, the Black Box, Primary Suspect, evidence-based attribution, Ambiguous Attribution, honest system/unknown states, Modpack Changes, Support Reports, notifications, retention, installation, compatibility, privacy, known limitations, bug reporting, and source builds.

The product language preserves:

- **Detect. Measure. Explain. Never accuse.**
- **Detective finds the evidence. You make the call.**

No public copy states that a mod is certainly responsible.

## 5. Early Alpha warning

README, changelog, and release notes all state that:

- core detection, attribution, UI, and Support Reports have been validated;
- a short smoke passed with 166 physical JARs and 257 loaded mod ids;
- long-duration Medium/Large/Stress validation is still ongoing;
- the short smoke is not presented as a stable long soak;
- users should report unexpected behavior.

## 6. Privacy

`PRIVACY.md` documents:

- no Detective telemetry or analytics;
- no automatic upload;
- no remote API call, external crash collection, or update check;
- local storage under the game instance's `detective` directory;
- locally generated Support Reports that are never uploaded automatically;
- standard report contents and explicit exclusions;
- no automatic `latest.log`, JAR, save, screenshot, memory dump, account/session identifier, OS username/hostname, IP/server/MAC address, or absolute personal path;
- a reminder to inspect reports before sharing them.

The static runtime audit found only `java.net.URI` and `java.net.URL` imports in `ModSourceResolver`; v0.6 already verified that these convert local code-source locations and do not open a connection. No networking code was added in v0.7.

## 7. Changelog and release notes

`CHANGELOG.md` contains a public `0.7.0-alpha.1` entry covering the existing player-facing engine, UI, Support Report, daily-use, privacy, compatibility, and hardening results without exposing internal harness instructions.

`RELEASE-NOTES-v0.7.0-alpha.1.md` is ready as a CurseForge/Modrinth text base. It includes Early Alpha status, highlights, installation, client-only behavior, privacy, known limitations, and bug-report guidance.

## 8. Issue template

The GitHub Issue Form requests:

- Detective version;
- Minecraft version;
- NeoForge version;
- modpack or mod list;
- observed behavior;
- reproduction steps;
- optional Detective Support Report;
- optional non-sensitive context.

It requires confirmation that attachments were reviewed and explicitly says not to share tokens, sessions, private addresses, personal identifiers, or other secrets. It does not request `latest.log` automatically.

## 9. License state

No license was selected or changed.

Current state:

- Gradle/JAR metadata: **All Rights Reserved**.
- Repository `LICENSE` file: **absent**.

**A deliberate license decision is still required before public upload.** The chosen platform license setting, `mod_license` metadata, and an appropriate repository `LICENSE` file should agree. This report does not recommend or select a license on the owner's behalf.

## 10. `.gitignore` audit

Confirmed ignored:

- `.gradle/`, `build/`, `run/`, `logs/`;
- historical root `run*.stdout.log` / `run*.stderr.log` files;
- IntelliJ, VS Code, Eclipse, temporary, crash, heap-dump, and replay state;
- generated `detective-report-*.zip` and root `reports/`;
- local root `mods/` and `saves/`;
- `.mrpack` files and downloaded validation JARs below `validation-pack/`.

`git ls-files` reports no tracked build output, run directory, local log, generated ZIP, `.mrpack`, local world, or third-party validation JAR. The standard `gradle/wrapper/gradle-wrapper.jar` remains intentionally tracked.

No required source or validation script is ignored.

## 11. Build

Command:

```powershell
.\gradlew.bat clean build --no-daemon
```

Result: **PASS** in 1 min 9 s.

The initial sandboxed invocation stopped before Gradle because network access to the official Wrapper distribution was denied. The exact command was rerun with approved access; Gradle 9.2.1 is locked by the Wrapper SHA-256 declared in `build.gradle`. The successful build compiled main and all development validation source sets, executed tests, and packaged only the public main source set.

## 12. Tests

Separate command:

```powershell
.\gradlew.bat test --no-daemon
```

Result: **PASS** in 32 s (`UP-TO-DATE` after the clean build).

JUnit XML totals:

- suites: **28**;
- tests: **96**;
- failures: **0**;
- errors: **0**;
- skipped: **0**.

No test was removed, disabled, or weakened.

## 13. JAR size and SHA-256

- File: `build/libs/detective-0.7.0-alpha.1.jar`
- Size: **208,373 bytes**
- SHA-256: **`4ECE4CAFE6314452EB9C4591D0A19323E4F2351F77A715310ED91A31EE96647A`**

## 14. JAR inspection

| Check | Result |
|---|---:|
| Total ZIP entries | 111 |
| Java classes | 90 |
| Nested JARs | 0 |
| Harness / validation classes | 0 |
| Culprit classes | 0 |
| Ground truth | 0 |
| Fixtures | 0 |
| Generated reports | 0 |
| Worlds / saves | 0 |
| `latest.log` | 0 |
| GC validation tools/assets | 0 |
| Third-party mod JARs | 0 |

Public contents are limited to normal `META-INF`, `assets/detective`, and `fr/apocalypsebleu/moddetective` entries plus their directory records. Embedded TOML confirms the final id, name, version, description, Minecraft range, NeoForge floor, and client-side dependencies. Bytecode inspection confirms `Dist.CLIENT` on the mod entrypoint.

## 15. Known limitations

- Long-duration Medium/Large/Stress modpack soaks remain **NOT VALIDATED**.
- The cumulative 140-minute coverage target was not reached.
- The 257-loaded-mod-id result is a short compatibility smoke, not stable gameplay performance evidence.
- Physical Alt+Tab, complete resolution/GUI-scale coverage, keyboard traversal, window resizing, fullscreen, and real Explorer `Open Folder` remain manual.
- End and a dedicated modded-dimension round trip were not demonstrated by the existing hardening campaign.
- A physically constructed, continuously active Mekanism machine network was not exercised.
- The optional 2/4/8/12 GiB and FPS/VSync matrices are incomplete; only the 4 GiB Large smoke is newly demonstrated.
- Some incidents legitimately remain ambiguous, insufficient, possible-system, or unknown.

These are Alpha limitations and are not represented as completed validation.

## 16. Still required before CurseForge/Modrinth upload

1. Choose the project license deliberately; add the matching `LICENSE` file and update metadata if the choice differs from All Rights Reserved.
2. Complete the critical human checks in `MANUAL-TEST-CHECKLIST-v0.6.md`, especially physical focus, keyboard/mouse, scale/resolution, and `Open Folder`.
3. Perform a final physical installation smoke using the exact public JAR in a normal launcher profile before upload.
4. Decide and provide the real project/source/issue URLs for platform listings and optional future metadata; none was invented in this pass.
5. Configure the CurseForge/Modrinth project as Minecraft 1.21.1, NeoForge, client-side, Early Alpha, with NeoForge 21.1.235 minimum and 21.1.248 recommended/tested.
6. Review the final README, Privacy policy, release notes, and issue form as they will appear on the public repository/platform.
7. Upload only `detective-0.7.0-alpha.1.jar` and verify its SHA-256 against this report.

## Release-preparation conclusion

Repository documentation, metadata, automated build/tests, and public JAR composition are ready for an Early Alpha candidate.

This does **not** override the outstanding manual and long-duration coverage limitations. No publication-readiness claim beyond the documented Alpha scope is made.
