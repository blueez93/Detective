package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.support.DetectiveSupportService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClearIncidentHistoryScreen extends Screen {
    private final DetectiveSettingsScreen parent;
    private boolean clearing;
    private boolean failed;
    private Button clearButton;

    public ClearIncidentHistoryScreen(DetectiveSettingsScreen parent) {
        super(Component.translatable("detective.ui.settings.clear_confirm.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonY = this.height / 2 + 30;
        clearButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.settings.clear_confirm.action"),
                        button -> clear())
                .bounds(this.width / 2 - 124, buttonY, 120, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.cancel"), button -> onClose())
                .bounds(this.width / 2 + 4, buttonY, 120, 20)
                .build());
    }

    private void clear() {
        if (clearing) {
            return;
        }
        clearing = true;
        failed = false;
        clearButton.active = false;
        DetectiveSupportService.clearIncidentHistory().whenComplete((result, error) ->
                this.minecraft.execute(() -> {
                    clearing = false;
                    if (error != null) {
                        failed = true;
                        clearButton.active = true;
                    } else {
                        parent.historyCleared(result.deleted());
                        this.minecraft.setScreen(parent);
                    }
                }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int width = Math.min(430, this.width - 32);
        int left = (this.width - width) / 2;
        int top = this.height / 2 - 50;
        DetectiveUiRenderer.panel(graphics, left, top, width, 92);
        graphics.drawCenteredString(this.font,
                Component.translatable("detective.ui.settings.clear_confirm.title"),
                this.width / 2, top + 13, 0xFFFF7777);
        DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                Component.translatable("detective.ui.settings.clear_confirm.body"),
                this.width / 2, top + 33, width - 24, DetectiveUiRenderer.TEXT);
        if (clearing) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.settings.clearing"),
                    this.width / 2, top + 70, DetectiveUiRenderer.MUTED);
        } else if (failed) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.settings.clear_failed"),
                    this.width / 2, top + 70, 0xFFFF7777);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
