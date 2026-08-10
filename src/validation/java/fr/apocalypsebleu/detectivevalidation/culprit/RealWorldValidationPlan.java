package fr.apocalypsebleu.detectivevalidation.culprit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicBoolean;

final class RealWorldValidationPlan {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int SOAK_MINUTES = Math.max(5, Integer.getInteger("detective.validation.soakMinutes", 30));
    private static final boolean EXIT_AFTER = Boolean.getBoolean("detective.validation.exitAfterAutorun");

    private RealWorldValidationPlan() {}

    static boolean start() {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }

        long totalMs = SOAK_MINUTES * 60_000L;
        phase("stable_gameplay", true, 0L);
        phase("rapid_chunk_generation", true, fraction(totalMs, 0.20));
        for (int index = 0; index < 6; index++) {
            int distance = (index + 1) * 4_000;
            schedule(() -> sendCommand("tp @s " + distance + " 100 " + distance),
                    fraction(totalMs, 0.21 + index * 0.025));
        }

        phase("large_inventory_menu", true, fraction(totalMs, 0.40));
        schedule(RealWorldValidationPlan::openInventory, fraction(totalMs, 0.405));
        schedule(() -> Minecraft.getInstance().setScreen(null), fraction(totalMs, 0.44));

        phase("dimension_change", true, fraction(totalMs, 0.48));
        schedule(() -> sendCommand("execute in minecraft:the_nether run tp @s 0 90 0"), fraction(totalMs, 0.49));
        schedule(() -> sendCommand("execute in minecraft:overworld run tp @s 0 100 0"), fraction(totalMs, 0.55));

        phase("resource_reload", true, fraction(totalMs, 0.58));
        schedule(() -> Minecraft.getInstance().reloadResourcePacks(), fraction(totalMs, 0.59));

        phase("explicit_gc_pressure", false, fraction(totalMs, 0.66));
        schedule(RealWorldValidationPlan::requestGcUnderPressure, fraction(totalMs, 0.67));

        phase("controlled_attribution", false, fraction(totalMs, 0.72));
        long attributionStart = fraction(totalMs, 0.73);
        schedule(RealWorldValidationPlan::focusWindow, fraction(totalMs, 0.715));
        schedule(() -> ValidationCommands.runStall("realworld-direct-a", 600L, true,
                ControlledFreezeGenerator.Path.DIRECT_A, 1), attributionStart);
        schedule(() -> ValidationCommands.runStall("realworld-direct-b", 600L, true,
                ControlledFreezeGenerator.Path.DIRECT_B, 1), attributionStart + 3_500L);
        schedule(() -> ValidationCommands.runStall("realworld-scheduled-c", 600L, true,
                ControlledFreezeGenerator.Path.SCHEDULED_STANDARD_C, 1), attributionStart + 7_000L);
        schedule(() -> ValidationCommands.runStall("realworld-indirect-b", 600L, true,
                ControlledFreezeGenerator.Path.INDIRECT_A_TO_B, 3), attributionStart + 10_500L);
        schedule(() -> ValidationCommands.runStall("realworld-nested-c", 600L, true,
                ControlledFreezeGenerator.Path.NESTED_A_TO_B_TO_C, 3), attributionStart + 14_000L);

        phase("pause_menu", true, fraction(totalMs, 0.82));
        schedule(() -> Minecraft.getInstance().setScreen(new PauseScreen(true)), fraction(totalMs, 0.825));
        schedule(() -> Minecraft.getInstance().setScreen(null), fraction(totalMs, 0.86));

        phase("alt_tab_unfocused", true, fraction(totalMs, 0.865));
        schedule(RealWorldValidationPlan::iconifyWindow, fraction(totalMs, 0.87));
        schedule(RealWorldValidationPlan::restoreWindow, fraction(totalMs, 0.875));

        phase("world_disconnect_reconnect", true, fraction(totalMs, 0.88));
        schedule(() -> Minecraft.getInstance().disconnect(new TitleScreen()), fraction(totalMs, 0.885));
        schedule(RealWorldValidationPlan::reconnect, fraction(totalMs, 0.90));

        phase("stable_gameplay_final", true, fraction(totalMs, 0.92));
        schedule(() -> {
            ValidationHarness.beginPhase("shutdown", true);
            ValidationHarness.logMetrics();
            RUNNING.set(false);
            if (EXIT_AFTER) {
                Minecraft.getInstance().stop();
            }
        }, totalMs);

        DetectiveTestCulprit.LOGGER.info(
                "[Detective Validation] Real-world validation plan scheduled for {} minute(s)", SOAK_MINUTES);
        return true;
    }

    private static long fraction(long totalMs, double fraction) {
        return Math.round(totalMs * fraction);
    }

    private static void phase(String name, boolean falsePositiveEligible, long delayMs) {
        schedule(() -> ValidationHarness.beginPhase(name, falsePositiveEligible), delayMs);
    }

    private static void schedule(Runnable action, long delayMs) {
        if (!ValidationHarness.scheduleOnRenderThread(action, delayMs)) {
            RUNNING.set(false);
        }
    }

    private static void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Command skipped without an active connection: {}", command);
            return;
        }
        minecraft.player.connection.sendCommand(command);
    }

    private static void openInventory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
        }
    }

    private static void requestGcUnderPressure() {
        byte[][] pressure = new byte[8][];
        for (int index = 0; index < pressure.length; index++) {
            pressure[index] = new byte[8 * 1024 * 1024];
            pressure[index][0] = (byte) index;
        }
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] Requesting explicit GC after 64 MiB temporary allocation");
        pressure = null;
        System.gc();
    }

    private static void reconnect() {
        String server = System.getProperty("detective.validation.autoconnectServer", "").trim();
        if (server.isEmpty()) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Reconnect skipped: no validation server configured");
            return;
        }
        ValidationCommands.reconnectToConfiguredServer();
    }

    private static void iconifyWindow() {
        GLFW.glfwIconifyWindow(Minecraft.getInstance().getWindow().getWindow());
    }

    private static void restoreWindow() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwRestoreWindow(window);
        GLFW.glfwFocusWindow(window);
    }

    static void focusWindow() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwRestoreWindow(window);
        GLFW.glfwFocusWindow(window);
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] Focus restored before controlled attribution");
    }
}
