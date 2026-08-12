package fr.apocalypsebleu.moddetective.core.casefile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministically carries stable Case identities across recomputed memberships. */
public final class CaseIdentityResolver {
    private CaseIdentityResolver() {}

    public static List<CaseFile> resolve(List<CaseFile> current, List<CaseFile> previous) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(previous, "previous");
        List<CaseFile> next = current.stream()
                .sorted(Comparator.comparingLong(CaseFile::firstSeenEpochMs)
                        .thenComparing(CaseFile::caseId))
                .toList();
        List<CaseFile> prior = previous.stream()
                .sorted(Comparator.comparingLong(CaseFile::firstSeenEpochMs)
                        .thenComparing(CaseFile::caseId))
                .toList();

        List<Overlap> overlaps = new ArrayList<>();
        for (int currentIndex = 0; currentIndex < next.size(); currentIndex++) {
            for (int previousIndex = 0; previousIndex < prior.size(); previousIndex++) {
                int shared = intersection(
                        next.get(currentIndex).relatedIncidentIds(),
                        prior.get(previousIndex).relatedIncidentIds());
                if (shared > 0) {
                    overlaps.add(new Overlap(currentIndex, previousIndex, shared));
                }
            }
        }
        overlaps.sort(Comparator.comparingInt(Overlap::sharedIncidents).reversed()
                .thenComparing((Overlap overlap) -> prior.get(overlap.previousIndex()).firstSeenEpochMs())
                .thenComparing(overlap -> prior.get(overlap.previousIndex()).caseId())
                .thenComparing(overlap -> next.get(overlap.currentIndex()).caseId()));

        String[] inheritedIds = new String[next.size()];
        boolean[] previousClaimed = new boolean[prior.size()];
        for (Overlap overlap : overlaps) {
            if (inheritedIds[overlap.currentIndex()] == null
                    && !previousClaimed[overlap.previousIndex()]) {
                inheritedIds[overlap.currentIndex()] = prior.get(overlap.previousIndex()).caseId();
                previousClaimed[overlap.previousIndex()] = true;
            }
        }

        Set<String> usedIds = new HashSet<>();
        List<CaseFile> resolved = new ArrayList<>(next.size());
        for (int index = 0; index < next.size(); index++) {
            CaseFile candidate = next.get(index);
            String requested = inheritedIds[index] == null ? candidate.caseId() : inheritedIds[index];
            String identity = uniqueIdentity(requested, candidate.relatedIncidentIds(), usedIds);
            usedIds.add(identity);
            resolved.add(candidate.withCaseId(identity));
        }
        resolved.sort(Comparator.comparingLong(CaseFile::firstSeenEpochMs)
                .thenComparing(CaseFile::caseId));
        return List.copyOf(resolved);
    }

    private static int intersection(List<String> first, List<String> second) {
        Set<String> values = new HashSet<>(first);
        int shared = 0;
        for (String value : second) {
            if (values.contains(value)) {
                shared++;
            }
        }
        return shared;
    }

    private static String uniqueIdentity(String requested, List<String> members, Set<String> used) {
        if (!used.contains(requested)) {
            return requested;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("\n", members).getBytes(StandardCharsets.UTF_8));
            StringBuilder suffix = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                suffix.append("%02x".formatted(hash[index] & 0xff));
            }
            return requested + '-' + suffix;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private record Overlap(int currentIndex, int previousIndex, int sharedIncidents) {}
}
