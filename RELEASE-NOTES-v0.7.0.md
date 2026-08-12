# Detective 0.7.0

**Modpack diagnostics, without the guesswork.**

Detective is a client-side diagnostic mod for Minecraft 1.21.1 on NeoForge. It automatically records meaningful render-thread freezes, preserves nearby Black Box performance history, and presents cautious evidence about probable mod suspects.

**Detect. Measure. Explain. Never accuse.**

## Highlights

- Automatic freeze detection with an adaptive threshold.
- 30-second Black Box around each recorded Incident.
- Evidence-based Primary Suspect and Other Suspects.
- Explicit Ambiguous Attribution, Insufficient Evidence, Possible System Stall, and Unknown Source states.
- Incident dashboard, history, detail view, Technical Evidence, and Black Box graph.
- Modpack Changes between launches.
- Local Support Report ZIP export for developers and modpack authors.
- Optional notifications, bounded history, and essential settings.

Detective finds the evidence. You make the call. A Primary Suspect is not proof that a mod is defective or solely responsible.

## Installation

1. Use Minecraft **1.21.1** with Java **21**.
2. Install NeoForge **21.1.235 or newer in the 21.1.x line**.
3. Place `detective-0.7.0.jar` in the client instance's `mods` folder.

NeoForge 21.1.248 is the recommended tested runtime. Detective is client-side only and is not required on the server.

## Privacy

Detective stores diagnostics locally. It contains no telemetry, analytics, remote API calls, automatic uploads, or update checker. Support Reports are generated locally and are never uploaded automatically.

Standard reports do not include `latest.log`, mod JARs, saves, screenshots, memory dumps, account/session identifiers, server addresses, or personal paths. Review a report before sharing it.

## License

Detective is distributed under the **Detective Proprietary License 1.0**. See [LICENSE](LICENSE) for the complete terms.

## Known limitations

- Core detection, attribution, UI, Support Reports, and a short smoke test with **257 loaded mod IDs** have been validated.
- Long-duration Medium/Large/Stress modpack soaks are not yet complete.
- Physical Alt+Tab, the full resolution/GUI-scale matrix, keyboard navigation, and `Open Folder` behavior still require final human validation.
- Some stalls cannot be attributed reliably and will remain ambiguous, system-related, or unknown.

## Reporting a bug

Please include:

- Detective version;
- Minecraft version;
- NeoForge version;
- modpack name or mod list;
- what happened and steps to reproduce it;
- a Detective Support Report, if available.

Inspect the report before sharing it. Do not post tokens, session data, private server addresses, personal identifiers, or other secrets.
