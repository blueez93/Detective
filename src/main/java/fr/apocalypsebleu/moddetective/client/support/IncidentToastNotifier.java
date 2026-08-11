package fr.apocalypsebleu.moddetective.client.support;

import fr.apocalypsebleu.moddetective.client.ui.model.EvidenceBadge;
import fr.apocalypsebleu.moddetective.client.ui.model.UiFormatters;
import fr.apocalypsebleu.moddetective.core.AttributionEvidence;
import fr.apocalypsebleu.moddetective.core.FreezeIncident;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

final class IncidentToastNotifier {
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(5_000L);

    private IncidentToastNotifier() {}

    static void show(Minecraft minecraft, FreezeIncident incident) {
        Component title = Component.translatable("detective.notification.recorded",
                UiFormatters.duration(incident.durationMs()));
        Component message = notificationMessage(incident);
        SystemToast.add(minecraft.getToasts(), TOAST_ID, title, message);
    }

    private static Component notificationMessage(FreezeIncident incident) {
        if (incident.attributionEvidence().state() == AttributionEvidence.State.ATTRIBUTED
                && !incident.suspects().isEmpty()) {
            var suspect = incident.suspects().getFirst();
            var view = new fr.apocalypsebleu.moddetective.client.ui.model.SuspectViewModel(
                    suspect.modId(), suspect.modName(), suspect.version(),
                    suspect.presenceSamples(), suspect.presenceSharePercent(),
                    suspect.leafOwnershipCount(), suspect.leafOwnershipSharePercent(),
                    suspect.averageFirstFrameDepth(), suspect.minimumFirstFrameDepth(),
                    suspect.repeatedLeafOwnership(), suspect.callerOnlySamples(), suspect.stackDiversity());
            EvidenceBadge evidence = EvidenceBadge.from("ATTRIBUTED", view);
            return Component.translatable("detective.notification.primary",
                    suspect.modName(), Component.translatable(evidence.translationKey()));
        }
        return Component.translatable("detective.notification.unattributed");
    }
}
