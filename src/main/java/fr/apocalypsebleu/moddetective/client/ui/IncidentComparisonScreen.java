package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.data.IncidentComparisonUiAdapter;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentComparisonViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import fr.apocalypsebleu.moddetective.client.ui.model.UiTextFitter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

/** Evidence-first comparison of exactly two retained incidents. */
public final class IncidentComparisonScreen extends Screen {
    private static final int CONTENT_TOP = DetectiveUiRenderer.HEADER_HEIGHT + 4;

    private final Screen parent;
    private final IncidentSummaryViewModel firstSummary;
    private final IncidentSummaryViewModel secondSummary;
    private final Map<String, String> ownerDisplayNames;
    private final DetectiveScrollState scrollState = new DetectiveScrollState();
    private IncidentComparisonViewModel comparison;
    private boolean loading;
    private boolean loadFailed;
    private int contentHeight;

    public IncidentComparisonScreen(
            Screen parent,
            IncidentSummaryViewModel firstSummary,
            IncidentSummaryViewModel secondSummary
    ) {
        this(parent, firstSummary, secondSummary, Map.of(), null);
    }

    public IncidentComparisonScreen(
            Screen parent,
            IncidentSearchRecord first,
            IncidentSearchRecord second
    ) {
        this(parent, first.summary(), second.summary(), safeOwnerDisplayNames(first, second), null);
    }

    /** Allows development-only routes to render controlled comparison states without disk access. */
    public IncidentComparisonScreen(
            Screen parent,
            IncidentComparisonViewModel preloadedComparison
    ) {
        this(parent, preloadedComparison.first().incident(), preloadedComparison.second().incident(),
                Map.of(), preloadedComparison);
    }

    private IncidentComparisonScreen(
            Screen parent,
            IncidentSummaryViewModel firstSummary,
            IncidentSummaryViewModel secondSummary,
            Map<String, String> ownerDisplayNames,
            IncidentComparisonViewModel preloadedComparison
    ) {
        super(Component.translatable("detective.ui.comparison.title"));
        this.parent = parent;
        this.firstSummary = java.util.Objects.requireNonNull(firstSummary, "firstSummary");
        this.secondSummary = java.util.Objects.requireNonNull(secondSummary, "secondSummary");
        this.ownerDisplayNames = Map.copyOf(ownerDisplayNames);
        this.comparison = preloadedComparison;
    }

    @Override
    protected void init() {
        scrollState.cancelDrag();
        int buttonWidth = Math.min(110, Math.max(82, (this.width - 32) / 4));
        int gap = 4;
        int total = buttonWidth * 3 + gap * 2;
        int left = (this.width - total) / 2;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.comparison.open_a"),
                        button -> openIncident(firstSummary))
                .bounds(left, this.height - 28, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + buttonWidth + gap, this.height - 28, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.comparison.open_b"),
                        button -> openIncident(secondSummary))
                .bounds(left + (buttonWidth + gap) * 2, this.height - 28, buttonWidth, 20).build());

        if (comparison == null && !loading) {
            loading = true;
            DetectiveUiService.compareIncidents(firstSummary.source(), secondSummary.source())
                    .whenComplete((loaded, error) -> this.minecraft.execute(() -> {
                        loading = false;
                        loadFailed = error != null;
                        if (error == null) {
                            comparison = IncidentComparisonUiAdapter.from(
                                    loaded, firstSummary, secondSummary, ownerDisplayNames);
                        }
                    }));
        }
    }

    private void openIncident(IncidentSummaryViewModel incident) {
        this.minecraft.setScreen(new IncidentDetailScreen(this, incident));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.comparison.title"),
                Component.translatable("detective.ui.comparison.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);

        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, this.height / 2, DetectiveUiRenderer.MUTED);
        } else if (loadFailed || comparison == null) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("detective.ui.comparison.load_failed"),
                    this.width / 2, this.height / 2, 0xFFFF7777);
        } else {
            renderComparison(graphics);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    private void renderComparison(GuiGraphics graphics) {
        int viewportBottom = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 2;
        int viewportHeight = Math.max(1, viewportBottom - CONTENT_TOP);
        int width = Math.min(600, this.width - 20);
        int left = (this.width - width) / 2;
        int scrollbarX = Math.min(this.width - 6, left + width + 4);
        scrollState.updateLayout(contentHeight, CONTENT_TOP, viewportHeight, scrollbarX, 4);
        int y = CONTENT_TOP - scrollState.roundedOffset();
        int startY = y;
        graphics.enableScissor(0, CONTENT_TOP, this.width, viewportBottom);
        y = renderCaution(graphics, left, y, width) + 6;
        y = renderSimilarity(graphics, left, y, width) + 6;
        y = renderSides(graphics, left, y, width) + 6;
        y = renderEvidenceAvailability(graphics, left, y, width) + 6;
        y = renderEvidenceGroup(graphics, left, y, width,
                "detective.ui.comparison.evidence.shared", comparison.evidence().shared()) + 6;
        y = renderEvidenceGroup(graphics, left, y, width,
                "detective.ui.comparison.evidence.only_a", comparison.evidence().onlyFirst()) + 6;
        y = renderEvidenceGroup(graphics, left, y, width,
                "detective.ui.comparison.evidence.only_b", comparison.evidence().onlySecond());
        graphics.disableScissor();
        contentHeight = y - startY;
        scrollState.updateLayout(contentHeight, CONTENT_TOP, viewportHeight, scrollbarX, 4);
        DetectiveUiRenderer.scrollbar(graphics, scrollState);
    }

    private int renderEvidenceAvailability(GuiGraphics graphics, int x, int y, int width) {
        List<IncidentComparisonViewModel.UnavailableCategory> unavailable =
                comparison.evidence().unavailableCategories();
        if (unavailable.isEmpty()) {
            return y - 6;
        }
        int height = 28 + unavailable.size() * 14;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font,
                Component.translatable("detective.ui.comparison.evidence.availability"),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        for (int index = 0; index < unavailable.size(); index++) {
            IncidentComparisonViewModel.UnavailableCategory item = unavailable.get(index);
            Component category = Component.translatable(
                    "detective.ui.comparison.evidence.category."
                            + item.category().name().toLowerCase(Locale.ROOT));
            Component status = Component.translatable(item.availability()
                    == fr.apocalypsebleu.moddetective.core.comparison.IncidentComparison
                    .EvidenceAvailability.NOT_CAPTURED
                    ? "detective.ui.comparison.not_captured"
                    : "detective.ui.comparison.insufficient_comparable");
            Component line = Component.translatable(
                    "detective.ui.comparison.metric", category, status);
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(line.getString(), width - 20),
                    x + 10, y + 25 + index * 14, DetectiveUiRenderer.MUTED, false);
        }
        return y + height;
    }

    private int renderCaution(GuiGraphics graphics, int x, int y, int width) {
        Component message = Component.translatable("detective.ui.comparison.caution");
        int height = 20 + DetectiveUiRenderer.wrappedHeight(this.font, message, width - 20);
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        DetectiveUiRenderer.wrappedText(graphics, this.font, message,
                x + 10, y + 10, width - 20, DetectiveUiRenderer.MUTED);
        return y + height;
    }

    private int renderSimilarity(GuiGraphics graphics, int x, int y, int width) {
        int height = 54;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font,
                Component.translatable("detective.ui.comparison.similarity.title"),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        Component value = comparison.similarity().available()
                ? Component.literal(UiFormatters.percent(
                        comparison.similarity().score().orElseThrow() * 100.0))
                : Component.translatable("detective.ui.comparison.unavailable");
        graphics.drawString(this.font, value, x + 10, y + 25,
                comparison.similarity().available() ? DetectiveUiRenderer.TEXT : DetectiveUiRenderer.MUTED,
                false);
        graphics.drawString(this.font,
                Component.translatable(comparison.similarity().available()
                        ? "detective.ui.comparison.similarity.available"
                        : "detective.ui.comparison.similarity.insufficient"),
                x + 10, y + 39, DetectiveUiRenderer.MUTED, false);
        return y + height;
    }

    private int renderSides(GuiGraphics graphics, int x, int y, int width) {
        if (width < 500) {
            int afterFirst = renderSide(graphics, x, y, width, comparison.first(), "A");
            return renderSide(graphics, x, afterFirst + 6, width, comparison.second(), "B");
        }
        int gap = 6;
        int sideWidth = (width - gap) / 2;
        renderSide(graphics, x, y, sideWidth, comparison.first(), "A");
        renderSide(graphics, x + sideWidth + gap, y, width - sideWidth - gap,
                comparison.second(), "B");
        return y + 151;
    }

    private int renderSide(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            IncidentComparisonViewModel.Side side,
            String marker
    ) {
        int height = 151;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font,
                Component.translatable("detective.ui.comparison.incident_marker", marker),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        metric(graphics, x + 10, y + 25, width - 20,
                "detective.ui.comparison.timestamp",
                side.detectedAtEpochMs().isPresent()
                        ? UiFormatters.dateTime(side.detectedAtEpochMs().getAsLong()) : unavailable());
        metric(graphics, x + 10, y + 41, width - 20,
                "detective.ui.comparison.duration",
                side.stallDurationMs().isPresent()
                        ? UiFormatters.duration(side.stallDurationMs().getAsDouble()) : unavailable());
        metric(graphics, x + 10, y + 57, width - 20,
                "detective.ui.comparison.samples",
                side.capturedSampleCount().isPresent()
                        ? Integer.toString(side.capturedSampleCount().getAsInt()) : unavailable());
        Component attribution = side.incident().hasPrimarySuspect()
                ? Component.translatable("detective.ui.comparison.attribution.primary",
                        side.incident().primarySuspect())
                : Component.translatable("detective.ui.comparison.attribution."
                        + side.attributionState().name().toLowerCase(Locale.ROOT));
        graphics.drawString(this.font,
                Component.translatable("detective.ui.comparison.attribution_label"),
                x + 10, y + 73, DetectiveUiRenderer.MUTED, false);
        graphics.drawString(this.font,
                UiTextFitter.ellipsize(attribution.getString(), width - 20, this.font::width),
                x + 10, y + 87, DetectiveUiRenderer.TEXT, false);
        metric(graphics, x + 10, y + 103, width - 20,
                "detective.ui.comparison.dimension",
                side.dimensionId().map(UiFormatters::dimension).orElse(unavailable()));
        metric(graphics, x + 10, y + 119, width - 20,
                "detective.ui.comparison.memory.used",
                formatMemory(side.usedMemoryBytes()));
        metric(graphics, x + 10, y + 135, width - 20,
                "detective.ui.comparison.memory.maximum",
                formatMemory(side.maximumMemoryBytes()));
        return y + height;
    }

    private int renderEvidenceGroup(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String titleKey,
            List<IncidentComparisonViewModel.EvidenceItem> items
    ) {
        int rows = Math.max(1, items.size());
        int height = 28 + rows * 14;
        DetectiveUiRenderer.panel(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable(titleKey),
                x + 10, y + 9, DetectiveUiRenderer.ACCENT, false);
        if (items.isEmpty()) {
            Component empty = Component.translatable(comparison.evidence().comparisonAvailable()
                    ? "detective.ui.comparison.evidence.none"
                    : "detective.ui.comparison.evidence.unavailable");
            graphics.drawString(this.font, empty, x + 10, y + 25,
                    DetectiveUiRenderer.MUTED, false);
            return y + height;
        }
        for (int index = 0; index < items.size(); index++) {
            Component label = evidenceLabel(items.get(index));
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(label.getString(), width - 20),
                    x + 10, y + 25 + index * 14, DetectiveUiRenderer.TEXT, false);
        }
        return y + height;
    }

    private Component evidenceLabel(IncidentComparisonViewModel.EvidenceItem item) {
        return switch (item.kind()) {
            case OWNER -> Component.translatable(
                    "detective.ui.comparison.evidence.owner", item.ownerLabel());
            case TECHNICAL_SIGNATURE -> Component.translatable(
                    "detective.ui.comparison.evidence.signature", item.signatureNumber());
        };
    }

    private void metric(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            String labelKey,
            String value
    ) {
        Component line = Component.translatable(
                "detective.ui.comparison.metric", Component.translatable(labelKey), value);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), width),
                x, y, DetectiveUiRenderer.TEXT, false);
    }

    private static String formatMemory(OptionalLong bytes) {
        return bytes.isPresent() ? UiFormatters.memory(bytes.getAsLong()) : unavailable();
    }

    private static String unavailable() {
        return "—";
    }

    private static Map<String, String> safeOwnerDisplayNames(
            IncidentSearchRecord first,
            IncidentSearchRecord second
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        addSafeOwnerDisplayName(result, first);
        addSafeOwnerDisplayName(result, second);
        return Map.copyOf(result);
    }

    private static void addSafeOwnerDisplayName(
            Map<String, String> target,
            IncidentSearchRecord incident
    ) {
        if (incident.ownerIds().size() == 1 && incident.modDisplayNames().size() == 1) {
            target.putIfAbsent(incident.ownerIds().iterator().next(),
                    incident.modDisplayNames().iterator().next());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollState.isWithinViewport(mouseY) && scrollState.scrollWheel(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollState.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
