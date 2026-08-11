package fr.apocalypsebleu.detectivevalidation.culprit;

import fr.apocalypsebleu.moddetective.ModDetective;
import fr.apocalypsebleu.moddetective.client.ui.DetectiveHomeScreen;
import fr.apocalypsebleu.moddetective.client.ui.IncidentDetailScreen;
import fr.apocalypsebleu.moddetective.client.ui.IncidentListScreen;
import fr.apocalypsebleu.moddetective.client.ui.ModpackChangesScreen;
import fr.apocalypsebleu.moddetective.client.ClientPerformanceEvents;
import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.util.concurrent.atomic.AtomicBoolean;

/** Development-only screenshot route for the v0.4 screens. */
public final class UiValidationPlan {
    private static final String VALIDATION_WORLD = "DetectiveValidation";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int WORLD_READINESS_POLLS_BEFORE_FALLBACK = 12;
    private static boolean registered;
    private static boolean worldOpenRequested;
    private static int worldReadinessPolls;
    private static long worldReadyNanos;

    private UiValidationPlan() {}

    public static void registerIfRequested() {
        String requested = System.getProperty("detective.validation.autorun", "").trim();
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI route property='{}'", requested);
        if (!"ui".equals(requested) || registered) {
            return;
        }
        registered = true;
        schedule(UiValidationPlan::pollUntilReady, 1_000L);
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI route readiness poll scheduled");
    }

    private static void pollUntilReady() {
        if (RUNNING.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            worldReadyNanos = 0L;
            worldReadinessPolls++;
            if (minecraft.screen instanceof AccessibilityOnboardingScreen onboardingScreen) {
                DetectiveTestCulprit.LOGGER.info(
                        "[Detective Validation] Completing the development client's accessibility onboarding");
                onboardingScreen.onClose();
                schedule(UiValidationPlan::pollUntilReady, 1_000L);
                return;
            }
            if (!worldOpenRequested && minecraft.screen instanceof BackupConfirmScreen backupScreen) {
                worldOpenRequested = pressBackupSkip(backupScreen);
            }
            if (!worldOpenRequested
                    && minecraft.screen instanceof TitleScreen
                    && minecraft.getLevelSource().levelExists(VALIDATION_WORLD)) {
                worldOpenRequested = true;
                DetectiveTestCulprit.LOGGER.info(
                        "[Detective Validation] Explicitly opening validation world '{}'", VALIDATION_WORLD);
                minecraft.createWorldOpenFlows().openWorld(VALIDATION_WORLD, () -> {
                    DetectiveTestCulprit.LOGGER.warn(
                            "[Detective Validation] Validation world '{}' could not be opened", VALIDATION_WORLD);
                    minecraft.setScreen(new TitleScreen());
                });
            }
            if (worldReadinessPolls >= WORLD_READINESS_POLLS_BEFORE_FALLBACK) {
                DetectiveTestCulprit.LOGGER.warn(
                        "[Detective Validation] No world became available (screen={}); validating from the title context",
                        minecraft.screen == null ? "<none>" : minecraft.screen.getClass().getName());
                start(false);
                return;
            }
            schedule(UiValidationPlan::pollUntilReady, 1_000L);
            return;
        }
        if (worldReadyNanos == 0L) {
            worldReadyNanos = System.nanoTime();
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI route found a loaded world; warming up");
            schedule(UiValidationPlan::pollUntilReady, 1_000L);
            return;
        }
        if (System.nanoTime() - worldReadyNanos < 3_000_000_000L
                || ClientPerformanceEvents.diagnostics().blackBoxSamples() < 60) {
            schedule(UiValidationPlan::pollUntilReady, 1_000L);
            return;
        }
        start(true);
    }

    private static boolean pressBackupSkip(BackupConfirmScreen screen) {
        String skipLabel = Component.translatable("selectWorld.backupJoinSkipButton").getString();
        for (var child : screen.children()) {
            if (child instanceof Button button && skipLabel.equals(button.getMessage().getString())) {
                DetectiveTestCulprit.LOGGER.info(
                        "[Detective Validation] Proceeding into the validation world without creating a backup");
                button.onPress();
                return true;
            }
        }
        DetectiveTestCulprit.LOGGER.warn(
                "[Detective Validation] Backup confirmation was visible but its skip button was not found");
        return false;
    }

    static boolean start(boolean worldAvailable) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI screenshot route starting");
        DetectiveUiService.refreshIndex().whenComplete((index, error) -> {
            if (error != null) {
                DetectiveTestCulprit.LOGGER.error("[Detective Validation] UI data loading failed", error);
                RUNNING.set(false);
                return;
            }
            scheduleScreens(index, worldAvailable);
        });
        return true;
    }

    static boolean isRunning() {
        return RUNNING.get();
    }

    private static void scheduleScreens(IncidentIndexViewModel index, boolean worldAvailable) {
        schedule(() -> Minecraft.getInstance().setScreen(
                worldAvailable ? new PauseScreen(true) : new TitleScreen()), 0L);
        schedule(() -> screenshot("detective-v04-01-pause-entry.png"), 1_500L);

        schedule(() -> {
            Screen parent = Minecraft.getInstance().screen;
            Minecraft.getInstance().setScreen(new DetectiveHomeScreen(parent));
        }, 2_500L);
        schedule(() -> screenshot("detective-v04-02-home.png"), 4_500L);

        schedule(() -> Minecraft.getInstance().setScreen(new IncidentListScreen(
                Minecraft.getInstance().screen, index)), 5_500L);
        schedule(() -> screenshot("detective-v04-03-incidents-real.png"), 7_000L);

        IncidentSummaryViewModel selectedIncident = index.incidents().stream()
                .filter(IncidentSummaryViewModel::hasPrimarySuspect)
                .findFirst()
                .orElse(index.incidents().isEmpty() ? null : index.incidents().getFirst());
        if (selectedIncident != null) {
            schedule(() -> Minecraft.getInstance().setScreen(new IncidentDetailScreen(
                    Minecraft.getInstance().screen, selectedIncident)), 8_000L);
            schedule(() -> screenshot("detective-v04-04-incident-detail.png"), 10_500L);
            schedule(() -> {
                if (Minecraft.getInstance().screen instanceof IncidentDetailScreen detailScreen) {
                    detailScreen.mouseScrolled(0.0, 0.0, 0.0, -9.0);
                }
            }, 11_000L);
            schedule(() -> screenshot("detective-v04-04b-incident-black-box.png"), 12_500L);
        }

        schedule(() -> Minecraft.getInstance().setScreen(new ModpackChangesScreen(
                Minecraft.getInstance().screen)), 13_500L);
        schedule(() -> screenshot("detective-v04-05-modpack-changes.png"), 15_000L);

        schedule(() -> Minecraft.getInstance().setScreen(new IncidentListScreen(
                Minecraft.getInstance().screen,
                IncidentIndexViewModel.empty(System.currentTimeMillis(), ModDetective.SESSION_STARTED_AT_EPOCH_MS))), 16_000L);
        schedule(() -> screenshot("detective-v04-06-incidents-empty.png"), 17_500L);

        schedule(() -> Minecraft.getInstance().setScreen(new TitleScreen()), 19_000L);
        schedule(() -> screenshot("detective-v04-07-title-entry.png"), 21_500L);
        schedule(() -> {
            RUNNING.set(false);
            DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI screenshot route completed");
        }, 23_000L);
        if (Boolean.getBoolean("detective.validation.exitAfterAutorun")) {
            schedule(() -> {
                DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI route complete; stopping Minecraft cleanly");
                Minecraft.getInstance().stop();
            }, 25_000L);
        }
    }

    private static void screenshot(String fileName) {
        Minecraft minecraft = Minecraft.getInstance();
        Screenshot.grab(
                FMLPaths.GAMEDIR.get().toFile(),
                fileName,
                minecraft.getMainRenderTarget(),
                message -> DetectiveTestCulprit.LOGGER.info(
                        "[Detective Validation] UI_SCREENSHOT {}: {}", fileName, message.getString()));
    }

    private static void schedule(Runnable action, long delayMs) {
        if (!ValidationHarness.scheduleOnRenderThread(action, delayMs)) {
            RUNNING.set(false);
        }
    }
}
