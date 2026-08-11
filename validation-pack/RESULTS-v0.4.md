# Detective v0.4.0-alpha.1 — First usable UI

Runtime de validation : Minecraft 1.21.1, NeoForge 21.1.235, Java 21, pack réaliste v0.3.1 chargé avec 21 mods au total. Le moteur d’attribution v0.3.1 n’a pas été modifié.

## 1. Fichiers créés

- `client/ui`: `DetectiveMenuEvents`, `DetectiveUiRenderer`, `DetectiveHomeScreen`, `IncidentListScreen`, `IncidentDetailScreen`, `ModpackChangesScreen`.
- `client/ui/data`: `DetectiveUiRepository`, `DetectiveUiService`, `IncidentJsonAdapter`, `ModpackChangesAdapter`.
- `client/ui/model`: `EvidenceBadge`, `SuspectViewModel`, `BlackBoxPoint`, `IncidentSummaryViewModel`, `IncidentDetailViewModel`, `IncidentIndexViewModel`, `DetectiveSummaryViewModel`, `ModpackChangesViewModel`, `UiFormatters`.
- Cinq classes de tests UI purs sous `src/test/java/fr/apocalypsebleu/moddetective/client/ui`.
- `src/validation/.../UiValidationPlan.java`, route de captures strictement développement.
- Ce rapport `validation-pack/RESULTS-v0.4.md`.

## 2. Fichiers modifiés

- `gradle.properties`: version `0.4.0-alpha.1`.
- `ModDetective.java`: instant de début de session exposé au résumé UI.
- `ClientPerformanceEvents.java`: arrêt propre du worker UI avec le client.
- `ModSnapshotService.java`: dernier diff de modpack conservé en mémoire pour l’adapter UI.
- `assets/detective/lang/en_us.json` et `fr_fr.json`: libellés des écrans, états et preuves.
- `ValidationCommands`, `ValidationHarness` et `DetectiveTestCulpritA`: démarrage/arrêt et diagnostics robustes de la route UI de développement.
- `AGENTS.md`, `PROJECT_STATE.md` et `README.md`: jalon, architecture, commandes et limites v0.4.

## 3. Architecture UI choisie

Les écrans ne consomment pas directement les records du watchdog ou de l’analyseur. Des adapters tolérants lisent les JSON persistés et le diff de snapshot vers des view models immuables. Un unique worker daemon de faible priorité indexe les incidents à l’ouverture et charge les détails à la demande ; rien n’est reparsé à chaque frame.

La lecture des résumés est streaming et saute entièrement les gros tableaux `blackBox`. Le détail complet n’est lu qu’après sélection. Le graphe conserve les pics lors du downsampling et est plafonné à 240 points. Les erreurs d’un fichier isolé sont comptées sans faire tomber tout l’écran.

Les tiers `HIGH_EVIDENCE`, `MODERATE_EVIDENCE` et `LOW_EVIDENCE` sont une présentation UI des preuves déjà persistées. Ils ne remplacent ni ne modifient l’état de confiance du moteur. Classement et confiance restent séparés.

## 4. Écrans créés

- Accueil Detective : statut du monitoring, incidents de session/récents, dernier incident, compteurs de preuve et navigation.
- Liste scrollable : plus récent d’abord, durée, état/badge, suspect principal prudent, date, dimension et coordonnées.
- Détail scrollable : contexte, seuil, samples, état brut, suspect principal, autres suspects, explication prudente, Black Box 2D et preuves leaf/presence/depth/repetition/diversity.
- Changements du modpack : ajouts, suppressions et mises à jour, avec états indisponible/vide.

## 5. Points d’entrée ajoutés

Un `ScreenEvent.Init.Post` client-only ajoute un bouton vanilla `Detective` en haut à droite du `TitleScreen` et du `PauseScreen`. Les deux boutons ont été observés dans les captures runtime. Aucun code UI n’est chargé comme fonctionnalité serveur.

## 6. Gestion des états spéciaux

L’UI représente séparément `HIGH_EVIDENCE`, `MODERATE_EVIDENCE`, `LOW_EVIDENCE`, `AMBIGUOUS_ATTRIBUTION`, `INSUFFICIENT_EVIDENCE`, `JVM_GC_SUSPECTED`, `NATIVE_OR_DRIVER_STALL_POSSIBLE` et `UNKNOWN`. Elle n’affiche jamais le share de samples comme un pourcentage de culpabilité.

Les chemins sans incident, sans suspect, JSON partiel/manquant, Black Box vide/partielle, comparaison de modpack absente et fichier illisible ont un fallback lisible. Un index vide synthétique a validé l’empty state sans supprimer les incidents réels.

## 7. Validation `runClient`

Commande finale :

```powershell
.\gradlew.bat runClient --no-daemon -PdetectiveValidationWorld=DetectiveValidation -PdetectiveValidationAutorun=ui -PdetectiveValidationExit=true
```

Résultat : succès. Minecraft, Detective `0.4.0-alpha.1`, le monde `DetectiveValidation` et le pack réaliste ont chargé. Le harness a capturé et vérifié visuellement : vrai menu pause, accueil, liste réelle, détail attribué, Black Box scrollée, changements de modpack, liste vide et menu titre. Les textes personnalisés ont d’abord révélé un double rendu du flou vanilla ; l’ordre de rendu a été corrigé et les captures finales sont nettes.

Le watchdog et le serveur intégré se sont arrêtés proprement. Aucune exception issue de l’UI publique ou de Detective n’est présente dans la passe finale. Le contrôleur GUI interactif de l’environnement Codex n’a pas pu être initialisé (`EPERM` sur son runtime local) : la validation a donc utilisé une route NeoForge/Minecraft de développement et une inspection visuelle des PNG, pas des clics souris externes.

Observation honnête : le gros pack a produit un stall d’entrée monde de 318 ms classé `INSUFFICIENT_EVIDENCE`, puis un stall de 186 ms classé `NATIVE_OR_DRIVER_STALL_POSSIBLE` à la fin du parcours. Ils n’ont pas été filtrés ni transformés pour embellir la validation UI. Le second survient autour de la transition titre/arrêt et mérite une revalidation de continuité en v0.4.1.

## 8. Résultats build et tests

Commandes obligatoires :

```powershell
.\gradlew.bat clean build --no-daemon
.\gradlew.bat test --no-daemon
```

Résultat : `BUILD SUCCESSFUL`. Les 39 tests moteur existants sont conservés et 10 tests UI purs ont été ajoutés : 49 tests, 0 échec, 0 erreur, 0 ignoré. Ils couvrent mapping complet/partiel, downsampling avec conservation des pics, mapping du diff de modpack, tri newest-first, compteurs de résumé, labels/tiers d’état et formatage.

Le JAR public `detective-0.4.0-alpha.1.jar` contient le mod id `detective`, la version attendue et des dépendances Minecraft/NeoForge `side="CLIENT"`. L’inspection ZIP ne trouve aucun harness, culprit, ground truth, mod tiers, asset de validation, configuration GC ou JAR imbriqué.

## 9. Problèmes UX ou techniques restants

- L’échelle UI et les très petites/grandes résolutions n’ont été inspectées qu’en 854 × 480 avec l’échelle de développement actuelle.
- La liste n’a ni recherche ni filtre ; avec plusieurs milliers d’incidents, une pagination/virtualisation plus explicite pourra devenir utile, même si `ObjectSelectionList` ne rend que les lignes visibles.
- Le cache est rafraîchi à l’ouverture de l’accueil ; il n’existe pas encore de notification temps réel quand un incident est écrit pendant qu’un écran reste ouvert.
- Le graphe privilégie les pics et la lisibilité, pas une échelle temporelle exacte ni une interaction avancée.
- Le nom de package Java historique `fr.apocalypsebleu.moddetective` reste volontairement inchangé.
- Une passe manuelle souris/clavier sur plusieurs facteurs d’échelle reste souhaitable, notamment navigation clavier/narration.

## 10. Recommandations v0.4.1

1. Faire une passe d’accessibilité et de responsive layout sur plusieurs résolutions, facteurs d’échelle et langues.
2. Ajouter recherche/filtre léger par état, dimension et suspect si les données réelles le justifient.
3. Rafraîchir discrètement l’index lorsqu’un nouvel incident arrive, sans watcher coûteux ni parsing par frame.
4. Revalider le gate de continuité autour du retour titre et de l’arrêt afin de comprendre le stall `NATIVE_OR_DRIVER_STALL_POSSIBLE`, sans masquer les pauses réelles.
5. Ajouter une capture de preuves techniques plus bas dans le détail et un test manuel complet clavier/souris avant toute bêta publique.

La v0.4 atteint son objectif d’interface alpha exploitable. Elle n’est pas encore une UI de release finale.
