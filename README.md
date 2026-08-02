# kbeauty-ai-core-service

Microservice Java/Spring Boot — Phase 2 de la roadmap montee en competence
(voir kbeauty-shop/docs/montee-competence/ROADMAP.md).

## Concepts de base (pour quelqu'un qui vient de PHP/Laravel)

| Concept Java/Spring | Equivalent Laravel | Role |
|---|---|---|
| Maven (pom.xml) | composer (composer.json) | Gere les dependances et la compilation |
| Spring Boot | Laravel lui-meme | Le framework |
| @SpringBootApplication | public/index.php | Point d'entree de l'appli |
| Hibernate/JPA | Eloquent | ORM |
| @Entity | class Product extends Model | Une classe = une table |
| @RestController | class ProductController extends Controller | Gere les routes HTTP, renvoie du JSON directement |
| @GetMapping("/hello") | Route::get('/hello', ...) | Declare une route |
| application.properties | .env | Configuration |
| RabbitMQ (@RabbitListener) | Jobs/Queues Laravel (dispatch + queue:work) | File d'attente asynchrone |

Difference cle avec PHP : Java est compile avant de tourner (mvn compile),
PHP est interprete a chaque requete. Chaque fichier .java doit etre dans un
dossier qui correspond exactement a son package (com.kbeauty.aicore -> dossier
com/kbeauty/aicore) — regle stricte du langage.

Journal pedagogique detaille (une entree par etape) dans [`docs/`](docs/README.md).

## Ce qui a ete fait (Phase 2 de la roadmap montee en competence)

- **pom.xml** : Spring Boot 21, `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-amqp`, driver PostgreSQL (connexion Neon).
- **entity/DiagnosticRequest.java** + **repository/DiagnosticRequestRepository.java** :
  une entite JPA simple (`status`, horodatage) persistee dans Neon via
  `JpaRepository` (equivalent Eloquent).
- **controller/DiagnosticController.java** : `POST /diagnostics` (cree une ligne en
  base avec le statut `pending`, puis publie un evenement RabbitMQ) et
  `GET /diagnostics` (liste tout).
- **amqp/RabbitMQConfig.java**, **DiagnosticEventPublisher.java**,
  **DiagnosticEventListener.java** : publication + consommation d'un evenement
  `diagnostic.created` sur RabbitMQ, decouple du controleur.
- **controller/HealthController.java** : `GET /hello`, route de sante d'origine, conservee.
- Encore a faire : contrat OpenAPI/Swagger (Springdoc) — seul item Phase 2 restant.

## Comment lancer le serveur (a faire toi-meme dans le terminal WSL)

Prerequis : `kbeauty_rabbitmq` demarre (`docker start kbeauty_rabbitmq`, voir
`kbeauty-ecosystem/docker-compose.yml`) et `application.properties` rempli avec
les credentials Neon (copier `application.properties.example`, jamais commit).

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
curl -X POST http://localhost:8080/diagnostics
curl http://localhost:8080/diagnostics
```

La premiere commande cree une ligne dans Neon et publie un evenement RabbitMQ
(visible dans les logs du serveur, cote listener) ; la seconde liste les lignes
enregistrees. `curl http://localhost:8080/hello` fonctionne toujours aussi.
