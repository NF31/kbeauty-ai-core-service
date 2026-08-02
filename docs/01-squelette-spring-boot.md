← [Retour au sommaire](README.md)

# Etape 1 — Squelette minimal (route de sante)

## Fichiers crees

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

## Verification

`mvn clean compile` puis `mvn spring-boot:run`, puis
`curl http://localhost:8080/hello` -> doit renvoyer le texte du controleur.
Une 404 "Whitelabel Error Page" sur une AUTRE route que /hello est normale
(c'est la page d'erreur par defaut de Spring Boot, elle prouve que le serveur
tourne).
