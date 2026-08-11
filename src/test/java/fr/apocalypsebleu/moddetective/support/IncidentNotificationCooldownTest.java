package fr.apocalypsebleu.moddetective.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentNotificationCooldownTest {
    @Test
    void showsOnlyTheFirstIncidentInARapidBurst() {
        IncidentNotificationCooldown cooldown = new IncidentNotificationCooldown(8_000L);

        assertTrue(cooldown.register("freeze-1", 10_000L, true));
        assertFalse(cooldown.register("freeze-2", 11_000L, true));
        assertFalse(cooldown.register("freeze-3", 12_000L, true));
        assertFalse(cooldown.register("freeze-4", 13_000L, true));
        assertTrue(cooldown.register("freeze-5", 18_000L, true));
    }

    @Test
    void neverShowsTheSameIncidentTwiceAndHonorsDisabledSettings() {
        IncidentNotificationCooldown cooldown = new IncidentNotificationCooldown(1L);

        assertFalse(cooldown.register("disabled", 1L, false));
        assertFalse(cooldown.register("disabled", 100L, true));
        assertTrue(cooldown.register("enabled", 101L, true));
        assertFalse(cooldown.register("enabled", 200L, true));
    }

    @Test
    void remainsBoundedDuringAHighVolumeIncidentHistory() {
        IncidentNotificationCooldown cooldown = new IncidentNotificationCooldown(0L);

        for (int index = 0; index < 10_000; index++) {
            assertTrue(cooldown.register("freeze-" + index, index, true));
        }

        assertTrue(cooldown.trackedIncidentCount() <= 256);
    }
}
