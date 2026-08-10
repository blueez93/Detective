package fr.apocalypsebleu.detectivevalidation.culprit;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = DetectiveTestCulprit.MOD_ID, dist = Dist.CLIENT)
public final class DetectiveTestCulprit {
    public static final String MOD_ID = "detective_testculprit";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DetectiveTestCulprit() {
        ValidationHarness.start();
        LOGGER.info("[Detective Validation] Development-only culprit loaded");
    }
}
