package fr.apocalypsebleu.moddetective.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compact, privacy-conscious technical signatures derived from already captured watchdog stacks.
 *
 * <p>Version 1 persists only truncated SHA-256 hashes of normalized class names, class/method
 * frames, and stack paths. Source files, line numbers, thread names, object values, player data,
 * and raw stack text are never included. Hidden-class runtime suffixes such as {@code /0x...} are
 * removed before hashing so identical technical symbols remain stable across launches.</p>
 */
public record DerivedIncidentEvidence(
        int schemaVersion,
        String signatureFormat,
        int representedSamples,
        List<SignatureObservation> classSignatures,
        List<SignatureObservation> frameSignatures,
        List<SignatureObservation> stackPathSignatures,
        List<OwnerObservation> ownerObservations
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String SIGNATURE_FORMAT = "sha256-128-symbol-v1";
    public static final int MAXIMUM_REPRESENTED_SAMPLES = 600;
    public static final int MAXIMUM_STACK_DEPTH = 64;
    public static final int MAXIMUM_CLASS_SIGNATURES = 48;
    public static final int MAXIMUM_FRAME_SIGNATURES = 64;
    public static final int MAXIMUM_STACK_PATH_SIGNATURES = 32;
    public static final int MAXIMUM_OWNER_OBSERVATIONS = 16;

    public DerivedIncidentEvidence {
        schemaVersion = SCHEMA_VERSION;
        signatureFormat = Objects.requireNonNull(signatureFormat, "signatureFormat");
        if (!SIGNATURE_FORMAT.equals(signatureFormat)) {
            throw new IllegalArgumentException("Unsupported derived evidence signature format");
        }
        if (representedSamples < 0 || representedSamples > MAXIMUM_REPRESENTED_SAMPLES) {
            throw new IllegalArgumentException("representedSamples is outside the supported bound");
        }
        classSignatures = immutableSignatures(
                classSignatures, MAXIMUM_CLASS_SIGNATURES, "classSignatures");
        frameSignatures = immutableSignatures(
                frameSignatures, MAXIMUM_FRAME_SIGNATURES, "frameSignatures");
        stackPathSignatures = immutableSignatures(
                stackPathSignatures, MAXIMUM_STACK_PATH_SIGNATURES, "stackPathSignatures");
        ownerObservations = immutableOwners(ownerObservations);
    }

    public static DerivedIncidentEvidence capture(
            List<StackSnapshot> snapshots,
            SuspectAnalyzer.Analysis analysis
    ) {
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(analysis, "analysis");

        int start = Math.max(0, snapshots.size() - MAXIMUM_REPRESENTED_SAMPLES);
        Map<String, Integer> classes = new HashMap<>();
        Map<String, Integer> frames = new HashMap<>();
        Map<String, Integer> paths = new HashMap<>();
        int representedSamples = 0;
        for (int index = start; index < snapshots.size(); index++) {
            StackSnapshot snapshot = snapshots.get(index);
            if (snapshot == null || snapshot.stack() == null || snapshot.stack().length == 0) {
                continue;
            }
            representedSamples++;
            Set<String> sampleClasses = new HashSet<>();
            Set<String> sampleFrames = new HashSet<>();
            List<String> samplePath = new ArrayList<>();
            int depthLimit = Math.min(snapshot.stack().length, MAXIMUM_STACK_DEPTH);
            for (int depth = 0; depth < depthLimit; depth++) {
                StackTraceElement frame = snapshot.stack()[depth];
                if (frame == null) {
                    continue;
                }
                String className = normalizeSymbol(frame.getClassName());
                String methodName = normalizeSymbol(frame.getMethodName());
                if (className.isEmpty() || methodName.isEmpty()) {
                    continue;
                }
                String normalizedFrame = className + '#' + methodName;
                sampleClasses.add(classSignature(className));
                sampleFrames.add(frameSignature(normalizedFrame));
                samplePath.add(normalizedFrame);
            }
            sampleClasses.forEach(signature -> classes.merge(signature, 1, Integer::sum));
            sampleFrames.forEach(signature -> frames.merge(signature, 1, Integer::sum));
            if (!samplePath.isEmpty()) {
                paths.merge(stackPathSignature(String.join("\n", samplePath)), 1, Integer::sum);
            }
        }

        List<OwnerObservation> owners = analysis.suspects().stream()
                .filter(Objects::nonNull)
                .map(suspect -> new OwnerObservation(
                        suspect.modId(),
                        suspect.presenceSamples(),
                        suspect.leafOwnershipCount(),
                        suspect.stackDiversity()))
                .sorted(Comparator.comparing(OwnerObservation::ownerId))
                .limit(MAXIMUM_OWNER_OBSERVATIONS)
                .toList();
        return new DerivedIncidentEvidence(
                SCHEMA_VERSION,
                SIGNATURE_FORMAT,
                representedSamples,
                observations(classes, MAXIMUM_CLASS_SIGNATURES),
                observations(frames, MAXIMUM_FRAME_SIGNATURES),
                observations(paths, MAXIMUM_STACK_PATH_SIGNATURES),
                owners);
    }

    public boolean usable() {
        return representedSamples > 0
                && (!classSignatures.isEmpty() || !frameSignatures.isEmpty()
                || !stackPathSignatures.isEmpty());
    }

    /** Produces the same class-level key used to compare enhanced and legacy incidents safely. */
    public static String classSignature(String normalizedClassName) {
        return signature("class", normalizeSymbol(normalizedClassName));
    }

    private static String frameSignature(String normalizedFrame) {
        return signature("frame", normalizedFrame);
    }

    private static String stackPathSignature(String normalizedPath) {
        return signature("path", normalizedPath);
    }

    private static String signature(String namespace, String normalizedValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((namespace + '\0' + normalizedValue)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (int index = 0; index < 16; index++) {
                result.append("%02x".formatted(hash[index] & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    private static String normalizeSymbol(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip();
        int hiddenSuffix = normalized.indexOf("/0x");
        if (hiddenSuffix >= 0) {
            normalized = normalized.substring(0, hiddenSuffix);
        }
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }

    private static List<SignatureObservation> observations(
            Map<String, Integer> counts,
            int maximum
    ) {
        return counts.entrySet().stream()
                .map(entry -> new SignatureObservation(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(SignatureObservation::observations).reversed()
                        .thenComparing(SignatureObservation::signature))
                .limit(maximum)
                .toList();
    }

    private static List<SignatureObservation> immutableSignatures(
            List<SignatureObservation> source,
            int maximum,
            String name
    ) {
        Objects.requireNonNull(source, name);
        if (source.size() > maximum) {
            throw new IllegalArgumentException(name + " exceeds its persistence bound");
        }
        List<SignatureObservation> sorted = new ArrayList<>(source);
        sorted.forEach(value -> Objects.requireNonNull(value, name + " value"));
        sorted.sort(Comparator.comparingInt(SignatureObservation::observations).reversed()
                .thenComparing(SignatureObservation::signature));
        return Collections.unmodifiableList(sorted);
    }

    private static List<OwnerObservation> immutableOwners(List<OwnerObservation> source) {
        Objects.requireNonNull(source, "ownerObservations");
        if (source.size() > MAXIMUM_OWNER_OBSERVATIONS) {
            throw new IllegalArgumentException("ownerObservations exceeds its persistence bound");
        }
        List<OwnerObservation> sorted = new ArrayList<>(source);
        sorted.forEach(value -> Objects.requireNonNull(value, "ownerObservations value"));
        sorted.sort(Comparator.comparing(OwnerObservation::ownerId));
        return Collections.unmodifiableList(sorted);
    }

    public record SignatureObservation(String signature, int observations) {
        public SignatureObservation {
            Objects.requireNonNull(signature, "signature");
            if (!signature.matches("[0-9a-f]{32}") || observations < 1) {
                throw new IllegalArgumentException("A signature must be a 128-bit lowercase hash with observations");
            }
        }
    }

    public record OwnerObservation(
            String ownerId,
            int presenceSamples,
            int leafOwnershipSamples,
            int stackDiversity
    ) {
        public OwnerObservation {
            Objects.requireNonNull(ownerId, "ownerId");
            ownerId = ownerId.strip();
            if (ownerId.isEmpty() || ownerId.length() > 256
                    || presenceSamples < 0 || leafOwnershipSamples < 0 || stackDiversity < 0) {
                throw new IllegalArgumentException("Owner evidence is invalid");
            }
        }
    }
}
