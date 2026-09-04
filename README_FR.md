# Bible Amplifiée FR — Android

Application Android native construite à partir du fichier `EnglishAmplifiedBible.xml` fourni.

## Fonctionnalités

- 66 livres, 1 189 chapitres, 31 102 versets.
- Noms des livres en français.
- Lecture en Français, Français + English ou English.
- Traduction automatique anglaise → française avec Google ML Kit.
- Téléchargement du modèle de traduction au premier besoin.
- Cache SQLite : une fois un verset traduit, il reste disponible hors ligne.
- Lecture audio Android TTS en français.
- Recherche dans le texte anglais et dans les traductions françaises déjà mises en cache.
- Versets favoris.
- Mode sombre et réglage de taille du texte.

## Prérequis de compilation

- Android Studio Quail 4 (2026.1.4) ou version compatible.
- Android SDK 37.
- JDK 17.
- Connexion Internet pour le premier téléchargement des dépendances Gradle et du modèle ML Kit.

## Compiler l'APK

1. Ouvrir le dossier `BibleAmplifieeFR` dans Android Studio.
2. Laisser Gradle effectuer la synchronisation.
3. Installer le SDK 37 si Android Studio le demande.
4. Menu **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
5. APK debug : `app/build/outputs/apk/debug/app-debug.apk`.

En ligne de commande, si Gradle 9.6.0 est installé :

```bash
./gradlew assembleDebug
```

Sous Windows :

```bat
gradlew.bat assembleDebug
```

Le fichier `gradle/wrapper/gradle-wrapper.properties` indique également la distribution Gradle 9.6.0 attendue par Android Studio.

## Traduction française

L'application ne contient pas une édition officielle française de l'Amplified Bible. Elle traduit le texte anglais fourni avec ML Kit et conserve localement le résultat. Le texte source lui-même contient une mention de copyright ; vérifiez les droits applicables avant redistribution publique ou commerciale.
