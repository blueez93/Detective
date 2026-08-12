# Changelog

## 0.7.0

First public release for Minecraft 1.21.1 and NeoForge.

### Included

- Automatic render-thread freeze detection with an adaptive threshold.
- A 30-second Black Box of nearby performance and world context.
- Evidence-based Primary Suspect ranking with explicit ambiguous, insufficient, possible-system, and unknown states.
- Incident dashboard, newest-first history, detail view, Technical Evidence, and Black Box graph.
- Modpack Changes between recorded launches.
- Local Support Report ZIP export with privacy preview and schema-versioned JSON.
- Optional Incident notifications, bounded history retention, and essential settings.
- Client-only operation with no required server installation.
- No Detective telemetry, analytics, remote API calls, or automatic uploads.
- Adopted the **Detective Proprietary License 1.0**; see [LICENSE](LICENSE) for the complete terms.

### Release hardening

- Validated Minecraft 1.21.1 on NeoForge 21.1.235, 21.1.238, and 21.1.248.
- Preserved 9/9 controlled Top-1 and 9/9 Top-3 attribution results from the v0.6 hardening baseline.
- Added corruption recovery, migration, bounded-queue/history, Support Report privacy, and client/server-isolation coverage.
- Completed a short 4 GiB compatibility smoke with 166 physical JARs and 257 loaded mod IDs under NeoForge 21.1.248.

### Known limitations

- Long-duration Medium/Large/Stress modpack soaks are not complete.
- Physical focus, resolution, GUI-scale, keyboard-navigation, and folder-opening checks still require final human validation.
- Attribution expresses captured evidence, not proof that a mod is defective.
