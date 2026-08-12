package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.model.CaseEvidenceViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseFileViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseOwnerViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.RelatedIncidentViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class CaseFileDetailScreen extends Screen {
    private static final int CONTENT_TOP = DetectiveUiRenderer.HEADER_HEIGHT + 4;

    private final Screen parent;
    private final CaseFileViewModel caseFile;
    private final List<RelatedHit> relatedHits = new ArrayList<>();
    private double scrollOffset;
    private int contentHeight;

    public CaseFileDetailScreen(Screen parent, CaseFileViewModel caseFile) {
        super(Component.translatable("detective.ui.case.title"));
        this.parent = parent;
        this.caseFile = caseFile;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.case.title"),
                Component.translatable("detective.ui.case.subtitle", caseFile.shortCaseId()));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);

        int viewportBottom = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 2;
        int width = Math.min(560, this.width - 24);
        int left = (this.width - width) / 2;
        int y = CONTENT_TOP - (int) scrollOffset;
        relatedHits.clear();
        graphics.enableScissor(0, CONTENT_TOP, this.width, viewportBottom);
        y = renderOverview(graphics, left, y, width) + 8;
        y = renderSafety(graphics, left, y, width) + 8;
        y = renderOwners(graphics, left, y, width) + 8;
        y = renderEvidence(graphics, left, y, width) + 8;
        y = renderRelatedIncidents(graphics, left, y, width, mouseX, mouseY);
        graphics.disableScissor();
        contentHeight = y - (CONTENT_TOP - (int) scrollOffset);
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maximumScroll());
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
            if (relatedIncident.isAvailable() && inside(mouseX, mouseY, x + 7, rowY - 2, width - 14, 40)) {
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
        if (button == 0) {
            for (RelatedHit hit : relatedHits) {
                if (inside(mouseX, mouseY, hit.x(), hit.y(), hit.width(), hit.height())) {
                    this.minecraft.setScreen(new IncidentDetailScreen(this, hit.incident()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Mth.clamp(scrollOffset - scrollY * 24.0, 0.0, maximumScroll());
        return true;
    }

    private double maximumScroll() {
        int viewportHeight = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - CONTENT_TOP - 2;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private record RelatedHit(IncidentSummaryViewModel incident, int x, int y, int width, int height) {}
}
