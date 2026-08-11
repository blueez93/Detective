package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.BlackBoxPoint;
import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
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

import java.util.ArrayList;
import java.util.List;

public final class IncidentDetailScreen extends Screen {
    private static final int CONTENT_TOP = DetectiveUiRenderer.HEADER_HEIGHT + 4;

    private final Screen parent;
    private final IncidentSummaryViewModel summary;
    private final IncidentDetailViewModel preloadedDetail;
    private IncidentDetailViewModel detail;
    private boolean loading = true;
    private boolean loadFailed;
    private double scrollOffset;
    private int contentHeight;
    private Component pendingTooltip;
    private boolean technicalActionVisible;
    private int technicalActionX;
    private int technicalActionY;
    private int technicalActionWidth;

    public IncidentDetailScreen(Screen parent, IncidentSummaryViewModel summary) {
        this(parent, summary, null);
    }

    /** Allows the development-only visual harness to render every evidence state deterministically. */
    public IncidentDetailScreen(Screen parent, IncidentDetailViewModel detail) {
        this(parent, detail.summary(), detail);
    }

    private IncidentDetailScreen(
            Screen parent,
            IncidentSummaryViewModel summary,
            IncidentDetailViewModel preloadedDetail
    ) {
        super(Component.translatable("detective.ui.incident.title"));
        this.parent = parent;
        this.summary = summary;
        this.preloadedDetail = preloadedDetail;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
        if (preloadedDetail != null) {
            detail = preloadedDetail;
            loading = false;
            loadFailed = false;
            return;
        }
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
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);

        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, this.height / 2, DetectiveUiRenderer.MUTED);
        } else if (loadFailed || detail == null) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.incident.unreadable"),
                    this.width / 2, this.height / 2, 0xFFFF7777);
        } else {
            renderContent(graphics, mouseX, mouseY);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int viewportBottom = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 2;
        int width = Math.min(540, this.width - 24);
        int left = (this.width - width) / 2;
        int y = CONTENT_TOP - (int) scrollOffset;
        int startY = y;
        pendingTooltip = null;
        technicalActionVisible = false;

        graphics.enableScissor(0, CONTENT_TOP, this.width, viewportBottom);
        y = renderOverview(graphics, left, y, width) + 8;
        y = renderPrimaryOrSpecialState(graphics, left, y, width) + 8;
        if (shouldRenderSuspectList()) {
            y = renderOtherSuspects(graphics, left, y, width) + 8;
        }
        y = renderBlackBox(graphics, left, y, width) + 8;
        y = renderTechnicalEvidence(graphics, left, y, width, mouseX, mouseY) + 8;
        graphics.disableScissor();

        contentHeight = y - startY;
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maximumScroll());
        if (pendingTooltip != null) {
            graphics.renderTooltip(this.font, this.font.split(pendingTooltip, Math.min(300, this.width - 32)),
                    mouseX, mouseY);
        }
    }

    private int renderOverview(GuiGraphics graphics, int x, int y, int width) {
        int height = 94;
        int rightColumn = x + width / 2;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.overview"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.duration",
                        UiFormatters.duration(detail.summary().durationMs())),
                x + 10, y + 25, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.occurred",
                        detail.summary().occurredAt()),
                rightColumn, y + 25, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.location",
                        detail.summary().dimension(), detail.summary().coordinates()),
                x + 10, y + 43, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.threshold",
                        UiFormatters.duration(detail.summary().thresholdMs())),
                x + 10, y + 61, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.samples",
                        detail.summary().watchdogSamples()),
                rightColumn, y + 61, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.classification",
                        Component.translatable(detail.summary().evidence().translationKey())),
                x + 10, y + 78, DetectiveUiRenderer.MUTED, false);
        return y + height;
    }

    private int renderPrimaryOrSpecialState(GuiGraphics graphics, int x, int y, int width) {
        if (detail.summary().hasPrimarySuspect()
                && detail.summary().evidence().isAttributedTier()
                && !detail.suspects().isEmpty()) {
            return renderAttributedPrimary(graphics, x, y, width);
        }
        return renderSpecialState(graphics, x, y, width);
    }

    private int renderAttributedPrimary(GuiGraphics graphics, int x, int y, int width) {
        SuspectViewModel top = detail.suspects().getFirst();
        int innerWidth = width - 20;
        Component suspectName = Component.literal(top.modName() + " (" + top.modId() + ")");
        Component ownership = top.leafOwnershipCount() > 0
                ? Component.translatable("detective.ui.incident.primary_ownership", top.modName(),
                        top.leafOwnershipCount(), detail.summary().watchdogSamples())
                : Component.translatable("detective.ui.incident.primary_presence", top.modName(),
                        top.presenceSamples(), detail.summary().watchdogSamples());
        Component whyOne = Component.translatable("detective.ui.incident.why.1");
        Component whyTwo = Component.translatable("detective.ui.incident.why.2", top.modName());
        Component whyThree = Component.translatable("detective.ui.incident.why.3");

        int height = 25
                + DetectiveUiRenderer.wrappedHeight(this.font, suspectName, innerWidth)
                + 19
                + DetectiveUiRenderer.wrappedHeight(this.font, ownership, innerWidth)
                + 18
                + DetectiveUiRenderer.wrappedHeight(this.font, whyOne, innerWidth)
                + DetectiveUiRenderer.wrappedHeight(this.font, whyTwo, innerWidth)
                + DetectiveUiRenderer.wrappedHeight(this.font, whyThree, innerWidth)
                + 22;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.primary"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        int cursor = y + 25;
        cursor = DetectiveUiRenderer.wrappedText(graphics, this.font, suspectName, x + 10, cursor,
                innerWidth, DetectiveUiRenderer.TEXT) + 3;
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.evidence_strength"),
                x + 10, cursor, DetectiveUiRenderer.MUTED, false);
        graphics.drawString(this.font, Component.translatable(detail.summary().evidence().strengthKey()),
                x + 128, cursor, detail.summary().evidence().color(), false);
        cursor += 17;
        cursor = DetectiveUiRenderer.wrappedText(graphics, this.font, ownership, x + 10, cursor,
                innerWidth, DetectiveUiRenderer.TEXT) + 5;
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.why.title"),
                x + 10, cursor, DetectiveUiRenderer.ACCENT, false);
        cursor += 17;
        cursor = DetectiveUiRenderer.wrappedText(graphics, this.font, whyOne, x + 10, cursor,
                innerWidth, DetectiveUiRenderer.MUTED) + 3;
        cursor = DetectiveUiRenderer.wrappedText(graphics, this.font, whyTwo, x + 10, cursor,
                innerWidth, DetectiveUiRenderer.MUTED) + 3;
        DetectiveUiRenderer.wrappedText(graphics, this.font, whyThree, x + 10, cursor,
                innerWidth, DetectiveUiRenderer.MUTED);
        return y + height;
    }

    private int renderSpecialState(GuiGraphics graphics, int x, int y, int width) {
        EvidenceBadge evidence = detail.summary().evidence();
        List<Component> paragraphs = specialStateParagraphs(evidence);
        int innerWidth = width - 20;
        int height = 29;
        for (Component paragraph : paragraphs) {
            height += DetectiveUiRenderer.wrappedHeight(this.font, paragraph, innerWidth) + 4;
        }
        if (evidence == EvidenceBadge.INSUFFICIENT_EVIDENCE) {
            height += 17;
        }
        height += 8;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable(evidence.translationKey()), x + 10, y + 9,
                evidence.color(), false);
        int cursor = y + 27;
        for (int index = 0; index < paragraphs.size(); index++) {
            cursor = DetectiveUiRenderer.wrappedText(graphics, this.font, paragraphs.get(index),
                    x + 10, cursor, innerWidth, index == 0 ? DetectiveUiRenderer.TEXT : DetectiveUiRenderer.MUTED) + 4;
        }
        if (evidence == EvidenceBadge.INSUFFICIENT_EVIDENCE) {
            Component action = Component.translatable("detective.ui.incident.view_technical");
            technicalActionVisible = true;
            technicalActionX = x + 10;
            technicalActionY = cursor + 2;
            technicalActionWidth = this.font.width(action);
            graphics.drawString(this.font, action,
                    x + 10, cursor + 2, DetectiveUiRenderer.ACCENT, false);
        }
        return y + height;
    }

    private static List<Component> specialStateParagraphs(EvidenceBadge evidence) {
        List<Component> paragraphs = new ArrayList<>();
        if (evidence == EvidenceBadge.AMBIGUOUS_ATTRIBUTION) {
            paragraphs.add(Component.translatable("detective.ui.incident.ambiguous.lead"));
            paragraphs.add(Component.translatable("detective.ui.incident.ambiguous.body"));
        } else if (evidence == EvidenceBadge.INSUFFICIENT_EVIDENCE) {
            paragraphs.add(Component.translatable("detective.ui.incident.insufficient.body"));
            paragraphs.add(Component.translatable("detective.ui.incident.insufficient.closing"));
        } else if (evidence.isSystemStall()) {
            paragraphs.add(Component.translatable("detective.ui.incident.system.lead"));
            paragraphs.add(Component.translatable("detective.ui.incident.system.sources"));
            paragraphs.add(Component.translatable("detective.ui.incident.system.closing"));
        } else {
            paragraphs.add(Component.translatable("detective.ui.incident.unknown.body"));
        }
        return List.copyOf(paragraphs);
    }

    private boolean shouldRenderSuspectList() {
        if (detail.suspects().isEmpty()) {
            return false;
        }
        return detail.summary().evidence().isAttributedTier()
                || detail.summary().evidence() == EvidenceBadge.AMBIGUOUS_ATTRIBUTION;
    }

    private int renderOtherSuspects(GuiGraphics graphics, int x, int y, int width) {
        List<SuspectViewModel> suspects = detail.suspects();
        boolean ambiguous = detail.summary().evidence() == EvidenceBadge.AMBIGUOUS_ATTRIBUTION;
        int first = ambiguous ? 0 : 1;
        int count = Math.max(0, suspects.size() - first);
        int innerWidth = width - 20;
        List<List<net.minecraft.util.FormattedCharSequence>> names = new ArrayList<>();
        int rowsHeight = 0;
        for (int index = first; index < suspects.size(); index++) {
            SuspectViewModel suspect = suspects.get(index);
            List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(
                    Component.literal("#" + (index + 1) + "  " + suspect.modName() + " (" + suspect.modId() + ")"),
                    innerWidth);
            names.add(lines);
            rowsHeight += Math.min(2, lines.size()) * (this.font.lineHeight + 1) + 15;
        }
        int height = 31 + (count == 0 ? 16 : rowsHeight);
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable(ambiguous
                        ? "detective.ui.incident.possible_suspects" : "detective.ui.incident.other_suspects"),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        if (count == 0) {
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.no_other_suspects"),
                    x + 10, y + 25, DetectiveUiRenderer.MUTED, false);
            return y + height;
        }
        int cursor = y + 25;
        for (int relativeIndex = 0; relativeIndex < count; relativeIndex++) {
            SuspectViewModel suspect = suspects.get(first + relativeIndex);
            List<net.minecraft.util.FormattedCharSequence> lines = names.get(relativeIndex);
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                graphics.drawString(this.font, lines.get(line), x + 10, cursor,
                        DetectiveUiRenderer.TEXT, false);
                cursor += this.font.lineHeight + 1;
            }
            Component evidence = suspect.leafOwnershipCount() > 0
                    ? Component.translatable("detective.ui.incident.secondary_evidence",
                            suspect.leafOwnershipCount(), detail.summary().watchdogSamples())
                    : Component.translatable("detective.ui.incident.background_presence",
                            suspect.presenceSamples(), detail.summary().watchdogSamples());
            graphics.drawString(this.font, evidence, x + 10, cursor, DetectiveUiRenderer.MUTED, false);
            cursor += 15;
        }
        return y + height;
    }

    private int renderBlackBox(GuiGraphics graphics, int x, int y, int width) {
        int innerWidth = width - 20;
        int graphX = x + 10;
        int graphY = y + 42;
        int graphWidth = innerWidth;
        int graphHeight = 68;
        int cursorAfterGraph = graphY + graphHeight + 5;
        int height = 158;
        if (detail.blackBoxPartial()) {
            height += 18 + DetectiveUiRenderer.wrappedHeight(this.font,
                    Component.translatable("detective.ui.incident.black_box.partial.body"), innerWidth);
        }
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.black_box"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.black_box.description"),
                x + 10, y + 23, DetectiveUiRenderer.MUTED, false);
        graphics.fill(graphX, graphY, graphX + graphWidth, graphY + graphHeight, 0xCC080C10);
        graphics.renderOutline(graphX, graphY, graphWidth, graphHeight, 0xFF354657);
        if (detail.blackBox().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.incident.black_box_empty"),
                    x + width / 2, graphY + graphHeight / 2 - 4, DetectiveUiRenderer.MUTED);
        } else {
            renderBlackBoxBars(graphics, graphX, graphY, graphWidth, graphHeight);
        }
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.threshold_line"),
                graphX, cursorAfterGraph, 0xFFFFAA55, false);
        BlackBoxPoint first = detail.blackBox().isEmpty() ? null : detail.blackBox().getFirst();
        BlackBoxPoint last = detail.blackBox().isEmpty() ? null : detail.blackBox().getLast();
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.black_box.frames",
                        first == null ? "—" : UiFormatters.duration(first.frameMs()),
                        UiFormatters.duration(detail.summary().durationMs())),
                graphX, cursorAfterGraph + 14, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.black_box.metadata",
                        last == null ? "—" : UiFormatters.memory(last.usedMemoryBytes()),
                        detail.originalBlackBoxSamples()),
                graphX, cursorAfterGraph + 28, DetectiveUiRenderer.TEXT, false);
        if (detail.blackBoxPartial()) {
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.black_box.partial.title"),
                    graphX, cursorAfterGraph + 45, 0xFFFFAA55, false);
            DetectiveUiRenderer.wrappedText(graphics, this.font,
                    Component.translatable("detective.ui.incident.black_box.partial.body"),
                    graphX, cursorAfterGraph + 59, innerWidth, DetectiveUiRenderer.MUTED);
        }
        return y + height;
    }

    private void renderBlackBoxBars(GuiGraphics graphics, int graphX, int graphY, int graphWidth, int graphHeight) {
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
    }

    private int renderTechnicalEvidence(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        int count = detail.suspects().size();
        int height = 51 + (count == 0 ? 18 : count * 66);
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical"), x + 10, y + 9,
                DetectiveUiRenderer.ACCENT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical.samples",
                        detail.summary().watchdogSamples()),
                x + 10, y + 25, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical.classification",
                        detail.summary().rawEvidenceState()),
                x + width / 2, y + 25, DetectiveUiRenderer.TEXT, false);
        if (count == 0) {
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.no_technical_evidence"),
                    x + 10, y + 42, DetectiveUiRenderer.MUTED, false);
            return y + height;
        }
        for (int index = 0; index < count; index++) {
            SuspectViewModel suspect = detail.suspects().get(index);
            int rowY = y + 43 + index * 66;
            graphics.drawString(this.font, Component.literal("#" + (index + 1) + " " + suspect.modName()
                            + " (" + suspect.modId() + ")"),
                    x + 10, rowY, DetectiveUiRenderer.TEXT, false);
            int leafY = rowY + 13;
            int presenceY = rowY + 25;
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical.leaf",
                            suspect.leafOwnershipCount(), UiFormatters.percent(suspect.leafOwnershipSharePercent())),
                    x + 10, leafY, DetectiveUiRenderer.MUTED, false);
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical.presence",
                            suspect.presenceSamples(), UiFormatters.percent(suspect.presenceSharePercent())),
                    x + 10, presenceY, DetectiveUiRenderer.MUTED, false);
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical.depth",
                            formatDepth(suspect.averageFirstFrameDepth()), suspect.minimumFirstFrameDepth()),
                    x + 10, rowY + 37, DetectiveUiRenderer.MUTED, false);
            graphics.drawString(this.font, Component.translatable("detective.ui.incident.technical.extra",
                            suspect.repeatedLeafOwnership(), suspect.stackDiversity()),
                    x + 10, rowY + 49, DetectiveUiRenderer.MUTED, false);
            if (inside(mouseX, mouseY, x + 8, leafY - 2, width - 16, 12)) {
                pendingTooltip = Component.translatable("detective.ui.incident.technical.leaf.tooltip");
            } else if (inside(mouseX, mouseY, x + 8, presenceY - 2, width - 16, 12)) {
                pendingTooltip = Component.translatable("detective.ui.incident.technical.presence.tooltip");
            }
        }
        return y + height;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && technicalActionVisible
                && mouseX >= technicalActionX && mouseX < technicalActionX + technicalActionWidth
                && mouseY >= technicalActionY - 2 && mouseY < technicalActionY + this.font.lineHeight + 3) {
            scrollOffset = maximumScroll();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static String formatDepth(double depth) {
        return Double.isFinite(depth) && depth >= 0.0
                ? String.format(java.util.Locale.ROOT, "%.1f", depth) : "—";
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
