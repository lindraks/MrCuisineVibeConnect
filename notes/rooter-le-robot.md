# Rooter le robot (Magisk) : procédure suivie et résultats

Date : 2026-11-19. Testé sur un Monsieur Cuisine Connect SKMC 1200 C3, Android 6.0 (MRA58K), MT6580
(voir `notes/analyse-code.md`, section « Matériel testé »). C'est la procédure d'`AGENTS.md`
(étapes `root`, `flash-root`, `android`), faite à la main parce que le mode console de SP Flash Tool
échoue (voir `notes/activer-adb-sans-root.md`). Résultat : `su` renvoie uid 0, Toque est autorisé dans
Magisk, la partition `boot` est identique à l'image patchée.

**Essai à vide réussi (2026-11-19, signalé par l'utilisateur) :** Toque en « Machine réelle »,
badge « Prête », cuisson manuelle de 10 s à vitesse 2, bol vide et couvercle fermé : la lame a
tourné doucement environ 10 s puis s'est arrêtée seule, sans alerte. Le protocole du build 292
fonctionne donc au moins pour la commande moteur de base sur un robot dont l'appli d'origine est en
build 285 (firmware 4922).

**Détection du couvercle : fonctionne** (signalé par l'utilisateur) : un message s'affiche quand le
couvercle est mal fermé.

**Non testé :** chauffe et températures, sens inverse, Turbo, balance, détection du bol,
programmes (pétrir, vapeur, rissoler), recettes guidées, reprise après ouverture du couvercle en
cours de programme.

## Pré-requis

- Sauvegardes de `boot` et `recovery` déjà vérifiées (`boot-original.img`, `recovery-original.img`,
  16 Mio, copiées ailleurs). Le pilote MediaTek est installé et l'intégrité de la mémoire de Windows
  désactivée : c'est le seul chemin de retour (SP Flash Tool).
- Magisk v31.0 (`Magisk-v31.0.apk`, SHA-256 `2c8a488b9a5293e578e95ae4f07e3c57aba4feec4a52ca4dd852a2692d6dd4e8`),
  TWRP du dépôt EliasKotlyar (`recovery.img`), adb de Google, 7-Zip.
- ADB en USB pour la phase TWRP. Le port réseau 5555 n'accepte qu'un client à la fois (voir la note
  sur ADB), donc la connexion de Home Assistant ne gêne pas l'USB.

## Étapes

1. **TWRP dans `boot`** (SP Flash Tool, fenêtre, *Download Only*, `boot` seul = `recovery.img`),
   puis robot éteint, câble débranché, robot allumé : TWRP s'affiche, ensuite on rebranche le câble.
2. **Patch de Magisk dans TWRP** avec `tools/robot/twrp-root.sh` (source : `boot-original.img`).
   Il produit `/tmp/magisk/new-boot.img`, qu'on récupère avec `adb pull` (`boot-magisk.img`).
   Le script ajoute aussi trois lignes d'ADB à `/system/build.prop` et supprime
   `/system/recovery-from-boot.p` (sinon la recovery d'origine reviendrait sur TWRP) ; les
   originaux sont copiés dans `/tmp/orig`.
3. **Deuxième flash** (SP Flash Tool, *Download Only*) : `boot` = `boot-magisk.img`,
   `recovery` = `recovery.img` (TWRP, pour `adb reboot recovery` ensuite).
4. **Redémarrage** robot éteint, câble débranché, robot allumé : l'appli du robot démarre.
5. **Application Magisk** : `adb install -r Magisk-v31.0.apk`.
6. **Autorisation du shell** : `adb shell su -c 'id -u'` fait afficher une demande « Shell » sur l'écran
   du robot ; il faut toucher **Autoriser** dans les 10 secondes (sinon refus définitif).
7. **`tools/robot/setup-root.sh`** en root : autorise Toque dans Magisk sans fenêtre (politique
   écrite en base) et copie le busybox de Magisk dans `/data/adb/magisk/busybox`, dont Toque a besoin
   pour le port série.
   Il installe aussi `/data/adb/service.d/toque-su-policy.sh`, qui refait cette autorisation à chaque
   démarrage : au boot, la fenêtre Magisk expire sans réponse et la politique de Toque repasse en refus
   (symptômes : « Toque s'est vu refuser les droits de super-utilisateur », badge « Base absente »).
   Journal : `/data/local/tmp/toque-su-policy.log`. Vérifié sur deux redémarrages.

## Vérifications faites

- `su -v` : `31.0:MAGISKSU` ; `su -c 'id -u'` : `0`, sans nouvelle demande.
- La partition `boot`, relue sur le robot, a le même SHA-256 que `boot-magisk.img`.
- `boot-magisk.img` : 16 777 216 octets, en-tête `ANDROID!`, noyau identique à l'original, ramdisk
  avec `.backup` et `overlay.d`.
- `/dev/ttyMT0` existe ; `busybox` installé ; les données de Toque (1 667 recettes) sont intactes
  après les deux flashs.

## Écueils rencontrés

- **`unzip` de TWRP échoue sur l'APK de Magisk** (`unzip: invalid zip magic 00000000`) : le busybox de
  TWRP 3.0.2 ne sait pas lire l'APK aligné de Magisk v31, alors que fichier et espace libre sont
  corrects. Contournement : extraire `assets/` et `lib/armeabi-v7a/` sur le PC avec 7-Zip, les
  pousser dans `/tmp/apkx` ; `twrp-root.sh` les utilise si ce dossier existe (correctif commité).
- Le patch affiche des « Failed to patch » et `can't create /proc/self/fd/` dans TWRP : l'image
  produite est valide malgré tout, le vrai test est le démarrage.
- Magisk propose au premier lancement une « installation supplémentaire » avec redémarrage. Non
  nécessaire ici : `setup-root.sh` installe lui-même le busybox. Choix fait : Annuler.
- Commandes `adb shell` avec guillemets depuis PowerShell : voir la note sur ADB.

## Retour arrière

- Retour à l'appli d'origine en gardant le root : Toque > Réglages > Outils > « Revenir à l'appli
  d'origine » ou `adb uninstall app.toque`.
- Retour complet : SP Flash Tool, *Download Only*, `boot` = `boot-original.img` et `recovery` =
  `recovery-original.img`. Les trois lignes ajoutées à `/system/build.prop` restent : le script
  copie les originaux dans `/tmp/orig` de TWRP (perdu à l'extinction), il faut donc les récupérer
  avec `adb pull /tmp/orig` avant de quitter TWRP (fait ici) pour pouvoir les restaurer.

## Reste à faire

- Essais progressifs des fonctions non testées ci-dessus, avec de l'eau et la main près de
  l'interrupteur principal (chauffe en premier : vérifier que la température affichée suit la
  sonde et que la chauffe s'arrête bien en fin de programme).
- Ménage du PC : retirer le pilote MediaTek (`cleanup`) et réactiver l'intégrité de la mémoire.
