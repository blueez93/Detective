package fr.apocalypsebleu.moddetective.core;

public record StackSnapshot(long nanoTime, StackTraceElement[] stack) {}
