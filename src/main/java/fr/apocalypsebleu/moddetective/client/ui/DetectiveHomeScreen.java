package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.DetectiveSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class DetectiveHomeScreen extends Screen {
    private final Screen parent;
    private final boolean refreshOnInit;
    private IncidentIndexViewModel index;
    private boolean loading = true;
    private boolean loadFailed;
    private Button lastIncidentButton;
    private double scrollOffset;
    private int contentHeight;

    public DetectiveHomeScreen(Screen parent) {
        this(parent, null, true);
    }

    /** Allows the development-only visual harness to render deterministic states without disk I/O. */
    public DetectiveHomeScreen(Screen parent, IncidentIndexViewModel preloadedIndex) {
        this(parent, preloadedIndex, false);
    }

    private DetectiveHomeScreen(Screen parent, IncidentIndexViewModel preloadedIndex, boolean refreshOnInit) {
        super(Component.translatable("detective.ui.home.title"));
        this.parent = parent;
        this.index = preloadedIndex;
        this.loading = refreshOnInit;
        this.refreshOnInit = refreshOnInit;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(440, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int gap = 4;
        int buttonWidth = (contentWidth - gap * 2) / 3;
        int navigationY = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 24;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.home.incidents"),
                        button -> this.minecraft.setScreen(new IncidentListScreen(this, index)))
                .bounds(left, navigationY, buttonWidth, 20)
                .build());
        this.lastIncidentButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.home.last_incident"),
                        button -> openLastIncident())
                .bounds(left + buttonWidth + gap, navigationY, buttonWidth, 20)
                .build());
        this.lastIncidentButton.active = index != null && index.summary().lastIncident() != null;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.home.modpack_changes"),
                        button -> this.minecraft.setScreen(new ModpackChangesScreen(this)))
                .bounds(left + (buttonWidth + gap) * 2, navigationY, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());

        if (refreshOnInit) {
            refreshData();
        }
    }

    private void refreshData() {
        loading = true;
        loadFailed = false;
        DetectiveUiService.refreshIndex().whenComplete((loaded, error) -> this.minecraft.execute(() -> {
            if (error != null) {
                loadFailed = true;
            } else {
                index = loaded;
            }
            loading = false;
            if (lastIncidentButton != null) {
                lastIncidentButton.active = index != null && index.summary().lastIncident() != null;
            }
        }));
    }

    private void openLastIncident() {
        if (index != null && index.summary().lastIncident() != null) {
            this.minecraft.setScreen(new IncidentDetailScreen(this, index.summary().lastIncident()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.home.title"),
                Component.translatable("detective.ui.home.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);

        int contentWidth = Math.min(440, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int navigationY = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 24;
        int statusY = DetectiveUiRenderer.HEADER_HEIGHT + 8;
        int contentY = statusY - (int) scrollOffset;
        int statusHeight = 88;
        int summaryY = contentY + statusHeight + 8;
        int summaryHeight = index != null && index.unreadableFiles() > 0 ? 130 : 115;
        graphics.enableScissor(0, statusY, this.width, navigationY - 3);
        DetectiveUiRenderer.panel(graphics, left, contentY, contentWidth, statusHeight);
        DetectiveUiRenderer.panel(graphics, left, summaryY, contentWidth, summaryHeight);

        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, contentY + 22, DetectiveUiRenderer.MUTED);
        } else if (loadFailed) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.load_failed"),
                    this.width / 2, contentY + 22, 0xFFFF7777);
        } else if (index != null) {
            renderStatus(graphics, left + 12, contentY + 10, contentWidth - 24, index.summary());
            renderSummary(graphics, left + 12, summaryY + 10, contentWidth - 24, index);
        }
        graphics.disableScissor();
        contentHeight = statusHeight + 8 + summaryHeight;
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maximumScroll());
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatus(GuiGraphics graphics, int x, int y, int width, DetectiveSummaryViewModel summary) {
        graphics.drawString(this.font, Component.translatable("detective.ui.home.status"), x, y,
                DetectiveUiRenderer.ACCENT, false);
        boolean incidentsDetected = summary.sessionIncidents() > 0;
        graphics.fill(x, y + 18, x + 7, y + 25, incidentsDetected ? 0xFFE0A83E : 0xFF55AA55);
        graphics.drawString(this.font, Component.translatable(incidentsDetected
                        ? "detective.ui.home.issues_detected" : "detective.ui.home.monitoring"), x + 12, y + 17,
                DetectiveUiRenderer.TEXT, false);
        if (!incidentsDetected) {
            int nextY = DetectiveUiRenderer.wrappedText(graphics, this.font,
                    Component.translatable("detective.ui.home.empty.headline"), x, y + 35, width,
                    DetectiveUiRenderer.TEXT);
            DetectiveUiRenderer.wrappedText(graphics, this.font,
                    Component.translatable("detective.ui.home.empty.body"), x, nextY, width,
                    DetectiveUiRenderer.MUTED);
            return;
        }
        String countKey = summary.sessionIncidents() == 1
                ? "detective.ui.home.session_incidents.one" : "detective.ui.home.session_incidents.many";
        graphics.drawString(this.font, Component.translatable(countKey, summary.sessionIncidents()),
                x, y + 35, DetectiveUiRenderer.TEXT, false);
        int strong = summary.highEvidenceIncidents();
        String strongKey = strong == 1
                ? "detective.ui.home.strong_attribution.one" : "detective.ui.home.strong_attribution.many";
        DetectiveUiRenderer.wrappedText(graphics, this.font, Component.translatable(strongKey, strong),
                x, y + 50, width, DetectiveUiRenderer.MUTED);
    }

    private void renderSummary(GuiGraphics graphics, int x, int y, int width, IncidentIndexViewModel loaded) {
        DetectiveSummaryViewModel summary = loaded.summary();
        graphics.drawString(this.font, Component.translatable("detective.ui.home.session"), x, y,
                DetectiveUiRenderer.ACCENT, false);
        IncidentSummaryViewModel last = summary.lastIncident();
        drawMetric(graphics, x, y + 17, "detective.ui.home.metric.incidents",
                Integer.toString(summary.sessionIncidents()));
        drawMetric(graphics, x, y + 32, "detective.ui.home.metric.recent",
                Integer.toString(summary.recentIncidents()));
        drawMetric(graphics, x, y + 47, "detective.ui.home.metric.last_freeze",
                last == null ? Component.translatable("detective.ui.none").getString()
                        : UiFormatters.duration(last.durationMs()));
        drawMetric(graphics, x, y + 62, "detective.ui.home.metric.last_suspect",
                last == null || !last.hasPrimarySuspect()
                        ? Component.translatable("detective.ui.none").getString() : last.primarySuspect());
        drawMetric(graphics, x, y + 77, "detective.ui.home.metric.evidence",
                summary.highEvidenceIncidents() + " / " + summary.moderateEvidenceIncidents());
        drawMetric(graphics, x, y + 92, "detective.ui.home.metric.status",
                Component.translatable("detective.ui.home.status.monitoring").getString());
        if (loaded.unreadableFiles() > 0) {
            graphics.drawString(this.font,
                    Component.translatable("detective.ui.unreadable_files", loaded.unreadableFiles()),
                    x, y + 107, 0xFFFFAA55, false);
        }
    }

    private void drawMetric(GuiGraphics graphics, int x, int y, String labelKey, String value) {
        graphics.drawString(this.font, Component.translatable("detective.ui.home.metric",
                        Component.translatable(labelKey), value),
                x, y, DetectiveUiRenderer.TEXT, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Mth.clamp(scrollOffset - scrollY * 24.0, 0.0, maximumScroll());
        return true;
    }

    private double maximumScroll() {
        int statusY = DetectiveUiRenderer.HEADER_HEIGHT + 8;
        int navigationY = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 24;
        int viewportHeight = Math.max(1, navigationY - 3 - statusY);
        return Math.max(0, contentHeight - viewportHeight);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
