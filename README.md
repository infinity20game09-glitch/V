# V — assistant vocal Jarvis (Android, 100 % hors ligne)

## Compiler (GitHub, gratuit)
1. Crée un dépôt GitHub et envoie-y TOUT le contenu de ce dossier.
   Le dossier caché `.github/workflows/build.yml` est indispensable.
   S'il n'a pas été envoyé : crée-le à la main sur GitHub (Add file > Create new file,
   nom `.github/workflows/build.yml`) et colle le contenu de `github-workflow-build.yml`.
2. Onglet **Actions** > « Build APK » > **Run workflow** (ou il démarre seul au push).
3. Quand c'est vert : ouvre l'exécution > **Artifacts** > télécharge `V-apk` > installe `app-debug.apk`.
   (La 1re compilation dure 15 à 25 min : llama.cpp est compilé.)

## Utilisation
- Paramètres (roue dentée) > Ajouter un modèle (fichier .gguf) > barre de chargement > tableau de bord.
- Dis le premier mot du nom du fichier du modèle (ex. `gemma-2b.gguf` -> « gemma ») pour réveiller V.
  Toucher l'orbe fait la même chose. Nom d'appel modifiable dans les paramètres.
- Langue FR ou EN au choix dans Paramètres (une à la fois).

## Compiler en local
Android Studio + NDK. Il faut mettre les modèles Vosk dans `app/src/main/assets/model-fr`
et `model-en` (voir `.github/workflows/build.yml` pour les liens).
