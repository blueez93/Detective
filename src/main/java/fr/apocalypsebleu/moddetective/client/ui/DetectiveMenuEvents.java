package fr.apocalypsebleu.moddetective.client.ui;

import fr.apocalypsebleu.moddetective.ModDetective;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = ModDetective.MOD_ID, value = Dist.CLIENT)
public final class DetectiveMenuEvents {
    private DetectiveMenuEvents() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof TitleScreen) && !(screen instanceof PauseScreen)) {
            return;
        }

        event.addListener(Button.builder(
                        Component.translatable("detective.ui.menu_button"),
                        button -> Minecraft.getInstance().setScreen(new DetectiveHomeScreen(screen)))
                .bounds(Math.max(8, screen.width - 108), 8, 100, 20)
                .build());
    }
}
