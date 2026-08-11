package fr.apocalypsebleu.moddetective.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StackFrameOrderTest {
    @Test
    void currentJavaRuntimeReturnsActiveFrameBeforeItsCaller() {
        StackTraceElement[] stack = caller();

        int leaf = indexOf(stack, "leaf");
        int caller = indexOf(stack, "caller");
        assertTrue(leaf >= 0, "leaf frame must be present");
        assertTrue(caller > leaf, "Thread.getStackTrace must order the active frame before callers");
    }

    private static StackTraceElement[] caller() {
        return leaf();
    }

    private static StackTraceElement[] leaf() {
        return Thread.currentThread().getStackTrace();
    }

    private static int indexOf(StackTraceElement[] stack, String method) {
        return Arrays.stream(stack).map(StackTraceElement::getMethodName).toList().indexOf(method);
    }
}
