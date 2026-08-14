package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvidenceViewModel;
import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionLoadState;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvolutionViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseFileViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseOwnerViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.HistoryCoverageViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.NearbyChangeViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.RelatedIncidentViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import fr.apocalypsebleu.moddetective.client.ui.model.UiTextFitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CaseFileDetailScreen extends Screen {
    private static final int CONTENT_TOP = DetectiveUiRenderer.HEADER_HEIGHT + 4;

    private final Screen parent;
    private final CaseFileViewModel caseFile;
    private final CaseEvolutionLoadState evolutionState;
    private final List<RelatedHit> relatedHits = new ArrayList<>();
    private final DetectiveScrollState scrollState = new DetectiveScrollState();
    private Button modpackChangesButton;
    private int contentHeight;

    public CaseFileDetailScreen(Screen parent, CaseFileViewModel caseFile) {
        this(parent, caseFile, null);
    }

    /** Allows development-only validation routes to render deterministic evolution states. */
    public CaseFileDetailScreen(
            Screen parent,
            CaseFileViewModel caseFile,
            CaseEvolutionViewModel preloadedEvolution
    ) {
        super(Component.translatable("detective.ui.case.title"));
        this.parent = parent;
        this.caseFile = java.util.Objects.requireNonNull(caseFile, "caseFile");
        if (preloadedEvolution != null
                && !caseFile.caseId().equals(preloadedEvolution.caseId())) {
            throw new IllegalArgumentException("Preloaded evolution belongs to another Case");
        }
        this.evolutionState = preloadedEvolution == null
                ? new CaseEvolutionLoadState()
                : CaseEvolutionLoadState.preloaded(preloadedEvolution);
    }

    @Override
    protected void init() {
        scrollState.cancelDrag();
        int availableWidth = Math.max(180, this.width - 28);
        int backWidth = Math.min(100, Math.max(72, availableWidth / 3));
        int changesWidth = Math.min(170, availableWidth - backWidth - 4);
        int totalWidth = changesWidth + backWidth + 4;
        int buttonLeft = (this.width - totalWidth) / 2;
        modpackChangesButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.case.evolution.view_modpack_changes"),
                        button -> this.minecraft.setScreen(new ModpackChangesScreen(this)))
                .bounds(buttonLeft, this.height - 28, changesWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(buttonLeft + changesWidth + 4, this.height - 28, backWidth, 20)
                .build());
        updateModpackChangesButton();
        if (evolutionState.status() == CaseEvolutionLoadState.Status.IDLE) {
            requestEvolution();
        }
    }

    private void requestEvolution() {
        String caseId = caseFile.caseId();
        long requestGeneration = evolutionState.begin(caseId);
        updateModpackChangesButton();
        var client = this.minecraft;
        DetectiveUiService.caseEvolutionViewModel(caseId)
                .whenComplete((loaded, error) -> client.execute(() -> {
                    boolean accepted = error == null
                            ? evolutionState.complete(requestGeneration, caseId, loaded)
                            : evolutionState.fail(requestGeneration, caseId);
                    if (accepted) {
                        updateModpackChangesButton();
                    }
                }));
    }

    private void updateModpackChangesButton() {
        if (modpackChangesButton == null) {
            return;
        }
        CaseEvolutionViewModel evolution = evolutionState.value();
        modpackChangesButton.active = evolutionState.status() == CaseEvolutionLoadState.Status.LOADED
                && evolution != null
                && evolution.canViewModpackChanges();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.case.title"),
                Component.translatable("detective.ui.case.subtitle", caseFile.shortCaseId()));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);

        int viewportBottom = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 2;
        int viewportHeight = Math.max(1, viewportBottom - CONTENT_TOP);
        int width = Math.min(560, this.width - 24);
        int left = (this.width - width) / 2;
        int scrollbarX = Math.min(this.width - 6, left + width + 4);
        scrollState.updateLayout(contentHeight, CONTENT_TOP, viewportHeight, scrollbarX, 4);
        int y = CONTENT_TOP - scrollState.roundedOffset();
        int startY = y;
        relatedHits.clear();
        graphics.enableScissor(0, CONTENT_TOP, this.width, viewportBottom);
        y = renderOverview(graphics, left, y, width) + 8;
        y = renderSafety(graphics, left, y, width) + 8;
        y = renderEvolution(graphics, left, y, width) + 8;
        y = renderOwners(graphics, left, y, width) + 8;
        y = renderEvidence(graphics, left, y, width) + 8;
        y = renderRelatedIncidents(graphics, left, y, width, mouseX, mouseY);
        graphics.disableScissor();
        contentHeight = y - startY;
        scrollState.updateLayout(contentHeight, CONTENT_TOP, viewportHeight, scrollbarX, 4);
        DetectiveUiRenderer.scrollbar(graphics, scrollState);
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    private int renderOverview(GuiGraphics graphics, int x, int y, int width) {
        int height = 123;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        int textX = x + 10;
        graphics.drawString(this.font, Component.translatable("detective.ui.case.overview"),
                textX, y + 9, DetectiveUiRenderer.ACCENT, false);
        metric(graphics, textX, y + 25, "detective.ui.case.first_seen",
                UiFormatters.dateTime(caseFile.firstSeenEpochMs()));
        metric(graphics, textX, y + 41, "detective.ui.case.last_seen",
                UiFormatters.dateTime(caseFile.lastSeenEpochMs()));
        metric(graphics, textX, y + 57, "detective.ui.case.occurrences",
                Integer.toString(caseFile.occurrenceCount()));
        metric(graphics, textX, y + 73, "detective.ui.case.average_duration",
                UiFormatters.duration(caseFile.averageStallDurationMs()));
        metric(graphics, textX, y + 89, "detective.ui.case.longest_duration",
                UiFormatters.duration(caseFile.longestStallDurationMs()));
        graphics.drawString(this.font, Component.translatable("detective.ui.case.strength",
                        UiFormatters.percent(caseFile.consistencyPercent()),
                        UiFormatters.percent(caseFile.evidenceStrengthPercent())),
                textX, y + 105, DetectiveUiRenderer.TEXT, false);
        return y + height;
    }

    private int renderEvolution(GuiGraphics graphics, int x, int y, int width) {
        int height = evolutionHeight(width);
        int innerWidth = width - 20;
        int cursor = y + 9;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.case.evolution.title"),
                x + 10, cursor, DetectiveUiRenderer.ACCENT, false);
        cursor += 16;

        CaseEvolutionViewModel evolution = evolutionState.value();
        Component firstRecorded = Component.translatable(
                "detective.ui.case.evolution.first_recorded",
                evolution == null || evolution.firstRecordedOccurrenceEpochMs().isEmpty()
                        ? Component.translatable("detective.ui.case.evolution.unavailable")
                        : UiFormatters.dateTime(
                                evolution.firstRecordedOccurrenceEpochMs().getAsLong()));
        DetectiveUiRenderer.wrappedText(graphics, this.font, firstRecorded,
                x + 10, cursor, innerWidth, DetectiveUiRenderer.TEXT);
        cursor += DetectiveUiRenderer.wrappedHeight(this.font, firstRecorded, innerWidth) + 4;

        Component coverage = coverageLine(evolution);
        DetectiveUiRenderer.wrappedText(graphics, this.font, coverage,
                x + 10, cursor, innerWidth, DetectiveUiRenderer.MUTED);
        cursor += DetectiveUiRenderer.wrappedHeight(this.font, coverage, innerWidth) + 6;

        Component caution = Component.translatable("detective.ui.case.evolution.caution");
        DetectiveUiRenderer.wrappedText(graphics, this.font, caution,
                x + 10, cursor, innerWidth, DetectiveUiRenderer.MUTED);
        cursor += DetectiveUiRenderer.wrappedHeight(this.font, caution, innerWidth) + 10;

        graphics.drawString(this.font,
                Component.translatable("detective.ui.case.evolution.nearby_changes"),
                x + 10, cursor, DetectiveUiRenderer.ACCENT, false);
        cursor += 16;

        switch (evolutionState.status()) {
            case IDLE, LOADING -> renderEvolutionState(graphics, x + 10, cursor, innerWidth,
                    "detective.ui.case.evolution.loading");
            case FAILED -> renderEvolutionState(graphics, x + 10, cursor, innerWidth,
                    "detective.ui.case.evolution.load_failed");
            case LOADED -> {
                if (evolution == null
                        || evolution.historyAvailability()
                        == CaseEvolutionViewModel.HistoryAvailability.UNAVAILABLE) {
                    renderEvolutionState(graphics, x + 10, cursor, innerWidth,
                            "detective.ui.case.evolution.history_unavailable");
                } else if (evolution.nearbyChanges().isEmpty()) {
                    renderEvolutionState(graphics, x + 10, cursor, innerWidth,
                            "detective.ui.case.evolution.none");
                } else {
                    int cardWidth = width - 20;
                    for (NearbyChangeViewModel change : evolution.nearbyChanges()) {
                        cursor = renderNearbyChange(graphics, change, x + 10, cursor, cardWidth) + 6;
                    }
                    if (evolution.omittedNearbyChangeCount() > 0) {
                        Component omitted = Component.translatable(
                                "detective.ui.case.evolution.additional_changes",
                                evolution.omittedNearbyChangeCount());
                        DetectiveUiRenderer.wrappedText(graphics, this.font, omitted,
                                x + 10, cursor, innerWidth, DetectiveUiRenderer.MUTED);
                    }
                }
            }
        }
        return y + height;
    }

    private int evolutionHeight(int width) {
        int innerWidth = width - 20;
        CaseEvolutionViewModel evolution = evolutionState.value();
        Component firstRecorded = Component.translatable(
                "detective.ui.case.evolution.first_recorded",
                evolution == null || evolution.firstRecordedOccurrenceEpochMs().isEmpty()
                        ? Component.translatable("detective.ui.case.evolution.unavailable")
                        : UiFormatters.dateTime(
                                evolution.firstRecordedOccurrenceEpochMs().getAsLong()));
        Component coverage = coverageLine(evolution);
        Component caution = Component.translatable("detective.ui.case.evolution.caution");
        int height = 25
                + DetectiveUiRenderer.wrappedHeight(this.font, firstRecorded, innerWidth) + 4
                + DetectiveUiRenderer.wrappedHeight(this.font, coverage, innerWidth) + 6
                + DetectiveUiRenderer.wrappedHeight(this.font, caution, innerWidth) + 10
                + 16;
        if (evolutionState.status() != CaseEvolutionLoadState.Status.LOADED
                || evolution == null
                || evolution.historyAvailability()
                == CaseEvolutionViewModel.HistoryAvailability.UNAVAILABLE
                || evolution.nearbyChanges().isEmpty()) {
            Component state = Component.translatable(evolutionStateKey(evolution));
            return height + DetectiveUiRenderer.wrappedHeight(this.font, state, innerWidth) + 10;
        }
        for (NearbyChangeViewModel change : evolution.nearbyChanges()) {
            height += nearbyChangeHeight(change, width - 20) + 6;
        }
        if (evolution.omittedNearbyChangeCount() > 0) {
            Component omitted = Component.translatable(
                    "detective.ui.case.evolution.additional_changes",
                    evolution.omittedNearbyChangeCount());
            height += DetectiveUiRenderer.wrappedHeight(this.font, omitted, innerWidth);
        }
        return height + 4;
    }

    private String evolutionStateKey(CaseEvolutionViewModel evolution) {
        return switch (evolutionState.status()) {
            case IDLE, LOADING -> "detective.ui.case.evolution.loading";
            case FAILED -> "detective.ui.case.evolution.load_failed";
            case LOADED -> evolution == null
                    || evolution.historyAvailability()
                    == CaseEvolutionViewModel.HistoryAvailability.UNAVAILABLE
                    ? "detective.ui.case.evolution.history_unavailable"
                    : "detective.ui.case.evolution.none";
        };
    }

    private Component coverageLine(CaseEvolutionViewModel evolution) {
        HistoryCoverageViewModel coverage = evolution == null
                ? new HistoryCoverageViewModel(HistoryCoverageViewModel.Status.UNKNOWN)
                : evolution.historyCoverage();
        return Component.translatable("detective.ui.case.evolution.coverage",
                Component.translatable(coverage.messageKey()));
    }

    private void renderEvolutionState(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String key
    ) {
        DetectiveUiRenderer.wrappedText(graphics, this.font, Component.translatable(key),
                x, y, width, DetectiveUiRenderer.MUTED);
    }

    private int renderNearbyChange(
            GuiGraphics graphics,
            NearbyChangeViewModel change,
            int x,
            int y,
            int width
    ) {
        int height = nearbyChangeHeight(change, width);
        int textX = x + 8;
        int textWidth = width - 16;
        int cursor = y + 8;
        graphics.fill(x, y, x + width, y + height, 0x4424313E);
        graphics.fill(x, y, x + 2, y + height, 0xFF4D7EA8);
        graphics.drawString(this.font,
                UiTextFitter.ellipsize(change.displayLabel(), textWidth, this.font::width),
                textX, cursor, DetectiveUiRenderer.TEXT, false);
        cursor += 14;
        graphics.drawString(this.font, Component.translatable(
                        "detective.ui.case.evolution.change."
                                + change.type().name().toLowerCase(Locale.ROOT)),
                textX, cursor, DetectiveUiRenderer.ACCENT, false);
        cursor += 13;
        String version = versionLine(change).getString();
        graphics.drawString(this.font, UiTextFitter.ellipsize(version, textWidth, this.font::width),
                textX, cursor, DetectiveUiRenderer.MUTED, false);
        cursor += 15;

        Component offset = offsetLine(change);
        DetectiveUiRenderer.wrappedText(graphics, this.font, offset,
                textX, cursor, textWidth, DetectiveUiRenderer.TEXT);
        cursor += DetectiveUiRenderer.wrappedHeight(this.font, offset, textWidth) + 4;
        if (change.sameRecordedLaunch()) {
            Component sameLaunch = Component.translatable(
                    "detective.ui.case.evolution.same_launch");
            DetectiveUiRenderer.wrappedText(graphics, this.font, sameLaunch,
                    textX, cursor, textWidth, DetectiveUiRenderer.MUTED);
            cursor += DetectiveUiRenderer.wrappedHeight(this.font, sameLaunch, textWidth) + 4;
        }
        if (change.beforeAfter().available()) {
            Component before = Component.translatable("detective.ui.case.evolution.before",
                    relatedIncidentCount(change.beforeAfter().before().getAsInt()));
            Component after = Component.translatable("detective.ui.case.evolution.after",
                    relatedIncidentCount(change.beforeAfter().after().getAsInt()));
            DetectiveUiRenderer.wrappedText(graphics, this.font, before,
                    textX, cursor, textWidth, DetectiveUiRenderer.MUTED);
            cursor += DetectiveUiRenderer.wrappedHeight(this.font, before, textWidth) + 3;
            DetectiveUiRenderer.wrappedText(graphics, this.font, after,
                    textX, cursor, textWidth, DetectiveUiRenderer.MUTED);
        } else {
            Component unavailable = Component.translatable(
                    "detective.ui.case.evolution.before_after_unavailable");
            DetectiveUiRenderer.wrappedText(graphics, this.font, unavailable,
                    textX, cursor, textWidth, DetectiveUiRenderer.MUTED);
        }
        return y + height;
    }

    private int nearbyChangeHeight(NearbyChangeViewModel change, int width) {
        int textWidth = width - 16;
        int height = 8 + 14 + 13 + 15;
        Component offset = offsetLine(change);
        height += DetectiveUiRenderer.wrappedHeight(this.font, offset, textWidth) + 4;
        if (change.sameRecordedLaunch()) {
            Component sameLaunch = Component.translatable("detective.ui.case.evolution.same_launch");
            height += DetectiveUiRenderer.wrappedHeight(this.font, sameLaunch, textWidth) + 4;
        }
        if (change.beforeAfter().available()) {
            Component before = Component.translatable("detective.ui.case.evolution.before",
                    relatedIncidentCount(change.beforeAfter().before().getAsInt()));
            Component after = Component.translatable("detective.ui.case.evolution.after",
                    relatedIncidentCount(change.beforeAfter().after().getAsInt()));
            height += DetectiveUiRenderer.wrappedHeight(this.font, before, textWidth) + 3;
            height += DetectiveUiRenderer.wrappedHeight(this.font, after, textWidth);
        } else {
            Component unavailable = Component.translatable(
                    "detective.ui.case.evolution.before_after_unavailable");
            height += DetectiveUiRenderer.wrappedHeight(this.font, unavailable, textWidth);
        }
        return height + 8;
    }

    private Component versionLine(NearbyChangeViewModel change) {
        Component unavailable = Component.translatable("detective.ui.case.evolution.unavailable");
        return switch (change.type()) {
            case ADDED -> Component.translatable("detective.ui.case.evolution.version.added",
                    change.newVersion().orElseGet(unavailable::getString));
            case UPDATED -> Component.translatable("detective.ui.case.evolution.version.updated",
                    change.previousVersion().orElseGet(unavailable::getString),
                    change.newVersion().orElseGet(unavailable::getString));
            case REMOVED -> Component.translatable("detective.ui.case.evolution.version.removed",
                    change.previousVersion().orElseGet(unavailable::getString));
        };
    }

    private Component offsetLine(NearbyChangeViewModel change) {
        return switch (change.direction()) {
            case BEFORE -> Component.translatable("detective.ui.case.evolution.offset.before",
                    change.offsetMagnitude());
            case AT -> Component.translatable("detective.ui.case.evolution.offset.at");
            case AFTER -> Component.translatable("detective.ui.case.evolution.offset.after",
                    change.offsetMagnitude());
        };
    }

    private Component relatedIncidentCount(int count) {
        if (count == 0) {
            return Component.translatable("detective.ui.case.evolution.count.none");
        }
        return Component.translatable(count == 1
                ? "detective.ui.case.evolution.count.one"
                : "detective.ui.case.evolution.count.many", count);
    }

    private int renderSafety(GuiGraphics graphics, int x, int y, int width) {
        Component message = Component.translatable("detective.ui.case.safety");
        int height = 21 + DetectiveUiRenderer.wrappedHeight(this.font, message, width - 20);
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        DetectiveUiRenderer.wrappedText(graphics, this.font, message,
                x + 10, y + 10, width - 20, DetectiveUiRenderer.MUTED);
        return y + height;
    }

    private int renderOwners(GuiGraphics graphics, int x, int y, int width) {
        List<CaseOwnerViewModel> owners = caseFile.recurringOwners();
        int height = 39 + (owners.isEmpty() ? 16 : owners.size() * 34);
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.case.owners"),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        if (owners.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("detective.ui.case.owners.empty"),
                    x + 10, y + 26, DetectiveUiRenderer.MUTED, false);
            return y + height;
        }
        for (int index = 0; index < owners.size(); index++) {
            CaseOwnerViewModel owner = owners.get(index);
            int rowY = y + 26 + index * 34;
            Component ownerLine = Component.translatable("detective.ui.case.owner",
                    ownerDisplayLabel(owner.ownerId()), owner.supportingIncidents());
            graphics.drawString(this.font, this.font.plainSubstrByWidth(ownerLine.getString(), width - 20),
                    x + 10, rowY, DetectiveUiRenderer.TEXT, false);
            graphics.drawString(this.font, Component.translatable("detective.ui.case.owner.shares",
                            UiFormatters.percent(owner.averageLeafSharePercent()),
                            UiFormatters.percent(owner.averageStackPresenceSharePercent())),
                    x + 10, rowY + 14, DetectiveUiRenderer.MUTED, false);
        }
        return y + height;
    }

    private int renderEvidence(GuiGraphics graphics, int x, int y, int width) {
        List<CaseEvidenceViewModel> evidence = caseFile.recurringEvidence();
        int height = 39 + (evidence.isEmpty() ? 16 : evidence.size() * 32);
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.case.evidence"),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        if (evidence.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("detective.ui.case.evidence.empty"),
                    x + 10, y + 26, DetectiveUiRenderer.MUTED, false);
            return y + height;
        }
        for (int index = 0; index < evidence.size(); index++) {
            CaseEvidenceViewModel observation = evidence.get(index);
            int rowY = y + 26 + index * 32;
            Component label = Component.translatable("detective.ui.case.evidence.item", index + 1);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), width - 20),
                    x + 10, rowY, DetectiveUiRenderer.TEXT, false);
            graphics.drawString(this.font, Component.translatable("detective.ui.case.evidence.support",
                            observation.supportingIncidents(),
                            UiFormatters.percent(observation.averageObservedSharePercent())),
                    x + 10, rowY + 14, DetectiveUiRenderer.MUTED, false);
        }
        return y + height;
    }

    private int renderRelatedIncidents(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        List<RelatedIncidentViewModel> related = caseFile.relatedIncidents();
        int height = 39 + (related.isEmpty() ? 16 : related.size() * 44);
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.case.related"),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        if (related.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("detective.ui.case.related.empty"),
                    x + 10, y + 26, DetectiveUiRenderer.MUTED, false);
            return y + height;
        }
        for (int index = 0; index < related.size(); index++) {
            RelatedIncidentViewModel relatedIncident = related.get(index);
            int rowY = y + 25 + index * 44;
            if (relatedIncident.isAvailable()
                    && scrollState.isWithinViewport(mouseY)
                    && inside(mouseX, mouseY, x + 7, rowY - 2, width - 14, 40)) {
                graphics.fill(x + 7, rowY - 2, x + width - 7, rowY + 38, 0x553A4B5C);
            }
            renderRelatedIncident(graphics, relatedIncident, x + 10, rowY, width - 20);
            if (relatedIncident.isAvailable()
                    && rowY + 38 >= CONTENT_TOP
                    && rowY <= this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 2) {
                relatedHits.add(new RelatedHit(relatedIncident.incident(), x + 7, rowY - 2, width - 14, 40));
            }
        }
        return y + height;
    }

    private void renderRelatedIncident(
            GuiGraphics graphics,
            RelatedIncidentViewModel related,
            int x,
            int y,
            int width
    ) {
        IncidentSummaryViewModel incident = related.incident();
        if (incident == null) {
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(related.incidentId(), width),
                    x, y, DetectiveUiRenderer.MUTED, false);
            graphics.drawString(this.font, Component.translatable("detective.ui.case.related.unavailable"),
                    x, y + 14, DetectiveUiRenderer.MUTED, false);
            return;
        }
        graphics.drawString(this.font, Component.translatable("detective.ui.case.related.summary",
                        UiFormatters.duration(incident.durationMs()), incident.occurredAt()),
                x, y, DetectiveUiRenderer.TEXT, false);
        Component state = incident.hasPrimarySuspect()
                ? Component.translatable("detective.ui.primary_short", incident.primarySuspect())
                : Component.translatable(incident.evidence().listSummaryKey());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(state.getString(), width),
                x, y + 14, DetectiveUiRenderer.MUTED, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.case.related.open"),
                x, y + 28, DetectiveUiRenderer.ACCENT, false);
    }

    private static String ownerDisplayLabel(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return "unknown";
        }
        String id = ownerId.trim();
        String displayName = humanizeModId(id);
        return displayName.equalsIgnoreCase(id) ? id : displayName + " (" + id + ")";
    }

    private static String humanizeModId(String modId) {
        StringBuilder result = new StringBuilder();
        for (String word : modId.replace('-', '_').split("_")) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.isEmpty() ? modId : result.toString();
    }

    private void metric(GuiGraphics graphics, int x, int y, String label, String value) {
        graphics.drawString(this.font, Component.translatable("detective.ui.case.metric",
                Component.translatable(label), value), x, y, DetectiveUiRenderer.TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollState.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            for (RelatedHit hit : relatedHits) {
                if (scrollState.isWithinViewport(mouseY)
                        && inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height())) {
                    this.minecraft.setScreen(new IncidentDetailScreen(this, hit.incident()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollState.isWithinViewport(mouseY) && scrollState.scrollWheel(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (scrollState.mouseDragged(mouseY, button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollState.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        evolutionState.cancelPending();
        super.removed();
    }

    private record RelatedHit(IncidentSummaryViewModel incident, int x, int y, int width, int height) {}
}
