package fr.apocalypsebleu.detectivevalidation.culprita;

import fr.apocalypsebleu.detectivevalidation.culprit.DetectiveTestCulprit;
import fr.apocalypsebleu.detectivevalidation.culprit.UiValidationPlan;
import fr.apocalypsebleu.detectivevalidation.culprit.ValidationHarness;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = DetectiveTestCulpritA.MOD_ID, dist = Dist.CLIENT)
public final class DetectiveTestCulpritA {
    public static final String MOD_ID = "detective_testculprit_a";

    public DetectiveTestCulpritA(IEventBus modEventBus) {
        ValidationHarness.start();
        modEventBus.addListener(this::onClientSetup);
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] Development-only culprit A loaded");
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(UiValidationPlan::registerIfRequested);
    }
}
