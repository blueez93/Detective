# Detective Privacy

Detective is designed as a local-only diagnostic mod.

## No network behavior

Detective performs:

- no telemetry;
- no analytics;
- no automatic uploads;
- no remote API calls;
- no external crash collection;
- no update checks.

Minecraft, NeoForge, launchers, and other installed mods may have their own network behavior. This document covers Detective itself.

## Local data

Detective stores its settings, mod snapshots, bounded Modpack Launch History, Incident records, Case index, and generated Support Reports under the Minecraft instance's local `detective` data directory.

`launch-history.json` remains local. It contains observed launch timestamps and bounded added, updated, or removed mod metadata: change type, mod ID, display name, and applicable previous/new versions. It does not contain player identity, server addresses, raw stack dumps, or personal filesystem paths. The default retained history is 64 launch records, and it cannot reconstruct launches or changes Detective did not observe.

For new Incidents, Case Files may persist bounded counts and truncated SHA-256 signatures derived from normalized class, frame, and stack-path symbols already observed by the watchdog, plus bounded mod-owner observations. These derived signatures do not add full raw thread dumps, source filenames, line numbers, thread names, object values, player data, or arbitrary runtime text.

Incident history is bounded by the configured maximum count and age. Clearing Incident history removes only Detective's locally stored Incident records.

## Support Reports

Support Reports are generated locally and are never uploaded automatically. The player chooses whether and where to share a report.

A standard report may contain:

- captured Incident evidence and nearby Black Box metrics;
- installed mod IDs, display names, versions, and useful loader metadata;
- Modpack Changes recorded by Detective;
- Detective settings relevant to the report;
- Minecraft, NeoForge, Java, operating-system, architecture, processor-count, and approximate JVM-memory information.

Standard reports do **not** include:

- `launch-history.json`;
- the Case database/index;
- `latest.log`;
- mod JARs, worlds, screenshots, or memory dumps;
- Minecraft account names, UUIDs, sessions, or access tokens;
- operating-system usernames or hostnames;
- IP addresses, server addresses, or MAC addresses;
- absolute personal file paths.

Report data is assembled from an explicit allow-list and receives additional redaction as defense in depth. Missing information is represented as unavailable rather than replaced with unrelated personal data.

## Before sharing

No automated filter can guarantee that every future or unexpectedly formatted value is harmless. Review a Support Report before posting it publicly, especially when it contains metadata supplied by third-party mods.

If optional log attachment is added in a future version, it must be an explicit choice with a separate warning. Detective 0.9.0 does not automatically include Minecraft logs.
