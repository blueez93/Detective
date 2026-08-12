package fr.apocalypsebleu.moddetective.core.casefile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Deterministic, bounded clustering for local incident histories.
 *
 * <p>An incident joins a candidate only if it clears the configured threshold against every
 * existing member. This conservative complete-link rule prevents chains of weak similarities from
 * merging distinct technical patterns.</p>
 */
public final class CaseClusterer {
    public static final Configuration DEFAULT_CONFIGURATION = new Configuration(
            0.72,
            3,
            3,
            500,
            2.0 / 3.0);

    private static final Comparator<IncidentFingerprint> CANONICAL_ORDER =
            Comparator.comparing(IncidentFingerprint::incidentId)
                    .thenComparingLong(IncidentFingerprint::detectedAtEpochMs);
    private static final Comparator<IncidentFingerprint> NEWEST_FIRST =
            Comparator.comparingLong(IncidentFingerprint::detectedAtEpochMs).reversed()
                    .thenComparing(IncidentFingerprint::incidentId);

    private final CaseSimilarity similarity;
    private final Configuration configuration;

    public CaseClusterer() {
        this(new CaseSimilarity(), DEFAULT_CONFIGURATION);
    }

    public CaseClusterer(CaseSimilarity similarity, Configuration configuration) {
        this.similarity = Objects.requireNonNull(similarity, "similarity");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public List<CaseFile> cluster(List<IncidentFingerprint> input) {
        Objects.requireNonNull(input, "input");
        List<IncidentFingerprint> fingerprints = boundedEligibleInput(input);
        if (fingerprints.size() < configuration.minimumOccurrences()) {
            return List.of();
        }

        Map<Pair, CaseSimilarity.Breakdown> comparisons = comparisons(fingerprints);
        List<List<Integer>> candidates = new ArrayList<>();
        for (int fingerprintIndex = 0; fingerprintIndex < fingerprints.size(); fingerprintIndex++) {
            int selected = selectCandidate(fingerprintIndex, candidates, comparisons);
            if (selected < 0) {
                List<Integer> candidate = new ArrayList<>();
                candidate.add(fingerprintIndex);
                candidates.add(candidate);
            } else {
                candidates.get(selected).add(fingerprintIndex);
            }
        }

        return candidates.stream()
                .filter(candidate -> candidate.size() >= configuration.minimumOccurrences())
                .map(candidate -> buildCaseFile(candidate, fingerprints, comparisons))
                .sorted(Comparator.comparingLong(CaseFile::firstSeenEpochMs)
                        .thenComparing(CaseFile::caseId))
                .toList();
    }

    private List<IncidentFingerprint> boundedEligibleInput(List<IncidentFingerprint> input) {
        Set<String> ids = new HashSet<>();
        List<IncidentFingerprint> eligible = new ArrayList<>();
        for (IncidentFingerprint fingerprint : input) {
            Objects.requireNonNull(fingerprint, "fingerprint");
            if (!ids.add(fingerprint.incidentId())) {
                throw new IllegalArgumentException("Duplicate incident id: " + fingerprint.incidentId());
            }
            if (fingerprint.hasSufficientTechnicalEvidence(configuration.minimumCapturedSamples())) {
                eligible.add(fingerprint);
            }
        }
        if (eligible.size() > configuration.maximumIncidents()) {
            eligible.sort(NEWEST_FIRST);
            eligible = new ArrayList<>(eligible.subList(0, configuration.maximumIncidents()));
        }
        eligible.sort(CANONICAL_ORDER);
        return List.copyOf(eligible);
    }

    private Map<Pair, CaseSimilarity.Breakdown> comparisons(List<IncidentFingerprint> fingerprints) {
        Map<Pair, CaseSimilarity.Breakdown> result = new HashMap<>();
        for (int first = 0; first < fingerprints.size(); first++) {
            for (int second = first + 1; second < fingerprints.size(); second++) {
                result.put(new Pair(first, second),
                        similarity.compare(fingerprints.get(first), fingerprints.get(second)));
            }
        }
        return result;
    }

    private int selectCandidate(
            int fingerprintIndex,
            List<List<Integer>> candidates,
            Map<Pair, CaseSimilarity.Breakdown> comparisons
    ) {
        int selected = -1;
        double selectedAverage = -1.0;
        for (int candidateIndex = 0; candidateIndex < candidates.size(); candidateIndex++) {
            List<Integer> candidate = candidates.get(candidateIndex);
            double total = 0.0;
            boolean sufficientlySimilar = true;
            for (int member : candidate) {
                double score = comparison(member, fingerprintIndex, comparisons).score();
                if (score < configuration.similarityThreshold()) {
                    sufficientlySimilar = false;
                    break;
                }
                total += score;
            }
            if (sufficientlySimilar) {
                double average = total / candidate.size();
                if (average > selectedAverage) {
                    selected = candidateIndex;
                    selectedAverage = average;
                }
            }
        }
        return selected;
    }

    private CaseFile buildCaseFile(
            List<Integer> memberIndexes,
            List<IncidentFingerprint> fingerprints,
            Map<Pair, CaseSimilarity.Breakdown> comparisons
    ) {
        List<IncidentFingerprint> members = memberIndexes.stream()
                .map(fingerprints::get)
                .sorted(Comparator.comparingLong(IncidentFingerprint::detectedAtEpochMs)
                        .thenComparing(IncidentFingerprint::incidentId))
                .toList();
        List<String> incidentIds = members.stream().map(IncidentFingerprint::incidentId).toList();
        long firstSeen = members.stream().mapToLong(IncidentFingerprint::detectedAtEpochMs).min().orElseThrow();
        long lastSeen = members.stream().mapToLong(IncidentFingerprint::detectedAtEpochMs).max().orElseThrow();
        double averageDuration = members.stream().mapToDouble(IncidentFingerprint::stallDurationMs).average().orElse(0.0);
        double longestDuration = members.stream().mapToDouble(IncidentFingerprint::stallDurationMs).max().orElse(0.0);

        double totalSimilarity = 0.0;
        double totalEvidence = 0.0;
        int pairs = 0;
        for (int first = 0; first < memberIndexes.size(); first++) {
            for (int second = first + 1; second < memberIndexes.size(); second++) {
                CaseSimilarity.Breakdown score = comparison(
                        memberIndexes.get(first), memberIndexes.get(second), comparisons);
                totalSimilarity += score.score();
                totalEvidence += score.technicalEvidence();
                pairs++;
            }
        }

        int minimumSupport = (int) Math.ceil(members.size() * configuration.recurringEvidenceSupport());
        return new CaseFile(
                foundingCaseId(members, configuration.minimumOccurrences()),
                incidentIds,
                firstSeen,
                lastSeen,
                members.size(),
                averageDuration,
                longestDuration,
                recurringEvidence(members, minimumSupport),
                recurringOwners(members, minimumSupport),
                pairs == 0 ? 0.0 : totalSimilarity / pairs,
                pairs == 0 ? 0.0 : totalEvidence / pairs);
    }

    private static List<CaseFile.RecurringEvidence> recurringEvidence(
            List<IncidentFingerprint> members,
            int minimumSupport
    ) {
        List<CaseFile.RecurringEvidence> result = new ArrayList<>();
        addRecurringEvidence(result, members, minimumSupport,
                CaseFile.EvidenceKind.CLASS, IncidentFingerprint::classFingerprints);
        addRecurringEvidence(result, members, minimumSupport,
                CaseFile.EvidenceKind.FRAME, IncidentFingerprint::frameFingerprints);
        addRecurringEvidence(result, members, minimumSupport,
                CaseFile.EvidenceKind.STACK_PATH, IncidentFingerprint::stackPathFingerprints);
        return List.copyOf(result);
    }

    private static void addRecurringEvidence(
            List<CaseFile.RecurringEvidence> result,
            List<IncidentFingerprint> members,
            int minimumSupport,
            CaseFile.EvidenceKind kind,
            java.util.function.Function<IncidentFingerprint, Map<String, Double>> evidence
    ) {
        Set<String> keys = new TreeSet<>();
        members.forEach(member -> keys.addAll(evidence.apply(member).keySet()));
        for (String key : keys) {
            int support = 0;
            double total = 0.0;
            for (IncidentFingerprint member : members) {
                double value = evidence.apply(member).getOrDefault(key, 0.0);
                if (value > 0.0) {
                    support++;
                    total += value;
                }
            }
            if (support >= minimumSupport) {
                result.add(new CaseFile.RecurringEvidence(kind, key, support, total / support));
            }
        }
    }

    private static List<CaseFile.RecurringOwner> recurringOwners(
            List<IncidentFingerprint> members,
            int minimumSupport
    ) {
        Set<String> keys = new TreeSet<>();
        members.forEach(member -> {
            keys.addAll(member.leafOwners().keySet());
            keys.addAll(member.stackPresenceOwners().keySet());
        });
        List<CaseFile.RecurringOwner> result = new ArrayList<>();
        for (String key : keys) {
            int support = 0;
            double leaf = 0.0;
            double presence = 0.0;
            for (IncidentFingerprint member : members) {
                double memberLeaf = member.leafOwners().getOrDefault(key, 0.0);
                double memberPresence = member.stackPresenceOwners().getOrDefault(key, 0.0);
                if (memberLeaf > 0.0 || memberPresence > 0.0) {
                    support++;
                    leaf += memberLeaf;
                    presence += memberPresence;
                }
            }
            if (support >= minimumSupport) {
                result.add(new CaseFile.RecurringOwner(key, support, leaf / support, presence / support));
            }
        }
        return List.copyOf(result);
    }

    private static CaseSimilarity.Breakdown comparison(
            int first,
            int second,
            Map<Pair, CaseSimilarity.Breakdown> comparisons
    ) {
        return comparisons.get(new Pair(Math.min(first, second), Math.max(first, second)));
    }

    private static String foundingCaseId(List<IncidentFingerprint> members, int foundingSize) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> founders = members.stream()
                    .sorted(Comparator.comparingLong(IncidentFingerprint::detectedAtEpochMs)
                            .thenComparing(IncidentFingerprint::incidentId))
                    .limit(foundingSize)
                    .map(IncidentFingerprint::incidentId)
                    .toList();
            byte[] hash = digest.digest(("case-founders-v1\n" + String.join("\n", founders))
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("case-");
            for (int index = 0; index < 16; index++) {
                value.append("%02x".formatted(hash[index] & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private record Pair(int first, int second) {}

    public record Configuration(
            double similarityThreshold,
            int minimumOccurrences,
            int minimumCapturedSamples,
            int maximumIncidents,
            double recurringEvidenceSupport
    ) {
        public Configuration {
            if (!Double.isFinite(similarityThreshold)
                    || similarityThreshold < 0.0 || similarityThreshold > 1.0) {
                throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
            }
            if (minimumOccurrences < 3) {
                throw new IllegalArgumentException("A recurring Case requires at least three incidents");
            }
            if (minimumCapturedSamples < 1) {
                throw new IllegalArgumentException("minimumCapturedSamples must be positive");
            }
            if (maximumIncidents < minimumOccurrences) {
                throw new IllegalArgumentException("maximumIncidents must cover minimumOccurrences");
            }
            if (!Double.isFinite(recurringEvidenceSupport)
                    || recurringEvidenceSupport <= 0.5 || recurringEvidenceSupport > 1.0) {
                throw new IllegalArgumentException("recurringEvidenceSupport must be above 0.5 and at most 1.0");
            }
        }
    }
}
