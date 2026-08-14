package fr.apocalypsebleu.detectivevalidation.culprit;

import fr.apocalypsebleu.moddetective.client.ui.DetectiveHomeScreen;
import fr.apocalypsebleu.moddetective.client.ui.IncidentDetailScreen;
import fr.apocalypsebleu.moddetective.client.ui.IncidentListScreen;
import fr.apocalypsebleu.moddetective.client.ui.IncidentComparisonScreen;
import fr.apocalypsebleu.moddetective.client.ui.ModpackChangesScreen;
import fr.apocalypsebleu.moddetective.client.ui.ExportSupportReportScreen;
import fr.apocalypsebleu.moddetective.client.ui.SupportReportCreatedScreen;
import fr.apocalypsebleu.moddetective.client.ui.DetectiveSettingsScreen;
import fr.apocalypsebleu.moddetective.client.ui.ClearIncidentHistoryScreen;
import fr.apocalypsebleu.moddetective.client.ui.CaseFileDetailScreen;
import fr.apocalypsebleu.moddetective.client.ui.CaseFileListScreen;
import fr.apocalypsebleu.moddetective.client.ClientPerformanceEvents;
import fr.apocalypsebleu.moddetective.client.support.DetectiveSupportService;
import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.BlackBoxPoint;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.SuspectViewModel;
import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.FrameSample;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import fr.apocalypsebleu.moddetective.core.SuspectAnalyzer;
import fr.apocalypsebleu.moddetective.storage.ModDetectivePaths;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;
import fr.apocalypsebleu.moddetective.support.report.SupportReportData;
import fr.apocalypsebleu.moddetective.support.report.SupportReportExporter;
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

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Development-only screenshot route for the v0.4 investigation and v0.5 support screens. */
public final class UiValidationPlan {
    private static final String VALIDATION_WORLD = "DetectiveValidation";
    private static final String PUBLIC_DEMO_PRIMARY_ROUTE = "public-demo";
    private static final String PUBLIC_DEMO_AMBIGUOUS_ROUTE = "public-demo-ambiguous";
    private static final String PUBLIC_DEMO_BLACK_BOX_ROUTE = "public-demo-blackbox";
    private static final String PUBLIC_DEMO_CHANGES_ROUTE = "public-demo-changes";
    private static final String PUBLIC_DEMO_SUPPORT_REPORT_ROUTE = "public-demo-support-report";
    private static final String PUBLIC_DEMO_HOME_ROUTE = "public-demo-home";
    private static final String CASE_FILES_EMPTY_ROUTE = "case-files-empty";
    private static final String CASE_FILES_ONE_ROUTE = "case-files-one";
    private static final String CASE_FILES_MULTIPLE_ROUTE = "case-files-multiple";
    private static final String CASE_FILE_DETAIL_ROUTE = "case-file-detail";
    private static final String CASE_FILE_MIXED_ROUTE = "case-file-mixed";
    private static final String CASE_FILES_LEGACY_ROUTE = "case-files-legacy";
    private static final String CASE_FILES_HOME_ROUTE = "case-files-home";
    private static final String INCIDENT_SEARCH_ROUTE = "incident-search";
    private static final String INCIDENT_SEARCH_EMPTY_ROUTE = "incident-search-empty";
    private static final String INCIDENT_FILTERS_ROUTE = "incident-filters";
    private static final String INCIDENT_COMPARE_SELECT_ROUTE = "incident-compare-select";
    private static final String INCIDENT_COMPARISON_HIGH_ROUTE = "incident-comparison-high";
    private static final String INCIDENT_COMPARISON_LOW_ROUTE = "incident-comparison-low";
    private static final String INCIDENT_COMPARISON_LEGACY_ROUTE = "incident-comparison-legacy";
    private static final String INCIDENT_COMPARISON_INSUFFICIENT_ROUTE =
            "incident-comparison-insufficient";
    private static final String PUBLIC_DEMO_PRIMARY_SCREENSHOT = "detective-v070-public-demo-incident.png";
    private static final String PUBLIC_DEMO_AMBIGUOUS_SCREENSHOT =
            "detective-v070-public-demo-ambiguous.png";
    private static final String PUBLIC_DEMO_BLACK_BOX_SCREENSHOT =
            "detective-v070-public-demo-blackbox.png";
    private static final String PUBLIC_DEMO_CHANGES_SCREENSHOT =
            "detective-v070-public-demo-modpack-changes.png";
    private static final String PUBLIC_DEMO_SUPPORT_REPORT_SCREENSHOT =
            "detective-v070-public-demo-support-report.png";
    private static final String PUBLIC_DEMO_HOME_SCREENSHOT = "detective-v070-public-demo-home.png";
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int WORLD_READINESS_POLLS_BEFORE_FALLBACK = 12;
    private static boolean registered;
    private static String publicDemoRoute = "";
    private static boolean worldOpenRequested;
    private static int worldReadinessPolls;
    private static long worldReadyNanos;

    private UiValidationPlan() {}

    public static void registerIfRequested() {
        String requested = System.getProperty("detective.validation.autorun", "").trim();
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI route property='{}'", requested);
        boolean publicDemoRequested = isPublicDemoRoute(requested);
        if (!("ui".equals(requested) || publicDemoRequested) || registered) {
            return;
        }
        registered = true;
        publicDemoRoute = publicDemoRequested ? requested : "";
        if (publicDemoRequested) {
            schedule(() -> start(false), 3_000L);
            DetectiveTestCulprit.LOGGER.info(
                    "[Detective Validation] Public screenshot demo scheduled without runtime incident data");
            return;
        }
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
        if (!publicDemoRoute.isEmpty()) {
            beginPublicDemo();
            return true;
        }
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

    private static boolean isPublicDemoRoute(String route) {
        return PUBLIC_DEMO_PRIMARY_ROUTE.equals(route)
                || PUBLIC_DEMO_AMBIGUOUS_ROUTE.equals(route)
                || PUBLIC_DEMO_BLACK_BOX_ROUTE.equals(route)
                || PUBLIC_DEMO_CHANGES_ROUTE.equals(route)
                || PUBLIC_DEMO_SUPPORT_REPORT_ROUTE.equals(route)
                || PUBLIC_DEMO_HOME_ROUTE.equals(route)
                || isIncidentInvestigationRoute(route)
                || isCaseFileRoute(route);
    }

    private static boolean isIncidentInvestigationRoute(String route) {
        return INCIDENT_SEARCH_ROUTE.equals(route)
                || INCIDENT_SEARCH_EMPTY_ROUTE.equals(route)
                || INCIDENT_FILTERS_ROUTE.equals(route)
                || INCIDENT_COMPARE_SELECT_ROUTE.equals(route)
                || INCIDENT_COMPARISON_HIGH_ROUTE.equals(route)
                || INCIDENT_COMPARISON_LOW_ROUTE.equals(route)
                || INCIDENT_COMPARISON_LEGACY_ROUTE.equals(route)
                || INCIDENT_COMPARISON_INSUFFICIENT_ROUTE.equals(route);
    }

    private static boolean isCaseFileRoute(String route) {
        return CASE_FILES_EMPTY_ROUTE.equals(route)
                || CASE_FILES_ONE_ROUTE.equals(route)
                || CASE_FILES_MULTIPLE_ROUTE.equals(route)
                || CASE_FILE_DETAIL_ROUTE.equals(route)
                || CASE_FILE_MIXED_ROUTE.equals(route)
                || CASE_FILES_LEGACY_ROUTE.equals(route)
                || CASE_FILES_HOME_ROUTE.equals(route);
    }

    private static void beginPublicDemo() {
        if (isIncidentInvestigationRoute(publicDemoRoute)) {
            beginIncidentInvestigationDemo();
            return;
        }
        if (isCaseFileRoute(publicDemoRoute)) {
            beginCaseFileDemo();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean ambiguous = PUBLIC_DEMO_AMBIGUOUS_ROUTE.equals(publicDemoRoute);
        boolean blackBox = PUBLIC_DEMO_BLACK_BOX_ROUTE.equals(publicDemoRoute);
        boolean changes = PUBLIC_DEMO_CHANGES_ROUTE.equals(publicDemoRoute);
        boolean supportReport = PUBLIC_DEMO_SUPPORT_REPORT_ROUTE.equals(publicDemoRoute);
        boolean home = PUBLIC_DEMO_HOME_ROUTE.equals(publicDemoRoute);
        IncidentDetailViewModel demoDetail = changes || supportReport || home
                ? null
                : blackBox
                ? publicDemoBlackBoxDetail()
                : ambiguous ? publicDemoAmbiguousDetail() : publicDemoDetail();
        String screenshotName = home
                ? PUBLIC_DEMO_HOME_SCREENSHOT
                : supportReport
                ? PUBLIC_DEMO_SUPPORT_REPORT_SCREENSHOT
                : changes
                ? PUBLIC_DEMO_CHANGES_SCREENSHOT
                : blackBox
                ? PUBLIC_DEMO_BLACK_BOX_SCREENSHOT
                : ambiguous ? PUBLIC_DEMO_AMBIGUOUS_SCREENSHOT : PUBLIC_DEMO_PRIMARY_SCREENSHOT;
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        minecraft.getLanguageManager().setSelected("en_us");
        minecraft.reloadResourcePacks().whenComplete((ignored, error) -> {
            if (error != null) {
                DetectiveTestCulprit.LOGGER.error(
                        "[Detective Validation] Could not load public demo resources", error);
                RUNNING.set(false);
                return;
            }
            if (home) {
                schedule(() -> Minecraft.getInstance().setScreen(new DetectiveHomeScreen(
                        Minecraft.getInstance().screen,
                        IncidentIndexViewModel.empty(
                                System.currentTimeMillis(), System.currentTimeMillis() - 60_000L))), 500L);
            } else if (supportReport) {
                schedule(() -> Minecraft.getInstance().setScreen(new SupportReportCreatedScreen(
                        Minecraft.getInstance().screen,
                        Path.of("detective-report-2026-08-12_14-35-00.zip"))), 500L);
            } else if (changes) {
                schedule(() -> Minecraft.getInstance().setScreen(new ModpackChangesScreen(
                        Minecraft.getInstance().screen, publicDemoChanges())), 500L);
            } else {
                schedule(() -> Minecraft.getInstance().setScreen(new IncidentDetailScreen(
                        Minecraft.getInstance().screen, demoDetail)), 500L);
            }
            if (ambiguous) {
                schedule(() -> scrollDetail(-4.5), 1_300L);
            } else if (blackBox) {
                schedule(() -> scrollDetail(-13.5), 1_300L);
            }
            schedule(() -> screenshot(screenshotName), 2_500L);
            schedule(UiValidationPlan::complete, 3_500L);
        });
    }

    private static void beginIncidentInvestigationDemo() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        minecraft.getLanguageManager().setSelected("en_us");
        minecraft.reloadResourcePacks().whenComplete((ignored, error) -> {
            if (error != null) {
                DetectiveTestCulprit.LOGGER.error(
                        "[Detective Validation] Could not load investigation demo resources", error);
                RUNNING.set(false);
                return;
            }
            String screenshotName = "detective-v090-" + publicDemoRoute + ".png";
            schedule(() -> {
                Screen parent = Minecraft.getInstance().screen;
                switch (publicDemoRoute) {
                    case INCIDENT_SEARCH_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentListScreen(parent,
                                    IncidentInvestigationValidationFixtures.searchResults()));
                    case INCIDENT_SEARCH_EMPTY_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentListScreen(parent,
                                    IncidentInvestigationValidationFixtures.emptySearchResults()));
                    case INCIDENT_FILTERS_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentListScreen(parent,
                                    IncidentInvestigationValidationFixtures.activeFilters()));
                    case INCIDENT_COMPARE_SELECT_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentListScreen(parent,
                                    IncidentInvestigationValidationFixtures.comparisonSelection()));
                    case INCIDENT_COMPARISON_HIGH_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentComparisonScreen(parent,
                                    IncidentInvestigationValidationFixtures.highComparison()));
                    case INCIDENT_COMPARISON_LOW_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentComparisonScreen(parent,
                                    IncidentInvestigationValidationFixtures.lowComparison()));
                    case INCIDENT_COMPARISON_LEGACY_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentComparisonScreen(parent,
                                    IncidentInvestigationValidationFixtures.legacyComparison()));
                    case INCIDENT_COMPARISON_INSUFFICIENT_ROUTE -> Minecraft.getInstance().setScreen(
                            new IncidentComparisonScreen(parent,
                                    IncidentInvestigationValidationFixtures.insufficientComparison()));
                    default -> throw new IllegalStateException(
                            "Unknown Incident Investigation route: " + publicDemoRoute);
                }
            }, 500L);
            if (INCIDENT_COMPARISON_HIGH_ROUTE.equals(publicDemoRoute)) {
                schedule(() -> scrollCurrentScreen(-3.0), 1_500L);
                schedule(() -> screenshot(screenshotName), 2_500L);
                schedule(() -> scrollCurrentScreen(-6.0), 2_800L);
                schedule(() -> screenshot("detective-v090-incident-comparison-high-b.png"), 3_800L);
                schedule(UiValidationPlan::complete, 4_800L);
                return;
            }
            if (INCIDENT_COMPARISON_LEGACY_ROUTE.equals(publicDemoRoute)) {
                schedule(() -> scrollCurrentScreen(-8.0), 1_500L);
            }
            schedule(() -> screenshot(screenshotName), 2_500L);
            schedule(UiValidationPlan::complete, 3_500L);
        });
    }

    private static void beginCaseFileDemo() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.guiScale().set(2);
        minecraft.resizeDisplay();
        minecraft.getLanguageManager().setSelected("en_us");
        minecraft.reloadResourcePacks().whenComplete((ignored, error) -> {
            if (error != null) {
                DetectiveTestCulprit.LOGGER.error(
                        "[Detective Validation] Could not load Case Files demo resources", error);
                RUNNING.set(false);
                return;
            }
            String screenshotName = "detective-v080-" + publicDemoRoute + ".png";
            schedule(() -> {
                Screen parent = Minecraft.getInstance().screen;
                switch (publicDemoRoute) {
                    case CASE_FILES_EMPTY_ROUTE -> Minecraft.getInstance().setScreen(
                            new CaseFileListScreen(parent, CaseFileValidationFixtures.emptyCaseFiles()));
                    case CASE_FILES_ONE_ROUTE -> Minecraft.getInstance().setScreen(
                            new CaseFileListScreen(parent, CaseFileValidationFixtures.oneHighConsistencyCase()));
                    case CASE_FILES_MULTIPLE_ROUTE -> Minecraft.getInstance().setScreen(
                            new CaseFileListScreen(parent, CaseFileValidationFixtures.multipleCases()));
                    case CASE_FILE_DETAIL_ROUTE -> Minecraft.getInstance().setScreen(
                            new CaseFileDetailScreen(parent,
                                    CaseFileValidationFixtures.caseDetailWithSeveralRelatedIncidents()));
                    case CASE_FILE_MIXED_ROUTE -> Minecraft.getInstance().setScreen(
                            new CaseFileDetailScreen(parent,
                                    CaseFileValidationFixtures.mixedAttributionStates()));
                    case CASE_FILES_LEGACY_ROUTE -> Minecraft.getInstance().setScreen(
                            new CaseFileListScreen(parent, CaseFileValidationFixtures.legacySparseHistory()));
                    case CASE_FILES_HOME_ROUTE -> Minecraft.getInstance().setScreen(
                            new DetectiveHomeScreen(parent,
                                    IncidentIndexViewModel.empty(
                                            System.currentTimeMillis(), System.currentTimeMillis() - 60_000L),
                                    CaseFileValidationFixtures.oneHighConsistencyCase()));
                    default -> throw new IllegalStateException("Unknown Case Files route: " + publicDemoRoute);
                }
            }, 500L);
            schedule(() -> screenshot(screenshotName), 2_500L);
            schedule(UiValidationPlan::complete, 3_500L);
        });
    }

    private static void scheduleScreens(IncidentIndexViewModel index, boolean worldAvailable) {
        List<IncidentDetailViewModel> states = List.of(
                syntheticDetail(EvidenceBadge.HIGH_EVIDENCE, false),
                syntheticDetail(EvidenceBadge.MODERATE_EVIDENCE, false),
                syntheticDetail(EvidenceBadge.LOW_EVIDENCE, false),
                syntheticDetail(EvidenceBadge.AMBIGUOUS_ATTRIBUTION, false),
                syntheticDetail(EvidenceBadge.INSUFFICIENT_EVIDENCE, false),
                syntheticDetail(EvidenceBadge.NATIVE_OR_DRIVER_STALL_POSSIBLE, false),
                syntheticDetail(EvidenceBadge.UNKNOWN, false));
        IncidentDetailViewModel partialBlackBox = syntheticDetail(EvidenceBadge.HIGH_EVIDENCE, true);
        long now = System.currentTimeMillis();
        IncidentIndexViewModel visualIndex = IncidentIndexViewModel.create(
                states.stream().map(IncidentDetailViewModel::summary).toList(),
                now, now - 60_000L, 0);
        IncidentIndexViewModel emptyIndex = IncidentIndexViewModel.empty(now, now - 60_000L);

        schedule(() -> Minecraft.getInstance().setScreen(
                worldAvailable ? new PauseScreen(true) : new TitleScreen()), 0L);
        schedule(() -> screenshot("detective-v041-01-pause-entry.png"), 1_500L);

        schedule(() -> {
            Screen parent = Minecraft.getInstance().screen;
            Minecraft.getInstance().setScreen(new DetectiveHomeScreen(parent, visualIndex));
        }, 2_500L);
        schedule(() -> screenshot("detective-v041-02-home-with-incidents.png"), 4_000L);

        schedule(() -> scrollHome(-6.0), 4_300L);
        schedule(() -> screenshot("detective-v041-02b-home-session-summary.png"), 5_500L);

        schedule(() -> Minecraft.getInstance().setScreen(new DetectiveHomeScreen(
                Minecraft.getInstance().screen, emptyIndex)), 6_200L);
        schedule(() -> screenshot("detective-v041-03-home-empty.png"), 7_600L);

        schedule(() -> Minecraft.getInstance().setScreen(new IncidentListScreen(
                Minecraft.getInstance().screen, visualIndex)), 8_400L);
        schedule(() -> screenshot("detective-v041-04-incidents-multiple.png"), 9_800L);

        showDetail(states.get(0), "detective-v041-05-high-evidence.png", 10_600L, 0.0);
        schedule(() -> scrollDetail(-4.5), 12_300L);
        schedule(() -> screenshot("detective-v041-05b-primary-why.png"), 13_500L);
        schedule(() -> scrollDetail(-3.5), 13_600L);
        schedule(() -> screenshot("detective-v041-05c-primary-caution.png"), 13_750L);

        schedule(() -> Minecraft.getInstance().setScreen(new IncidentDetailScreen(
                Minecraft.getInstance().screen, states.get(0))), 13_800L);
        schedule(() -> scrollDetail(-15.0), 14_700L);
        schedule(() -> screenshot("detective-v041-06-black-box-complete.png"), 15_800L);
        schedule(() -> scrollDetail(-6.5), 16_100L);
        schedule(() -> screenshot("detective-v041-06b-black-box-metadata.png"), 17_300L);

        schedule(() -> Minecraft.getInstance().setScreen(new IncidentDetailScreen(
                Minecraft.getInstance().screen, states.get(0))), 17_600L);
        schedule(() -> scrollDetail(-25.4), 18_500L);
        schedule(() -> screenshot("detective-v041-07-technical-evidence.png"), 19_600L);
        schedule(() -> scrollDetail(-3.0), 19_900L);
        schedule(() -> screenshot("detective-v041-07b-technical-evidence-bottom.png"), 21_000L);

        showDetail(states.get(1), "detective-v041-08-moderate-evidence.png", 21_500L, -4.5);
        showDetail(states.get(2), "detective-v041-09-low-evidence.png", 23_500L, -4.5);
        showDetail(states.get(3), "detective-v041-10-ambiguous.png", 25_500L, -4.5);
        showDetail(states.get(4), "detective-v041-11-insufficient.png", 27_500L, -4.5);
        showDetail(states.get(5), "detective-v041-12-system-stall.png", 29_500L, -4.5);
        showDetail(states.get(6), "detective-v041-13-unknown.png", 31_500L, -4.5);

        schedule(() -> Minecraft.getInstance().setScreen(new IncidentDetailScreen(
                Minecraft.getInstance().screen, partialBlackBox)), 33_500L);
        schedule(() -> scrollDetail(-15.0), 34_400L);
        schedule(() -> screenshot("detective-v041-14-black-box-partial.png"), 35_500L);
        schedule(() -> scrollDetail(-6.5), 35_800L);
        schedule(() -> screenshot("detective-v041-15-black-box-partial-message.png"), 37_000L);

        ModpackChangesViewModel changedPack = syntheticModpackChanges();
        schedule(() -> Minecraft.getInstance().setScreen(new ModpackChangesScreen(
                Minecraft.getInstance().screen, changedPack)), 37_500L);
        schedule(() -> screenshot("detective-v041-16-modpack-changes.png"), 38_900L);
        schedule(() -> scrollCurrentScreen(-5.0), 39_200L);
        schedule(() -> screenshot("detective-v041-16b-modpack-removed.png"), 40_400L);

        schedule(() -> Minecraft.getInstance().setScreen(new ModpackChangesScreen(
                Minecraft.getInstance().screen,
                new ModpackChangesViewModel(true, 42, List.of()))), 41_000L);
        schedule(() -> screenshot("detective-v041-17-modpack-empty.png"), 42_400L);

        schedule(() -> Minecraft.getInstance().setScreen(new ModpackChangesScreen(
                Minecraft.getInstance().screen,
                new ModpackChangesViewModel(false, 42, List.of()))), 43_000L);
        schedule(() -> screenshot("detective-v041-18-modpack-no-snapshot.png"), 44_400L);

        schedule(() -> Minecraft.getInstance().setScreen(new IncidentListScreen(
                Minecraft.getInstance().screen, emptyIndex)), 45_000L);
        schedule(() -> screenshot("detective-v041-19-incidents-empty.png"), 46_400L);

        schedule(() -> Minecraft.getInstance().setScreen(new ExportSupportReportScreen(
                Minecraft.getInstance().screen, states.get(0).summary())), 47_000L);
        schedule(() -> screenshot("detective-v050-20-export-preview.png"), 48_400L);

        schedule(() -> Minecraft.getInstance().setScreen(new SupportReportCreatedScreen(
                Minecraft.getInstance().screen,
                ModDetectivePaths.reports().resolve("detective-report-2026-08-11_21-42-16.zip"))), 49_000L);
        schedule(() -> screenshot("detective-v050-21-export-success.png"), 50_400L);

        schedule(() -> Minecraft.getInstance().setScreen(new DetectiveSettingsScreen(
                Minecraft.getInstance().screen, DetectiveSettings.defaults())), 51_000L);
        schedule(() -> screenshot("detective-v050-22-settings.png"), 52_400L);

        schedule(() -> {
            if (Minecraft.getInstance().screen instanceof DetectiveSettingsScreen settingsScreen) {
                Minecraft.getInstance().setScreen(new ClearIncidentHistoryScreen(settingsScreen));
            }
        }, 53_000L);
        schedule(() -> screenshot("detective-v050-23-clear-history.png"), 54_400L);

        schedule(() -> createRuntimeValidationReport(states.get(0)), 54_600L);
        schedule(() -> showNotificationBurst(states.get(0)), 55_000L);
        schedule(() -> Minecraft.getInstance().setScreen(
                worldAvailable ? new PauseScreen(true) : new TitleScreen()), 57_400L);
        schedule(() -> screenshot("detective-v050-24-notification-cooldown.png"), 58_200L);

        schedule(() -> beginFrenchValidation(states.get(3)), 59_000L);
    }

    private static void showDetail(
            IncidentDetailViewModel detail,
            String screenshot,
            long startMs,
            double scrollAmount
    ) {
        schedule(() -> Minecraft.getInstance().setScreen(new IncidentDetailScreen(
                Minecraft.getInstance().screen, detail)), startMs);
        if (scrollAmount != 0.0) {
            schedule(() -> scrollDetail(scrollAmount), startMs + 800L);
        }
        schedule(() -> screenshot(screenshot), startMs + 1_400L);
    }

    private static void scrollHome(double amount) {
        if (Minecraft.getInstance().screen instanceof DetectiveHomeScreen homeScreen) {
            homeScreen.mouseScrolled(0.0, 0.0, 0.0, amount);
        }
    }

    private static void scrollCurrentScreen(double amount) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, amount);
        }
    }

    private static void scrollDetail(double amount) {
        if (Minecraft.getInstance().screen instanceof IncidentDetailScreen detailScreen) {
            detailScreen.mouseScrolled(0.0, 0.0, 0.0, amount);
        }
    }

    private static void beginFrenchValidation(IncidentDetailViewModel ambiguous) {
        Minecraft minecraft = Minecraft.getInstance();
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] Switching visual validation to fr_fr");
        minecraft.getLanguageManager().setSelected("fr_fr");
        minecraft.reloadResourcePacks().whenComplete((ignored, error) -> {
            if (error != null) {
                DetectiveTestCulprit.LOGGER.error(
                        "[Detective Validation] Could not reload fr_fr resources", error);
            }
            schedule(() -> Minecraft.getInstance().setScreen(new IncidentDetailScreen(
                    Minecraft.getInstance().screen, ambiguous)), 1_000L);
            schedule(() -> scrollDetail(-4.5), 2_000L);
            schedule(() -> screenshot("detective-v041-20-fr-ambiguous.png"), 2_800L);
            schedule(() -> Minecraft.getInstance().setScreen(new ModpackChangesScreen(
                    Minecraft.getInstance().screen,
                    new ModpackChangesViewModel(false, 42, List.of()))), 3_600L);
            schedule(() -> screenshot("detective-v041-21-fr-no-snapshot.png"), 5_200L);
            schedule(() -> Minecraft.getInstance().setScreen(new DetectiveSettingsScreen(
                    Minecraft.getInstance().screen, DetectiveSettings.defaults())), 5_800L);
            schedule(() -> screenshot("detective-v050-25-fr-settings.png"), 7_000L);
            schedule(() -> Minecraft.getInstance().setScreen(new ExportSupportReportScreen(
                    Minecraft.getInstance().screen, ambiguous.summary())), 7_600L);
            schedule(() -> screenshot("detective-v050-26-fr-export-preview.png"), 8_800L);
            schedule(() -> Minecraft.getInstance().setScreen(new TitleScreen()), 9_400L);
            schedule(() -> screenshot("detective-v041-22-title-entry.png"), 10_800L);
            schedule(UiValidationPlan::complete, 11_800L);
        });
    }

    private static void complete() {
        RUNNING.set(false);
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] UI screenshot route completed");
        if (Boolean.getBoolean("detective.validation.exitAfterAutorun")) {
            schedule(() -> {
                DetectiveTestCulprit.LOGGER.info(
                        "[Detective Validation] UI route complete; stopping Minecraft cleanly");
                Minecraft.getInstance().stop();
            }, 1_500L);
        }
    }

    private static IncidentDetailViewModel syntheticDetail(EvidenceBadge evidence, boolean partialBlackBox) {
        int samples = 30;
        int leaf = switch (evidence) {
            case HIGH_EVIDENCE -> 29;
            case MODERATE_EVIDENCE -> 18;
            case LOW_EVIDENCE -> 4;
            case AMBIGUOUS_ATTRIBUTION -> 15;
            default -> 0;
        };
        boolean attributed = evidence.isAttributedTier();
        List<SuspectViewModel> suspects = new ArrayList<>();
        if (attributed || evidence == EvidenceBadge.AMBIGUOUS_ATTRIBUTION) {
            suspects.add(suspect("create", "Create", leaf, samples));
            suspects.add(suspect("example_library", "Example Library", evidence == EvidenceBadge.AMBIGUOUS_ATTRIBUTION
                    ? leaf : Math.max(1, leaf / 3), samples));
        }
        String rawState = switch (evidence) {
            case HIGH_EVIDENCE, MODERATE_EVIDENCE, LOW_EVIDENCE -> "ATTRIBUTED";
            case AMBIGUOUS_ATTRIBUTION -> "AMBIGUOUS_ATTRIBUTION";
            case INSUFFICIENT_EVIDENCE -> "INSUFFICIENT_EVIDENCE";
            case JVM_GC_SUSPECTED -> "JVM_GC_SUSPECTED";
            case NATIVE_OR_DRIVER_STALL_POSSIBLE -> "NATIVE_OR_DRIVER_STALL_POSSIBLE";
            case UNKNOWN -> "UNKNOWN";
        };
        long now = System.currentTimeMillis();
        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                "#0042", Path.of("visual-" + evidence.name() + ".json"), now,
                621.0, 120.0, samples, evidence, rawState,
                attributed ? "Create" : "", attributed,
                "2026-08-11 14:32:08", "Overworld", "128, 64, -342");
        List<BlackBoxPoint> blackBox = partialBlackBox
                ? List.of(new BlackBoxPoint(now, 621.0, 1.6, 900L * 1024L * 1024L))
                : syntheticBlackBox(now);
        return new IncidentDetailViewModel(summary, suspects, blackBox,
                blackBox.size(), partialBlackBox);
    }

    private static IncidentDetailViewModel publicDemoDetail() {
        int samples = 30;
        long now = System.currentTimeMillis();
        SuspectViewModel exampleMod = new SuspectViewModel(
                "example_mod", "Example Mod", "1.0.0", samples, 100.0, 29, 29.0 * 100.0 / samples,
                1.2, 1, 28, 0, 3);
        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                "#DEMO", Path.of("public-screenshot-demo.json"), now,
                621.0, 120.0, samples, EvidenceBadge.HIGH_EVIDENCE, "ATTRIBUTED",
                "Example Mod", true,
                "2026-08-12 14:32:08", "Overworld", "128, 64, -342");
        List<BlackBoxPoint> blackBox = syntheticBlackBox(now);
        return new IncidentDetailViewModel(
                summary, List.of(exampleMod), blackBox, blackBox.size(), false,
                "minecraft:overworld", 128, 64, -342);
    }

    private static IncidentDetailViewModel publicDemoAmbiguousDetail() {
        int samples = 30;
        long now = System.currentTimeMillis();
        SuspectViewModel exampleModA = new SuspectViewModel(
                "example_mod_a", "Example Mod A", "1.0.0", 29, 29.0 * 100.0 / samples,
                14, 14.0 * 100.0 / samples, 1.4, 1, 13, 1, 3);
        SuspectViewModel exampleModB = new SuspectViewModel(
                "example_mod_b", "Example Mod B", "1.0.0", 28, 28.0 * 100.0 / samples,
                13, 13.0 * 100.0 / samples, 1.5, 1, 12, 2, 3);
        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                "#DEMO", Path.of("public-screenshot-ambiguous-demo.json"), now,
                478.0, 120.0, samples, EvidenceBadge.AMBIGUOUS_ATTRIBUTION,
                "AMBIGUOUS_ATTRIBUTION", "", false,
                "2026-08-12 14:36:42", "Overworld", "128, 64, -342");
        List<BlackBoxPoint> blackBox = syntheticBlackBox(now);
        return new IncidentDetailViewModel(
                summary, List.of(exampleModA, exampleModB), blackBox, blackBox.size(), false,
                "minecraft:overworld", 128, 64, -342);
    }

    private static IncidentDetailViewModel publicDemoBlackBoxDetail() {
        int samples = 30;
        long now = System.currentTimeMillis();
        SuspectViewModel exampleMod = new SuspectViewModel(
                "example_mod", "Example Mod", "1.0.0", samples, 100.0, 29, 29.0 * 100.0 / samples,
                1.2, 1, 28, 0, 3);
        IncidentSummaryViewModel summary = new IncidentSummaryViewModel(
                "#DEMO", Path.of("public-screenshot-blackbox-demo.json"), now,
                621.0, 120.0, samples, EvidenceBadge.HIGH_EVIDENCE, "ATTRIBUTED",
                "Example Mod", true,
                "2026-08-12 14:42:18", "Overworld", "128, 64, -342");
        List<BlackBoxPoint> blackBox = publicDemoBlackBox(now);
        return new IncidentDetailViewModel(
                summary, List.of(exampleMod), blackBox, blackBox.size(), false,
                "minecraft:overworld", 128, 64, -342);
    }

    private static List<BlackBoxPoint> publicDemoBlackBox(long incidentEpochMs) {
        int pointCount = 181;
        int incidentIndex = pointCount / 2;
        long startEpochMs = incidentEpochMs - 15_000L;
        List<BlackBoxPoint> points = new ArrayList<>(pointCount);
        for (int index = 0; index < pointCount; index++) {
            double frameMs;
            if (index == incidentIndex) {
                frameMs = 621.0;
            } else if (index == 38 || index == 67 || index == 121 || index == 154) {
                frameMs = 26.0 + index % 10;
            } else if (Math.abs(index - incidentIndex) == 1) {
                frameMs = 31.0;
            } else if (Math.abs(index - incidentIndex) == 2) {
                frameMs = 23.0;
            } else {
                frameMs = 12.0 + (index % 8) * 0.8;
            }
            points.add(new BlackBoxPoint(
                    startEpochMs + index * 167L,
                    frameMs,
                    1_000.0 / frameMs,
                    (860L + index / 12L) * 1024L * 1024L));
        }
        return List.copyOf(points);
    }

    private static ModpackChangesViewModel publicDemoChanges() {
        return new ModpackChangesViewModel(true, 42, List.of(
                new ModpackChangesViewModel.Change(
                        ModpackChangesViewModel.Type.ADDED,
                        "example_machines", "Example Machines", "", "1.0.0"),
                new ModpackChangesViewModel.Change(
                        ModpackChangesViewModel.Type.ADDED,
                        "example_worldgen", "Example Worldgen", "", "2.1.0"),
                new ModpackChangesViewModel.Change(
                        ModpackChangesViewModel.Type.UPDATED,
                        "example_storage", "Example Storage", "1.4.2", "1.5.0"),
                new ModpackChangesViewModel.Change(
                        ModpackChangesViewModel.Type.REMOVED,
                        "example_decoration", "Example Decoration", "0.9.4", "")));
    }

    private static SuspectViewModel suspect(String modId, String name, int leaf, int samples) {
        double share = samples == 0 ? 0.0 : leaf * 100.0 / samples;
        return new SuspectViewModel(
                modId, name, "1.0", Math.max(leaf, 1), share, leaf, share,
                2.0, 2, Math.max(0, leaf - 1), 0, 2);
    }

    private static List<BlackBoxPoint> syntheticBlackBox(long now) {
        List<BlackBoxPoint> points = new ArrayList<>();
        for (int index = 0; index < 48; index++) {
            double frameMs = index == 37 ? 621.0 : 13.0 + (index % 5);
            points.add(new BlackBoxPoint(now - (48L - index) * 50L, frameMs,
                    1_000.0 / frameMs, (820L + index) * 1024L * 1024L));
        }
        return List.copyOf(points);
    }

    private static ModpackChangesViewModel syntheticModpackChanges() {
        return new ModpackChangesViewModel(true, 42, List.of(
                new ModpackChangesViewModel.Change(ModpackChangesViewModel.Type.ADDED,
                        "new_content", "New Content", "", "2.0.0"),
                new ModpackChangesViewModel.Change(ModpackChangesViewModel.Type.UPDATED,
                        "create", "Create", "6.0.0", "6.0.2"),
                new ModpackChangesViewModel.Change(ModpackChangesViewModel.Type.REMOVED,
                        "old_qol", "Old QoL", "1.4.1", "")));
    }

    private static void createRuntimeValidationReport(IncidentDetailViewModel incident) {
        try {
            SupportReportData data = new SupportReportData(
                    "0.6.0-alpha.1",
                    "1.21.1",
                    "21.1.235",
                    incident,
                    List.of(new SupportReportData.InstalledMod(
                            "detective", "Detective", "0.6.0-alpha.1", "detective.jar")),
                    syntheticModpackChanges(),
                    DetectiveSettings.defaults(),
                    SupportReportData.Environment.capture());
            Path report = SupportReportExporter.export(data, ModDetectivePaths.reports());
            boolean noLog = true;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(report.toFile())) {
                noLog = zip.stream().noneMatch(entry -> entry.getName().endsWith("latest.log"));
            }
            DetectiveTestCulprit.LOGGER.info(
                    "[Detective Validation] SUPPORT_REPORT result={} file={} size={} latestLogIncluded={}",
                    noLog ? "PASS" : "FAIL", report.getFileName(), Files.size(report), !noLog);
        } catch (Exception error) {
            DetectiveTestCulprit.LOGGER.error("[Detective Validation] SUPPORT_REPORT result=FAIL", error);
        }
    }

    private static void showNotificationBurst(IncidentDetailViewModel detail) {
        DetectiveSupportService.updateSettings(settings -> settings.withIncidentNotifications(true))
                .whenComplete((settings, error) -> {
                    if (error != null) {
                        DetectiveTestCulprit.LOGGER.error(
                                "[Detective Validation] Notification settings preparation failed", error);
                        return;
                    }
                    FreezeIncident incident = notificationIncident(detail);
                    for (int index = 0; index < 4; index++) {
                        DetectiveSupportService.onIncidentRecorded(incident,
                                ModDetectivePaths.incidents().resolve("validation-notification-" + index + ".json"));
                    }
                    DetectiveTestCulprit.LOGGER.info(
                            "[Detective Validation] NOTIFICATION_BURST submitted=4; cooldown expectedVisible=1");
                });
    }

    private static FreezeIncident notificationIncident(IncidentDetailViewModel detail) {
        SuspectViewModel view = detail.suspects().getFirst();
        SuspectAnalyzer.Suspect suspect = new SuspectAnalyzer.Suspect(
                view.modId(), view.modName(), view.version(), view.presenceSamples(),
                view.presenceSharePercent(), view.leafOwnershipCount(), view.leafOwnershipSharePercent(),
                view.averageFirstFrameDepth(), view.minimumFirstFrameDepth(), view.repeatedLeafOwnership(),
                view.callerOnlySamples(), view.stackDiversity());
        FrameSample frame = new FrameSample(
                System.currentTimeMillis(), System.nanoTime(), detail.summary().durationMs(), 1.6,
                900L * 1024L * 1024L, 2L * 1024L * 1024L * 1024L,
                detail.dimensionId(), 128, 64, -342);
        return new FreezeIncident(
                frame.epochMs(), frame.frameMs(), detail.summary().thresholdMs(), frame,
                detail.summary().watchdogSamples(),
                new AttributionEvidence(AttributionEvidence.State.ATTRIBUTED,
                        detail.summary().watchdogSamples(), view.presenceSamples(), 0, 0),
                List.of(suspect), List.of(), List.of(frame));
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
