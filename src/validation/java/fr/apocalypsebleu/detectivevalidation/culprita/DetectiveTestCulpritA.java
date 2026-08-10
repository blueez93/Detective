package fr.apocalypsebleu.detectivevalidation.culprita;

import fr.apocalypsebleu.detectivevalidation.culprit.DetectiveTestCulprit;
import fr.apocalypsebleu.detectivevalidation.culprit.ValidationHarness;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = DetectiveTestCulpritA.MOD_ID, dist = Dist.CLIENT)
public final class DetectiveTestCulpritA {
    public static final String MOD_ID = "detective_testculprit_a";

    public DetectiveTestCulpritA() {
        ValidationHarness.start();
        DetectiveTestCulprit.LOGGER.info("[Detective Validation] Development-only culprit A loaded");
    }
}
