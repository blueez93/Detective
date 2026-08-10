package fr.apocalypsebleu.detectivevalidation.culpritb;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = DetectiveTestCulpritB.MOD_ID, dist = Dist.CLIENT)
public final class DetectiveTestCulpritB {
    public static final String MOD_ID = "detective_testculprit_b";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DetectiveTestCulpritB() {
        LOGGER.info("[Detective Validation] Development-only culprit B loaded");
    }
}
