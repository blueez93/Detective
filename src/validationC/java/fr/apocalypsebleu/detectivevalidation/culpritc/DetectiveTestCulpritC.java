package fr.apocalypsebleu.detectivevalidation.culpritc;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(value = DetectiveTestCulpritC.MOD_ID, dist = Dist.CLIENT)
public final class DetectiveTestCulpritC {
    public static final String MOD_ID = "detective_testculprit_c";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DetectiveTestCulpritC() {
        LOGGER.info("[Detective Validation] Development-only culprit C loaded");
    }
}
