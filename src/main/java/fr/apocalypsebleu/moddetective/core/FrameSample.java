package fr.apocalypsebleu.moddetective.core;

public record FrameSample(
        long epochMs,
        long nanoTime,
        double frameMs,
        double approximateFps,
        long usedMemoryBytes,
        long maxMemoryBytes,
        String dimension,
        int playerX,
        int playerY,
        int playerZ
) {}
