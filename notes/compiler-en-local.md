# Compiler Toque en local (Windows, sans Android Studio)

Date : 2026-11-20. Fait sur un PC Windows 11 sans JDK ni Android SDK. Tout est installé hors du
dépôt, dans `C:\Toque\dev` (rien dans le PATH ni dans le registre). Résultat de la première
compilation : 56 tests réussis, 3 ignorés (`TwinIntegrationTest`, qui demande `tools/twin.py`),
APK signé avec la clé publique du projet. Durée : environ 4 minutes, téléchargements compris.

## Outils installés

| Outil | Version | Source | Vérification |
|---|---|---|---|
| JDK | Temurin 17.0.21+9 (zip) | API Adoptium | SHA-256 donné par l'API |
| Outils en ligne de commande Android | zip « latest » (148 Mo) | `dl.google.com`, liste `repository2-3.xml` | SHA-1 de cette liste |
| Plate-forme Android | `platforms/android-34` | outil `android` ci-dessous | — |
| Build-tools | `build-tools/34.0.0` | idem | — |
| Gradle | 8.13 (zip, 131 Mo) | `services.gradle.org` | SHA-256 `distributionSha256Sum` de `gradle/wrapper/gradle-wrapper.properties` |

7-Zip sert à extraire les zip (le zip des outils Android et le JDK s'extraient sans droits
d'administrateur). La licence du SDK Android doit être acceptée par la personne qui installe.

## Commande de compilation (PowerShell)

```
$env:JAVA_HOME = 'C:\Toque\dev\jdk17\jdk-17.0.21+9'
$env:ANDROID_HOME = 'C:\Toque\dev\android-sdk'
$env:GRADLE_USER_HOME = 'C:\Toque\dev\gradle-home'
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
$env:GRADLE_OPTS = '-Dorg.gradle.internal.http.socketTimeout=180000 -Dorg.gradle.internal.http.connectionTimeout=180000'
C:\Toque\dev\gradle-8.13\bin\gradle.bat --no-daemon --console=plain :core:test :app:assembleRelease
```

L'APK sort en `app\build\outputs\apk\release\app-release.apk`. Pour le nommer comme dans les
versions publiées : le copier en `MCC-285-Toque-X.Y.Z.apk` (le workflow `release.yml` fait la même
copie). Contrôles : `apksigner verify --print-certs` (certificat `CN=Toque`, schéma v2) et
`aapt2 dump badging` (version et `versionCode`) dans `build-tools\34.0.0`.

## Pièges rencontrés

- **L'antivirus inspecte le HTTPS.** Avast (« Web/Mail Shield ») signe les certificats avec sa
  propre racine, installée dans Windows mais pas dans Java : `SSLHandshakeException: PKIX path
  building failed` dès le téléchargement de Gradle. Solution propre : faire utiliser à Java le
  magasin de certificats de Windows (`-Djavax.net.ssl.trustStoreType=Windows-ROOT`, ici par
  `JAVA_TOOL_OPTIONS`). Ne pas désactiver la validation. Même cause que l'échec du magasin
  `msstore` de `winget`.
- **Le wrapper `gradlew` expire** (`networkTimeout=10000` dans `gradle-wrapper.properties`, 10 s) pendant
  le téléchargement de Gradle, ralenti par l'inspection. Contournement sans toucher au dépôt :
  télécharger le zip à la main, vérifier son SHA-256 contre `distributionSha256Sum`, l'extraire et
  lancer `gradle.bat` directement.
- **`sdkmanager` est obsolète** : son script prévient qu'il est remplacé par `android sdk`. La
  syntaxe change : `android.exe --no-metrics --sdk=<dossier> sdk install platforms/android-34`
  (barre oblique, pas de point-virgule) ; `sdk list --all` donne les noms. Avec l'ancien
  `sdkmanager.bat`, PowerShell/cmd coupent les noms de paquets au point-virgule
  (`Package platforms not found`). L'option `--licenses` n'est plus nécessaire.
- **`git add` refuse certains fichiers** : `.gitignore` contient `Recipes` et Windows ne distingue pas
  la casse, donc le dossier `app/src/main/kotlin/app/toque/recipes/` paraît ignoré alors que ses
  fichiers sont suivis. `git add <fichier>` échoue (et annule tout l'ajout) ; `git add -u -- <fichiers>`
  fonctionne.
- Les scripts shell écrits pour le robot doivent rester en LF (`.gitattributes`) ; `en.json`, lui,
  est en CRLF : l'éditer en préservant les fins de ligne.

## Installer sur le robot sans bloquer le port série

Un arrêt brutal de la liaison de Toque avec la base peut laisser `/dev/ttyMT0` coincé dans le
noyau jusqu'au redémarrage (voir `notes/activer-adb-sans-root.md`). Avant de réinstaller :

1. passer Toque en **Démo** par l'écran (Réglages › Machine › Démo › Appliquer), qui ferme proprement
   la liaison ;
2. `adb install -r MCC-285-Toque-X.Y.Z.apk` (mêmes signature et `applicationId` : réglages, recettes et
   autorisation Magisk sont conservés) ;
3. repasser en **Machine réelle** sur l'écran du robot : la fenêtre de sécurité (bol vide, couvercle
   fermé) doit être validée par la personne.

Pendant la réinstallation, Android relance l'appli d'origine (elle aussi application d'accueil) :
elle peut afficher « MC2 s'est arrêté » en échouant à joindre son serveur. C'est sans effet pour
Toque ; fermer la fenêtre.

## Publier

Un `push` sur `main` lance `.github/workflows/release.yml` : tests, compilation, puis création de la
version `vX.Y.Z` avec l'APK `MCC-285-Toque-X.Y.Z.apk` (rien n'est publié si le tag existe déjà). Il faut
donc augmenter `versionCode` et `versionName` dans `app/build.gradle.kts` pour une nouvelle version. L'APK
publié vient d'une autre machine : même version et même signature, pas le même hash que l'APK local.
