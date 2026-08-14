package fr.apocalypsebleu.moddetective.client.ui.data;

import fr.apocalypsebleu.moddetective.client.ui.model.IncidentComparisonViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentComparisonViewModel.EvidenceItem;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

/** Removes raw derived hashes and maps comparison evidence into stable, cautious UI labels. */
public final class IncidentComparisonUiAdapter {
    private IncidentComparisonUiAdapter() {}

    public static IncidentComparisonViewModel from(
            IncidentComparison comparison,
            IncidentSummaryViewModel firstSummary,
            IncidentSummaryViewModel secondSummary
    ) {
        return from(comparison, firstSummary, secondSummary, Map.of());
    }

    public static IncidentComparisonViewModel from(
            IncidentComparison comparison,
            IncidentSummaryViewModel firstSummary,
            IncidentSummaryViewModel secondSummary,
            Map<String, String> ownerDisplayNames
    ) {
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(firstSummary, "firstSummary");
        Objects.requireNonNull(secondSummary, "secondSummary");
        Objects.requireNonNull(ownerDisplayNames, "ownerDisplayNames");

        SignatureLabels signatures = signatureLabels(comparison);
        IncidentComparisonViewModel.EvidenceColumns evidence = new IncidentComparisonViewModel.EvidenceColumns(
                evidenceItems(comparison.owners().shared(), signatures.shared(), ownerDisplayNames),
                evidenceItems(comparison.owners().onlyFirst(), signatures.onlyFirst(), ownerDisplayNames),
                evidenceItems(comparison.owners().onlySecond(), signatures.onlySecond(), ownerDisplayNames),
                comparison.owners().availability() == IncidentComparison.EvidenceAvailability.AVAILABLE
                        || signatures.comparisonAvailable(),
                unavailableCategories(comparison));

        return new IncidentComparisonViewModel(
                side(firstSummary, comparison.firstIncident(), true, comparison),
                side(secondSummary, comparison.secondIncident(), false, comparison),
                new IncidentComparisonViewModel.Similarity(
                        comparison.technicalSimilarity().availability(),
                        comparison.technicalSimilarity().score()),
                evidence);
    }

    private static IncidentComparisonViewModel.Side side(
            IncidentSummaryViewModel summary,
            IncidentComparison.IncidentDescriptor descriptor,
            boolean first,
            IncidentComparison comparison
    ) {
        return new IncidentComparisonViewModel.Side(
                summary,
                first ? comparison.detectedAtEpochMs().firstValue()
                        : comparison.detectedAtEpochMs().secondValue(),
                first ? comparison.stallDurationMs().firstValue()
                        : comparison.stallDurationMs().secondValue(),
                first ? comparison.capturedSampleCount().firstValue()
                        : comparison.capturedSampleCount().secondValue(),
                descriptor.attributionState(),
                first ? comparison.context().dimension().firstValue()
                        : comparison.context().dimension().secondValue(),
                first ? comparison.context().usedMemoryBytes().firstValue()
                        : comparison.context().usedMemoryBytes().secondValue(),
                first ? comparison.context().maximumMemoryBytes().firstValue()
                        : comparison.context().maximumMemoryBytes().secondValue());
    }

    private static List<EvidenceItem> evidenceItems(
            List<String> owners,
            List<Integer> signatures,
            Map<String, String> ownerDisplayNames
    ) {
        List<EvidenceItem> result = new ArrayList<>(owners.size() + signatures.size());
        owners.stream()
                .map(owner -> ownerDisplayLabel(owner, ownerDisplayNames))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(EvidenceItem::owner)
                .forEach(result::add);
        signatures.stream().sorted().map(EvidenceItem::technicalSignature).forEach(result::add);
        return List.copyOf(result);
    }

    private static List<IncidentComparisonViewModel.UnavailableCategory> unavailableCategories(
            IncidentComparison comparison
    ) {
        List<IncidentComparisonViewModel.UnavailableCategory> result = new ArrayList<>();
        addUnavailable(result, IncidentComparisonViewModel.EvidenceCategory.CLASS_SIGNATURES,
                comparison.classSignatures());
        addUnavailable(result, IncidentComparisonViewModel.EvidenceCategory.FRAME_SIGNATURES,
                comparison.frameSignatures());
        addUnavailable(result, IncidentComparisonViewModel.EvidenceCategory.STACK_PATH_SIGNATURES,
                comparison.stackPathSignatures());
        addUnavailable(result, IncidentComparisonViewModel.EvidenceCategory.OWNERS,
                comparison.owners());
        return List.copyOf(result);
    }

    private static void addUnavailable(
            List<IncidentComparisonViewModel.UnavailableCategory> target,
            IncidentComparisonViewModel.EvidenceCategory category,
            IncidentComparison.EvidenceSetComparison evidence
    ) {
        if (evidence.availability() != IncidentComparison.EvidenceAvailability.AVAILABLE) {
            target.add(new IncidentComparisonViewModel.UnavailableCategory(
                    category, evidence.availability()));
        }
    }

    private static SignatureLabels signatureLabels(IncidentComparison comparison) {
        List<SignatureEvidence> sources = List.of(
                new SignatureEvidence("class", comparison.classSignatures()),
                new SignatureEvidence("frame", comparison.frameSignatures()),
                new SignatureEvidence("path", comparison.stackPathSignatures()));
        TreeSet<SignatureKey> all = new TreeSet<>(Comparator
                .comparing(SignatureKey::kind)
                .thenComparing(SignatureKey::value));
        for (SignatureEvidence source : sources) {
            source.evidence().shared().forEach(value -> all.add(new SignatureKey(source.kind(), value)));
            source.evidence().onlyFirst().forEach(value -> all.add(new SignatureKey(source.kind(), value)));
            source.evidence().onlySecond().forEach(value -> all.add(new SignatureKey(source.kind(), value)));
        }
        Map<SignatureKey, Integer> numbers = new LinkedHashMap<>();
        int number = 1;
        for (SignatureKey key : all) {
            numbers.put(key, number++);
        }

        TreeSet<Integer> shared = new TreeSet<>();
        TreeSet<Integer> first = new TreeSet<>();
        TreeSet<Integer> second = new TreeSet<>();
        boolean available = false;
        for (SignatureEvidence source : sources) {
            if (source.evidence().availability() == IncidentComparison.EvidenceAvailability.AVAILABLE) {
                available = true;
            }
            addNumbers(shared, source.kind(), source.evidence().shared(), numbers);
            addNumbers(first, source.kind(), source.evidence().onlyFirst(), numbers);
            addNumbers(second, source.kind(), source.evidence().onlySecond(), numbers);
        }
        return new SignatureLabels(List.copyOf(shared), List.copyOf(first), List.copyOf(second), available);
    }

    private static void addNumbers(
            Set<Integer> target,
            String kind,
            List<String> values,
            Map<SignatureKey, Integer> numbers
    ) {
        values.forEach(value -> target.add(numbers.get(new SignatureKey(kind, value))));
    }

    public static String ownerDisplayLabel(String ownerId) {
        return ownerDisplayLabel(ownerId, Map.of());
    }

    public static String ownerDisplayLabel(String ownerId, Map<String, String> ownerDisplayNames) {
        if (ownerId == null || ownerId.isBlank()) {
            return "unknown";
        }
        String id = ownerId.strip();
        String displayName = ownerDisplayNames.get(id);
        if (displayName == null || displayName.isBlank() || displayName.strip().equalsIgnoreCase(id)) {
            return id;
        }
        return displayName.strip() + " (" + id + ")";
    }

    private record SignatureEvidence(String kind, IncidentComparison.EvidenceSetComparison evidence) {}

    private record SignatureKey(String kind, String value) {}

    private record SignatureLabels(
            List<Integer> shared,
            List<Integer> onlyFirst,
            List<Integer> onlySecond,
            boolean comparisonAvailable
    ) {}
}
