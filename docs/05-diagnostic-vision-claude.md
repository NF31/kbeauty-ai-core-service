← [Retour au sommaire](README.md)

# Etape 5 — Diagnostic peau réel (vision Claude)

## Le problème que ça résout

Jusqu'ici, `POST /diagnostics` créait une ligne `status=pending` et publiait
un événement — mais aucune analyse n'existait réellement. Côté Laravel, la
page `/diagnostic-peau` affiche un texte **en dur** selon le type de peau
déclaré (`hardcodedAnalysis()`), pas une vraie analyse. Cette étape branche
la vraie IA : l'API vision de Claude (Anthropic) analyse une photo envoyée
par l'utilisateur.

## Nouveau endpoint : POST /diagnostics/analyze

Contrairement à `POST /diagnostics` (aucun paramètre), celui-ci reçoit un
`multipart/form-data` : `image` (fichier) + `skin_type` (texte). Pas de
JSON ici — un upload de fichier binaire s'envoie en multipart, c'est
`MultipartFile` (Spring) qui le décode, l'équivalent du `$request->file()`
Laravel.

## Fichiers créés/modifiés

### service/AnthropicVisionService.java

Le cœur de l'étape. `RestClient` (client HTTP synchrone intégré à Spring
depuis la 6.1, plus simple que `WebClient` pour un usage bloquant comme ici)
appelle `POST https://api.anthropic.com/v1/messages` avec l'image encodée en
base64 dans le corps de la requête (format `content: [{type: image, source:
{type: base64, ...}}, {type: text, text: prompt}]`, le format multimodal de
l'API Messages Anthropic). Le prompt demande explicitement à Claude de
répondre en JSON strict (`{"scores": {...}, "analysis": "..."}`) — comme un
LLM ne garantit pas un JSON pur (il peut l'entourer de balises ```` ```json
````), `parseAnalysis()` extrait le premier bloc `{...}` trouvé via une regex
plutôt que de parser la réponse brute telle quelle.

**Contrainte RGPD** : l'image ne touche jamais le disque. Elle est lue en
mémoire (`image.getBytes()`), encodée en base64 pour l'appel HTTP, puis
jetée dès que la réponse arrive — seuls le texte d'analyse et les scores
(anonymisés, sans référence à l'image) sont persistés.

### service/SkinAnalysisResult.java, service/AnthropicVisionException.java

Un record pour le résultat parsé (`scores` + `analysis`), une exception
dédiée levée à chaque point de défaillance possible (appel HTTP échoué,
réponse vide, pas de JSON trouvé, JSON invalide) — le contrôleur n'a qu'un
seul type d'erreur à intercepter, peu importe la cause exacte.

### entity/DiagnosticRequest.java (modifié)

Deux colonnes `TEXT` ajoutées : `analysis`, `scoresJson` (les scores stockés
en JSON sérialisé — pas de colonnes séparées par score, un `Map<String,
Integer>` change de forme trop facilement pour figer un schéma SQL dessus).
`spring.jpa.hibernate.ddl-auto=update` crée ces colonnes automatiquement au
prochain démarrage, sans migration à écrire (voir la mise en garde dans
`application.properties` sur les limites de cette approche).

### controller/DiagnosticController.java (modifié)

`POST /diagnostics/analyze` : lit l'image, appelle `AnthropicVisionService`,
persiste le résultat (`status=completed`), publie l'événement
`diagnostic.created` (même `DiagnosticEventPublisher` que `POST
/diagnostics`, donc validé contre le même contrat JSON Schema — `status`
vaut cette fois `"completed"`, déjà dans l'enum du contrat, aucun changement
nécessaire côté schéma). Toute erreur Anthropic devient un `502 Bad Gateway`
avec un message générique — jamais l'exception brute ou une trace technique
renvoyée au client.

## Bug trouvé en conditions réelles : le bloc "thinking" avant le texte

`extractText()` prenait `response.get("content").get(0)` en dur, en
supposant que le premier bloc de la réponse est toujours le texte. Faux
pour `claude-sonnet-5` : ce modèle renvoie l'**extended thinking** activé
par défaut (sans même le demander via le paramètre `thinking` de l'API), ce
qui ajoute un bloc `{"type": "thinking", ...}` **avant** le bloc `{"type":
"text", ...}`. Résultat : `content[0].path("text").asText()` sur un bloc
`thinking` renvoie une chaîne vide (Jackson ne lève pas d'exception sur un
champ absent, juste `""`), et `parseAnalysis()` échouait ensuite avec
« Aucun JSON trouvé » — pas parce que la réponse était vide, mais parce
qu'on lisait le mauvais bloc.

**Invisible en test unitaire** : `AnthropicVisionServiceTest` teste
`parseAnalysis()` directement avec un texte déjà extrait, jamais
`extractText()` avec une vraie réponse Anthropic — le bug ne pouvait
apparaître qu'en conditions réelles. Fix : parcourir `content[]` et prendre
le premier bloc dont `type == "text"`, plutôt que de supposer une position
fixe.

## Vérification

`mvn test` passe (parsing JSON testé isolément, sans appel réseau réel —
`AnthropicVisionServiceTest` construit le service avec une fausse clé et
teste uniquement `parseAnalysis()`).

En conditions réelles (`mvn spring-boot:run`, RabbitMQ démarré, une vraie
clé Anthropic dans `application.properties`), testé à trois niveaux
successifs jusqu'au chemin de succès complet :

1. **Compte sans crédit** : premier essai, `502` immédiat côté client avec
   `{"error": "Analyse IA indisponible"}`. Confirmé par un `curl` direct
   vers `api.anthropic.com` avec la même clé : `invalid_request_error` /
   crédit insuffisant, pas une erreur d'authentification ni de format de
   requête — la clé et le format d'appel étaient corrects, il manquait
   juste du crédit sur le compte.
2. **Après recharge** (5$, propagation en quelques minutes) : la requête
   passe l'étape crédit mais échoue encore, avec le bug `extractText()`
   ci-dessus (reproduit en isolant l'appel Anthropic brut au même prompt/
   image via `curl`, qui a montré le bloc `thinking` en premier).
3. **Après le fix** : chemin de succès confirmé de bout en bout, y compris
   depuis la vraie page Laravel `/diagnostic-peau` (upload multipart réel
   depuis le navigateur, pas juste un `curl` direct sur ce service) —
   `Hibernate: insert into diagnostic_request (analysis,scores_json,
   status)` suivi de `Evenement recu depuis RabbitMQ :
   diagnosticId=X, status=completed`. Testé aussi avec une photo qui n'est
   **pas** une peau (une capture d'écran quelconque) : Claude l'a
   correctement signalé dans `analysis` plutôt que d'inventer des scores —
   comportement voulu, pas un bug.
