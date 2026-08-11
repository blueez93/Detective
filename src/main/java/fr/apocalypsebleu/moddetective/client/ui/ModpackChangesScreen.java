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

import java.util.List;

public final class ModpackChangesScreen extends Screen {
    private final Screen parent;
    private final ModpackChangesViewModel preloadedChanges;
    private ModpackChangesViewModel changes;
    private ChangeList list;

    public ModpackChangesScreen(Screen parent) {
        this(parent, null);
    }

    /** Allows the development-only visual harness to exercise first-launch and empty states. */
    public ModpackChangesScreen(Screen parent, ModpackChangesViewModel preloadedChanges) {
        super(Component.translatable("detective.ui.modpack.title"));
        this.parent = parent;
        this.preloadedChanges = preloadedChanges;
    }

    @Override
    protected void init() {
        changes = preloadedChanges == null ? DetectiveUiService.modpackChanges() : preloadedChanges;
        list = this.addRenderableWidget(new ChangeList(
                this.minecraft, this.width,
                Math.max(20, this.height - DetectiveUiRenderer.HEADER_HEIGHT - DetectiveUiRenderer.FOOTER_HEIGHT + 2),
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
                Component.translatable("detective.ui.modpack.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
        if (!changes.comparisonAvailable()) {
            int y = this.height / 2 - 12;
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.modpack.unavailable.title"),
                    this.width / 2, y, DetectiveUiRenderer.TEXT);
            DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.modpack.unavailable.body"), this.width / 2, y + 16,
                    Math.min(420, this.width - 32), DetectiveUiRenderer.MUTED);
        } else if (changes.changes().isEmpty()) {
            int y = this.height / 2 - 12;
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.modpack.empty.title"),
                    this.width / 2, y, DetectiveUiRenderer.TEXT);
            DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.modpack.empty.body"), this.width / 2, y + 16,
                    Math.min(440, this.width - 32), DetectiveUiRenderer.MUTED);
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
                for (ModpackChangesViewModel.Type type : List.of(
                        ModpackChangesViewModel.Type.ADDED,
                        ModpackChangesViewModel.Type.UPDATED,
                        ModpackChangesViewModel.Type.REMOVED)) {
                    List<ModpackChangesViewModel.Change> matching = model.changes().stream()
                            .filter(change -> change.type() == type)
                            .toList();
                    if (!matching.isEmpty()) {
                        addEntry(new Entry(type));
                        matching.forEach(change -> addEntry(new Entry(change)));
                    }
                }
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
            private final ModpackChangesViewModel.Type section;

            private Entry(ModpackChangesViewModel.Change change) {
                this.change = change;
                this.section = null;
            }

            private Entry(ModpackChangesViewModel.Type section) {
                this.change = null;
                this.section = section;
            }

            @Override
            public void render(
                    GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick
            ) {
                if (section != null) {
                    Component heading = Component.translatable(
                            "detective.ui.modpack." + section.name().toLowerCase());
                    graphics.drawString(font, heading, left + 6, top + 16, color(section), false);
                    graphics.fill(left + 6 + font.width(heading) + 8, top + 20, left + width - 6, top + 21,
                            0xFF394554);
                    return;
                }
                if (hovered) {
                    graphics.fill(left, top, left + width, top + height, 0x553A4B5C);
                }
                graphics.drawString(font, font.plainSubstrByWidth(
                                change.modName() + " (" + change.modId() + ")", width - 12),
                        left + 6, top + 6, DetectiveUiRenderer.TEXT, false);
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
                if (section != null) {
                    return Component.translatable("detective.ui.modpack." + section.name().toLowerCase());
                }
                return switch (change.type()) {
                    case ADDED -> Component.translatable("detective.ui.modpack.narration.added",
                            change.modName(), change.newVersion());
                    case UPDATED -> Component.translatable("detective.ui.modpack.narration.updated",
                            change.modName(), change.oldVersion(), change.newVersion());
                    case REMOVED -> Component.translatable("detective.ui.modpack.narration.removed",
                            change.modName(), change.oldVersion());
                };
            }

            private int color(ModpackChangesViewModel.Type type) {
                return switch (type) {
                    case ADDED -> 0xFF55AA55;
                    case REMOVED -> 0xFFD9534F;
                    case UPDATED -> 0xFFE0A83E;
                };
            }
        }
    }
}
