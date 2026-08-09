# Contrats de données RabbitMQ

Schémas JSON Schema (draft 2020-12) décrivant la forme exacte de chaque
message publié sur `kbeauty.exchange`. Objectif : si le format d'un message
change côté producteur (ce service) sans mise à jour du schéma, la
validation échoue *avant* la publication plutôt que de laisser le
consommateur (`kbeauty-shop`) planter silencieusement en prod sur un champ
manquant ou renommé.

## Source de vérité

Ce dossier (`kbeauty-ai-core-service/contracts/`) est la source de vérité,
car ce service est le producteur des événements. `kbeauty-shop/contracts/`
contient une **copie identique**, committée séparément côté Laravel.

Pas de schema registry partagé ici (Confluent Schema Registry ou
équivalent, hors scope pédagogique pour un seul événement) — convention
manuelle à la place : toute modification d'un schéma doit être répercutée
dans les deux repos dans la même session de travail, vérifiée avant commit
avec :

```bash
./contracts/check-sync.sh ../kbeauty-shop
```

## Fichiers

- `rabbitmq/diagnostic-created.schema.json` — événement `diagnostic.created`
  (exchange `kbeauty.exchange`, routing key `diagnostic.created`), publié par
  `DiagnosticEventPublisher` à chaque `POST /diagnostics`, consommé côté
  Laravel par la commande Artisan `rabbitmq:consume-diagnostic-created`.
