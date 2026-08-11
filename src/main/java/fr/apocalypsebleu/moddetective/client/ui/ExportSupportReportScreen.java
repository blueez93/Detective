package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.support.DetectiveSupportService;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ExportSupportReportScreen extends Screen {
    private final Screen parent;
    private final IncidentSummaryViewModel incident;
    private boolean exporting;
    private boolean exportFailed;
    private Button exportButton;

    public ExportSupportReportScreen(Screen parent, IncidentSummaryViewModel incident) {
        super(Component.translatable("detective.ui.export.title"));
        this.parent = parent;
        this.incident = incident;
    }

    @Override
    protected void init() {
        int buttonY = this.height - 28;
        this.exportButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.export.action"), button -> export())
                .bounds(this.width / 2 - 124, buttonY, 120, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.cancel"), button -> onClose())
                .bounds(this.width / 2 + 4, buttonY, 120, 20)
                .build());
    }

    private void export() {
        if (exporting || incident == null) {
            return;
        }
        exporting = true;
        exportFailed = false;
        exportButton.active = false;
        DetectiveSupportService.exportSupportReport(incident.source()).whenComplete((report, error) ->
                this.minecraft.execute(() -> {
                    exporting = false;
                    if (error != null) {
                        exportFailed = true;
                        exportButton.active = true;
                    } else {
                        this.minecraft.setScreen(new SupportReportCreatedScreen(parent, report));
                    }
                }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.export.title"),
                Component.translatable("detective.ui.export.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);

        int width = Math.min(460, this.width - 24);
        int left = (this.width - width) / 2;
        int top = DetectiveUiRenderer.HEADER_HEIGHT + 7;
        int bottom = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 4;
        DetectiveUiRenderer.panel(graphics, left, top, width, Math.max(40, bottom - top));
        int x = left + 12;
        int innerWidth = width - 24;
        int y = top + 10;
        y = DetectiveUiRenderer.wrappedText(graphics, this.font,
                Component.translatable("detective.ui.export.description"), x, y, innerWidth,
                DetectiveUiRenderer.TEXT) + 5;
        for (String key : new String[]{
                "detective.ui.export.contains.incident",
                "detective.ui.export.contains.mods",
                "detective.ui.export.contains.changes",
                "detective.ui.export.contains.versions",
                "detective.ui.export.contains.system"}) {
            graphics.drawString(this.font, Component.translatable("detective.ui.export.bullet",
                    Component.translatable(key)), x + 4, y, DetectiveUiRenderer.MUTED, false);
            y += 12;
        }
        DetectiveUiRenderer.wrappedText(graphics, this.font,
                Component.translatable("detective.ui.export.local_only"), x, y + 3, innerWidth,
                DetectiveUiRenderer.ACCENT);
        if (exporting) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.export.creating"),
                    this.width / 2, bottom - 14, DetectiveUiRenderer.MUTED);
        } else if (exportFailed) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.export.failed"),
                    this.width / 2, bottom - 14, 0xFFFF7777);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
