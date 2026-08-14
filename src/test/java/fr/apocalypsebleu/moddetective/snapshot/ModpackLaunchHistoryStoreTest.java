package fr.apocalypsebleu.moddetective.snapshot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.apocalypsebleu.moddetective.client.ui.data.evolution.ModpackChangeHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModpackLaunchHistoryStoreTest {
    private static final long HOUR = Duration.ofHours(1).toMillis();

    @TempDir
    Path temporaryDirectory;

    @Test
    void firstLaunchCreatesHistoryWithoutFabricatingEarlierChanges() throws IOException {
        Path target = target();
        ModpackLaunchHistoryStore.RecordResult result = store(target).record(
                ModpackLaunchRecord.from(ModSnapshotDiff.between(
                        null, snapshot(10L, List.of(mod("alpha", "Alpha", "1.0", "alpha.jar"))))));

        assertEquals(ModpackLaunchHistoryStore.LoadStatus.MISSING, result.sourceStatus());
        assertTrue(result.written());
        assertTrue(result.currentLaunchPersisted());
        assertTrue(Files.isRegularFile(target));
        assertEquals(1, result.history().records().size());
        assertTrue(result.history().records().getFirst().previousLaunchAtEpochMs().isEmpty());
        assertTrue(result.history().records().getFirst().changes().isEmpty());
        assertTrue(result.history().earlierHistoryUnavailable());
    }

    @Test
    void updatedModRoundTripsWithVersions() throws IOException {
        ModpackLaunchRecord expected = ModpackLaunchRecord.from(updated(20L, "alpha"));
        ModpackLaunchHistoryStore store = store(target());

        store.record(expected);
        ModpackLaunchRecord actual = store.load().history().records().getFirst();

        assertEquals(expected, actual);
        assertEquals(ModpackLaunchRecord.ChangeType.UPDATED, actual.changes().getFirst().type());
        assertEquals(Optional.of("1.0"), actual.changes().getFirst().previousVersion());
        assertEquals(Optional.of("2.0"), actual.changes().getFirst().newVersion());
    }

    @Test
    void addedUpdatedAndRemovedChangesUseOnlyCompactAllowListedFields() throws IOException {
        ModSnapshot previous = snapshot(30L, List.of(
                mod("alpha", "Alpha", "1.0", "C:\\Users\\private\\alpha.jar"),
                mod("removed", "Removed", "3.0", "secret-path.jar")));
        ModSnapshot current = snapshot(40L, List.of(
                mod("alpha", "Alpha", "2.0", "alpha-new.jar"),
                mod("added", "Added", "1.0", "added.jar")));
        ModpackLaunchHistoryStore store = store(target());

        store.record(ModpackLaunchRecord.from(ModSnapshotDiff.between(previous, current)));

        String persisted = Files.readString(target());
        JsonObject root = JsonParser.parseString(persisted).getAsJsonObject();
        JsonObject record = root.getAsJsonArray("records").get(0).getAsJsonObject();
        assertEquals(Set.of(
                "schemaVersion", "earlierHistoryUnavailable",
                "omittedEarlierRecords", "records"), root.keySet());
        assertEquals(Set.of(
                "launchAtEpochMs", "previousLaunchAtEpochMs", "changes"), record.keySet());
        assertEquals(List.of("added", "alpha", "removed"), record.getAsJsonArray("changes")
                .asList().stream().map(value -> value.getAsJsonObject()
                        .get("modId").getAsString()).toList());
        assertFalse(persisted.contains("fileName"));
        assertFalse(persisted.contains("private"));
        assertFalse(persisted.contains("secret-path"));
        assertFalse(persisted.contains("minecraftVersion"));
        assertFalse(persisted.contains("javaVersion"));
        assertFalse(persisted.contains("fingerprint"));
    }

    @Test
    void zeroChangeLaunchIsPersisted() throws IOException {
        ModpackLaunchHistoryStore store = store(target());

        store.record(ModpackLaunchRecord.from(unchanged(50L, "alpha")));

        assertEquals(1, store.load().history().records().size());
        assertTrue(store.load().history().records().getFirst().changes().isEmpty());
    }

    @Test
    void consecutiveZeroChangeLaunchesPreserveEveryBoundary() throws IOException {
        ModpackLaunchHistoryStore store = store(target());
        store.record(ModpackLaunchRecord.from(unchanged(60L, "alpha")));
        store.record(ModpackLaunchRecord.from(unchanged(70L, "alpha")));
        store.record(ModpackLaunchRecord.from(unchanged(80L, "alpha")));

        assertEquals(List.of(60L, 70L, 80L), store.load().history().records().stream()
                .map(ModpackLaunchRecord::launchAtEpochMs).toList());
    }

    @Test
    void sixtyFourRecordsAreRetained() throws IOException {
        ModpackLaunchHistoryStore store = store(target());
        for (int index = 1; index <= 64; index++) {
            store.record(record(index));
        }

        ModpackLaunchHistory history = store.load().history();
        assertEquals(64, history.records().size());
        assertEquals(0L, history.omittedEarlierRecords());
        assertEquals(1L, history.records().getFirst().launchAtEpochMs());
        assertEquals(64L, history.records().getLast().launchAtEpochMs());
    }

    @Test
    void sixtyFifthRecordEvictsOldestAndProtectsCurrent() throws IOException {
        ModpackLaunchHistoryStore store = store(target());
        for (int index = 1; index <= 65; index++) {
            store.record(record(index));
        }

        ModpackLaunchHistory history = store.load().history();
        assertEquals(64, history.records().size());
        assertEquals(1L, history.omittedEarlierRecords());
        assertEquals(2L, history.records().getFirst().launchAtEpochMs());
        assertEquals(65L, history.records().getLast().launchAtEpochMs());
    }

    @Test
    void maximumRecordCountIsCentrallyConfigurableForTests() throws IOException {
        ModpackLaunchHistoryStore store = new ModpackLaunchHistoryStore(target(), 3);
        for (int index = 1; index <= 4; index++) {
            store.record(record(index));
        }

        assertEquals(List.of(2L, 3L, 4L), store.load().history().records().stream()
                .map(ModpackLaunchRecord::launchAtEpochMs).toList());
        assertEquals(1L, store.load().history().omittedEarlierRecords());
    }

    @Test
    void missingHistoryLoadsSafelyWithoutCreatingAFile() {
        ModpackLaunchHistoryStore.LoadResult result = store(target()).load();

        assertEquals(ModpackLaunchHistoryStore.LoadStatus.MISSING, result.status());
        assertTrue(result.history().records().isEmpty());
        assertTrue(result.writable());
        assertFalse(Files.exists(target()));
    }

    @Test
    void corruptHistoryIsPreservedAndCurrentLaunchStaysInMemoryOnly() throws IOException {
        Files.writeString(target(), "{\"schemaVersion\":1,\"records\":[");
        String original = Files.readString(target());

        ModpackLaunchHistoryStore.RecordResult result = store(target()).record(record(90L));

        assertEquals(ModpackLaunchHistoryStore.LoadStatus.CORRUPT, result.sourceStatus());
        assertFalse(result.written());
        assertFalse(result.currentLaunchPersisted());
        assertEquals(1, result.history().records().size());
        assertEquals(original, Files.readString(target()));
    }

    @Test
    void unsupportedFutureSchemaIsPreserved() throws IOException {
        String future = """
                {"schemaVersion":999,"future":"keep","records":[]}
                """;
        Files.writeString(target(), future);

        ModpackLaunchHistoryStore.RecordResult result = store(target()).record(record(100L));

        assertEquals(ModpackLaunchHistoryStore.LoadStatus.UNSUPPORTED_SCHEMA,
                result.sourceStatus());
        assertFalse(result.written());
        assertEquals(future, Files.readString(target()));
    }

    @Test
    void legacyLastSessionCanSeedOnlyTheCurrentObservedDiffWithoutMutation() throws IOException {
        Path legacySnapshot = temporaryDirectory.resolve("last-session.json");
        String legacyContent = "legacy snapshot bytes stay untouched";
        Files.writeString(legacySnapshot, legacyContent);
        ModSnapshot previous = snapshot(110L, List.of(mod("alpha", "Alpha", "1.0", "alpha.jar")));
        ModSnapshot current = snapshot(120L, List.of(mod("alpha", "Alpha", "2.0", "alpha.jar")));

        ModpackLaunchHistoryState state = ModSnapshotService.persistLaunchHistory(
                target(), ModSnapshotDiff.between(previous, current), 64);

        assertEquals(legacyContent, Files.readString(legacySnapshot));
        assertTrue(state.currentLaunchPersisted());
        assertEquals(1, state.history().records().size());
        assertEquals(OptionalLong.of(110L),
                state.history().records().getFirst().previousLaunchAtEpochMs());
        assertEquals(1, state.history().records().getFirst().changes().size());
    }

    @Test
    void restartReloadsIdenticalHistory() throws IOException {
        ModpackLaunchHistoryStore firstProcess = store(target());
        firstProcess.record(record(130L));
        firstProcess.record(ModpackLaunchRecord.from(updated(140L, "alpha")));
        firstProcess.record(ModpackLaunchRecord.from(unchanged(150L, "alpha")));
        ModpackLaunchHistory beforeRestart = firstProcess.load().history();

        ModpackLaunchHistory afterRestart = store(target()).load().history();

        assertEquals(beforeRestart, afterRestart);
    }

    @Test
    void repeatedPersistenceDoesNotDuplicateOneLaunchOrRewriteFile() throws IOException {
        ModpackLaunchHistoryStore store = store(target());
        ModpackLaunchRecord launch = ModpackLaunchRecord.from(updated(160L, "alpha"));
        store.record(launch);
        String firstWrite = Files.readString(target());

        ModpackLaunchHistoryStore.RecordResult duplicate = store.record(launch);

        assertTrue(duplicate.duplicate());
        assertFalse(duplicate.written());
        assertEquals(1, duplicate.history().records().size());
        assertEquals(firstWrite, Files.readString(target()));
    }

    @Test
    void unknownOptionalFieldsAreIgnoredWithoutChangingKnownData() throws IOException {
        ModpackLaunchHistoryStore store = store(target());
        ModpackLaunchRecord expected = ModpackLaunchRecord.from(updated(170L, "alpha"));
        store.record(expected);
        JsonObject root = JsonParser.parseString(Files.readString(target())).getAsJsonObject();
        root.addProperty("futureRoot", "ignored");
        JsonObject record = root.getAsJsonArray("records").get(0).getAsJsonObject();
        record.addProperty("futureRecord", true);
        record.getAsJsonArray("changes").get(0).getAsJsonObject()
                .addProperty("futureChange", 42);
        Files.writeString(target(), root.toString());

        ModpackLaunchHistoryStore.LoadResult loaded = store.load();

        assertEquals(ModpackLaunchHistoryStore.LoadStatus.LOADED, loaded.status());
        assertEquals(List.of(expected), loaded.history().records());
    }

    @Test
    void malformedKnownOptionalFieldIsPreservedRatherThanRewritten() throws IOException {
        ModpackLaunchHistoryStore store = store(target());
        store.record(record(180L));
        JsonObject root = JsonParser.parseString(Files.readString(target())).getAsJsonObject();
        root.addProperty("omittedEarlierRecords", "not-a-number");
        String malformed = root.toString();
        Files.writeString(target(), malformed);

        ModpackLaunchHistoryStore.RecordResult result = store.record(record(190L));

        assertEquals(ModpackLaunchHistoryStore.LoadStatus.CORRUPT, result.sourceStatus());
        assertFalse(result.written());
        assertEquals(malformed, Files.readString(target()));
    }

    @Test
    void persistedHistoryMapsToPartialCaseEvolutionCoverageAfterEviction() throws IOException {
        ModpackLaunchHistoryStore store = store(target());
        for (int index = 1; index <= 65; index++) {
            store.record(record(index));
        }
        ModpackLaunchHistory history = store.load().history();
        ModpackChangeHistory evolution = ModpackChangeHistory.from(
                new ModpackLaunchHistoryState(history, 0, true));

        assertEquals(ModpackChangeHistory.Availability.PARTIAL, evolution.availability());
        assertEquals(64, evolution.launches().size());
        assertEquals(1L, evolution.omittedEarlierLaunches());
        assertTrue(evolution.earlierLaunchHistoryUnavailable());
    }

    @Test
    void boundedHistoryLoadAndSaveBenchmark() throws IOException {
        List<ModpackLaunchRecord> records = new ArrayList<>();
        for (int launch = 0; launch < 64; launch++) {
            List<ModpackLaunchRecord.ModChange> changes = new ArrayList<>();
            for (int change = 0; change < 24; change++) {
                changes.add(new ModpackLaunchRecord.ModChange(
                        ModpackLaunchRecord.ChangeType.UPDATED,
                        "mod-%03d".formatted(change),
                        "Example Mod %03d".formatted(change),
                        Optional.of("1.%d".formatted(launch)),
                        Optional.of("1.%d".formatted(launch + 1))));
            }
            records.add(new ModpackLaunchRecord(
                    1_000L + launch * HOUR,
                    launch == 0 ? OptionalLong.empty()
                            : OptionalLong.of(1_000L + (launch - 1L) * HOUR),
                    changes));
        }
        ModpackLaunchHistory history = new ModpackLaunchHistory(records, 0L, true);
        ModpackLaunchHistoryStore store = store(target());
        store.save(history);
        for (int warmup = 0; warmup < 10; warmup++) {
            store.load();
            store.save(history);
        }
        long[] loads = new long[31];
        long[] saves = new long[31];
        for (int sample = 0; sample < loads.length; sample++) {
            long loadStart = System.nanoTime();
            assertEquals(64, store.load().history().records().size());
            loads[sample] = System.nanoTime() - loadStart;
            long saveStart = System.nanoTime();
            store.save(history);
            saves[sample] = System.nanoTime() - saveStart;
        }
        java.util.Arrays.sort(loads);
        java.util.Arrays.sort(saves);
        System.out.printf(
                "MODPACK_LAUNCH_HISTORY_BENCHMARK records=64 changes=1536 load=%.3fms save=%.3fms%n",
                loads[loads.length / 2] / 1_000_000.0,
                saves[saves.length / 2] / 1_000_000.0);
    }

    private Path target() {
        return temporaryDirectory.resolve("launch-history.json");
    }

    private static ModpackLaunchHistoryStore store(Path target) {
        return new ModpackLaunchHistoryStore(target, 64);
    }

    private static ModpackLaunchRecord record(long timestamp) {
        return new ModpackLaunchRecord(timestamp, OptionalLong.empty(), List.of());
    }

    private static ModSnapshotDiff updated(long capturedAt, String id) {
        return ModSnapshotDiff.between(
                snapshot(capturedAt - 1L,
                        List.of(mod(id, displayName(id), "1.0", id + ".jar"))),
                snapshot(capturedAt,
                        List.of(mod(id, displayName(id), "2.0", id + ".jar"))));
    }

    private static ModSnapshotDiff unchanged(long capturedAt, String id) {
        return ModSnapshotDiff.between(
                snapshot(capturedAt - 1L,
                        List.of(mod(id, displayName(id), "1.0", id + ".jar"))),
                snapshot(capturedAt,
                        List.of(mod(id, displayName(id), "1.0", id + ".jar"))));
    }

    private static ModSnapshot snapshot(long capturedAt, List<ModSnapshot.LoadedMod> mods) {
        return new ModSnapshot(capturedAt, "1.21.1", "21", "fingerprint-" + capturedAt, mods);
    }

    private static ModSnapshot.LoadedMod mod(
            String id,
            String name,
            String version,
            String fileName
    ) {
        return new ModSnapshot.LoadedMod(id, name, version, fileName);
    }

    private static String displayName(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
