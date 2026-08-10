# Detective v0.3 realistic validation pack

This pack exists only for local development validation of Detective on Minecraft 1.21.1 / NeoForge 21.1.235. It is not a Detective dependency and is not a redistributed modpack.

`modrinth-pack.json` is the authoritative machine-readable manifest. Every file is fetched unchanged from the official Modrinth CDN and verified with SHA-512 before use.

## Pinned mods

| Category | Mod | Version | License | Runtime |
|---|---|---:|---|---|
| Optimization | ModernFix | 5.27.20+mc1.21.1 | LGPL-3.0-only | client + server |
| Optimization | FerriteCore | 7.0.3-neoforge | MIT | client + server |
| Rendering/client | Sodium | mc1.21.1-0.8.12-neoforge | PolyForm Shield 1.0.0 | client |
| Machines/block entities, content, worldgen | Mekanism | 10.7.19.85 | MIT | client + server |
| Content | Farmer's Delight | 1.21.1-1.3.2 | MIT | client + server |
| Worldgen/content | Regions Unexplored | 0.6.2-neoforge-21.1 | MIT | client + server |
| Worldgen library | Lithostitched | 1.7.13-neoforge-21.1 | MIT | client + server |
| Inventory/QoL | Just Enough Items | 19.39.0.372 | MIT | client |
| Client/QoL | Jade | 15.10.6+neoforge | CC-BY-NC-SA-4.0 | client + server |
| Inventory library | Curios API | 9.5.1+1.21.1 | LGPL-3.0-or-later | client + server |
| Animation/render library | GeckoLib | 4.9.2 | MIT | client + server |

JEI 19.39.0.372 is intentionally pinned instead of the newer 19.42+/19.44 builds: inspection of the embedded `META-INF/neoforge.mods.toml` showed that 19.42.0.379 and later require NeoForge 21.1.238, while 19.39.0.372 remains compatible with the project's fixed 21.1.235 target.

Terralith was evaluated and rejected because the Stardust Labs license explicitly prohibits use as part of an AI project. Regions Unexplored and Lithostitched provide a substantial MIT-licensed world-generation workload instead.

## Setup and verification

```powershell
.\gradlew.bat prepareValidationPack
.\gradlew.bat verifyValidationPack
```

The preparation task is intentionally separate from `build`; a normal public build performs no validation-pack download. Passing `-PdetectiveValidationPack=true` makes `runClient`/`runServer` require successful local verification.

## Long-session procedure

1. Start `runServer` with `-PdetectiveValidationPack=true` on a dedicated validation world.
2. Make the development player an operator so controlled teleports and dimension changes are real server commands.
3. Start `runClient` with `detectiveValidationAutorun=realworld`, a 30-minute soak, local-server autoconnect, and clean exit enabled.
4. Keep the Minecraft window active. The dev harness requests focus once before the baseline; its dedicated alt-tab phase later iconifies and restores the GLFW window.
5. Analyze `overhead-metrics.jsonl`, `phase-results.jsonl`, `ground-truth.jsonl`, incident reports, and `latest.log`.
6. Re-run `clean build`, `test`, and inspect the public JAR after validation.

The validation pack is local test input only. Its JARs remain under ignored `run/*/mods` directories and must never be copied into `build/libs` or Detective resources.
