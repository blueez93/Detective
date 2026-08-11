package fr.apocalypsebleu.moddetective.snapshot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.storage.AtomicFiles;
import fr.apocalypsebleu.moddetective.storage.ModDetectivePaths;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class ModSnapshotService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicReference<ModSnapshotDiff> LATEST_DIFF = new AtomicReference<>();

    private ModSnapshotService() {}

    public static ModSnapshotDiff captureAndPersist() {
        ModSnapshot current = capture();
        ModSnapshot previous = null;

        try {
            ModDetectivePaths.ensureDirectories();
            Path target = ModDetectivePaths.snapshots().resolve("last-session.json");
            previous = readPrevious(target);
            AtomicFiles.writeUtf8(target, GSON.toJson(current));
        } catch (IOException | RuntimeException e) {
            ModDetective.LOGGER.error("[Detective] Unable to persist the mod snapshot", e);
        }

        ModSnapshotDiff diff = ModSnapshotDiff.between(previous, current);
        LATEST_DIFF.set(diff);
        return diff;
    }

    public static ModSnapshotDiff latestDiff() {
        return LATEST_DIFF.get();
    }

    public static ModSnapshot capture() {
        List<ModSnapshot.LoadedMod> mods = ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .map(info -> new ModSnapshot.LoadedMod(
                        info.getModId(),
                        info.getDisplayName(),
                        info.getVersion().toString(),
                        safeFileName(info)))
                .toList();

        return new ModSnapshot(
                System.currentTimeMillis(),
                SharedConstants.getCurrentVersion().getName(),
                System.getProperty("java.version", "unknown"),
                fingerprint(mods),
                mods);
    }

    private static String safeFileName(IModInfo info) {
        try {
            return info.getOwningFile().getFile().getFileName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static ModSnapshot readPrevious(Path target) {
        if (!Files.isRegularFile(target)) {
            return null;
        }
        try {
            ModSnapshot snapshot = GSON.fromJson(Files.readString(target, StandardCharsets.UTF_8), ModSnapshot.class);
            if (snapshot == null) {
                throw new IllegalArgumentException("Snapshot JSON contained null");
            }
            return snapshot;
        } catch (Exception e) {
            ModDetective.LOGGER.warn("[Detective] Unable to read the previous snapshot; treating this as the first run", e);
            return null;
        }
    }

    private static String fingerprint(List<ModSnapshot.LoadedMod> mods) {
        String source = mods.stream()
                .map(mod -> mod.id() + "=" + mod.version())
                .collect(Collectors.joining("\n"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
