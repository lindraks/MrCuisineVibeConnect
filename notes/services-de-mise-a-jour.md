# Services de mise à jour du robot : MCUpdater et tgifota

Date : 2026-11-19. Robot : SKMC 1200 C3, Android 6.0, rooté (voir `notes/rooter-le-robot.md`).
Analyse **statique** de la copie du système (`system.img`, lue avec 7-Zip) : manifestes et chaînes
des APK. Je n'ai pas observé leur trafic réseau ni leur comportement en fonctionnement.

## En bref

| | `de.silpion.mcupdater` | `com.adups.tgifota` |
|---|---|---|
| Rôle | met à jour des **applis** | met à jour le **système** (firmware) |
| Chemin | `/system/priv-app/MCUpdater` | `/system/priv-app/tgifota` |
| Droits | `android.uid.system`, `INSTALL_PACKAGES`, `REBOOT`, `INTERNET`, `RECEIVE_BOOT_COMPLETED` | `android.uid.system`, `INTERNET`, `RECEIVE_BOOT_COMPLETED`, `MOUNT_UNMOUNT_FILESYSTEMS`, `BIND_JOB_SERVICE` |
| Risque pour le root | faible | élevé : peut remplacer `boot` et `system` |
| Décision | laissé actif | **désactivé** |

## MCUpdater

- Composants : `BootBroadcastReceiver` (au démarrage) et `MCUpdateService` (service lié, interface
  AIDL `IUpdateService`), appelé par l'appli du robot (`MCLauncher`) pour ses mises à jour.
- Fonctions vues dans le code : téléchargement (`DownloadProcessor`), validation par clé publique
  (`PublicKeyValidator`), installation de paquets (`PackageUpdater`), lecture d'une configuration
  JSON du fabricant (`MachineConfigResponse`), puce de chiffrement (`EncryptionChipServiceAdapter`).
- Je n'y ai trouvé ni `RecoverySystem` ni écriture de partition : il met à jour des applis, pas le
  firmware. L'adresse de son serveur n'apparaît pas dans son code.
- Il tourne en permanence après le démarrage (processus visible), mais il est piloté par
  `MCLauncher` ; l'appli d'origine étant arrêtée par Toque, il est probablement inactif.

## tgifota (Adups FOTA)

- Client de mise à jour du système du fabricant de logiciels Adups : `com.adups.tgifota`, version
  1.1, avec `RegisterService` et `JobSchedulerService`.
- Il lit les propriétés `ro.fota.*` du `build.prop` du robot (plate-forme `MTK8321_6.0`, type
  `pad`, carte `spm188a`, version `v3.9a_20190808`).
- Il s'enregistre sur `http://iotapi.adups.com/` (**HTTP en clair**) et utilise `RecoverySystem`
  pour installer un paquet.
- `/system/etc/DDConfig.txt` pointe vers un fichier `DDFile.json` hébergé sur
  `mc20.monsieur-cuisine.com` (HTTPS) : les mises à jour du système viennent donc du serveur du
  fabricant. L'URL complète contient un identifiant, volontairement non recopié ici.
- Je n'ai pas consulté ce fichier : je ne sais donc pas si une mise à jour est annoncée.
- Danger pour le root : un paquet installé par la recovery remplacerait `boot` et `system`.

## Désactivation de tgifota (faite)

```
adb shell su -c 'pm disable com.adups.tgifota; am force-stop com.adups.tgifota'
```

Vérification : `dumpsys package com.adups.tgifota` affiche `enabled=2`, le paquet figure dans
`pm list packages -d`, plus aucun processus `adups` ou `fota`. L'état est conservé dans
`/data/system/users/0/package-restrictions.xml` (non testé après redémarrage).

Annuler : `adb shell su -c 'pm enable com.adups.tgifota'`.

Limite : une mise à jour par un autre chemin (flash USB, port de fabrique) n'est pas bloquée ; c'est
un acte volontaire.

## `install-recovery.sh` du système

`/system/bin/install-recovery.sh` remet la recovery d'origine au démarrage avec `applypatch`, à partir
de `boot` et de `/system/recovery-from-boot.p`. Le script de root (`twrp-root.sh`) supprime ce fichier
`.p`, ce qui explique que TWRP reste en recovery. Une copie du fichier est dans
`system-originals/orig` côté PC.

## À savoir

- ADB en réseau n'accepte qu'un client à la fois : désactiver l'intégration du robot dans Home
  Assistant le temps de ces opérations (voir `notes/activer-adb-sans-root.md`).
- Si un jour une mise à jour officielle est souhaitée, réactiver `tgifota` puis revenir aux images
  d'origine (`boot-original.img`, `recovery-original.img`) avant de la laisser s'installer.
