package fr.apocalypsebleu.moddetective.core.casefile;

import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.DerivedIncidentEvidence;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stable, attribution-verdict-independent evidence extracted from one captured incident.
 *
 * <p>Schema-v2 incidents prefer compact derived symbol and stack-path signatures. Schema-v1
 * incidents fall back to hashed hot-class observations and persisted owner counts. Owner maps
 * deliberately ignore suspect rank and never treat the Primary Suspect label as evidence.</p>
 */
public record IncidentFingerprint(
        String incidentId,
        long detectedAtEpochMs,
        double stallDurationMs,
        StallType stallType,
        int capturedSampleCount,
        EvidenceSource evidenceSource,
        Map<String, Double> classFingerprints,
        Map<String, Double> frameFingerprints,
        Map<String, Double> stackPathFingerprints,
        Map<String, Double> leafOwners,
        Map<String, Double> stackPresenceOwners,
        Map<String, Double> stackDiversityByOwner
) {
    public IncidentFingerprint {
        incidentId = requireText(incidentId, "incidentId");
        stallType = Objects.requireNonNull(stallType, "stallType");
        if (!Double.isFinite(stallDurationMs) || stallDurationMs < 0.0) {
            stallDurationMs = 0.0;
        }
        capturedSampleCount = Math.max(0, capturedSampleCount);
        evidenceSource = Objects.requireNonNull(evidenceSource, "evidenceSource");
        classFingerprints = immutableEvidence(classFingerprints, "classFingerprints");
        frameFingerprints = immutableEvidence(frameFingerprints, "frameFingerprints");
        stackPathFingerprints = immutableEvidence(stackPathFingerprints, "stackPathFingerprints");
        leafOwners = immutableEvidence(leafOwners, "leafOwners");
        stackPresenceOwners = immutableEvidence(stackPresenceOwners, "stackPresenceOwners");
        stackDiversityByOwner = immutableEvidence(stackDiversityByOwner, "stackDiversityByOwner");
    }

    /** Compatibility constructor for the Phase 0.8-A class-level fallback model. */
    public IncidentFingerprint(
            String incidentId,
            long detectedAtEpochMs,
            double stallDurationMs,
            StallType stallType,
            int capturedSampleCount,
            Map<String, Double> classFingerprints,
            Map<String, Double> leafOwners,
            Map<String, Double> stackPresenceOwners,
            Map<String, Double> stackDiversityByOwner
    ) {
        this(incidentId, detectedAtEpochMs, stallDurationMs, stallType, capturedSampleCount,
                EvidenceSource.LEGACY_FALLBACK, classFingerprints, Map.of(), Map.of(),
                leafOwners, stackPresenceOwners, stackDiversityByOwner);
    }

    public static IncidentFingerprint from(String incidentId, FreezeIncident incident) {
        Objects.requireNonNull(incident, "incident");
        return fromPersistedEvidence(
                incidentId,
                incident.detectedAtEpochMs(),
                incident.durationMs(),
                incident.watchdogSamples(),
                incident.attributionEvidence(),
                incident.suspects(),
                incident.hotClasses(),
                incident.derivedEvidence());
    }

    public static IncidentFingerprint fromPersistedEvidence(
            String incidentId,
            long detectedAtEpochMs,
            double stallDurationMs,
            int capturedSampleCount,
            AttributionEvidence attributionEvidence,
            java.util.List<SuspectAnalyzer.Suspect> suspects,
            java.util.List<SuspectAnalyzer.HotClass> hotClasses,
            DerivedIncidentEvidence enhanced
    ) {
        Objects.requireNonNull(attributionEvidence, "attributionEvidence");
        Objects.requireNonNull(suspects, "suspects");
        Objects.requireNonNull(hotClasses, "hotClasses");
        if (enhanced != null && enhanced.usable()) {
            return fromEnhanced(
                    incidentId, detectedAtEpochMs, stallDurationMs, capturedSampleCount,
                    stallType(attributionEvidence), enhanced);
        }
        return fromLegacy(
                incidentId, detectedAtEpochMs, stallDurationMs, capturedSampleCount,
                stallType(attributionEvidence), suspects, hotClasses);
    }

    private static IncidentFingerprint fromEnhanced(
            String incidentId,
            long detectedAtEpochMs,
            double stallDurationMs,
            int capturedSampleCount,
            StallType stallType,
            DerivedIncidentEvidence enhanced
    ) {
        int samples = enhanced.representedSamples();
        Map<String, Double> classes = normalizedSignatures(enhanced.classSignatures(), samples);
        Map<String, Double> frames = normalizedSignatures(enhanced.frameSignatures(), samples);
        Map<String, Double> paths = normalizedSignatures(enhanced.stackPathSignatures(), samples);
        int ownerSampleDenominator = Math.max(1, capturedSampleCount);
        Map<String, Double> leaf = new TreeMap<>();
        Map<String, Double> presence = new TreeMap<>();
        Map<String, Double> diversity = new TreeMap<>();
        for (DerivedIncidentEvidence.OwnerObservation owner : enhanced.ownerObservations()) {
            putMaximum(leaf, owner.ownerId(),
                    normalizedCount(owner.leafOwnershipSamples(), ownerSampleDenominator));
            putMaximum(presence, owner.ownerId(),
                    normalizedCount(owner.presenceSamples(), ownerSampleDenominator));
            putMaximum(diversity, owner.ownerId(),
                    normalizedCount(owner.stackDiversity(), ownerSampleDenominator));
        }
        return new IncidentFingerprint(
                incidentId,
                detectedAtEpochMs,
                stallDurationMs,
                stallType,
                samples,
                EvidenceSource.DERIVED_V1,
                classes,
                frames,
                paths,
                leaf,
                presence,
                diversity);
    }

    private static IncidentFingerprint fromLegacy(
            String incidentId,
            long detectedAtEpochMs,
            double stallDurationMs,
            int capturedSampleCount,
            StallType stallType,
            java.util.List<SuspectAnalyzer.Suspect> suspects,
            java.util.List<SuspectAnalyzer.HotClass> hotClasses
    ) {
        int samples = Math.max(0, capturedSampleCount);
        Map<String, Double> classes = new TreeMap<>();
        for (SuspectAnalyzer.HotClass hotClass : hotClasses) {
            if (hotClass != null) {
                putMaximum(classes, DerivedIncidentEvidence.classSignature(hotClass.className()),
                        normalizedCount(hotClass.hits(), samples));
            }
        }

        Map<String, Double> leaf = new TreeMap<>();
        Map<String, Double> presence = new TreeMap<>();
        Map<String, Double> diversity = new TreeMap<>();
        for (SuspectAnalyzer.Suspect observation : suspects) {
            if (observation != null) {
                putMaximum(leaf, observation.modId(),
                        normalizedCount(observation.leafOwnershipCount(), samples));
                putMaximum(presence, observation.modId(),
                        normalizedCount(observation.presenceSamples(), samples));
                putMaximum(diversity, observation.modId(),
                        normalizedCount(observation.stackDiversity(), samples));
            }
        }

        return new IncidentFingerprint(
                incidentId,
                detectedAtEpochMs,
                stallDurationMs,
                stallType,
                samples,
                EvidenceSource.LEGACY_FALLBACK,
                classes,
                Map.of(),
                Map.of(),
                leaf,
                presence,
                diversity);
    }

    public boolean hasSufficientTechnicalEvidence(int minimumCapturedSamples) {
        if (minimumCapturedSamples < 1) {
            throw new IllegalArgumentException("minimumCapturedSamples must be positive");
        }
        return capturedSampleCount >= minimumCapturedSamples
                && !classFingerprints.isEmpty()
                && ((!leafOwners.isEmpty() || !stackPresenceOwners.isEmpty())
                || evidenceSource == EvidenceSource.DERIVED_V1
                && (!frameFingerprints.isEmpty() || !stackPathFingerprints.isEmpty()));
    }

    private static Map<String, Double> normalizedSignatures(
            java.util.List<DerivedIncidentEvidence.SignatureObservation> observations,
            int samples
    ) {
        Map<String, Double> result = new TreeMap<>();
        for (DerivedIncidentEvidence.SignatureObservation observation : observations) {
            putMaximum(result, observation.signature(),
                    normalizedCount(observation.observations(), samples));
        }
        return result;
    }

    private static StallType stallType(AttributionEvidence evidence) {
        return switch (Objects.requireNonNull(evidence, "attributionEvidence").state()) {
            case JVM_GC_SUSPECTED -> StallType.POSSIBLE_JVM_GC_STALL;
            case NATIVE_OR_DRIVER_STALL_POSSIBLE -> StallType.POSSIBLE_NATIVE_OR_DRIVER_STALL;
            default -> StallType.RENDER_THREAD_STALL;
        };
    }

    private static void putMaximum(Map<String, Double> target, String rawKey, double value) {
        if (rawKey == null || rawKey.isBlank() || value <= 0.0) {
            return;
        }
        target.merge(rawKey.trim(), value, Math::max);
    }

    private static double normalizedCount(int count, int samples) {
        if (count <= 0 || samples <= 0) {
            return 0.0;
        }
        return Math.min(1.0, count / (double) samples);
    }

    private static Map<String, Double> immutableEvidence(Map<String, Double> source, String name) {
        Objects.requireNonNull(source, name);
        Map<String, Double> sorted = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), name + " key");
            double value = Objects.requireNonNull(entry.getValue(), name + " value");
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(name + " values must be between 0.0 and 1.0");
            }
            if (value > 0.0) {
                sorted.put(key, value);
            }
        }
        return Collections.unmodifiableMap(sorted);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public enum StallType {
        RENDER_THREAD_STALL,
        POSSIBLE_JVM_GC_STALL,
        POSSIBLE_NATIVE_OR_DRIVER_STALL
    }

    public enum EvidenceSource {
        DERIVED_V1,
        LEGACY_FALLBACK
    }
}
