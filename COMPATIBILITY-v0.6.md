# Detective v0.6 — Compatibility report

Validation date: 2026-08-11  
Minecraft: **1.21.1**  
Java: **21.0.9 LTS**  
Loader line: **NeoForge 21.1.x**

## NeoForge resolution

The official NeoForge Maven metadata was queried on the validation date. The newest published 21.1.x artifact was **21.1.248** (`maven-metadata.xml` timestamp `20260810132716`). No preview/non-21.1 runtime was substituted.

## Demonstrated matrix

| NeoForge | Build | runClient / world | Controlled incident | Detective UI | Support Report | Shutdown | Notes |
|---|---|---|---|---|---|---|---|
| 21.1.235 | PASS | PASS | PASS, Direct A Top-1 | PASS | PASS | PASS | Final post-hardening rerun used historical JEI 19.39.0.372 and created exactly the controlled incident. |
| 21.1.238 | PASS | PASS | PASS, Direct A Top-1 | PASS | PASS | PASS | Modern pack, JEI 19.44.0.401. Post-lifecycle-fix run created exactly the controlled incident. |
| 21.1.248 | PASS | PASS | PASS, Direct A Top-1 | PASS | PASS | PASS | Modern pack and primary validation runtime. Post-lifecycle-fix run created exactly the controlled incident. |

The first 21.1.238 `clean build` attempt encountered a transient NeoGradle cache ordering problem (a generated access-transformer path was absent after `clean`). A retry after the dependency transform completed passed, and subsequent normal builds were reproducible. This was not a Detective compile/API error.

## Declared support

- **Minimum supported:** NeoForge **21.1.235**, the oldest checkpoint compiled and run with Detective. The release metadata range is `[21.1.235,)`.
- **Recommended:** NeoForge **21.1.248** for new 1.21.1 installations.
- **Primary validation runtime:** NeoForge **21.1.248**.
- **Known Detective-incompatible 21.1.x versions:** none demonstrated in the tested matrix. Untested versions are not implicitly certified.

## Validation-pack dependency requirements

Detective compatibility and test-pack compatibility are separate:

- JEI 19.44.0.401 requires a newer NeoForge floor than 21.1.235, so the modern pack is used on 21.1.238/248.
- The 21.1.235 checkpoint uses preserved `validation-pack/modrinth-pack-v0.3-historical.json` with JEI 19.39.0.372.
- Sodium 0.8.12 is retained because it is the latest stable 1.21.1 build; the newer 0.8.13 result was beta during the audit.
- All validation JARs are fetched from their official Modrinth CDN URLs and verified against pinned SHA-512 values. They are not dependencies of Detective and are never packaged into its JAR.

## Client-only compatibility

The dedicated `runServer` profile disables NeoGradle's automatic inclusion of the main source set. It loads nine server-compatible validation-pack mods plus a code-free development marker; **the `detective` mod id is absent from the server mod list**. A Detective client can connect to this local dedicated server. There is no Detective custom network channel, handshake, packet or server dependency.

The marker exists only because NeoGradle 7.1 requires a valid mod source for every run. Its source set is excluded from the public JAR.

The final Small soak connected a Detective client to that server for 20 minutes, disconnected/reconnected once in the phase plan, and shut down without a Detective network error. A separate 20-cycle run retained exactly one `Detective-Watchdog` thread and created zero incidents. Its first attempt exposed a validation-harness race where a new login began before the old Netty connection had closed; after requiring `Minecraft.getConnection() == null` plus a three-second settle, the 20-cycle rerun completed without the packet decoder error. No Detective runtime or network protocol was involved in that race.

## Caveats

- A physical end-user install of the public JAR and a human connection to an external server remain in `MANUAL-TEST-CHECKLIST-v0.6.md`.
- Regions Unexplored logs a dedicated-server mixin warning involving a client-only screen. The server still reaches `Done`; this belongs to the third-party validation pack, not Detective.
- Exact intermediate 21.1.x versions other than 235, 238 and 248 were not run and are not claimed as independently demonstrated.
