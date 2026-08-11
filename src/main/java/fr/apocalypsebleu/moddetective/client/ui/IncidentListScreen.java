package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class IncidentListScreen extends Screen {
    private final Screen parent;
    private IncidentIndexViewModel index;
    private IncidentSelectionList list;
    private boolean loading;
    private boolean loadFailed;

    public IncidentListScreen(Screen parent, IncidentIndexViewModel index) {
        super(Component.translatable("detective.ui.incidents.title"));
        this.parent = parent;
        this.index = index;
    }

    @Override
    protected void init() {
        this.list = this.addRenderableWidget(new IncidentSelectionList(
                this.minecraft, this.width,
                Math.max(20, this.height - DetectiveUiRenderer.HEADER_HEIGHT - DetectiveUiRenderer.FOOTER_HEIGHT + 2),
                DetectiveUiRenderer.HEADER_HEIGHT + 2, 64));
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
        if (index == null) {
            loading = true;
            DetectiveUiService.cachedIndex().whenComplete((loaded, error) -> this.minecraft.execute(() -> {
                loading = false;
                loadFailed = error != null;
                if (error == null) {
                    index = loaded;
                    list.setIncidents(loaded);
                }
            }));
        } else {
            list.setIncidents(index);
        }
    }

    private void openIncident(IncidentSummaryViewModel incident) {
        this.minecraft.setScreen(new IncidentDetailScreen(this, incident));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.incidents.title"),
                Component.translatable("detective.ui.incidents.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, this.height / 2, DetectiveUiRenderer.MUTED);
        } else if (loadFailed) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.load_failed"),
                    this.width / 2, this.height / 2, 0xFFFF7777);
        } else if (list.isEmpty()) {
            int y = this.height / 2 - 24;
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.incidents.empty.title"),
                    this.width / 2, y, DetectiveUiRenderer.TEXT);
            y = DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.incidents.empty.body"), this.width / 2, y + 16,
                    Math.min(420, this.width - 32), DetectiveUiRenderer.MUTED);
            DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.incidents.empty.hint"), this.width / 2, y + 3,
                    Math.min(420, this.width - 32), DetectiveUiRenderer.MUTED);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private final class IncidentSelectionList extends ObjectSelectionList<IncidentSelectionList.Entry> {
        private IncidentSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void setIncidents(IncidentIndexViewModel loaded) {
            clearEntries();
            loaded.incidents().forEach(incident -> addEntry(new Entry(incident)));
        }

        boolean isEmpty() {
            return children().isEmpty();
        }

        @Override
        public int getRowWidth() {
            return Math.min(520, getWidth() - 28);
        }

        @Override
        protected int getScrollbarPosition() {
            return Math.min(getWidth() - 8, getRowRight() + 4);
        }

        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final IncidentSummaryViewModel incident;

            private Entry(IncidentSummaryViewModel incident) {
                this.incident = incident;
            }

            @Override
            public void render(
                    GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick
            ) {
                if (hovered) {
                    graphics.fill(left, top, left + width, top + height, 0x553A4B5C);
                }
                Component duration = Component.translatable("detective.ui.incidents.freeze",
                        UiFormatters.duration(incident.durationMs()));
                int badgeWidth = DetectiveUiRenderer.badgeWidth(font, incident.evidence());
                graphics.drawString(font, duration,
                        left + 6, top + 6, DetectiveUiRenderer.TEXT, false);
                DetectiveUiRenderer.badge(graphics, font, incident.evidence(),
                        left + width - badgeWidth - 5, top + 3);
                Component primary = incident.hasPrimarySuspect()
                        ? Component.translatable("detective.ui.primary_short", incident.primarySuspect())
                        : Component.translatable(incident.evidence().listSummaryKey());
                var primaryLines = font.split(primary, width - 12);
                int lineY = top + 21;
                for (int line = 0; line < Math.min(2, primaryLines.size()); line++) {
                    graphics.drawString(font, primaryLines.get(line), left + 6, lineY,
                            line == 0 ? DetectiveUiRenderer.TEXT : DetectiveUiRenderer.MUTED, false);
                    lineY += font.lineHeight + 1;
                }
                Component context = Component.translatable("detective.ui.incidents.context",
                        incident.occurredAt(), incident.dimension(), incident.coordinates());
                graphics.drawString(font, font.plainSubstrByWidth(context.getString(), width - 12),
                        left + 6, top + 50, DetectiveUiRenderer.MUTED, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) {
                    return false;
                }
                IncidentSelectionList.this.setSelected(this);
                openIncident(incident);
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.translatable("detective.ui.incidents.narration",
                        UiFormatters.duration(incident.durationMs()),
                        incident.hasPrimarySuspect() ? incident.primarySuspect()
                                : Component.translatable(incident.evidence().translationKey()),
                        incident.occurredAt());
            }
        }
    }
}
