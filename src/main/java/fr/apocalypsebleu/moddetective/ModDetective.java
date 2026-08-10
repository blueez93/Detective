package fr.apocalypsebleu.moddetective;

import com.mojang.logging.LogUtils;
import fr.apocalypsebleu.moddetective.snapshot.ModSnapshotService;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod(value = ModDetective.MOD_ID, dist = Dist.CLIENT)
public final class ModDetective {
    public static final String MOD_ID = "detective";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModDetective(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                var diff = ModSnapshotService.captureAndPersist();
                LOGGER.info("[Detective] Ready: {} loaded mods, {} change(s) since the previous session",
                        diff.current().mods().size(), diff.totalChanges());

                if (diff.totalChanges() > 0) {
                    diff.added().forEach(mod -> LOGGER.info("[Detective] Added: {} {}", mod.id(), mod.version()));
                    diff.removed().forEach(mod -> LOGGER.info("[Detective] Removed: {} {}", mod.id(), mod.version()));
                    diff.updated().forEach(change -> LOGGER.info("[Detective] Updated: {} {} -> {}",
                            change.id(), change.oldVersion(), change.newVersion()));
                }
            } catch (RuntimeException e) {
                LOGGER.error("[Detective] Snapshot initialization failed; performance monitoring will remain available", e);
            }
        });
    }
}
