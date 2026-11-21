# Activer ADB (USB et réseau) sans root

Date : 2026-11-19. Testé sur un Monsieur Cuisine Connect SKMC 1200 C3 (voir
`notes/analyse-code.md`, section « Matériel testé »). Résultat : ADB en USB et en Wi-Fi, accès
`shell` sans root, appli d'origine et `/system` inchangés. Variante minimale de la procédure
d'`AGENTS.md` : pas de Magisk, pas de TWRP laissé en recovery.

## Pourquoi cette méthode

Sur ce robot, l'Android d'origine n'offre pas ADB (`default.prop` : `ro.secure=1`,
`ro.adb.secure=1`, `persist.sys.usb.config=mtp`), et l'appli du robot ne donne pas accès aux options
pour développeurs. La seule voie est d'écrire dans `/data` depuis TWRP.

## Principe

1. Sauvegarder `boot` et `recovery` (lecture seule, étape `backup`) et garder une copie ailleurs.
2. Flasher TWRP dans `boot` (SP Flash Tool, fenêtre, *Download Only*, `boot` seul).
3. Démarrer TWRP (robot éteint, câble débranché, allumer, puis brancher le câble).
4. Lancer `tools/robot/twrp-enable-adb.sh` (voir ci-dessous).
5. Remettre `boot-original.img` dans `boot` (même procédure qu'en 2).
6. Redémarrer : ADB est actif, la recovery d'origine n'a jamais été modifiée.

## Ce que fait le script

`tools/robot/twrp-enable-adb.sh`, exécuté dans TWRP, n'écrit que dans `/data` :

| Fichier | Contenu | Droits |
|---|---|---|
| `/data/property/persist.sys.usb.config` | `mtp,adb` | `root:root`, `0600`, `property_data_file` |
| `/data/property/persist.adb.tcp.port` | `5555` | idem |
| `/data/misc/adb/adb_keys` | clés publiques autorisées | `system:shell`, `0640`, `adb_keys_file` |

Les fichiers remplacés éventuels sont copiés dans `/tmp/orig` (vide ici : rien n'existait). Fichier
de clés fourni au script : `adb push <clés> /tmp/adbkeys.pub`, une clé publique par ligne.

Les clés à autoriser sont celles du PC (`%USERPROFILE%\.android\adbkey.pub`) et celle de Home
Assistant, que l'intégration « Android Debug Bridge » génère elle-même
(`/config/.storage/androidtv_adbkey.pub`, copiable par `scp` depuis le module Terminal & SSH). Une
clé publique ADB fait 700 caractères en base64 et se décode en 524 octets : le vérifier avant de
l'envoyer, une clé recopiée à la main avec une erreur est refusée sans message.

## Vérifications faites

- USB : `adb devices` voit le robot sans fenêtre d'autorisation (clé pré-autorisée), `ro.build` en
  Android 6.0, utilisateur `shell`.
- Réseau : `adbd` écoute sur `0.0.0.0:5555`, `adb connect <IP>:5555` et `adb shell` fonctionnent.
- Redémarrage : ADB en réseau revient seul après un redémarrage du robot.
- Home Assistant : l'intégration se connecte avec sa propre clé.

## Limites constatées

- **Un seul client réseau à la fois.** Pendant que Home Assistant est connecté, un
  `adb connect` depuis le PC échoue (`device offline`), et inversement : HA est ignoré tant que le PC
  est connecté (connexions de HA en `CLOSE_WAIT` côté robot, données non lues). Le câble USB est un
  autre canal et peut coexister avec la connexion de HA.
- Accès `shell` seulement (pas de root) : `adb_keys` n'est pas lisible depuis `shell`.
- Fixer l'adresse du robot dans la box (bail DHCP statique) : HA et `adb connect` en dépendent.
- Le port 5555 est ouvert sur tout le réseau local ; seules les clés autorisées passent
  (`ro.adb.secure=1`). Ne pas l'exposer sur Internet.

## Après un redémarrage de Home Assistant : connexion bloquée (2026-11-20)

Constat : Home Assistant (HA OS) a redémarré (arrêté puis relancé en environ 4 min 30 s). Au moment de
l'arrêt, tous ses appareils ADB ont expiré en même temps. Après le redémarrage, trois appareils se
sont reconnectés seuls ; le robot (Android 6) et une tablette (Android 7) sont restés en
`setup_retry` avec « Reading from <IP>:5555 timed out (9.0 seconds) » : la connexion TCP s'ouvre mais
`adbd` ne répond pas à HA (côté robot, ses sockets restent en `CLOSE_WAIT` avec 45 octets non lus).

- Pendant ce temps, `adb connect` depuis le PC fonctionnait, et le robot était sain (journal de
  Toque propre, Wi-Fi connecté, pas de redémarrage).
- La clé ADB de HA n'avait **pas** changé : la clé publique récupérée par `scp`
  (`/config/.storage/androidtv_adbkey.pub`) était identique à celle autorisée sur le robot.
- Recharger l'entrée dans HA (la désactiver puis la réactiver) n'a pas suffi.
- **Ce qui a débloqué** : relancer `adbd` sur le robot, puis recharger l'entrée ; HA s'est reconnecté
  dans la minute :

  ```
  adb shell su -c 'setprop ctl.restart adbd'
  ```

  (la connexion du PC tombe, c'est normal, le port 5555 revient). La coïncidence est forte mais la
  cause n'est pas démontrée : hypothèse, une connexion de HA morte (hôte redémarré sans fermer ses
  sockets) qui monopolise l'unique client d'`adbd`.
- À prévoir si cela se reproduit : relance d'`adbd` programmée (script Magisk) ou alerte dans HA
  quand l'entité reste indisponible.

Astuce : le service `androidtv.adb_command` de HA exécute une commande shell **par la connexion déjà
ouverte de HA**, sans prendre l'unique client réseau, par exemple
`input keyevent 223` pour éteindre l'écran. Le résultat se lit dans l'attribut `adb_response` de
l'entité (vide pour une commande sans sortie).

## Écueils rencontrés

- Le mode console de SP Flash Tool (`flash_tool.exe -i`) échoue sur Windows 11 (code -3, aucune
  sortie) : utiliser la fenêtre de l'outil.
- Le fichier de configuration généré par `toque-setup.ps1` démarre avec un BOM UTF-8 ; ce n'est pas la
  cause de l'échec, qui reste inexpliquée.
- Lancer une commande `adb shell` avec des guillemets depuis PowerShell les fait disparaître : le
  shell du robot reçoit alors une commande tronquée et peut se bloquer. Pousser un script
  (`adb push`) puis `adb shell sh /tmp/script.sh`.
- Le robot allumé câble branché s'arrête sur une image de batterie : brancher le câble après
  l'allumage.

## Retour arrière

Supprimer les trois fichiers listés plus haut depuis TWRP (ou refaire la procédure avec un script
qui les supprime), ou rétablir `boot`/`recovery` depuis les sauvegardes avec SP Flash Tool.
