← [Retour au sommaire](README.md)

# Etape 3 — RabbitMQ (publication d'evenements)

## Le concept : RabbitMQ, c'est quoi ?

RabbitMQ est une file d'attente de messages (message broker). Le principe :

- Un service (notre Spring Boot) publie un message dans une "queue" (file
  d'attente), sans attendre de reponse — il continue son travail
  immediatement.
- Un autre service (ou le meme) ecoute cette queue et traite le message
  quand il arrive, a son propre rythme.

C'est l'equivalent conceptuel des Jobs/Queues de Laravel
(`dispatch(new SendEmail())`, traite par `php artisan queue:work`) — sauf
que Laravel utilise Valkey/Redis comme file d'attente, alors qu'ici on
utilise RabbitMQ, un outil dedie plus robuste, avec un protocole standard
(AMQP) que n'importe quel langage peut parler (Java, PHP, Python...).

Pourquoi c'est utile ici : quand `POST /diagnostics` cree une ligne en
base, on veut aussi "annoncer" cet evenement pour que d'autres services
(plus tard : Laravel qui enverra un email, ou un futur service IA) puissent
reagir — sans que Spring Boot ait a connaitre ces autres services
directement. C'est le decouplage dont parlait le dossier d'architecture.

## Le concept AMQP (Exchange, Queue, Binding)

RabbitMQ ne fonctionne pas en "envoie direct a une queue" — il y a une etape
intermediaire, l'**Exchange**, qui route le message vers la ou les bonnes
queues selon des regles (le **Binding**). C'est plus flexible que Laravel
Queues (un seul niveau), ca permet par exemple qu'un meme message parte vers
plusieurs queues differentes.

Schema simple pour nous :
```
Publisher -> Exchange -> Binding -> Queue -> Listener
```

## Fichiers crees/modifies

### config/RabbitMQConfig.java

Declare les elements AMQP (Queue, Exchange, Binding) comme des "beans" geres
par Spring.

- `@Configuration` : dit a Spring "cette classe definit des objets a
  gerer" (similaire a un ServiceProvider Laravel avec sa methode
  `register()`).
- `@Bean` : chaque methode annotee `@Bean` cree un objet que Spring garde en
  memoire et peut injecter ailleurs (comme `$this->app->bind(...)` dans un
  ServiceProvider).
- `new Queue(QUEUE_NAME, true)` : le `true` = **durable**, la queue survit a
  un redemarrage de RabbitMQ (persistee sur disque, comme mentionne dans le
  dossier d'architecture).

### amqp/DiagnosticEventPublisher.java

Le service qui envoie le message.

- `@Component` : dit a Spring "gere cette classe comme un service"
  (generique, comme `@RestController` mais sans routes HTTP).
- `RabbitTemplate` : objet fourni automatiquement par Spring Boot (des que
  `spring-boot-starter-amqp` est present) pour envoyer des messages, sans
  qu'on ait a ecrire la logique de connexion AMQP nous-memes.

### controller/DiagnosticController.java (modifie)

Le constructeur prend maintenant DEUX dependances
(`DiagnosticRequestRepository` et `DiagnosticEventPublisher`) — montre que
l'injection de dependances scale naturellement a plusieurs services, comme
en Laravel. Apres `repository.save(diagnostic)`, on appelle
`publisher.publishDiagnosticCreated(saved.getId())` pour annoncer
l'evenement.

### amqp/DiagnosticEventListener.java

Le service qui recoit et traite le message — pour prouver que la boucle
complete fonctionne.

- `@RabbitListener(queues = ...)` : l'inverse du Publisher, dit a Spring
  "appelle cette methode automatiquement a chaque message qui arrive dans
  cette queue".
- `Logger`/`LoggerFactory` : le systeme de logs standard Java, equivalent de
  `Log::info()` en Laravel.

### application.properties / .example

Ajout de la connexion RabbitMQ (`spring.rabbitmq.host/port/username/password`)
— pointe vers le conteneur `kbeauty_rabbitmq` du docker-compose
kbeauty-ecosystem (localhost:5672, guest/guest par defaut).

## Verification

`mvn clean compile` (OK), puis `mvn spring-boot:run` — chercher au demarrage
une ligne `Created new connection: ... amqp://guest@127.0.0.1:5672/`. Puis
dans un autre terminal :

```bash
curl -X POST http://localhost:8080/diagnostics
```

Confirme en conditions reelles : le terminal du serveur affiche
`Hibernate: insert into diagnostic_request...` PUIS
`Message recu depuis RabbitMQ: Diagnostic created: id=X` — le cycle complet
creation -> insertion Neon -> publication -> ecoute -> traitement fonctionne
de bout en bout. Verifiable aussi sur le dashboard RabbitMQ
(http://localhost:15672, guest/guest), onglet Queues, la queue
`diagnostic.created` existe.
