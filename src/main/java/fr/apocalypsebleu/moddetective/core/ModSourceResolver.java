package fr.apocalypsebleu.moddetective.core;

import fr.apocalypsebleu.moddetective.ModDetective;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;

import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ModSourceResolver {
    private final Map<String, Optional<ResolvedMod>> classCache = new ConcurrentHashMap<>();
    private final ClassLoader gameClassLoader = ModSourceResolver.class.getClassLoader();
    private volatile SourceIndex sourceIndex;

    public Optional<ResolvedMod> resolve(String className) {
        if (className == null || className.isBlank() || isPlatformClass(className)) {
            return Optional.empty();
        }
        return classCache.computeIfAbsent(className, this::resolveUncached);
    }

    private Optional<ResolvedMod> resolveUncached(String className) {
        SourceIndex index = sourceIndex();
        int packageEnd = className.lastIndexOf('.');
        if (packageEnd > 0) {
            ResolvedMod packageOwner = index.packageOwners().get(className.substring(0, packageEnd));
            if (packageOwner != null) {
                return Optional.of(packageOwner);
            }
        }

        return resolveByCodeSource(className, index.sources());
    }

    private SourceIndex sourceIndex() {
        SourceIndex current = sourceIndex;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (sourceIndex == null) {
                sourceIndex = buildSourceIndex();
            }
            return sourceIndex;
        }
    }

    private static SourceIndex buildSourceIndex() {
        Map<String, ResolvedMod> packageOwners = new LinkedHashMap<>();
        List<ModSource> sources = new ArrayList<>();

        ModList modList = ModList.get();
        if (modList == null) {
            ModDetective.LOGGER.warn("[Detective] Mod list is unavailable; class attribution is disabled for this session");
            return new SourceIndex(Map.of(), List.of());
        }

        for (IModFileInfo fileInfo : modList.getModFiles()) {
            try {
                if (fileInfo.getMods().isEmpty()) {
                    continue;
                }

                ResolvedMod owner = describeOwner(fileInfo.getMods());
                Path path = fileInfo.getFile().getFilePath().toAbsolutePath().normalize();
                sources.add(new ModSource(path, owner));

                for (String packageName : fileInfo.getFile().getSecureJar()
                        .moduleDataProvider().descriptor().packages()) {
                    packageOwners.merge(packageName, owner, ModSourceResolver::preferUnambiguousOwner);
                }
            } catch (RuntimeException e) {
                ModDetective.LOGGER.debug("[Detective] Unable to index a mod file for class attribution", e);
            }
        }

        packageOwners.values().removeIf(ResolvedMod.AMBIGUOUS::equals);
        return new SourceIndex(Map.copyOf(packageOwners), List.copyOf(sources));
    }

    private static ResolvedMod preferUnambiguousOwner(ResolvedMod existing, ResolvedMod candidate) {
        if (existing.equals(candidate)) {
            return existing;
        }
        return ResolvedMod.AMBIGUOUS;
    }

    private static ResolvedMod describeOwner(List<IModInfo> modInfos) {
        List<IModInfo> sorted = modInfos.stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .toList();
        if (sorted.size() == 1) {
            IModInfo info = sorted.getFirst();
            return new ResolvedMod(info.getModId(), info.getDisplayName(), info.getVersion().toString());
        }

        String ids = sorted.stream().map(IModInfo::getModId).collect(Collectors.joining("+"));
        String names = sorted.stream().map(IModInfo::getDisplayName).collect(Collectors.joining(" / "));
        String versions = sorted.stream().map(info -> info.getVersion().toString()).distinct().collect(Collectors.joining(" / "));
        return new ResolvedMod(ids, names + " (shared mod file)", versions);
    }

    private Optional<ResolvedMod> resolveByCodeSource(String className, List<ModSource> sources) {
        try {
            Class<?> type = Class.forName(className, false, gameClassLoader);
            if (type.getProtectionDomain() == null || type.getProtectionDomain().getCodeSource() == null) {
                return Optional.empty();
            }

            URL location = type.getProtectionDomain().getCodeSource().getLocation();
            URI uri = location.toURI();
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }

            Path classPath = Path.of(uri).toAbsolutePath().normalize();
            for (ModSource source : sources) {
                if (source.path().equals(classPath)) {
                    return Optional.of(source.mod());
                }
            }
        } catch (Throwable ignored) {
            // Resolution is best effort. Failure must never affect the game.
        }
        return Optional.empty();
    }

    private static boolean isPlatformClass(String className) {
        return className.startsWith("java.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.");
    }

    private record ModSource(Path path, ResolvedMod mod) {}
    private record SourceIndex(Map<String, ResolvedMod> packageOwners, List<ModSource> sources) {}

    public record ResolvedMod(String id, String name, String version) {
        private static final ResolvedMod AMBIGUOUS = new ResolvedMod("ambiguous", "Ambiguous package ownership", "unknown");
    }
}
