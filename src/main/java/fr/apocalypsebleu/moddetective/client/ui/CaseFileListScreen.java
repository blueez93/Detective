package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseFileViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.CaseIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class CaseFileListScreen extends Screen {
    private final Screen parent;
    private CaseIndexViewModel index;
    private CaseSelectionList list;
    private boolean loading;
    private boolean loadFailed;

    public CaseFileListScreen(Screen parent) {
        this(parent, null);
    }

    /** Allows development-only validation routes to render deterministic local Case states. */
    public CaseFileListScreen(Screen parent, CaseIndexViewModel preloadedIndex) {
        super(Component.translatable("detective.ui.cases.title"));
        this.parent = parent;
        this.index = preloadedIndex;
    }

    @Override
    protected void init() {
        this.list = this.addRenderableWidget(new CaseSelectionList(
                this.minecraft, this.width,
                Math.max(20, this.height - DetectiveUiRenderer.HEADER_HEIGHT - DetectiveUiRenderer.FOOTER_HEIGHT + 2),
                DetectiveUiRenderer.HEADER_HEIGHT + 2, 84));
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
        if (index == null) {
            loading = true;
            DetectiveUiService.cachedCases().whenComplete((loaded, error) -> this.minecraft.execute(() -> {
                loading = false;
                loadFailed = error != null;
                if (error == null) {
                    index = loaded;
                    list.setCases(loaded);
                }
            }));
        } else {
            list.setCases(index);
        }
    }

    private void openCase(CaseFileViewModel caseFile) {
        this.minecraft.setScreen(new CaseFileDetailScreen(this, caseFile));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.cases.title"),
                Component.translatable("detective.ui.cases.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, this.height / 2, DetectiveUiRenderer.MUTED);
        } else if (loadFailed) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.cases.load_failed"),
                    this.width / 2, this.height / 2, 0xFFFF7777);
        } else if (list.isEmpty()) {
            int y = this.height / 2 - 12;
            graphics.drawCenteredString(this.font,
                    Component.translatable("detective.ui.cases.empty"),
                    this.width / 2, y, DetectiveUiRenderer.TEXT);
            DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.cases.empty.body"),
                    this.width / 2, y + 17, Math.min(440, this.width - 32), DetectiveUiRenderer.MUTED);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private final class CaseSelectionList extends ObjectSelectionList<CaseSelectionList.Entry> {
        private CaseSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void setCases(CaseIndexViewModel loaded) {
            clearEntries();
            loaded.cases().forEach(caseFile -> addEntry(new Entry(caseFile)));
        }

        boolean isEmpty() {
            return children().isEmpty();
        }

        @Override
        public int getRowWidth() {
            return Math.min(540, getWidth() - 28);
        }

        @Override
        protected int getScrollbarPosition() {
            return Math.min(getWidth() - 8, getRowRight() + 4);
        }

        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final CaseFileViewModel caseFile;

            private Entry(CaseFileViewModel caseFile) {
                this.caseFile = caseFile;
            }

            @Override
            public void render(
                    GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick
            ) {
                if (hovered) {
                    graphics.fill(left, top, left + width, top + height, 0x553A4B5C);
                }
                graphics.drawString(font,
                        Component.translatable("detective.ui.cases.case_short", caseFile.shortCaseId()),
                        left + 6, top + 6, DetectiveUiRenderer.TEXT, false);
                graphics.drawString(font,
                        Component.translatable("detective.ui.cases.occurrences", caseFile.occurrenceCount()),
                        left + width - 6 - font.width(Component.translatable(
                                "detective.ui.cases.occurrences", caseFile.occurrenceCount())),
                        top + 6, DetectiveUiRenderer.ACCENT, false);
                Component seenRange = Component.translatable("detective.ui.cases.seen_range",
                                UiFormatters.compactDateTime(caseFile.firstSeenEpochMs()),
                                UiFormatters.compactDateTime(caseFile.lastSeenEpochMs()));
                graphics.drawString(font, font.plainSubstrByWidth(seenRange.getString(), width - 12),
                        left + 6, top + 22, DetectiveUiRenderer.MUTED, false);
                Component durationSummary = Component.translatable("detective.ui.cases.duration_summary",
                                UiFormatters.duration(caseFile.averageStallDurationMs()),
                                UiFormatters.duration(caseFile.longestStallDurationMs()));
                graphics.drawString(font, font.plainSubstrByWidth(durationSummary.getString(), width - 12),
                        left + 6, top + 38, DetectiveUiRenderer.TEXT, false);
                Component strengthSummary = Component.translatable("detective.ui.cases.strength_summary",
                                UiFormatters.percent(caseFile.consistencyPercent()),
                                UiFormatters.percent(caseFile.evidenceStrengthPercent()));
                Component openHint = Component.translatable("detective.ui.cases.open");
                int openWidth = font.width(openHint);
                int strengthWidth = Math.max(40, width - 24 - openWidth);
                graphics.drawString(font, font.plainSubstrByWidth(strengthSummary.getString(), strengthWidth),
                        left + 6, top + 54, DetectiveUiRenderer.MUTED, false);
                graphics.drawString(font, openHint,
                        left + width - 6 - openWidth, top + 54, DetectiveUiRenderer.ACCENT, false);
                graphics.fill(left + 6, top + height - 2, left + width - 6, top + height - 1, 0x334C6A80);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) {
                    return false;
                }
                CaseSelectionList.this.setSelected(this);
                openCase(caseFile);
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.translatable("detective.ui.cases.narration",
                        caseFile.shortCaseId(), caseFile.occurrenceCount(),
                        UiFormatters.percent(caseFile.consistencyPercent()));
            }
        }
    }
}
