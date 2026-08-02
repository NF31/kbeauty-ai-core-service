# kbeauty-ai-core-service

Microservice Java/Spring Boot — Phase 2 de la roadmap montee en competence
(voir kbeauty-shop/docs/montee-competence/ROADMAP.md).

## Concepts de base (pour quelqu'un qui vient de PHP/Laravel)

| Concept Java/Spring | Equivalent Laravel | Role |
|---|---|---|
| Maven (pom.xml) | composer (composer.json) | Gere les dependances et la compilation |
| Spring Boot | Laravel lui-meme | Le framework |
| @SpringBootApplication | public/index.php | Point d'entree de l'appli |
| Hibernate/JPA (a venir) | Eloquent | ORM |
| @Entity (a venir) | class Product extends Model | Une classe = une table |
| @RestController | class ProductController extends Controller | Gere les routes HTTP, renvoie du JSON directement |
| @GetMapping("/hello") | Route::get('/hello', ...) | Declare une route |
| application.properties (a venir) | .env | Configuration |

Difference cle avec PHP : Java est compile avant de tourner (mvn compile),
PHP est interprete a chaque requete. Chaque fichier .java doit etre dans un
dossier qui correspond exactement a son package (com.kbeauty.aicore -> dossier
com/kbeauty/aicore) — regle stricte du langage.

## Ce qui a ete fait (etape 1 : squelette minimal)

- pom.xml : declare le projet, la version de Java (21), et une seule dependance
  pour l'instant (spring-boot-starter-web — le serveur HTTP). Les dependances
  base de donnees (Hibernate/PostgreSQL) et RabbitMQ seront ajoutees a l'etape
  suivante, une fois ce squelette confirme fonctionnel.
- AiCoreServiceApplication.java : la classe principale. Toute application Java
  a besoin d'une methode main() comme point d'entree.
- controller/HealthController.java : un controleur avec une seule route
  GET /hello qui renvoie un texte, juste pour verifier que le serveur demarre
  et repond.

## Comment lancer le serveur (a faire toi-meme dans le terminal WSL)

```bash
cd ~/projects/kbeauty-ai-core-service
mvn spring-boot:run
```

Ca va afficher pas mal de logs Spring Boot (normal), et se terminer par une
ligne du style :

```
Started AiCoreServiceApplication in X.XXX seconds
```

Le serveur tourne alors sur le port 8080 par defaut (Spring Boot). Laisse ce
terminal ouvert (Ctrl+C pour arreter), et dans un AUTRE terminal WSL, teste :

```bash
curl http://localhost:8080/hello
```

Tu dois voir : `kbeauty-ai-core-service is running`

Si tu vois cette ligne, le squelette Spring Boot fonctionne de bout en bout :
compilation OK, serveur demarre, route HTTP repond. On pourra passer a
l'etape suivante (connexion a Neon avec Hibernate/JPA).
