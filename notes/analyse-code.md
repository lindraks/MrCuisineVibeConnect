# Analyse du code de Toque

Date : 2026-11-17. Lecture de `Safety.kt`, `Updater.kt`, `RootSerial.kt`, `Releases.kt`,
début de `MachineController.kt` et `app/build.gradle.kts`. L'UI, les scripts PowerShell et les
tests n'ont pas été exécutés ni relus en détail.

## Matériel testé

- Étiquette : SilverCrest **SKMC 1200 C3** (« Kitchen Cooking Machine »), fabriqué en 11/2019,
  IAN 338924_1907. Le préfixe SKMC correspond au Monsieur Cuisine Connect visé par Toque
  (le Smart est le SKMS 1200).
- Le suffixe « C3 » n'apparaît nulle part ailleurs dans le dépôt : sa signification (révision
  matérielle, version régionale ou lot) est inconnue.
- Écran « À propos de… » de l'appli d'origine : firmware de la base moteur 4922, image système
  « v3.9a_2019-08-08 », appli d'origine 1.1.27-release **build 285**. Le protocole et les programmes
  de Toque viennent du build 292 (`Mcc.kt`, `Safety.kt`) : à comparer par un essai à vide avant tout
  pilotage réel.
- Confirmé par une lecture complète de la mémoire (SP Flash Tool, Read Back, aucune écriture) :
  **Android 6.0** (MRA58K, SDK 23, build du 2019-08-08, « test-keys »), puce **MT6580**, carte
  `spm188a`, eMMC d'environ 14,5 Gio, sans chiffrement des données. Le fichier de partition du
  dépôt correspond : `boot` en `0x1d20000` et `recovery` en `0x2d20000`, 16 Mio chacun ; `system`
  1,5 Gio ; `userdata` 12,6 Gio.
- `default.prop` du ramdisk de `boot` : `ro.secure=1`, `ro.debuggable=0`, `ro.adb.secure=1`,
  `persist.sys.usb.config=mtp`. Au démarrage, ADB est donc absent, et même une fois activé il n'offre
  qu'un accès `shell` sans root, avec autorisation obligatoire de la clé du PC. Empreinte :
  `alps/full_spm188a/spm188a:6.0/MRA58K/1565230695:user/test-keys`.
- Copie de `userdata` : ADB n'a jamais été activé. `adb_enabled=0` dans
  `system/users/0/settings_global.xml` (Android 6 stocke les réglages en XML, pas dans `settings.db`),
  aucune entrée `development_settings_enabled`, `misc/adb/` vide (pas de `adb_keys`), pas de
  `persist.sys.usb.config` dans `property/`. Activer ADB suppose une modification du système, comme
  le fait `tools/robot/twrp-root.sh` depuis TWRP.
- Applis d'origine : `MCLauncher` (appli du robot), `MCUpdater`, plus les applis Android
  d'un système MediaTek générique (dont `Settings`, `EngineerMode`, `FactoryMode`).
- Le mode console de SP Flash Tool v5.1924 (`flash_tool.exe -i`, étape `backup` du script) a échoué
  sur Windows 11 : arrêt en 3 s, code -3, aucune sortie, même sans BOM dans le XML. La lecture dans
  la fenêtre de l'outil (repli décrit dans `AGENTS.md`) a fonctionné.

## Architecture

- `core/` : Kotlin pur, sans Android, testé. Protocole série (`protocol/Mcc.kt`), règles de
  sécurité (`machine/Safety.kt`), contrôleur (`MachineController.kt`) et boucle (`MachineLoop.kt`),
  recettes, JSON maison, i18n, comparaison de versions.
- `app/` : appli Android (minSdk 23, sans AndroidX, vues construites en code). UI, `MachineService`,
  synchronisation des recettes, `Updater`.
- `tools/` : scripts PowerShell et shell d'installation/restauration, `twin.py` (jumeau simulé de
  la base moteur, utilisé par `TwinIntegrationTest`).

## Points forts

- `SafetyPolicy` est une fonction pure qui renvoie des violations codées : vitesse en chauffe et en
  sens inverse, durées maximales, limite du fouet, Turbo bloqué à chaud, confirmation du mixage chaud.
- Le contrôleur corrige les défauts de l'appli d'origine : préconditions avant chaque démarrage,
  échantillon frais, armement sur 3 échantillons, STOP confirmé par une trame reçue après lui, arrêt
  si la liaison devient muette, alertes verrouillées jusqu'à acquittement, horloge monotone.
- Code découplé (`Clock`, `Transport`) et testable sans matériel ; golden files de trames.

## Points à surveiller

1. **Clé de signature publique** (`app/signature/toque.p12`, mot de passe `toque-public` dans
   `app/build.gradle.kts`). Choix assumé pour que les mises à jour s'installent par-dessus, mais
   Android ne protège plus contre un APK malveillant signé avec la même clé. L'`Updater` ne vérifie
   que le nom du paquet et le `versionCode`, ni l'hôte de `apkUrl` (`Releases.parse`), ni un hash,
   puis installe en root avec `pm install -r`. La confiance repose sur HTTPS vers GitHub et sur le
   compte du dépôt.
   Piste : contrôler l'hôte (`github.com`, `objects.githubusercontent.com`) et publier un SHA-256
   dans la release, à vérifier avant installation.
2. **Concurrence dans `RootShell`** (`RootSerial.kt:33`) : `drained` est un `WeakHashMap` non
   synchronisé et partagé par tous les appels de `run`. Des appels simultanés depuis plusieurs fils
   peuvent le corrompre. Piste : renvoyer le buffer directement depuis `drain`.
3. **Détection d'échec fragile dans `installRoot`** (`Updater.kt:134`) : recherche de « Failure » ou
   « Error » dans la sortie. Si `pm install` ne se termine pas et n'écrit rien, l'utilisateur attend
   180 s sans retour.
4. **Détenteurs du port série** (`RootSerial.kt:114`) : la condition `cmd.contains("cat") …` peut
   tuer un processus étranger dont la ligne de commande contient « cat ». Piste : reconnaître
   précisément le pont de l'appli.
5. **Fichiers volumineux** : `MachineController` (677 lignes), `MainActivity` (454), `SettingsScreen`
   (492) mériteraient d'être découpés.
6. **`targetSdk = 28`** avec lints d'obsolescence désactivés : cohérent avec Android 6, à garder en
   tête pour toute distribution hors de ce robot.

## Verdict

La partie critique (sécurité du moteur et de la chauffe) est solide et bien testée. Le principal
risque est la chaîne de mise à jour (point 1), car elle s'exécute avec les droits root. Les points 2
et 4 sont de petites corrections.

Non vérifié : `gradlew :core:test` et la compilation n'ont pas été lancés.
