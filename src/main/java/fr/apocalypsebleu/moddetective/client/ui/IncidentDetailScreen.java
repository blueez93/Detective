package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.BlackBoxPoint;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentDetailViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.SuspectViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public final class IncidentDetailScreen extends Screen {
    private static final int CONTENT_TOP = DetectiveUiRenderer.HEADER_HEIGHT + 4;

    private final Screen parent;
    private final IncidentSummaryViewModel summary;
    private IncidentDetailViewModel detail;
    private boolean loading = true;
    private boolean loadFailed;
    private double scrollOffset;
    private int contentHeight;

    public IncidentDetailScreen(Screen parent, IncidentSummaryViewModel summary) {
        super(Component.translatable("detective.ui.incident.title"));
        this.parent = parent;
        this.summary = summary;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
        DetectiveUiService.loadDetail(summary.source()).whenComplete((loaded, error) -> this.minecraft.execute(() -> {
            loading = false;
            loadFailed = error != null;
            detail = error == null ? loaded : null;
            scrollOffset = 0.0;
        }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.incident.title"),
                Component.translatable("detective.ui.incident.subtitle", summary.occurredAt()));
        DetectiveUiRenderer.footer(graphics, this.width, this.height);

        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, this.height / 2, DetectiveUiRenderer.MUTED);
        } else if (loadFailed || detail == null) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.incident.unreadable"),
                    this.width / 2, this.height / 2, 0xFFFF7777);
        } else {
            renderContent(graphics);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics graphics) {
        int viewportBottom = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 2;
        int width = Math.min(520, this.width - 24);
        int left = (this.width - width) / 2;
        int y = CONTENT_TOP - (int) scrollOffset;
        int startY = y;

        graphics.enableScissor(0, CONTENT_TOP, this.width, viewportBottom);
        y = renderOverview(graphics, left, y, width) + 8;
        y = renderPrimary(graphics, left, y, width) + 8;
        y = renderOtherSuspects(graphics, left, y, width) + 8;
        y = renderBlackBox(graphics, left, y, width) + 8;
        y = renderTechnicalEvidence(graphics, left, y, width) + 8;
        graphics.disableScissor();

        contentHeight = y - startY;
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maximumScroll());
    }

    private int renderOverview(GuiGraphics graphics, int x, int y, int width) {
        int height = 75;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.overview"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.duration",
                        UiFormatters.duration(detail.summary().durationMs())),
                x + 10, y + 25, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.threshold",
                        UiFormatters.duration(detail.summary().thresholdMs())),
                x + width / 2, y + 25, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.dimension",
                        detail.summary().dimension()),
                x + 10, y + 41, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.coordinates",
                        detail.summary().coordinates()),
                x + width / 2, y + 41, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.samples",
                        detail.summary().watchdogSamples()),
                x + 10, y + 57, DetectiveUiRenderer.MUTED, false);
        graphics.drawString(this.font, Component.literal(detail.summary().rawEvidenceState()),
                x + width / 2, y + 57, DetectiveUiRenderer.MUTED, false);
        return y + height;
    }

    private int renderPrimary(GuiGraphics graphics, int x, int y, int width) {
        int height = 72;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.primary"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        DetectiveUiRenderer.badge(graphics, this.font, detail.summary().evidence(), x + 10, y + 25);
        int textX = x + 10;
        int descriptionY = y + 47;
        if (detail.summary().hasPrimarySuspect() && !detail.suspects().isEmpty()) {
            SuspectViewModel top = detail.suspects().getFirst();
            String suspect = top.modName() + "  (" + top.modId() + ")";
            graphics.drawString(this.font, this.font.plainSubstrByWidth(suspect, width - 155),
                    x + 145, y + 28, DetectiveUiRenderer.TEXT, false);
        }
        DetectiveUiRenderer.wrappedText(graphics, this.font,
                Component.translatable(detail.summary().evidence().descriptionKey()),
                textX, descriptionY, width - 20, DetectiveUiRenderer.MUTED);
        return y + height;
    }

    private int renderOtherSuspects(GuiGraphics graphics, int x, int y, int width) {
        List<SuspectViewModel> suspects = detail.suspects();
        int first = detail.summary().hasPrimarySuspect() ? 1 : 0;
        int count = Math.max(0, suspects.size() - first);
        int height = 32 + Math.max(1, count) * 16;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.other_suspects"),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        if (count == 0) {
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.no_other_suspects"),
                    x + 10, y + 25, DetectiveUiRenderer.MUTED, false);
        } else {
            for (int index = first; index < suspects.size(); index++) {
                SuspectViewModel suspect = suspects.get(index);
                String row = "#" + (index + 1) + "  " + suspect.modName() + " (" + suspect.modId() + ")";
                graphics.drawString(this.font, this.font.plainSubstrByWidth(row, width - 20),
                        x + 10, y + 25 + (index - first) * 16, DetectiveUiRenderer.TEXT, false);
            }
        }
        return y + height;
    }

    private int renderBlackBox(GuiGraphics graphics, int x, int y, int width) {
        int height = 124;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.black_box"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        graphics.drawString(this.font,
                Component.translatable("detective.ui.incident.black_box_samples", detail.originalBlackBoxSamples()),
                x + width - 10 - this.font.width(Component.translatable(
                        "detective.ui.incident.black_box_samples", detail.originalBlackBoxSamples())),
                y + 9, DetectiveUiRenderer.MUTED, false);

        int graphX = x + 10;
        int graphY = y + 27;
        int graphWidth = width - 20;
        int graphHeight = 76;
        graphics.fill(graphX, graphY, graphX + graphWidth, graphY + graphHeight, 0xCC080C10);
        graphics.renderOutline(graphX, graphY, graphWidth, graphHeight, 0xFF354657);
        if (detail.blackBox().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.incident.black_box_empty"),
                    x + width / 2, graphY + graphHeight / 2 - 4, DetectiveUiRenderer.MUTED);
            return y + height;
        }

        double maximum = Math.max(detail.summary().durationMs(), detail.summary().thresholdMs() * 1.2);
        for (BlackBoxPoint point : detail.blackBox()) {
            maximum = Math.max(maximum, point.frameMs());
        }
        maximum = Math.max(1.0, maximum);
        List<BlackBoxPoint> points = detail.blackBox();
        for (int index = 0; index < points.size(); index++) {
            BlackBoxPoint point = points.get(index);
            int barX = graphX + 1 + index * Math.max(1, graphWidth - 2) / points.size();
            int nextX = graphX + 1 + (index + 1) * Math.max(1, graphWidth - 2) / points.size();
            int barHeight = Math.max(1, (int) Math.round(point.frameMs() / maximum * (graphHeight - 3)));
            int color = point.frameMs() >= detail.summary().thresholdMs() ? 0xFFD9534F : 0xFF6FA8DC;
            graphics.fill(barX, graphY + graphHeight - 1 - barHeight, Math.max(barX + 1, nextX),
                    graphY + graphHeight - 1, color);
        }
        int thresholdY = graphY + graphHeight - 1
                - (int) Math.round(detail.summary().thresholdMs() / maximum * (graphHeight - 3));
        graphics.fill(graphX + 1, thresholdY, graphX + graphWidth - 1, thresholdY + 1, 0xFFFFAA55);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.threshold_line"),
                graphX, graphY + graphHeight + 5, 0xFFFFAA55, false);
        if (detail.blackBoxPartial()) {
            String partial = Component.translatable("detective.ui.incident.black_box_partial").getString();
            graphics.drawString(this.font, partial, graphX + graphWidth - this.font.width(partial),
                    graphY + graphHeight + 5, DetectiveUiRenderer.MUTED, false);
        }
        return y + height;
    }

    private int renderTechnicalEvidence(GuiGraphics graphics, int x, int y, int width) {
        int count = detail.suspects().size();
        int height = 34 + Math.max(1, count) * 31;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        if (count == 0) {
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.no_technical_evidence"),
                    x + 10, y + 25, DetectiveUiRenderer.MUTED, false);
        } else {
            for (int index = 0; index < count; index++) {
                SuspectViewModel suspect = detail.suspects().get(index);
                int rowY = y + 25 + index * 31;
                graphics.drawString(this.font, "#" + (index + 1) + " " + suspect.modName(),
                        x + 10, rowY, DetectiveUiRenderer.TEXT, false);
                String first = Component.translatable("detective.ui.incident.leaf_evidence",
                        suspect.leafOwnershipCount(), UiFormatters.percent(suspect.leafOwnershipSharePercent()),
                        suspect.presenceSamples(), UiFormatters.percent(suspect.presenceSharePercent())).getString();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(first, width - 20),
                        x + 10, rowY + 11, DetectiveUiRenderer.MUTED, false);
                String second = Component.translatable("detective.ui.incident.depth_evidence",
                        formatDepth(suspect.averageFirstFrameDepth()), suspect.minimumFirstFrameDepth(),
                        suspect.repeatedLeafOwnership(), suspect.stackDiversity()).getString();
                graphics.drawString(this.font, this.font.plainSubstrByWidth(second, width - 20),
                        x + 10, rowY + 21, DetectiveUiRenderer.MUTED, false);
            }
        }
        return y + height;
    }

    private static String formatDepth(double depth) {
        return Double.isFinite(depth) && depth >= 0.0 ? String.format(java.util.Locale.ROOT, "%.1f", depth) : "—";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detail == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollOffset = Mth.clamp(scrollOffset - scrollY * 24.0, 0.0, maximumScroll());
        return true;
    }

    private double maximumScroll() {
        int viewportHeight = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - CONTENT_TOP - 2;
        return Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
