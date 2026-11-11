# kbeauty-ai-core-service

Microservice Java/Spring Boot de l'écosystème kbeauty-shop (dépôt privé) :
diagnostic de peau à partir d'une photo (API vision de Claude), demandes persistées en
PostgreSQL et événements publiés sur RabbitMQ. Phase 2 de la roadmap de montée en compétence
(voir [ROADMAP.md](https://github.com/NF31/kbeauty-ecosystem/blob/main/docs/montee-competence/ROADMAP.md)).

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

- **pom.xml** : Spring Boot 3.5.16 (Java 25 LTS), `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
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
- **contracts/rabbitmq/diagnostic-created.schema.json** + **amqp/DiagnosticCreatedEvent.java**,
  **DiagnosticCreatedEventSchemaValidator.java** : le message RabbitMQ est
  desormais du JSON structure valide contre un contrat versionne (Axe hors
  programme 1, voir [docs/04-contrat-donnees-json-schema.md](docs/04-contrat-donnees-json-schema.md)),
  pas une string libre.
- **service/AnthropicVisionService.java** + **controller/DiagnosticController.java**
  (`POST /diagnostics/analyze`) : diagnostic peau reel — une photo envoyee en
  multipart est analysee par l'API vision de Claude (Anthropic), jamais
  ecrite sur disque (voir [docs/05-diagnostic-vision-claude.md](docs/05-diagnostic-vision-claude.md)).
- **service/AnthropicApiClient.java** : appel HTTP a Anthropic protege par
  Resilience4j (retry avec backoff exponentiel, puis circuit breaker si les
  echecs persistent) — Axe 2 hors programme, voir
  [docs/06-resilience-anthropic.md](docs/06-resilience-anthropic.md).
- Encore a faire : contrat OpenAPI/Swagger (Springdoc) — seul item Phase 2 restant.

## Lancer le service

Prérequis :

- RabbitMQ démarré (`docker compose up -d rabbitmq` dans
  [kbeauty-ecosystem](https://github.com/NF31/kbeauty-ecosystem)) ;
- `src/main/resources/application.properties` créé depuis
  `application.properties.example`, avec les accès PostgreSQL (Neon) et la clé
  Anthropic. Ce fichier n'est jamais commité.

```bash
mvn spring-boot:run
```

Le service écoute sur le port 8080 :

```bash
curl -X POST http://localhost:8080/diagnostics   # crée une demande et publie diagnostic.created
curl http://localhost:8080/diagnostics           # liste les demandes enregistrées
curl http://localhost:8080/hello                 # route de santé
```

## Scan de secrets (gitleaks)

Garde-fou contre le commit accidentel de clés API / tokens.

```bash
# Une fois par clone :
git config core.hooksPath .githooks
```

Le hook `pre-commit` lance `gitleaks git --staged` et bloque le commit si un
secret est détecté (`git commit --no-verify` pour forcer). Même scan en CI sur
chaque push / PR (`.github/workflows/gitleaks.yml`), sur l'historique complet.
Faux positifs éventuels à déclarer dans `.gitleaks.toml`.
