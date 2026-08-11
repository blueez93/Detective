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

public final class DetectiveHomeScreen extends Screen {
    private final Screen parent;
    private IncidentIndexViewModel index;
    private boolean loading = true;
    private boolean loadFailed;
    private Button lastIncidentButton;

    public DetectiveHomeScreen(Screen parent) {
        super(Component.translatable("detective.ui.home.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(440, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int gap = 4;
        int buttonWidth = (contentWidth - gap * 2) / 3;
        int navigationY = this.height - 58;

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

        refreshData();
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
        DetectiveUiRenderer.footer(graphics, this.width, this.height);

        int contentWidth = Math.min(440, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        int availableHeight = this.height - DetectiveUiRenderer.HEADER_HEIGHT - 70;
        int statusHeight = Math.max(54, Math.min(72, availableHeight / 2 - 4));
        int statusY = DetectiveUiRenderer.HEADER_HEIGHT + 8;
        int summaryY = statusY + statusHeight + 8;
        int summaryHeight = Math.max(54, availableHeight - statusHeight - 8);
        DetectiveUiRenderer.panel(graphics, left, statusY, contentWidth, statusHeight);
        DetectiveUiRenderer.panel(graphics, left, summaryY, contentWidth, summaryHeight);

        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, statusY + 22, DetectiveUiRenderer.MUTED);
        } else if (loadFailed) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.load_failed"),
                    this.width / 2, statusY + 22, 0xFFFF7777);
        } else if (index != null) {
            renderStatus(graphics, left + 12, statusY + 10, contentWidth - 24, index.summary());
            renderSummary(graphics, left + 12, summaryY + 10, contentWidth - 24, index);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatus(GuiGraphics graphics, int x, int y, int width, DetectiveSummaryViewModel summary) {
        graphics.drawString(this.font, Component.translatable("detective.ui.home.status"), x, y,
                DetectiveUiRenderer.ACCENT, false);
        graphics.fill(x, y + 17, x + 7, y + 24, 0xFF55AA55);
        graphics.drawString(this.font, Component.translatable("detective.ui.home.monitoring"), x + 12, y + 16,
                DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("detective.ui.home.session_incidents", summary.sessionIncidents()),
                x, y + 32, DetectiveUiRenderer.MUTED, false);
        IncidentSummaryViewModel last = summary.lastIncident();
        if (last != null && y + 47 < this.height - 70) {
            String text = Component.translatable("detective.ui.home.last_summary",
                    UiFormatters.duration(last.durationMs()), last.hasPrimarySuspect()
                            ? last.primarySuspect()
                            : Component.translatable(last.evidence().translationKey()).getString()).getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(text, width), x, y + 47,
                    DetectiveUiRenderer.MUTED, false);
        }
    }

    private void renderSummary(GuiGraphics graphics, int x, int y, int width, IncidentIndexViewModel loaded) {
        DetectiveSummaryViewModel summary = loaded.summary();
        graphics.drawString(this.font, Component.translatable("detective.ui.home.summary"), x, y,
                DetectiveUiRenderer.ACCENT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.home.recent", summary.recentIncidents()),
                x, y + 17, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font,
                Component.translatable("detective.ui.home.evidence_counts",
                        summary.highEvidenceIncidents(), summary.moderateEvidenceIncidents()),
                x, y + 32, DetectiveUiRenderer.TEXT, false);
        if (summary.totalIncidents() == 0) {
            graphics.drawString(this.font, Component.translatable("detective.ui.home.empty"), x, y + 47,
                    DetectiveUiRenderer.MUTED, false);
        } else if (loaded.unreadableFiles() > 0) {
            graphics.drawString(this.font,
                    Component.translatable("detective.ui.unreadable_files", loaded.unreadableFiles()),
                    x, y + 47, 0xFFFFAA55, false);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
