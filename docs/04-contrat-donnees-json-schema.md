← [Retour au sommaire](README.md)

# Etape 4 — Contrat de données (JSON Schema)

## Le problème que ça résout

Depuis l'étape 3, `DiagnosticEventPublisher` envoyait un message texte brut :
`"Diagnostic created: id=42"`. Ça fonctionne tant que le seul consommateur
est `DiagnosticEventListener`, dans ce même service — mais rien n'empêchait
de renommer un champ, d'en retirer un, ou de changer son type du jour au
lendemain, sans que rien ne le signale. Un futur consommateur externe (le
vrai objectif : Laravel côté `kbeauty-shop`) casserait silencieusement en
prod le jour où le format change.

**Le contrat de données** répond à ça : un schéma JSON (JSON Schema) décrit
la forme exacte attendue d'un message, versionné dans un dossier partagé
(`contracts/`) entre les deux repos, et **vérifié activement** — pas juste
documenté en commentaire.

## Pourquoi JSON Schema (et pas Avro/Protobuf)

Avro et Protobuf existent aussi pour ça, mais imposent un format binaire et
une étape de compilation de schéma vers du code généré — plus robuste à
grande échelle, mais nettement plus de mise en place pour deux services qui
échangent un seul type de message. JSON Schema reste du JSON lisible
directement, sans génération de code, et suffit largement à ce stade
(mentionné aussi comme piste d'évolution possible dans
`kbeauty-shop/docs/montee-competence/07-axes-hors-programme.md`).

## Fichiers créés/modifiés

### contracts/rabbitmq/diagnostic-created.schema.json

Le contrat lui-même : `eventType` (constante `"diagnostic.created"`),
`diagnosticId` (entier), `status` (`"pending"` ou `"completed"`, les deux
seules valeurs que `DiagnosticRequest.status` prend aujourd'hui),
`occurredAt` (date-heure ISO-8601). `additionalProperties: false` — un champ
en trop dans le message est aussi une violation du contrat, pas seulement un
champ manquant.

Ce dossier `contracts/` est la **source de vérité** (ce service est le
producteur) ; `kbeauty-shop/contracts/` en garde une copie identique,
vérifiable avec `./contracts/check-sync.sh ../kbeauty-shop` (voir
`contracts/README.md`).

### pom.xml

Ajout de `com.networknt:json-schema-validator` (implémentation JSON Schema
draft 2020-12 pure Java) et `spring-boot-starter-test` (absent jusqu'ici,
nécessaire pour écrire un premier test JUnit). Ajout aussi d'un `<resource>`
Maven pointant vers `contracts/` avec `targetPath` `contracts` — ça copie le
dossier dans `target/classes/contracts/` au build, le rendant chargeable
via le classpath (`getClassLoader().getResourceAsStream("contracts/...")`)
sans dupliquer le fichier dans `src/main/resources`.

### amqp/DiagnosticCreatedEvent.java

Un **record** Java — la structure de données qui remplace la string brute.
Un record est l'équivalent d'un DTO immuable : `record Foo(String a, int b)`
génère automatiquement le constructeur, les getters (`a()`, `b()` — pas
`getA()`), `equals()`/`hashCode()`/`toString()`. Pas d'équivalent direct côté
Laravel/Eloquent (le plus proche serait un DTO en lecture seule, sans
Eloquent).

### amqp/DiagnosticCreatedEventSchemaValidator.java

Charge le schéma une fois (au démarrage, dans le constructeur — géré par
Spring comme un singleton `@Component`) et expose `validate(event)`, qui
lève `InvalidEventException` si l'événement ne respecte pas le contrat.
Utilise le même bean `ObjectMapper` que le reste de l'appli (celui
auto-configuré par Spring Boot, avec `WRITE_DATES_AS_TIMESTAMPS` désactivé —
sans ça, un `Instant` se sérialise en nombre plutôt qu'en string ISO-8601,
et le schéma le refuse).

### amqp/DiagnosticEventPublisher.java (modifié)

Construit un `DiagnosticCreatedEvent`, le valide contre le contrat, **puis
seulement** l'envoie. Si un futur changement de code casse accidentellement
le contrat, l'exception remonte ici, avant la publication — pas une fois le
message déjà parti vers un consommateur qui, lui, plantera.

### config/RabbitMQConfig.java (modifié)

Ajout d'un bean `Jackson2JsonMessageConverter`. Spring Boot l'applique
automatiquement au `RabbitTemplate` (publisher) **et** au
`rabbitListenerContainerFactory` par défaut (listener) dès qu'un seul bean
`MessageConverter` existe dans le contexte — les messages passent de texte
brut à JSON des deux côtés sans configuration supplémentaire.

### amqp/DiagnosticEventListener.java (modifié)

La méthode écoute désormais un `DiagnosticCreatedEvent` directement en
paramètre, plus une `String` brute — Spring désérialise le JSON reçu vers ce
type automatiquement (Jackson2JsonMessageConverter utilise le header
`__TypeId__` ajouté par le publisher, qui pointe vers cette même classe
Java — ça fonctionne ici car producteur et consommateur sont dans le même
service ; un consommateur écrit dans un autre langage, comme le futur
consommateur Laravel, doit décoder + valider le JSON lui-même, voir
`kbeauty-shop/docs/montee-competence/12-contrat-rabbitmq-laravel.md`).

## Vérification

`mvn test` — 4 tests dans `DiagnosticCreatedEventSchemaValidatorTest` :
un événement valide passe, un `status` hors de l'enum est rejeté, un
`diagnosticId` manquant est rejeté, un `eventType` inattendu est rejeté.

En conditions réelles (`mvn spring-boot:run`, RabbitMQ démarré) :

```bash
curl -X POST http://localhost:8080/diagnostics
```

Le terminal du serveur affiche `Hibernate: insert into diagnostic_request...`
puis `Evenement recu depuis RabbitMQ : diagnosticId=X, status=pending,
occurredAt=...` — le message circule bien en JSON structuré, désérialisé
automatiquement dans le record, pas juste en texte comme avant. Vérifié
aussi côté broker : `GET /api/exchanges/%2f/kbeauty.exchange/bindings/source`
et `GET /api/queues/%2f/diagnostic.created` sur l'API management RabbitMQ
(`localhost:15672`, `guest`/`guest`) confirment le routage et la
consommation.
