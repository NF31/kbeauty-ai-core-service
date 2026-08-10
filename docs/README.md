# Journal d'apprentissage — Java/Spring Boot pour quelqu'un qui vient de PHP/Laravel

Explications pedagogiques au fur et a mesure qu'on construit
kbeauty-ai-core-service. Un fichier par etape, a relire quand besoin.

## Table de correspondance generale

| Concept Java/Spring | Equivalent Laravel/PHP | Role |
|---|---|---|
| Maven (pom.xml) | composer (composer.json) | Gere les dependances et la compilation |
| Spring Boot | Laravel lui-meme | Le framework |
| @SpringBootApplication | public/index.php | Point d'entree de l'appli |
| Hibernate/JPA | Eloquent | ORM (Object-Relational Mapping) |
| @Entity | class Product extends Model | Une classe Java = une table |
| Repository (interface JpaRepository) | Model (classe Eloquent) | CRUD auto-genere |
| @RestController | class ProductController extends Controller | Gere les routes HTTP, renvoie du JSON directement |
| @GetMapping("/hello") | Route::get('/hello', ...) | Declare une route |
| application.properties | .env | Configuration |
| application.properties.example | .env.example | Modele de config sans secrets, committe |
| RabbitMQ | Jobs/Queues Laravel (dispatch + queue:work) | File d'attente asynchrone |

## Difference fondamentale : compilation vs interpretation

PHP est interprete a chaque requete HTTP. Java doit etre COMPILE avant de
tourner (`mvn compile`) — le code source .java est transforme en bytecode
.class que la JVM execute. C'est pour ca qu'on lance `mvn spring-boot:run`
au lieu d'un simple `php artisan serve`.

## Regle des packages (dossiers)

Chaque fichier .java declare un `package` en premiere ligne
(ex: `package com.kbeauty.aicore.controller;`). Ce package DOIT correspondre
exactement au chemin du dossier (com/kbeauty/aicore/controller/). C'est une
regle stricte du langage, pas une convention comme les namespaces PSR-4 en PHP
(qui sont plus souples).

## Sommaire des etapes

1. [Squelette Spring Boot (route de sante)](01-squelette-spring-boot.md)
2. [Connexion Neon via Hibernate/JPA](02-connexion-neon-jpa.md)
3. [RabbitMQ (publication d'evenements)](03-rabbitmq-events.md)
4. [Contrat de donnees (JSON Schema)](04-contrat-donnees-json-schema.md)
5. [Diagnostic peau reel (vision Claude)](05-diagnostic-vision-claude.md)
6. [Resilience applicative sur l'appel Anthropic (retry + circuit breaker)](06-resilience-anthropic.md)

## Pourquoi faut-il relancer le serveur a chaque modification de code ?

En PHP/Laravel, chaque requete HTTP relit et reinterprete le fichier PHP a la
volee — aucun redemarrage necessaire apres une modification.

En Java, le code est COMPILE en bytecode puis charge une fois dans la JVM
(machine virtuelle Java) au demarrage. Tant que le process tourne, il utilise
cette version compilee en memoire — il ne relit jamais les fichiers .java sur
disque. Toute modification necessite donc : arreter le serveur (Ctrl+C) ->
recompiler -> relancer.

Un outil, **Spring Boot DevTools**, automatise ce cycle en dev (redemarrage
automatique en quelques secondes des qu'un fichier change) — a introduire une
fois le cycle manuel bien compris.
