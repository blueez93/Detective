package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.client.support.DetectiveSupportService;
import fr.apocalypsebleu.moddetective.support.DetectiveSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.UnaryOperator;

public final class DetectiveSettingsScreen extends Screen {
    private static final int[] HISTORY_LIMITS = {25, 50, 100};
    private static final int[] RETENTION_DAYS = {7, 30, 90};

    private final Screen parent;
    private final boolean persistenceEnabled;
    private DetectiveSettings settings;
    private boolean saveFailed;
    private int clearedRecords = -1;
    private Button notificationsButton;
    private Button historyButton;
    private Button retentionButton;
    private Button technicalButton;

    public DetectiveSettingsScreen(Screen parent) {
        this(parent, DetectiveSupportService.settings(), true);
    }

    /** Development-only visual validation can preload settings without writing them. */
    public DetectiveSettingsScreen(Screen parent, DetectiveSettings settings) {
        this(parent, settings, false);
    }

    private DetectiveSettingsScreen(Screen parent, DetectiveSettings settings, boolean persistenceEnabled) {
        super(Component.translatable("detective.ui.settings.title"));
        this.parent = parent;
        this.settings = settings;
        this.persistenceEnabled = persistenceEnabled;
    }

    @Override
    protected void init() {
        int width = Math.min(440, this.width - 24);
        int left = (this.width - width) / 2;
        int controlX = left + width - 108;
        int top = DetectiveUiRenderer.HEADER_HEIGHT + 12;
        notificationsButton = this.addRenderableWidget(Button.builder(Component.empty(), button ->
                        update(current -> current.withIncidentNotifications(!current.incidentNotifications())))
                .bounds(controlX, top, 96, 20).build());
        historyButton = this.addRenderableWidget(Button.builder(Component.empty(), button ->
                        update(current -> current.withIncidentHistoryLimit(
                                next(HISTORY_LIMITS, current.incidentHistoryLimit()))))
                .bounds(controlX, top + 25, 96, 20).build());
        retentionButton = this.addRenderableWidget(Button.builder(Component.empty(), button ->
                        update(current -> current.withDataRetentionDays(
                                next(RETENTION_DAYS, current.dataRetentionDays()))))
                .bounds(controlX, top + 50, 96, 20).build());
        technicalButton = this.addRenderableWidget(Button.builder(Component.empty(), button ->
                        update(current -> current.withShowTechnicalEvidenceByDefault(
                                !current.showTechnicalEvidenceByDefault())))
                .bounds(controlX, top + 75, 96, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("detective.ui.settings.clear"),
                        button -> this.minecraft.setScreen(new ClearIncidentHistoryScreen(this)))
                .bounds(left + 12, top + 105, Math.min(190, width - 24), 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
        refreshButtonLabels();
    }

    private void update(UnaryOperator<DetectiveSettings> operation) {
        DetectiveSettings next = operation.apply(settings);
        settings = next;
        saveFailed = false;
        refreshButtonLabels();
        if (persistenceEnabled) {
            DetectiveSupportService.updateSettings(ignored -> next).whenComplete((saved, error) ->
                    this.minecraft.execute(() -> {
                        if (error != null) {
                            saveFailed = true;
                            settings = DetectiveSupportService.settings();
                            refreshButtonLabels();
                        } else {
                            settings = saved;
                            refreshButtonLabels();
                        }
                    }));
        }
    }

    private void refreshButtonLabels() {
        if (notificationsButton == null) {
            return;
        }
        notificationsButton.setMessage(toggle(settings.incidentNotifications()));
        historyButton.setMessage(Component.literal(Integer.toString(settings.incidentHistoryLimit())));
        retentionButton.setMessage(Component.translatable("detective.ui.settings.days",
                settings.dataRetentionDays()));
        technicalButton.setMessage(toggle(settings.showTechnicalEvidenceByDefault()));
    }

    private static Component toggle(boolean enabled) {
        return Component.translatable(enabled ? "detective.ui.settings.on" : "detective.ui.settings.off");
    }

    private static int next(int[] values, int current) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == current) {
                return values[(index + 1) % values.length];
            }
        }
        return values[0];
    }

    void historyCleared(int deleted) {
        clearedRecords = Math.max(0, deleted);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        DetectiveUiRenderer.header(graphics, this.font, this.width,
                Component.translatable("detective.ui.settings.title"),
                Component.translatable("detective.ui.settings.subtitle"));
        DetectiveUiRenderer.footer(graphics, this.font, this.width, this.height);
        int width = Math.min(440, this.width - 24);
        int left = (this.width - width) / 2;
        int top = DetectiveUiRenderer.HEADER_HEIGHT + 7;
        int bottom = this.height - DetectiveUiRenderer.FOOTER_HEIGHT - 4;
        DetectiveUiRenderer.panel(graphics, left, top, width, Math.max(40, bottom - top));
        int labelX = left + 12;
        int labelY = top + 10;
        graphics.drawString(this.font, Component.translatable("detective.ui.settings.notifications"),
                labelX, labelY, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.settings.history_limit"),
                labelX, labelY + 25, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.settings.retention"),
                labelX, labelY + 50, DetectiveUiRenderer.TEXT, false);
        graphics.drawString(this.font, Component.translatable("detective.ui.settings.technical_default"),
                labelX, labelY + 75, DetectiveUiRenderer.TEXT, false);
        if (saveFailed) {
            graphics.drawString(this.font, Component.translatable("detective.ui.settings.save_failed"),
                    left + 212, labelY + 110, 0xFFFF7777, false);
        } else if (clearedRecords >= 0) {
            graphics.drawString(this.font, Component.translatable("detective.ui.settings.cleared", clearedRecords),
                    left + 212, labelY + 110, DetectiveUiRenderer.ACCENT, false);
        }
        DetectiveUiRenderer.widgets(this, graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
