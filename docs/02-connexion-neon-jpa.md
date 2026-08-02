← [Retour au sommaire](README.md)

# Etape 2 — Connexion base de donnees (Neon/PostgreSQL avec Hibernate/JPA)

## Fichiers crees/modifies

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
    faire `$model->status` directement — il faut des methodes `getStatus()`/
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

## Verification

`mvn clean compile` (OK), puis `mvn spring-boot:run` — chercher dans les
logs une ligne `create table diagnostic_request` (Hibernate affiche le SQL
grace a `spring.jpa.show-sql=true`). Puis :

```bash
curl -X POST http://localhost:8080/diagnostics
curl http://localhost:8080/diagnostics
```

Confirme en conditions reelles : insertion (`{"id":1,"status":"pending"}`)
et lecture (`[{"id":1,"status":"pending"}]`) fonctionnelles.
