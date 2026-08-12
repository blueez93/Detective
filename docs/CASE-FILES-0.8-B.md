# Detective 0.8 Case Files — Phase B backend format

This phase adds local, backend-only recurring-pattern persistence. It does not add UI, telemetry,
uploads, networking, server requirements, or a causality verdict. A Case is a recurring technical
pattern supported by captured evidence, not proof that an owner is defective.

## Incident schema 2

New incidents use `FreezeIncident.schemaVersion = 2` and may contain `derivedEvidence` version 1.
The field is optional so schema-1 Detective 0.7 incidents remain readable without rewriting or
migrating them.

`derivedEvidence` contains:

- `signatureFormat`: `sha256-128-symbol-v1`.
- `representedSamples`: watchdog samples represented by the signature, bounded to the newest 600.
- `classSignatures`: up to 48 class-identity hashes with the number of samples containing each.
- `frameSignatures`: up to 64 class-and-method hashes with sample-presence counts.
- `stackPathSignatures`: up to 32 ordered stack-path hashes with occurrence counts. Each path uses
  at most the first 64 frames in active-leaf-first order.
- `ownerObservations`: up to 16 owner IDs with presence samples, leaf-ownership samples, and stack
  diversity. Current attribution output supplies at most five owner observations.

Counts are sorted by descending observations and then by signature. Owner observations are sorted
by owner ID. This makes serialization deterministic and compact.

Before hashing, Detective uses only JVM class and method symbols. Hidden-class suffixes beginning
with `/0x` are removed because they change between launches. Hash inputs are domain-separated as
`class`, `frame`, or `path`; SHA-256 is computed and the first 128 bits are stored as 32 lowercase
hexadecimal characters.

The derived block never includes raw stack dumps, source-file names, line numbers, thread names,
object values, exception messages, world/server text, player coordinates, account data, or local
filesystem paths. Mod IDs remain visible as technical owner identifiers, matching existing incident
owner evidence. Standard Support Reports remain explicit allow-lists and do not serialize this
derived block or the Case index.

## Backward compatibility

When valid derived evidence exists, Case fingerprints use its class, frame, path, and owner counts.
When it is absent, unsupported, incomplete, or corrupt, only that optional block is discarded.
Detective then uses the real schema-1 hot-class and owner observations already in the incident.
Legacy hot-class names are normalized and hashed with the same class-signature function so mixed
legacy/new histories can compare at class level. No enhanced frame or path evidence is invented for
legacy incidents.

Sparse records still require at least three captured samples. A legacy record additionally requires
class evidence and owner presence/leaf evidence. Enhanced records may use strong frame/path evidence
without an owner, allowing neutral recurring system-pattern detection where supported.

## Similarity

For two enhanced fingerprints, structural evidence contributes:

- 0.25 class-signature weighted Jaccard overlap
- 0.15 frame-signature weighted Jaccard overlap
- 0.25 stack-path weighted Jaccard overlap

Owner and context contributions remain:

- 0.15 leaf-owner weighted Jaccard overlap
- 0.07 stack-presence-owner weighted Jaccard overlap
- 0.03 owner stack-diversity weighted Jaccard overlap
- 0.04 captured-sample-count ratio
- 0.03 stall-type equality
- 0.03 stall-duration ratio

Technical evidence therefore remains 90% of the score. When either incident is legacy, the full
0.65 structural weight uses only the comparable class-signature overlap; absent enhanced frame/path
channels are not fabricated or treated as negative evidence. The default threshold remains 0.72 and
is centrally configurable.

## Stable identity and evolution

On first creation, a Case ID is a deterministic 128-bit truncated SHA-256 hash of the three oldest
founding incident IDs. Adding a later matching incident therefore does not rename the Case. Active Case
records are stored atomically in `detective/cases/index.json` with schema version 1.

Every history refresh reconciles recomputed groups with the prior index using shared incident
membership only; shared owners never establish identity.

- Join: a complete-link-compatible incident joins the Case and the prior ID is retained.
- Unrelated incident: no Case membership or ID changes.
- Merge: possible only when the recomputed group itself satisfies complete-link safety. The prior
  Case with the largest membership overlap retains its ID; ties use earlier first-seen time and then
  lexical Case ID. Other prior IDs retire from the active index.
- Split: the child with the largest overlap retains the prior ID. Other children receive their own
  deterministic founding IDs. The same tie-breakers apply.
- Borderline bridge: cannot merge groups whose members are mutually below threshold, because every
  candidate must meet the threshold against every existing member.
- Missing/sparse evidence: excluded before clustering and cannot establish identity.

An unsupported or corrupt Case-index schema is not overwritten. Incident history is never rewritten
by Case analysis.

## Cost and threading

Derived signature generation runs on the existing incident worker after detection and attribution;
it does not run on the render or watchdog sampling threads. Historical loading, clustering, identity
reconciliation, and Case-index writes run on the existing low-priority support worker.

The history loader selects at most the newest 500 incident records before full fingerprint parsing.
Pairwise comparison is therefore bounded to 124,750 pairs; the normal default 50-record history has
at most 1,225 pairs. Complete-link clustering uses those cached comparisons.

Detect. Measure. Explain. Never accuse.
