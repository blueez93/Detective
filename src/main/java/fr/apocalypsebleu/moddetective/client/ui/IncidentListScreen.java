package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.ui.data.DetectiveUiService;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQuery;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentQueryResult;
import fr.apocalypsebleu.moddetective.client.ui.data.query.IncidentSearchRecord;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentIndexViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentInvestigationState;
import fr.apocalypsebleu.moddetective.client.ui.model.IncidentSummaryViewModel;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class IncidentListScreen extends Screen {
    private static final int SEARCH_TOP = DetectiveUiRenderer.HEADER_HEIGHT + 4;
    private static final int ACTION_TOP = SEARCH_TOP + 24;
    private static final int FILTER_FIRST_TOP = ACTION_TOP + 24;
    private static final int FILTER_SECOND_TOP = FILTER_FIRST_TOP + 24;
    private static final int LIST_COLLAPSED_TOP = FILTER_FIRST_TOP;
    private static final int LIST_EXPANDED_TOP = FILTER_SECOND_TOP + 24;

    private final Screen parent;
    private final IncidentIndexViewModel initialIndex;
    private final IncidentInvestigationState state;
    private final boolean deterministicValidationState;
    private IncidentSelectionList list;
    private EditBox searchBox;
    private Button clearSearchButton;
    private Button evidenceButton;
    private Button durationButton;
    private Button caseButton;
    private Button sortButton;
    private Button clearFiltersButton;
    private Button filterToggleButton;
    private Button compareButton;
    private Button cancelSelectionButton;
    private boolean selectionMode;
    private boolean filtersOpen;
    private boolean loading;
    private boolean loadFailed;
    private boolean selectionLimitReached;
    private int queryGeneration;

    public IncidentListScreen(Screen parent, IncidentIndexViewModel index) {
        this(parent, index, new IncidentInvestigationState(), false);
    }

    /** Development-only validation routes may supply a fully deterministic local UI state. */
    public IncidentListScreen(Screen parent, IncidentInvestigationState validationState) {
        this(parent, null, validationState, true);
    }

    private IncidentListScreen(
            Screen parent,
            IncidentIndexViewModel index,
            IncidentInvestigationState state,
            boolean deterministicValidationState
    ) {
        super(Component.translatable("detective.ui.incidents.title"));
        this.parent = parent;
        this.initialIndex = index;
        this.state = state;
        this.deterministicValidationState = deterministicValidationState;
    }

    @Override
    protected void init() {
        if (deterministicValidationState && state.activeFilterCount() > 0) {
            filtersOpen = true;
        }
        int contentWidth = Math.min(600, this.width - 16);
        int left = (this.width - contentWidth) / 2;
        int searchButtonWidth = Math.min(72, Math.max(54, contentWidth / 5));
        searchBox = this.addRenderableWidget(new EditBox(
                this.font, left, SEARCH_TOP, contentWidth - searchButtonWidth - 4, 20,
                Component.translatable("detective.ui.incidents.search")));
        searchBox.setHint(Component.translatable("detective.ui.incidents.search.hint"));
        searchBox.setValue(state.searchText());
        searchBox.setResponder(value -> {
            state.setSearchText(value);
            selectionLimitReached = false;
            refreshControls();
            requestQuery();
        });
        clearSearchButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.incidents.search.clear"), button -> {
                            searchBox.setValue("");
                            searchBox.setFocused(true);
                        })
                .bounds(left + contentWidth - searchButtonWidth, SEARCH_TOP, searchButtonWidth, 20)
                .build());

        int actionWidth = Math.min(132, Math.max(104, contentWidth / 3));
        filterToggleButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                    filtersOpen = !filtersOpen;
                    this.rebuildWidgets();
                }).bounds(left + contentWidth - actionWidth, ACTION_TOP, actionWidth, 20).build());

        int firstGap = 4;
        int firstWidth = (contentWidth - firstGap * 2) / 3;
        evidenceButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                    state.setEvidenceFilter(state.evidenceFilter().next());
                    criteriaChanged();
                }).bounds(left, FILTER_FIRST_TOP, firstWidth, 20).build());
        durationButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                    state.setDurationFilter(state.durationFilter().next());
                    criteriaChanged();
                }).bounds(left + firstWidth + firstGap, FILTER_FIRST_TOP, firstWidth, 20).build());
        caseButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                    state.setCaseFilter(state.caseFilter().next());
                    criteriaChanged();
                }).bounds(left + (firstWidth + firstGap) * 2, FILTER_FIRST_TOP,
                        contentWidth - (firstWidth + firstGap) * 2, 20).build());

        int clearWidth = Math.min(150, Math.max(112, contentWidth / 3));
        sortButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                    state.setSort(nextSort(state.sort()));
                    criteriaChanged();
                }).bounds(left, FILTER_SECOND_TOP, contentWidth - clearWidth - 4, 20).build());
        clearFiltersButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                    state.clearFilters();
                    criteriaChanged();
                }).bounds(left + contentWidth - clearWidth, FILTER_SECOND_TOP, clearWidth, 20).build());
        evidenceButton.visible = filtersOpen;
        durationButton.visible = filtersOpen;
        caseButton.visible = filtersOpen;
        sortButton.visible = filtersOpen;
        clearFiltersButton.visible = filtersOpen;

        int listTop = filtersOpen ? LIST_EXPANDED_TOP : LIST_COLLAPSED_TOP;
        int listHeight = Math.max(20, this.height - DetectiveUiRenderer.FOOTER_HEIGHT - listTop + 2);
        list = this.addRenderableWidget(new IncidentSelectionList(
                this.minecraft, this.width, listHeight, listTop, 64));

        int footerButtonWidth = Math.min(110, Math.max(82, (this.width - 32) / 4));
        int footerGap = 4;
        int footerTotal = footerButtonWidth * 3 + footerGap * 2;
        int footerLeft = (this.width - footerTotal) / 2;
        compareButton = this.addRenderableWidget(Button.builder(
                        Component.empty(), button -> onCompareAction())
                .bounds(footerLeft, this.height - 28, footerButtonWidth, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(footerLeft + footerButtonWidth + footerGap,
                        this.height - 28, footerButtonWidth, 20).build());
        cancelSelectionButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.incidents.compare.cancel"),
                        button -> cancelSelection())
                .bounds(footerLeft + (footerButtonWidth + footerGap) * 2,
                        this.height - 28, footerButtonWidth, 20).build());

        state.result().ifPresent(result -> list.setIncidents(result.matchingIncidents()));
        if (deterministicValidationState && state.selectionCount() > 0) {
            selectionMode = true;
        }
        refreshControls();
        if (state.result().isEmpty() && !deterministicValidationState) {
            loading = initialIndex == null;
            requestQuery();
        }
    }

    private void criteriaChanged() {
        selectionLimitReached = false;
        selectionMode = false;
        refreshControls();
        requestQuery();
    }

    private void requestQuery() {
        if (deterministicValidationState || this.minecraft == null) {
            return;
        }
        int generation = ++queryGeneration;
        loading = true;
        loadFailed = false;
        IncidentQuery requested = state.query();
        DetectiveUiService.queryIncidents(requested).whenComplete((result, error) ->
                this.minecraft.execute(() -> {
                    if (generation != queryGeneration) {
                        return;
                    }
                    loading = false;
                    loadFailed = error != null;
                    if (error == null) {
                        state.applyResult(result);
                        list.setIncidents(result.matchingIncidents());
                    }
                    refreshControls();
                }));
    }

    private void onCompareAction() {
        if (!selectionMode) {
            selectionMode = true;
            selectionLimitReached = false;
            refreshControls();
            return;
        }
        if (!state.canCompare()) {
            return;
        }
        List<IncidentSearchRecord> selected = state.selectedIncidents();
        this.minecraft.setScreen(new IncidentComparisonScreen(
                this, selected.get(0), selected.get(1)));
    }

    private void cancelSelection() {
        selectionMode = false;
        selectionLimitReached = false;
        state.clearSelection();
        refreshControls();
    }

    private void refreshControls() {
        if (clearSearchButton == null) {
            return;
        }
        clearSearchButton.active = !state.searchText().isBlank();
        evidenceButton.setMessage(Component.translatable(
                "detective.ui.incidents.filter.evidence",
                Component.translatable(evidenceTranslationKey(state.evidenceFilter()))));
        durationButton.setMessage(Component.translatable(
                "detective.ui.incidents.filter.duration",
                Component.translatable(durationTranslationKey(state.durationFilter()))));
        caseButton.setMessage(Component.translatable(
                "detective.ui.incidents.filter.case",
                Component.translatable(caseTranslationKey(state.caseFilter()))));
        sortButton.setMessage(Component.translatable(
                "detective.ui.incidents.sort",
                Component.translatable(sortTranslationKey(state.sort()))));
        clearFiltersButton.setMessage(Component.translatable(
                "detective.ui.incidents.filters.clear", state.activeFilterCount()));
        clearFiltersButton.active = state.activeFilterCount() > 0;
        filterToggleButton.setMessage(Component.translatable(
                "detective.ui.incidents.filters.toggle", state.activeFilterCount()));
        cancelSelectionButton.visible = selectionMode;
        compareButton.setMessage(selectionMode
                ? Component.translatable("detective.ui.incidents.compare.selected", state.selectionCount())
                : Component.translatable("detective.ui.incidents.compare.start"));
        compareButton.active = !selectionMode || state.canCompare();
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

        int contentWidth = Math.min(600, this.width - 16);
        int left = (this.width - contentWidth) / 2;
        Component feedback = feedbackMessage();
        int feedbackWidth = Math.max(60, contentWidth - Math.min(140, contentWidth / 3) - 8);
        graphics.drawString(this.font, this.font.plainSubstrByWidth(feedback.getString(), feedbackWidth),
                left + 2, ACTION_TOP + 6,
                selectionLimitReached ? 0xFFE0A83E : DetectiveUiRenderer.MUTED, false);

        if (loading && state.result().isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.loading"),
                    this.width / 2, (currentListTop() + this.height - DetectiveUiRenderer.FOOTER_HEIGHT) / 2,
                    DetectiveUiRenderer.MUTED);
        } else if (loadFailed) {
            graphics.drawCenteredString(this.font, Component.translatable("detective.ui.load_failed"),
                    this.width / 2, (currentListTop() + this.height - DetectiveUiRenderer.FOOTER_HEIGHT) / 2,
                    0xFFFF7777);
        } else if (list.isEmpty() && !loading) {
            renderEmptyState(graphics);
        }
    }

    private Component feedbackMessage() {
        if (selectionLimitReached) {
            return Component.translatable("detective.ui.incidents.compare.limit");
        }
        if (loading) {
            return Component.translatable("detective.ui.incidents.searching");
        }
        return state.result()
                .map(result -> Component.translatable("detective.ui.incidents.results",
                        result.matchingCount(), result.totalIncidentCountConsidered()))
                .orElse(Component.empty());
    }

    private void renderEmptyState(GuiGraphics graphics) {
        int y = (currentListTop() + this.height - DetectiveUiRenderer.FOOTER_HEIGHT) / 2 - 20;
        boolean historyEmpty = state.result()
                .map(result -> result.totalIncidentCountConsidered() == 0)
                .orElse(initialIndex == null || initialIndex.incidents().isEmpty());
        if (historyEmpty) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("detective.ui.incidents.empty.title"),
                    this.width / 2, y, DetectiveUiRenderer.TEXT);
            y = DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.incidents.empty.body"), this.width / 2, y + 16,
                    Math.min(420, this.width - 32), DetectiveUiRenderer.MUTED);
            DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.incidents.empty.hint"), this.width / 2, y + 3,
                    Math.min(420, this.width - 32), DetectiveUiRenderer.MUTED);
            return;
        }
        IncidentQueryResult result = state.result().orElse(null);
        if (result != null && result.caseFilterStatus() == IncidentQueryResult.CaseFilterStatus.UNAVAILABLE) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("detective.ui.incidents.case_filter.unavailable"),
                    this.width / 2, y, DetectiveUiRenderer.TEXT);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("detective.ui.incidents.no_matches"),
                    this.width / 2, y, DetectiveUiRenderer.TEXT);
            DetectiveUiRenderer.centeredWrappedText(graphics, this.font,
                    Component.translatable("detective.ui.incidents.no_matches.hint"),
                    this.width / 2, y + 16, Math.min(420, this.width - 32), DetectiveUiRenderer.MUTED);
        }
    }

    private int currentListTop() {
        return filtersOpen ? LIST_EXPANDED_TOP : LIST_COLLAPSED_TOP;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private static IncidentQuery.Sort nextSort(IncidentQuery.Sort sort) {
        IncidentQuery.Sort[] values = IncidentQuery.Sort.values();
        return values[(sort.ordinal() + 1) % values.length];
    }

    private static String evidenceTranslationKey(IncidentInvestigationState.EvidenceFilter filter) {
        return "detective.ui.incidents.filter.evidence." + filter.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String durationTranslationKey(IncidentInvestigationState.DurationFilter filter) {
        return "detective.ui.incidents.filter.duration." + filter.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String caseTranslationKey(IncidentInvestigationState.CaseFilter filter) {
        return "detective.ui.incidents.filter.case." + filter.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String sortTranslationKey(IncidentQuery.Sort sort) {
        return "detective.ui.incidents.sort." + sort.name().toLowerCase(java.util.Locale.ROOT);
    }

    private final class IncidentSelectionList extends ObjectSelectionList<IncidentSelectionList.Entry> {
        private IncidentSelectionList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void setIncidents(List<IncidentSearchRecord> incidents) {
            clearEntries();
            incidents.forEach(incident -> addEntry(new Entry(incident)));
        }

        boolean isEmpty() {
            return children().isEmpty();
        }

        @Override
        public int getRowWidth() {
            return Math.min(560, getWidth() - 28);
        }

        @Override
        protected int getScrollbarPosition() {
            return Math.min(getWidth() - 8, getRowRight() + 4);
        }

        private final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final IncidentSearchRecord record;

            private Entry(IncidentSearchRecord record) {
                this.record = record;
            }

            @Override
            public void render(
                    GuiGraphics graphics, int index, int top, int left, int width, int height,
                    int mouseX, int mouseY, boolean hovered, float partialTick
            ) {
                IncidentSummaryViewModel incident = record.summary();
                boolean selected = state.isSelected(record.incidentId());
                if (selected) {
                    graphics.fill(left, top, left + width, top + height, 0x66305F7A);
                    graphics.fill(left, top, left + 3, top + height, DetectiveUiRenderer.ACCENT);
                } else if (hovered) {
                    graphics.fill(left, top, left + width, top + height, 0x553A4B5C);
                }
                Component duration = Component.translatable("detective.ui.incidents.freeze",
                        UiFormatters.duration(incident.durationMs()));
                int badgeWidth = DetectiveUiRenderer.badgeWidth(font, incident.evidence());
                graphics.drawString(font, duration,
                        left + 6, top + 6, DetectiveUiRenderer.TEXT, false);
                int badgeX = left + width - badgeWidth - 5;
                DetectiveUiRenderer.badge(graphics, font, incident.evidence(), badgeX, top + 3);
                if (selected) {
                    int selectionIndex = state.selectedIncidents().indexOf(record);
                    Component marker = Component.translatable(
                            selectionIndex == 0
                                    ? "detective.ui.incidents.compare.marker_a"
                                    : "detective.ui.incidents.compare.marker_b");
                    graphics.drawString(font, marker,
                            badgeX - font.width(marker) - 7, top + 6, DetectiveUiRenderer.ACCENT, false);
                }
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
                if (!selectionMode) {
                    openIncident(record.summary());
                    return true;
                }
                IncidentInvestigationState.SelectionChange change = state.toggleSelection(record);
                selectionLimitReached = change == IncidentInvestigationState.SelectionChange.LIMIT_REACHED;
                refreshControls();
                return true;
            }

            @Override
            public Component getNarration() {
                IncidentSummaryViewModel incident = record.summary();
                Component base = Component.translatable("detective.ui.incidents.narration",
                        UiFormatters.duration(incident.durationMs()),
                        incident.hasPrimarySuspect() ? incident.primarySuspect()
                                : Component.translatable(incident.evidence().translationKey()),
                        incident.occurredAt());
                return state.isSelected(record.incidentId())
                        ? Component.translatable("detective.ui.incidents.compare.narration_selected", base)
                        : base;
            }
        }
    }
}
