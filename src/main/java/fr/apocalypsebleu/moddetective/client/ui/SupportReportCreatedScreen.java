package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.support.LocalFolderOpener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Objects;

public final class SupportReportCreatedScreen extends Screen {
    private final Screen parent;
    private final Path report;
    private boolean openFolderFailed;

    public SupportReportCreatedScreen(Screen parent, Path report) {
        super(Component.translatable("detective.ui.export.success.title"));
        this.parent = parent;
        this.report = Objects.requireNonNull(report, "report").toAbsolutePath().normalize();
    }

    @Override
    protected void init() {
        int buttonY = this.height - 28;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.export.open_folder"), button -> openFolder())
                .bounds(this.width / 2 - 104, buttonY, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 + 4, buttonY, 100, 20)
                .build());
    }

    private void openFolder() {
        Path folder = report.getParent();
        openFolderFailed = LocalFolderOpener.open(folder) != LocalFolderOpener.Result.OPENED;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.export.success.title"),
                Component.translatable("detective.ui.export.success.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);
        int width = Math.min(440, this.width - 24);
        int left = (this.width - width) / 2;
        int top = DetectiveUiRenderer.HEADER_HEIGHT + 18;
        DetectiveUiRenderer.panel(graphics, left, top, width, 74);
        graphics.drawCenteredString(this.font,
                Component.translatable("detective.ui.export.success.body"),
                this.width / 2, top + 15, DetectiveUiRenderer.TEXT);
        graphics.drawCenteredString(this.font,
                Component.literal(report.getFileName().toString()),
                this.width / 2, top + 34, DetectiveUiRenderer.ACCENT);
        graphics.drawCenteredString(this.font,
                Component.translatable("detective.ui.export.local_only"),
                this.width / 2, top + 53, DetectiveUiRenderer.MUTED);
        if (openFolderFailed) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("detective.ui.export.open_folder_failed"),
                    this.width / 2, top + 66, 0xFFFF7777);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
