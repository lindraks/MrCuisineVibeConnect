# Étude : interaction vocale Toque ↔ Home Assistant ↔ Alexa

Date : 2026-11-17. Statut : étude, rien n'est implémenté.

## Objectif

- Le robot annonce à voix haute qu'il attend une action de l'utilisateur (et d'autres événements).
- L'utilisateur peut demander où en est le robot, combien de temps il reste, etc.
- On peut cibler un Echo ou tous.

## Contexte

- Voix : Echo Pop et Echo Dot (Alexa). Ce ne sont pas des satellites Assist de Home Assistant.
- Home Assistant : installé, HACS présent. HA Cloud non souscrit et non souhaité. La version de HA
  n'a pas été relevée.
- Décision de l'utilisateur : Alexa Media Player (intégration communautaire HACS) est acceptée.
- Robot : tablette Android 6, `minSdk 23`, `targetSdk 28`, sans AndroidX, rootée.

## Pourquoi pas MCP

Alexa ne parle pas MCP et aucun agent LLM n'intervient. Les options « Toque serveur MCP » et
« Toque client du serveur MCP de HA » sont abandonnées. Le serveur MCP de HA n'expose de toute façon
que les entités et intentions exposées à Assist, pas `tts.speak` ni les annonces Alexa.

## Architecture retenue

```
Toque (MachineService) ──webhook JSON──▶ Home Assistant ──▶ Alexa Media Player ──▶ Echo (un ou tous)
```

### 1. Robot vers HA : webhook local

- `POST http://<hôte HA>:8123/api/webhook/<id secret>`, corps JSON, sans jeton, sans cloud.
- L'id est un secret : le stocker dans `Prefs`. Côté HA, déclencheur `webhook` avec
  `local_only: true`.
- `targetSdk = 28` bloque le HTTP en clair par défaut : ajouter un `networkSecurityConfig` qui
  n'autorise le clair que pour l'hôte de HA (ou utiliser du HTTPS).
- Le pont vit dans `MachineService` (processus `:machine`), alimenté par `publish(Snapshot)` et
  `broadcastEvent(MachineEvent)`. Il tourne sur un fil dédié avec une file bornée : un HA lent ou
  absent ne doit jamais retarder `MachineController.tick()` ni la cuisson. Échec silencieux
  (journal seulement).

Corps proposé :

```json
{
  "event": "waiting_user",
  "message": "Le robot attend que vous ajoutiez 200 g de farine.",
  "target": "all",
  "job": "Cuisson manuelle",
  "state": "PAUSED",
  "pause_reason": "LID",
  "remaining_s": 312,
  "temp_c": 82,
  "fault": ""
}
```

Événements à émettre :

| `event` | Source dans le code |
|---|---|
| `waiting_user` | `Snapshot.state == PAUSED` (`pauseReason`), `waitingStop`, mixage chaud à confirmer, étape de recette (côté UI, à vérifier dans `CookScreen`, demandera un message IPC) |
| `lid_opened` / `lid_closed` | `EventType.LID_OPENED` / `LID_CLOSED` |
| `job_started` / `job_paused` / `job_resumed` | `JOB_STARTED`, `JOB_PAUSED`, `JOB_RESUMED` |
| `phase_changed` | `PHASE_CHANGED` |
| `job_finished` / `job_aborted` | `JOB_FINISHED`, `JOB_ABORTED` |
| `fault` / `fault_cleared` | `FAULT`, `FAULT_CLEARED` |
| `status` | instantané périodique (toutes les 30 s pendant un programme) pour répondre aux questions |

Règles côté robot : le texte est déjà traduit avec `tr(...)` (donc ajouter les chaînes dans
`app/src/main/assets/lang/en.json`) ; anti-répétition (une pause « couvercle » n'est annoncée qu'une
fois, un rappel optionnel après N minutes) ; le champ `target` vaut `all` ou un alias saisi dans les
réglages (« Annoncer sur : Tous / Cuisine / Salon… »).

### 2. Ciblage : dans HA, pas dans le robot

Le robot ne connaît aucun identifiant Amazon. Il envoie un alias. Un script HA traduit l'alias en
liste de lecteurs.

Script d'exemple (à adapter, noms d'entités fictifs) :

```yaml
script:
  toque_annonce:
    fields:
      message: { description: Texte à dire }
      cible: { description: "all, cuisine, salon…" }
    sequence:
      - variables:
          cibles:
            all: [media_player.echo_pop_cuisine, media_player.echo_dot_salon]
            cuisine: [media_player.echo_pop_cuisine]
            salon: [media_player.echo_dot_salon]
      - service: notify.alexa_media
        data:
          message: "{{ message }}"
          target: "{{ cibles.get(cible, cibles['all']) }}"
          data:
            type: announce
```

Automatisation d'exemple :

```yaml
automation:
  - alias: Toque vers Alexa
    trigger:
      - platform: webhook
        webhook_id: toque_REMPLACER_PAR_UN_ID_LONG_ET_ALEATOIRE
        allowed_methods: [POST]
        local_only: true
    action:
      - service: script.toque_annonce
        data:
          message: "{{ trigger.json.message }}"
          cible: "{{ trigger.json.target | default('all') }}"
```

À vérifier à l'installation : la syntaxe exacte de `notify.alexa_media` et de `target` selon la
version d'Alexa Media Player, et l'existence éventuelle d'un groupe « partout » utilisable à la place
de la liste.

### 3. Questions vocales (partie délicate)

Il n'existe pas de solution officielle simple sans HA Cloud pour « Alexa, où en est le robot ? ».

| Approche | Avis |
|---|---|
| Annonces proactives (fin, pause, couvercle, action attendue, alerte, « il reste 5 minutes ») | À faire en premier, couvre l'essentiel, aucun risque. |
| Routine Alexa avec phrase personnalisée silencieuse + capteur « dernière commande » d'Alexa Media Player, HA répond par `announce` avec les vraies valeurs (instantané `status`) | À prototyper. Dépend d'un contournement non officiel : peut casser. |
| HA Cloud + compétence Smart Home | Abonnement, pas de phrases libres : écartée. |
| Compétence Alexa personnalisée | Trop lourde (compte développeur, HTTPS public, signatures) : écartée. |

Point à valider par un essai : que la routine reste réellement muette et qu'Alexa Media Player
remonte bien l'énoncé dans HA, sans réponse « je ne sais pas » d'Alexa.

## Sécurité

- **Lecture seule par défaut.** Aucun ordre vocal ne démarre un programme, ne règle vitesse ou
  température, ne confirme un mixage chaud, le couvercle ou une alerte. `SafetyPolicy` reste le seul
  arbitre. Démarrage, Turbo et acquittement d'alerte restent des gestes sur l'écran.
- Si un canal de retour est ajouté un jour : uniquement `pause`, `reprendre`, `arrêter`, via
  `MachineController`, avec authentification et un test dans `ControllerTest`. Cela suppose un point
  d'entrée sur la tablette (serveur HTTP), à étudier à part ; le webhook seul est à sens unique.
- Le secret du webhook est dans `Prefs` en clair : le limiter à ce webhook (aucun jeton HA à
  longue durée sur le robot).
- Alexa Media Player ouvre une session sur le compte Amazon : la double authentification peut
  imposer de se reconnecter de temps en temps. Prévoir que les annonces échouent sans effet sur la
  cuisson.
- Aucune dépendance de la cuisson au réseau.

## Plan

1. **Pont en lecture seule** : réglage « Home Assistant » (activer, URL, id du webhook, alias de
   cible, test d'envoi), file bornée dans `MachineService`, événements ci-dessus, tests unitaires du
   formatage et de l'anti-répétition dans `core/`.
2. **Documentation** : `docs/` avec le webhook, le script et l'automatisation d'exemple.
3. **Annonces « action attendue »** : relever dans `CookScreen` les étapes qui attendent
   l'utilisateur et les remonter au pont (IPC).
4. **Questions vocales** : prototype routine Alexa + dernière commande, instantané `status`.
5. **Optionnel** : commandes `pause`/`reprendre`/`arrêter`.

## Points ouverts

- Version de Home Assistant et d'Alexa Media Player (à relever).
- Où vivent exactement les attentes d'action des recettes (`CookScreen`, non lu).
- Faisabilité de la routine silencieuse (étape 4).
- Les Echo accèdent-ils tous au même compte Amazon que celui d'Alexa Media Player ?
