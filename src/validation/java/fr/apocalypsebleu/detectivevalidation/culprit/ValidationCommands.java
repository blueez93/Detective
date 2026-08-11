package fr.apocalypsebleu.detectivevalidation.culprit;

import com.mojang.brigadier.context.CommandContext;
import fr.apocalypsebleu.moddetective.client.ClientPerformanceEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.lwjgl.glfw.GLFW;
import fr.apocalypsebleu.detectivevalidation.culpritb.IndependentStallTaskB;
import fr.apocalypsebleu.detectivevalidation.culpritc.IndependentStallTaskC;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@EventBusSubscriber(modid = DetectiveTestCulprit.MOD_ID, value = Dist.CLIENT)
public final class ValidationCommands {
    private static final long BURST_STALL_MS = 150L;
    private static final long DOUBLE_STALL_MS = 600L;
    private static final AtomicLong NEXT_SCENARIO = new AtomicLong();
    private static final AtomicBoolean PLAN_RUNNING = new AtomicBoolean();
    private static final String AUTORUN_SCENARIO = System.getProperty("detective.validation.autorun", "").trim();
    private static final String AUTOCONNECT_SERVER = System.getProperty("detective.validation.autoconnectServer", "").trim();
    private static final boolean EXIT_AFTER_AUTORUN = Boolean.getBoolean("detective.validation.exitAfterAutorun");
    private static long autorunWorldReadyNanos;
    private static long autoconnectReadyNanos;
    private static boolean autorunTriggered;
    private static boolean autoconnectAttempted;
    private static boolean focusRequested;

    private ValidationCommands() {}

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("detective_validate")
                .then(Commands.literal("150").executes(context -> runSingle(context, 150L)))
                .then(Commands.literal("300").executes(context -> runSingle(context, 300L)))
                .then(Commands.literal("600").executes(context -> runSingle(context, 600L)))
                .then(Commands.literal("1200").executes(context -> runSingle(context, 1_200L)))
                .then(Commands.literal("below").executes(ValidationCommands::runBelowThreshold))
                .then(Commands.literal("burst").executes(ValidationCommands::runBurst))
                .then(Commands.literal("double").executes(ValidationCommands::runDouble))
                .then(Commands.literal("all").executes(ValidationCommands::runAll))
                .then(Commands.literal("direct_b").executes(context -> runPath(
                        context, "direct-b", 600L, ControlledFreezeGenerator.Path.DIRECT_B, 1)))
                .then(Commands.literal("scheduled_c").executes(context -> runPath(
                        context, "scheduled-c", 600L, ControlledFreezeGenerator.Path.SCHEDULED_STANDARD_C, 1)))
                .then(Commands.literal("indirect_b").executes(context -> runPath(
                        context, "indirect-a-b", 600L, ControlledFreezeGenerator.Path.INDIRECT_A_TO_B, 3)))
                .then(Commands.literal("nested_c").executes(context -> runPath(
                        context, "nested-a-b-c", 600L, ControlledFreezeGenerator.Path.NESTED_A_TO_B_TO_C, 3)))
                .then(Commands.literal("evidence").executes(ValidationCommands::runEvidenceStudy))
                .then(Commands.literal("focus").executes(ValidationCommands::runFocusStudy))
                .then(Commands.literal("metrics").executes(ValidationCommands::logMetrics)));
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        ValidationHarness.shutdown();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (AUTORUN_SCENARIO.isEmpty() || autorunTriggered) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            autorunWorldReadyNanos = 0L;
            attemptAutoconnect(minecraft);
            return;
        }
        if (!minecraft.isWindowActive() && !focusRequested) {
            focusRequested = true;
            GLFW.glfwFocusWindow(minecraft.getWindow().getWindow());
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] Requested window focus before autorun sampling");
        }
        if (autorunWorldReadyNanos == 0L) {
            autorunWorldReadyNanos = System.nanoTime();
            return;
        }
        if (System.nanoTime() - autorunWorldReadyNanos < 3_000_000_000L) {
            return;
        }
        if (ClientPerformanceEvents.diagnostics().blackBoxSamples() < 60) {
            return;
        }

        autorunTriggered = true;
        boolean scheduled = switch (AUTORUN_SCENARIO) {
            case "all" -> scheduleAll();
            case "burst" -> scheduleBurst();
            case "double" -> scheduleDouble();
            case "menus" -> scheduleLifecycleChecks();
            case "attribution" -> scheduleAttributionMatrix();
            case "evidence" -> scheduleEvidenceStudy();
            case "gc" -> scheduleGcStudy();
            case "focus" -> scheduleFocusStudy();
            case "ui" -> UiValidationPlan.start(true) || UiValidationPlan.isRunning();
            case "realworld" -> RealWorldValidationPlan.start();
            default -> false;
        };
        if (scheduled) {
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] Autorun '{}' started after world warm-up", AUTORUN_SCENARIO);
            if (EXIT_AFTER_AUTORUN && !"realworld".equals(AUTORUN_SCENARIO)) {
                long exitDelayMs = switch (AUTORUN_SCENARIO) {
                    case "all" -> 27_000L;
                    case "menus" -> 18_000L;
                    case "attribution" -> 32_000L;
                    case "evidence" -> 55_000L;
                    case "gc" -> 45_000L;
                    case "focus" -> 25_000L;
                    case "ui" -> 70_000L;
                    default -> 12_000L;
                };
                schedule(() -> {
                    DetectiveTestCulprit.LOGGER.info("[Detective Validation] Autorun complete; stopping Minecraft cleanly");
                    Minecraft.getInstance().stop();
                }, exitDelayMs);
            }
        } else {
            DetectiveTestCulprit.LOGGER.error("[Detective Validation] Unknown or unavailable autorun scenario '{}'", AUTORUN_SCENARIO);
        }
    }

    private static void attemptAutoconnect(Minecraft minecraft) {
        if (AUTOCONNECT_SERVER.isEmpty() || autoconnectAttempted || !minecraft.isGameLoadFinished()) {
            return;
        }
        if (autoconnectReadyNanos == 0L) {
            autoconnectReadyNanos = System.nanoTime();
            return;
        }
        if (System.nanoTime() - autoconnectReadyNanos < 2_000_000_000L) {
            return;
        }

        autoconnectAttempted = true;
        connectToLocalServer(minecraft);
    }

    private static void connectToLocalServer(Minecraft minecraft) {
        ServerData server = new ServerData("Detective Validation", AUTOCONNECT_SERVER, ServerData.Type.OTHER);
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] Connecting to local validation server {}", AUTOCONNECT_SERVER);
        ConnectScreen.startConnecting(
                new JoinMultiplayerScreen(new TitleScreen()),
                minecraft,
                ServerAddress.parseString(AUTOCONNECT_SERVER),
                server,
                true,
                null);
    }

    static void reconnectToConfiguredServer() {
        if (AUTOCONNECT_SERVER.isEmpty()) {
            return;
        }
        connectToLocalServer(Minecraft.getInstance());
    }

    private static boolean scheduleLifecycleChecks() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        schedule(() -> {
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] LIFECYCLE opening normal pause menu");
            Minecraft.getInstance().setScreen(new PauseScreen(true));
        }, 0L);
        schedule(() -> {
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] LIFECYCLE closing normal pause menu");
            Minecraft.getInstance().setScreen(null);
        }, 2_000L);
        schedule(() -> {
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] LIFECYCLE leaving world");
            Minecraft.getInstance().disconnect(new TitleScreen());
        }, 4_000L);
        schedule(() -> {
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] LIFECYCLE reconnecting to world");
            connectToLocalServer(Minecraft.getInstance());
        }, 7_000L);
        schedule(() -> {
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] LIFECYCLE leaving reloaded world");
            Minecraft.getInstance().disconnect(new TitleScreen());
        }, 14_000L);
        schedule(() -> PLAN_RUNNING.set(false), 16_000L);
        return true;
    }

    private static int runSingle(CommandContext<CommandSourceStack> context, long durationMs) {
        if (!worldLoaded(context)) {
            return 0;
        }
        runStall("single-" + durationMs, durationMs, true);
        context.getSource().sendSuccess(() -> Component.literal("Detective validation: " + durationMs + " ms stall triggered"), false);
        return 1;
    }

    private static int runBelowThreshold(CommandContext<CommandSourceStack> context) {
        if (!worldLoaded(context)) {
            return 0;
        }
        runStall("below-threshold", 80L, false);
        context.getSource().sendSuccess(() -> Component.literal("Detective validation: below-threshold stall triggered"), false);
        return 1;
    }

    private static int runPath(
            CommandContext<CommandSourceStack> context,
            String scenarioName,
            long durationMs,
            ControlledFreezeGenerator.Path path,
            int maximumAcceptedRank
    ) {
        if (!worldLoaded(context)) {
            return 0;
        }
        runStall(scenarioName, durationMs, true, path, maximumAcceptedRank);
        context.getSource().sendSuccess(() -> Component.literal(
                "Detective validation: " + scenarioName + " triggered"), false);
        return 1;
    }

    private static int runBurst(CommandContext<CommandSourceStack> context) {
        if (!worldLoaded(context) || !scheduleBurst()) {
            return planBusy(context);
        }
        context.getSource().sendSuccess(() -> Component.literal("Detective validation: four-stall burst scheduled"), false);
        return 1;
    }

    private static boolean scheduleBurst() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        for (int index = 0; index < 4; index++) {
            int burstIndex = index + 1;
            schedule(() -> runStall("burst-" + burstIndex, BURST_STALL_MS, burstIndex == 1), index * 400L);
        }
        schedule(() -> PLAN_RUNNING.set(false), 4_500L);
        return true;
    }

    private static int runDouble(CommandContext<CommandSourceStack> context) {
        if (!worldLoaded(context) || !scheduleDouble()) {
            return planBusy(context);
        }
        context.getSource().sendSuccess(() -> Component.literal("Detective validation: two stalls separated beyond debounce scheduled"), false);
        return 1;
    }

    private static boolean scheduleDouble() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        schedule(() -> runStall("double-1", DOUBLE_STALL_MS, true), 0L);
        schedule(() -> runStall("double-2", DOUBLE_STALL_MS, true), 3_200L);
        schedule(() -> PLAN_RUNNING.set(false), 7_000L);
        return true;
    }

    private static int runAll(CommandContext<CommandSourceStack> context) {
        if (!worldLoaded(context) || !scheduleAll()) {
            return planBusy(context);
        }
        context.getSource().sendSuccess(() -> Component.literal("Detective validation: 150/300/600/1200 ms matrix scheduled"), false);
        return 1;
    }

    private static boolean scheduleAll() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        schedule(() -> runStall("matrix-150", 150L, true), 1_500L);
        schedule(() -> runStall("matrix-300", 300L, true), 5_000L);
        schedule(() -> runStall("matrix-600", 600L, true), 9_000L);
        schedule(() -> runStall("matrix-1200", 1_200L, true), 13_500L);
        schedule(() -> runStall("matrix-below-threshold", 80L, false), 18_000L);
        schedule(() -> PLAN_RUNNING.set(false), 22_000L);
        return true;
    }

    private static boolean scheduleAttributionMatrix() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        schedule(RealWorldValidationPlan::focusWindow, 0L);
        schedule(() -> runStall("attribution-direct-a", 600L, true,
                ControlledFreezeGenerator.Path.DIRECT_A, 1), 2_000L);
        schedule(() -> runStall("attribution-direct-b", 600L, true,
                ControlledFreezeGenerator.Path.DIRECT_B, 1), 6_000L);
        schedule(() -> runStall("attribution-scheduled-c", 600L, true,
                ControlledFreezeGenerator.Path.SCHEDULED_STANDARD_C, 1), 10_000L);
        schedule(() -> runStall("attribution-indirect-b", 600L, true,
                ControlledFreezeGenerator.Path.INDIRECT_A_TO_B, 3), 14_000L);
        schedule(() -> runStall("attribution-nested-c", 600L, true,
                ControlledFreezeGenerator.Path.NESTED_A_TO_B_TO_C, 3), 18_000L);
        schedule(() -> PLAN_RUNNING.set(false), 25_000L);
        return true;
    }

    private static int runEvidenceStudy(CommandContext<CommandSourceStack> context) {
        if (!worldLoaded(context) || !scheduleEvidenceStudy()) {
            return planBusy(context);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Detective validation: stack evidence study scheduled"), false);
        return 1;
    }

    private static boolean scheduleEvidenceStudy() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        schedule(RealWorldValidationPlan::focusWindow, 0L);
        ControlledFreezeGenerator.Path[] paths = {
                ControlledFreezeGenerator.Path.DIRECT_A,
                ControlledFreezeGenerator.Path.DIRECT_B,
                ControlledFreezeGenerator.Path.SCHEDULED_STANDARD_C,
                ControlledFreezeGenerator.Path.INDIRECT_A_TO_B,
                ControlledFreezeGenerator.Path.NESTED_A_TO_B_TO_C,
                ControlledFreezeGenerator.Path.A_TO_C,
                ControlledFreezeGenerator.Path.B_TO_A,
                ControlledFreezeGenerator.Path.C_TO_B,
                ControlledFreezeGenerator.Path.B_TO_C_TO_A
        };
        for (int index = 0; index < paths.length; index++) {
            ControlledFreezeGenerator.Path path = paths[index];
            schedule(() -> runStall("evidence-" + path.name().toLowerCase(), 600L, true, path, 3),
                    2_000L + index * 5_000L);
        }
        schedule(() -> PLAN_RUNNING.set(false), 42_000L);
        return true;
    }

    private static boolean scheduleGcStudy() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        schedule(RealWorldValidationPlan::focusWindow, 0L);
        schedule(() -> ValidationHarness.beginPhase("explicit_gc_pressure", false), 0L);
        schedule(GcValidationScenario::start, 2_000L);
        schedule(() -> {
            ValidationHarness.beginPhase("gc_recovery", true);
            PLAN_RUNNING.set(false);
        }, 35_000L);
        return true;
    }

    private static int runFocusStudy(CommandContext<CommandSourceStack> context) {
        if (!worldLoaded(context) || !scheduleFocusStudy()) {
            return planBusy(context);
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Detective validation: focus continuity study scheduled"), false);
        return 1;
    }

    private static boolean scheduleFocusStudy() {
        if (!PLAN_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        schedule(RealWorldValidationPlan::focusWindow, 0L);
        schedule(() -> ValidationHarness.beginPhase("focus_control", true), 0L);
        schedule(() -> {
            ValidationHarness.beginPhase("focus_iconified", true);
            RealWorldValidationPlan.iconifyWindow();
        }, 2_000L);
        schedule(() -> {
            RealWorldValidationPlan.restoreWindow();
            ValidationHarness.beginPhase("focus_recovery", true);
        }, 10_000L);
        schedule(() -> {
            ValidationHarness.beginPhase("focus_stable_after_recovery", true);
            PLAN_RUNNING.set(false);
        }, 18_000L);
        return true;
    }

    private static int logMetrics(CommandContext<CommandSourceStack> context) {
        ValidationHarness.logMetrics();
        context.getSource().sendSuccess(() -> Component.literal("Detective validation: overhead metrics written to log"), false);
        return 1;
    }

    static void runStall(String scenarioName, long durationMs, boolean incidentExpected) {
        runStall(scenarioName, durationMs, incidentExpected, ControlledFreezeGenerator.Path.DIRECT_A, 1);
    }

    static void runStall(
            String scenarioName,
            long durationMs,
            boolean incidentExpected,
            ControlledFreezeGenerator.Path path,
            int maximumAcceptedRank
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            DetectiveTestCulprit.LOGGER.warn("[Detective Validation] Scenario '{}' skipped because no world is loaded", scenarioName);
            return;
        }

        Set<java.nio.file.Path> reportsBeforeStall = ValidationHarness.captureExistingReports();
        String scenarioId = scenarioName + '-' + NEXT_SCENARIO.incrementAndGet();
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] Triggering '{}' for {} ms on render thread", scenarioId, durationMs);
        if (path == ControlledFreezeGenerator.Path.DIRECT_B) {
            ValidationHarness.scheduleOnRenderThread(new IndependentStallTaskB(durationMs,
                    (startNanos, endNanos, startEpochMs, endEpochMs) -> ValidationHarness.validate(
                            ControlledFreezeGenerator.completedGroundTruth(
                                    scenarioId, path, durationMs, startNanos, endNanos, startEpochMs, endEpochMs),
                            incidentExpected,
                            maximumAcceptedRank,
                            reportsBeforeStall)), 1L);
            return;
        }
        if (path == ControlledFreezeGenerator.Path.SCHEDULED_STANDARD_C) {
            ValidationHarness.scheduleOnRenderThread(new IndependentStallTaskC(durationMs,
                    (startNanos, endNanos, startEpochMs, endEpochMs) -> ValidationHarness.validate(
                            ControlledFreezeGenerator.completedGroundTruth(
                                    scenarioId, path, durationMs, startNanos, endNanos, startEpochMs, endEpochMs),
                            incidentExpected,
                            maximumAcceptedRank,
                            reportsBeforeStall)), 1L);
            return;
        }
        ControlledFreezeGenerator.GroundTruth truth = ControlledFreezeGenerator.stallRenderThread(
                durationMs, scenarioId, path);
        ValidationHarness.validate(truth, incidentExpected, maximumAcceptedRank, reportsBeforeStall);
    }

    private static void schedule(Runnable action, long delayMs) {
        if (!ValidationHarness.scheduleOnRenderThread(action, delayMs)) {
            PLAN_RUNNING.set(false);
        }
    }

    private static boolean worldLoaded(CommandContext<CommandSourceStack> context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            return true;
        }
        context.getSource().sendFailure(Component.literal("Detective validation requires a loaded world"));
        return false;
    }

    private static int planBusy(CommandContext<CommandSourceStack> context) {
        if (Minecraft.getInstance().level != null && PLAN_RUNNING.get()) {
            context.getSource().sendFailure(Component.literal("A Detective validation plan is already running"));
        }
        return 0;
    }
}
