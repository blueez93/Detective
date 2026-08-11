package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class DetectiveUiRenderer {
    static final int HEADER_HEIGHT = 42;
    static final int FOOTER_HEIGHT = 46;
    static final int TEXT = 0xFFE6E6E6;
    static final int MUTED = 0xFFAAAAAA;
    static final int PANEL = 0xCC111820;
    static final int PANEL_BORDER = 0xFF394554;
    static final int ACCENT = 0xFF6FA8DC;

    private DetectiveUiRenderer() {}

    static void header(GuiGraphics graphics, Font font, int width, Component title, Component subtitle) {
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xDD0B1118);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0xFF354657);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        graphics.drawCenteredString(font, subtitle, width / 2, 23, MUTED);
    }

    static void footer(GuiGraphics graphics, Font font, int width, int height) {
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, 0xDD0B1118);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, 0xFF354657);
        Component tagline = Component.translatable("detective.ui.tagline");
        graphics.drawCenteredString(font, tagline, width / 2, height - FOOTER_HEIGHT + 7, MUTED);
    }

    static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, PANEL_BORDER);
    }

    static void widgets(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (var renderable : screen.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    static int badgeWidth(Font font, EvidenceBadge badge) {
        return font.width(Component.translatable(badge.translationKey())) + 10;
    }

    static void badge(GuiGraphics graphics, Font font, EvidenceBadge badge, int x, int y) {
        Component label = Component.translatable(badge.translationKey());
        int width = font.width(label) + 10;
        graphics.fill(x, y, x + width, y + 14, 0xE6181E25);
        graphics.renderOutline(x, y, width, 14, badge.color());
        graphics.drawString(font, label, x + 5, y + 3, badge.color(), false);
    }

    static int wrappedText(
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int width,
            int color
    ) {
        var lines = font.split(text, width);
        for (var line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight + 2;
        }
        return y;
    }

    static int wrappedHeight(Font font, Component text, int width) {
        return font.split(text, width).size() * (font.lineHeight + 2);
    }

    static int centeredWrappedText(
            GuiGraphics graphics,
            Font font,
            Component text,
            int centerX,
            int y,
            int width,
            int color
    ) {
        var lines = font.split(text, width);
        for (var line : lines) {
            graphics.drawCenteredString(font, line, centerX, y, color);
            y += font.lineHeight + 2;
        }
        return y;
    }
}
