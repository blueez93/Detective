# Detective v0.3.1 — revalidation and stack evidence study

## 1. Minecraft

Minecraft **1.21.1**, Java **21.0.9**. Detective reste client-side, sans réseau ni télémétrie.

## 2. NeoForge

Tous les builds et runs utilisent **NeoForge 21.1.235**. La version n'a pas changé entre les comparaisons.

## 3. Pack de validation

Les JAR tiers restent dans les répertoires `run` ignorés et ne sont jamais redistribués avec Detective.

| Mod | Version | Catégorie principale |
|---|---|---|
| ModernFix | 5.27.20+mc1.21.1 | optimisation |
| FerriteCore | 7.0.3-neoforge | optimisation |
| Sodium | 0.8.12+mc1.21.1 | rendu/client |
| Mekanism | 10.7.19.85 | machines, contenu, worldgen |
| Farmer's Delight | 1.3.2 | contenu |
| Regions Unexplored | 0.6.2 | worldgen, contenu |
| Lithostitched | 1.7.13 | bibliothèque, worldgen |
| Just Enough Items | 19.39.0.372 | inventaire/QoL |
| Jade | 15.10.6 | inventaire/QoL |
| Curios API | 9.5.1 | bibliothèque, inventaire/QoL |
| GeckoLib | 4.9.2 | bibliothèque, rendu |

Les versions, licences, URL Modrinth et SHA-512 exacts sont épinglés dans `modrinth-pack.json`.

## 4. Durée du second soak

- Soak exploitable : **30 min de protocole**, 30 min 45 s de processus, 373 observations d'overhead, arrêt propre.
- Revalidation focus post-correctif : environ 25 s de phases mesurées, plus chargement/arrêt, zéro incident.
- Un essai post-correctif supplémentaire de 30 min a été lancé, mais il est **exclu** : Codex a repris le focus Windows, GLFW n'a pas pu le récupérer, la Black Box a cessé de progresser normalement et les neuf ground truths n'ont créé aucun incident. Ce run ne prouve ni succès ni régression d'attribution.

Le fichier historique `RESULTS-v0.3.md` est inchangé. SHA-256 vérifié : `C2D454006B74530F8F67D8B47839E5926A06B46A6BE6CABF29F5324E291D3EDD`.

## 5. Overhead v0.3 vs v0.3.1

Les percentiles sont la médiane des percentiles glissants sur 4 096 samples. La moyenne de phase v0.3.1 est reconstruite par différence des compteurs cumulatifs.

| Mesure | v0.3 | v0.3.1 soak exploitable |
|---|---:|---:|
| Samples/s, session | 47.77 | 47.22 |
| Moyenne cumulative, session | 207.4 us | 259.9 us |
| p50 final | 106.2 us | 170.2 us |
| p95 final | 281.9 us | 626.5 us |
| p99 final | 571.0 us | 1,832.0 us |
| Maximum cumulatif | 446,081.8 us | 504,269.3 us |
| Estimation rétention max | 2.93 MiB | 2.90 MiB |
| Queue max | 0/8 | 0/8 |
| Incidents dropped | 0 | 0 |

Les fenêtres stables, qui déterminent le budget, restent conformes :

| Phase stable | Moyenne | p50 | p95 | p99 | Samples/s |
|---|---:|---:|---:|---:|---:|
| Début de session | 130.1 us | 103.4 us | 226.3 us | 411.8 us | 47.87 |
| Après reconnexion | 246.0 us | 153.9 us | 523.0 us | 1,737.7 us | 47.22 |

Les signaux leaf/depth sont calculés dans l'incident worker, jamais dans la capture watchdog. Le traitement des 18 incidents coûtait 34.4 ms en moyenne et 151.3 ms au maximum, hors render thread. Les transitions lourdes expliquent la moyenne globale ; aucune croissance de rétention ou de queue n'est observée. Heap JVM observé : 251.2 à 1,093.6 MiB. Black Box max : 3 553 entrées. Latency window max : 4 096.

## 6. Incidents observés

Dans le soak exploitable : **18 incidents**, dont 9 contrôlés et 9 dans des phases sans stall intentionnel.

| État | Total | Contrôlés | Phases négatives |
|---|---:|---:|---:|
| ATTRIBUTED | 11 | 9 | 2 |
| AMBIGUOUS_ATTRIBUTION | 0 | 0 | 0 |
| INSUFFICIENT_EVIDENCE | 1 | 0 | 1 |
| JVM_GC_SUSPECTED | 0 | 0 | 0 |
| NATIVE_OR_DRIVER_STALL_POSSIBLE | 6 | 0 | 6 |
| UNKNOWN | 0 | 0 | 0 |

Queue 0/8, zéro drop. Position et dimension sont présentes dans les neuf incidents contrôlés.

## 7. Faux positifs

Les neuf incidents de phases négatives sont des **candidats**, pas neuf faux positifs automatiquement confirmés : chacun correspond à une frame réellement supérieure au seuil.

- Trois frames stables de 275–279 ms : aucune attribution de mod, état native/driver possible.
- Worldgen : 434 ms native/driver possible, puis 143 ms avec preuve Sodium 1/7, donc insufficient evidence.
- Gros menu : 187 ms native/driver possible ; 129 ms avec JEI leaf dans 4/7 samples (57.1 %), attribution probable mais pas certitude causale.
- Deux artefacts confirmés du protocole : 194 ms causé par l'analyse JSON de phase sur le render thread, et 383 ms de restauration de focus. Les deux sont corrigés ; l'analyse de phase est désormais asynchrone et le focus post-correctif donne 0 incident.

Le harness sépare désormais `negativePhaseIncidents` de `confirmedFalsePositives`. L'ancienne sortie du soak avait surcompté les neuf candidats comme confirmés.

## 8. Pause et focus

- Test unitaire : suspension répétée et trois frames de stabilisation couvertes.
- Runtime ciblé post-correctif : `focus_control=0`, `focus_iconified=0`, `focus_recovery=0`, `focus_stable_after_recovery=0` incident.
- Le soak exploitable avait montré que sauter une seule frame ne suffisait pas : le stall GLFW natif débordait sur une frame suivante.
- La simulation GLFW iconify/restore est validée. Un véritable alt-tab humain piloté manuellement n'a pas pu être certifié, les outils Codex modifiant eux-mêmes le focus Windows.

## 9. GC

Le scénario dédié alloue et touche 512 MiB sur un worker, trois fois, conserve des marqueurs monotones/epoch, puis corrèle le résultat au log unifié G1.

| Passe | Pause Full G1 | Incident Detective | État Detective |
|---|---:|---:|---|
| 1 | 183.525 ms | 193.169 ms | INSUFFICIENT_EVIDENCE |
| 2 | 140.912 ms | aucun (debounce) | — |
| 3 | 127.246 ms | 135.723 ms | NATIVE_OR_DRIVER_STALL_POSSIBLE |

Dans le soak, les pauses Full étaient 287.087, 187.939 et 176.774 ms, sans incident. Detective n'a jamais accusé un mod. Limite observée : pendant un safepoint stop-the-world, le watchdog est lui aussi suspendu et ne voit généralement pas `System.gc` dans la stack ; une frame native juste après le GC peut donc rester `NATIVE_OR_DRIVER_STALL_POSSIBLE`. `JVM_GC_SUSPECTED` existe dans les données mais n'est fiable que si un marqueur de stack est réellement capturé. Aucune règle ground-truth/GC-log n'a été ajoutée au moteur public.

## 10. Métriques stack depth

`StackTraceElement[0]` est le point d'exécution actif ; un test cherche la méthode leaf avant son caller. Par suspect, le JSON conserve :

- `presenceSamples` et `presenceSharePercent` ;
- `leafOwnershipCount` et `leafOwnershipSharePercent` ;
- `averageFirstFrameDepth` et `minimumFirstFrameDepth` ;
- `repeatedLeafOwnership` ;
- `callerOnlySamples` ;
- `stackDiversity`.

Dans les stacks contrôlées propres, le vrai exécutant est leaf à profondeur moyenne 2.0 dans 96.6–100 % des samples ; ses callers ont 0 leaf ownership et des profondeurs croissantes.

## 11. Direct A/B/C

| Scénario | Expected | Presence rank/share | Leaf rank/share | Profondeur moyenne | Final |
|---|---|---:|---:|---:|---:|
| Direct A | A | 1 / 100.0 % | 1 / 100.0 % | 2.0 | 1 |
| Direct B | B | 1 / 96.7 % | 1 / 96.7 % | 2.0 | 1 |
| Scheduled C | C | 1 / 96.6 % | 1 / 96.6 % | 2.0 | 1 |

## 12. Indirect A → B

Expected B : presence rank 2 à 96.7 %, leaf rank 1 à 96.7 %, profondeur moyenne 2.0, final rank 1. A est uniquement caller dans les samples propres. Résultat naturel : **B devant A**.

## 13. Nested A → B → C

Expected C : presence rank 3 à 96.7 %, leaf rank 1 à 96.7 %, profondeur moyenne 2.0, final rank 1. Le replay focalisé propre donne **C devant B devant A**. Dans le long soak, un sample de bord a donné un leaf à A ; C reste #1 mais l'ordre des callers devient C/A/B. Le classement du vrai exécutant est stable, celui des callers sans leaf reste sensible au bruit.

## 14. Permutations supplémentaires

| Scénario | Expected | Presence rank | Leaf rank | Leaf share | Final |
|---|---|---:|---:|---:|---:|
| A → C | C | 2 | 1 | 96.6 % | 1 |
| B → A | A | 1 | 1 | 100.0 % | 1 |
| C → B | B | 2 | 1 | 100.0 % | 1 |
| B → C → A | A | 1 | 1 | 100.0 % | 1 |

L'ordre alphabétique n'est plus le tie-break décisif lorsqu'une preuve leaf existe.

## 15. Top-1 avant

Baseline historique v0.3 : **3/5, 60 %**. Dans l'étude pré-changement sur huit incidents capturés : présence **4/8**, leaf **8/8**, présence-puis-leaf **8/8**, profondeur **8/8**. Le neuvième a été filtré par perte de focus et n'a pas été compté.

## 16. Top-1 après

- Cinq scénarios historiques : **5/5, 100 %**.
- Matrice focalisée avec permutations : **9/9, 100 %**.
- Matrice incluse dans le soak exploitable : leaf **9/9** ; présence seule **5/9** ; présence-puis-leaf **7/9** ; profondeur **9/9**.

## 17. Top-3

**100 %** avant et après sur tous les incidents contrôlés effectivement capturés.

## 18. Cas ambigus

Aucun scénario runtime n'a produit de preuves leaf pratiquement équivalentes. Le nouvel état `AMBIGUOUS_ATTRIBUTION` est testé avec des candidats à une observation près et une profondeur équivalente. Il reste distinct du rang : un #1 peut être ambigu ou insufficient evidence.

## 19. Modèle retenu

Le classement de production passe de presence share à **leaf ownership** avec fallback déterministe sur présence, profondeur puis mod id. Aucun coefficient n'est introduit.

Raison : leaf et profondeur étaient parfaits sur le premier jeu, mais le long soak a fait tomber le modèle présence-puis-leaf à 7/9 lorsque le caller avait un sample de présence supplémentaire. Leaf ownership reste 9/9 et représente directement le propriétaire du frame moddé le plus proche de l'exécution active. La confiance reste calculée séparément ; Detective parle toujours de suspect probable.

## 20. Tests

**39 tests JUnit passent** : les 29 tests v0.3 sont conservés et les cas ciblés couvrent ordre réel des stacks Java, leaf owner, profondeur, répétition, caller-only, diversité, égalités, permutations, absence d'échantillons, ambiguïté, evidence insuffisante et stabilisation focus répétée.

Commandes finales : `gradlew clean build` puis `gradlew test`.

## 21. Build

Version artefact : `0.3.1-alpha.1`. `gradlew clean build` et `gradlew test` réussissent sous Java 21 / NeoForge 21.1.235.

## 22. Contenu du JAR public

Le JAR public `detective-0.3.1-alpha.1.jar` est inspecté explicitement. Il contient le moteur `main`, mais aucun `detective_testculprit`, package `detectivevalidation`, harness, ground truth, mod tiers, asset de validation, configuration GC ni manifeste de pack.

## 23. Limites restantes

- La corrélation GC fiable nécessite encore une source JVM dédiée ; une stack render seule ne distingue pas toujours GC et retour natif.
- Le classement des callers secondaires peut varier avec un unique sample de bord, même si le leaf responsable reste #1.
- Les incidents native/driver indiquent un stall réel mais pas sa cause GPU/OS.
- L'essai long post-correctif a été invalidé par le contrôle de focus de l'environnement Codex. La matrice post-correctif et la simulation focus sont valides ; un alt-tab manuel et, idéalement, un soak manuel focalisé restent recommandés.
- Le maximum cumulatif est dominé par les transitions et n'est pas un coût stable. La moyenne stable respecte le budget, la moyenne globale le dépasse légèrement.

## 24. Recommandation UI v0.4

**GO prudent** pour commencer l'UI v0.4 autour des données existantes : attribution Top-1 100 % sur neuf formes contrôlées, confiance séparée, aucune accusation de mod pendant les GC observés, buffers bornés, zéro drop, rétention < 4 MiB, stable mean ≤ 250 us et stable p99 < 2 ms.

Conditions pendant l'UI : afficher « suspect probable », exposer clairement ambiguous/insufficient/native/unknown, ne pas transformer le rang en certitude, et programmer avant release publique un soak manuel focalisé ainsi qu'une meilleure corrélation GC JVM. Aucun dashboard, cloud, réseau, Fabric ou serveur public n'a été développé dans cette étape.
