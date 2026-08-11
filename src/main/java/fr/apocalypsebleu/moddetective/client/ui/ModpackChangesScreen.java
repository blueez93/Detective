package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.ModpackChangesViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class ModpackChangesScreen extends Screen {
    private final Screen parent;
    private ModpackChangesViewModel changes;
    private ChangeList list;

    public ModpackChangesScreen(Screen parent) {
        super(Component.translatable("detective.ui.modpack.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        changes = DetectiveUiService.modpackChanges();
        list = this.addRenderableWidget(new ChangeList(
                this.minecraft, this.width, Math.max(20, this.height - 80),
                DetectiveUiRenderer.HEADER_HEIGHT + 2, 38));
        list.setChanges(changes);
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.modpack.title"),
                Component.translatable("detective.ui.modpack.subtitle", changes.currentModCount()));
        DetectiveUiRenderer.footer(graphics, this.width, this.height);
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
        if (!changes.comparisonAvailable()) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.modpack.unavailable"),
                    this.width / 2, this.height / 2, DetectiveUiRenderer.MUTED);
        } else if (changes.changes().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.modpack.empty"),
                    this.width / 2, this.height / 2, DetectiveUiRenderer.MUTED);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private final class ChangeList extends ObjectSelectionList<ChangeList.Entry> {
        private ChangeList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void setChanges(ModpackChangesViewModel model) {
            clearEntries();
            if (model.comparisonAvailable()) {
                model.changes().forEach(change -> addEntry(new Entry(change)));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(500, getWidth() - 28);
        }

        @Override
        protected int getScrollbarPosition() {
            return Math.min(getWidth() - 8, getRowRight() + 4);
        }

        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final ModpackChangesViewModel.Change change;

            private Entry(ModpackChangesViewModel.Change change) {
                this.change = change;
            }

            @Override
            public void render(
                    GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick
            ) {
                if (hovered) {
                    graphics.fill(left, top, left + width, top + height, 0x553A4B5C);
                }
                Component type = Component.translatable("detective.ui.modpack." + change.type().name().toLowerCase());
                int color = switch (change.type()) {
                    case ADDED -> 0xFF55AA55;
                    case REMOVED -> 0xFFD9534F;
                    case UPDATED -> 0xFFE0A83E;
                };
                graphics.drawString(font, type, left + 6, top + 6, color, false);
                graphics.drawString(font, font.plainSubstrByWidth(change.modName() + " (" + change.modId() + ")", width - 92),
                        left + 82, top + 6, DetectiveUiRenderer.TEXT, false);
                String versions = switch (change.type()) {
                    case ADDED -> change.newVersion();
                    case REMOVED -> change.oldVersion();
                    case UPDATED -> change.oldVersion() + "  →  " + change.newVersion();
                };
                graphics.drawString(font, font.plainSubstrByWidth(versions, width - 12),
                        left + 6, top + 21, DetectiveUiRenderer.MUTED, false);
            }

            @Override
            public Component getNarration() {
                return Component.literal(change.type() + ", " + change.modName() + ", "
                        + change.oldVersion() + ", " + change.newVersion());
            }
        }
    }
}
