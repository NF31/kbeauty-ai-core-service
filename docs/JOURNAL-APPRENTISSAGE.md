# Journal d'apprentissage — Java/Spring Boot pour quelqu'un qui vient de PHP/Laravel

Ce document explique chaque concept au fur et a mesure qu'on construit
kbeauty-ai-core-service. A relire quand besoin, complete a chaque etape.

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

---

## Etape 1 — Squelette minimal (route de sante)

### Fichiers crees
- **pom.xml** : declare le projet (groupId/artifactId comme un nom de package
  composer), la version de Java, et les dependances. Pour ce squelette, une
  seule dependance : `spring-boot-starter-web` (le serveur HTTP integre —
  Spring Boot n'a pas besoin d'Apache/Nginx pour tourner en dev, contrairement
  a PHP).
- **AiCoreServiceApplication.java** : la classe principale. Toute application
  Java a besoin d'une methode `main(String[] args)` comme point d'entree —
  contrairement a PHP ou `index.php` suffit tel quel.
  - `@SpringBootApplication` : annotation (commence par @, similaire aux
    attributs PHP 8 `#[...]`) qui dit a Spring "c'est ici que tout demarre,
    scanne les classes autour pour trouver les controleurs/services".
- **controller/HealthController.java** : premier controleur, une route
  `GET /hello`.
  - `@RestController` : equivalent d'un Controller Laravel, mais chaque
    methode renvoie directement du JSON/texte (pas de vue Blade a charger).
  - `@GetMapping("/hello")` : equivalent de `Route::get('/hello', ...)`.

### Verification
`mvn clean compile` puis `mvn spring-boot:run`, puis
`curl http://localhost:8080/hello` -> doit renvoyer le texte du controleur.
Une 404 "Whitelabel Error Page" sur une AUTRE route que /hello est normale
(c'est la page d'erreur par defaut de Spring Boot, elle prouve que le serveur
tourne).

---

## Etape 2 — Connexion base de donnees (Neon/PostgreSQL avec Hibernate/JPA)

### Fichiers crees/modifies
- **pom.xml** : ajout de `spring-boot-starter-data-jpa` (Hibernate, l'ORM) et
  du driver `org.postgresql:postgresql` (Java a besoin d'un driver specifique
  par type de base de donnees, contrairement a PDO en PHP qui est plus
  generique).
- **application.properties** (JAMAIS committe — contient le mot de passe Neon
  en clair, ajoute au .gitignore comme .env). Un `application.properties.example`
  committe a la place, avec des valeurs placeholder, meme logique que
  `.env`/`.env.example`.
  - `spring.jpa.hibernate.ddl-auto=update` : Hibernate cree/modifie
    automatiquement la table selon l'entite Java. Pratique pour ce squelette
    pedagogique (table isolee, dediee a ce service, ne touche pas au schema
    Laravel). **A NE JAMAIS faire en vrai projet en equipe** : ca peut modifier
    le schema de facon imprevisible sans historique. Laravel utilise des
    migrations explicites pour cette raison — l'equivalent Java serait Flyway
    ou Liquibase (a voir plus tard si besoin).
- **entity/DiagnosticRequest.java** : la classe qui represente la table.
  - `@Entity` : dit a Hibernate "cette classe = une table" (nom de table par
    defaut derive du nom de la classe).
  - `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` : cle
    primaire auto-incrementee, equivalent du `id` auto-gere d'Eloquent.
  - Getters/setters explicites : en Java, contrairement a PHP, on ne peut pas
    faire `->status` directement — il faut des methodes `getStatus()`/
    `setStatus()`. Un outil, **Lombok**, permet de generer ce code
    automatiquement (a introduire plus tard une fois le concept de base
    acquis).
- **repository/DiagnosticRequestRepository.java** : juste une interface qui
  etend `JpaRepository<DiagnosticRequest, Long>`. En heritant de cette
  interface, elle obtient GRATUITEMENT des methodes comme `save()`,
  `findAll()`, `findById()`, `deleteById()` — Spring genere l'implementation
  reelle au demarrage, sans qu'on ecrive une seule ligne de code SQL.
  Similaire a ce qu'Eloquent donne deja via `Model::find()`, mais ici c'est
  explicite via l'heritage d'interface plutot qu'implicite via une classe de
  base.
- **controller/DiagnosticController.java** : deux routes,
  `POST /diagnostics` (cree une ligne, status "pending") et
  `GET /diagnostics` (liste tout).
  - Le constructeur `DiagnosticController(DiagnosticRequestRepository repository)`
    illustre l'**injection de dependances** : exactement comme un controleur
    Laravel qui declare un service dans son constructeur et Laravel le fournit
    automatiquement via le service container, Spring voit que le controleur a
    besoin d'un `DiagnosticRequestRepository`, en cree un et l'injecte tout
    seul.

### Verification
`mvn clean compile` (OK), puis `mvn spring-boot:run` — chercher dans les
logs une ligne `create table diagnostic_request` (Hibernate affiche le SQL
grace a `spring.jpa.show-sql=true`). Puis :
```bash
curl -X POST http://localhost:8080/diagnostics
curl http://localhost:8080/diagnostics
```
