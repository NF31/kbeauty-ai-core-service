← [Retour au sommaire](README.md)

# 6. Resilience applicative sur l'appel Anthropic (Axe 2)

Voir `kbeauty-shop/docs/montee-competence/07-axes-hors-programme.md` (repo
separe, lien texte brut volontaire - un lien Markdown casserait sur GitHub
puisque ce sont deux arbres Git distincts) pour le contexte : cet axe avait
ete evalue premature le 2026-08-09 (aucun
appel API externe n'existait encore sur `main`), debloque par la Phase 5
(voir [05-diagnostic-vision-claude.md](05-diagnostic-vision-claude.md)) qui
donne enfin une cible reelle a un circuit breaker/retry : l'appel HTTP a
`api.anthropic.com` dans `AnthropicVisionService`.

## Le probleme

Avant ce commit, un timeout ou une erreur 5xx transitoire cote Anthropic
faisait echouer `analyze()` du premier coup, sans retenter. Et rien
n'empechait de continuer a taper sur une API deja en train de mal repondre,
ce qui aggrave la situation cote Anthropic (et fait attendre l'utilisateur
pour rien, appel apres appel).

## Ce qui a ete ajoute

**Resilience4j** (`resilience4j-spring-boot3` + `spring-boot-starter-aop`) :
retry avec backoff exponentiel, puis circuit breaker si les echecs
persistent.

### AnthropicApiClient.java (nouveau)

L'appel HTTP brut a Anthropic, qui vivait dans `AnthropicVisionService`, est
extrait dans sa propre classe `@Component`, annotee `@Retry(name =
"anthropicVision")` et `@CircuitBreaker(name = "anthropicVision")`.

**Piege Spring AOP** : ces annotations reposent sur un proxy genere par
Spring (CGLIB) autour du bean. Le proxy n'intercepte que les appels
**externes** au bean (`unAutreBean.call(...)`) — jamais une auto-invocation
interne (`this.call(...)` depuis une methode de la meme classe). Si
`@Retry`/`@CircuitBreaker` avaient ete poses sur une methode privee de
`AnthropicVisionService` appelee depuis `analyze()` de la meme instance, les
annotations auraient ete **silencieusement ignorees** — aucune erreur, juste
aucun retry ni circuit breaker en pratique. D'ou cette classe separee,
injectee comme dependance.

### Ordre des aspects : retry autour du circuit breaker

```properties
resilience4j.retry.retry-aspect-order=1
resilience4j.circuitbreaker.circuit-breaker-aspect-order=2
```

Plus petit = aspect le plus exterieur. Le retry doit envelopper le circuit
breaker (et non l'inverse) : ainsi, chaque tentative de retry repasse par le
circuit breaker, et si celui-ci bascule OPEN en cours de route, les
tentatives de retry restantes echouent **immediatement**
(`CallNotPermittedException`, sans toucher le reseau) plutot que d'attendre
le delai de backoff exponentiel pour un echec deja couru d'avance.

### Ce qui declenche (ou pas) un retry / une ouverture de circuit

Les deux ignorent volontairement `HttpClientErrorException` (les reponses
4xx d'Anthropic — cle API invalide, credit insuffisant, requete malformee) :

- Ce sont des erreurs **du client** (nous), pas un signe que l'API Anthropic
  elle-meme va mal. Les compter contre le circuit breaker fausserait sa
  mesure de sante du service distant.
- Retenter une requete qui echoue en 4xx ne la fera pas reussir : c'est un
  probleme structurel (cle, credit, format), pas transitoire. Retenter
  ferait juste perdre du temps (jusqu'a plusieurs secondes de backoff) avant
  un echec certain.

Le retry ignore en plus `CallNotPermittedException` (circuit deja ouvert) —
meme logique : pas la peine d'attendre un backoff pour un appel qu'on sait
deja refuse.

### Config (application.properties.example)

```properties
resilience4j.retry.instances.anthropicVision.max-attempts=3
resilience4j.retry.instances.anthropicVision.wait-duration=1s
resilience4j.retry.instances.anthropicVision.enable-exponential-backoff=true
resilience4j.retry.instances.anthropicVision.exponential-backoff-multiplier=2

resilience4j.circuitbreaker.instances.anthropicVision.sliding-window-size=10
resilience4j.circuitbreaker.instances.anthropicVision.minimum-number-of-calls=5
resilience4j.circuitbreaker.instances.anthropicVision.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.anthropicVision.wait-duration-in-open-state=30s
resilience4j.circuitbreaker.instances.anthropicVision.permitted-number-of-calls-in-half-open-state=3
```

3 tentatives max (1s, puis 2s de backoff), fenetre glissante de 10 appels
pour le circuit breaker (minimum 5 avant de calculer un taux d'echec), et 30s
de pause avant de retenter en semi-ouvert si le circuit s'est ouvert.

### AnthropicVisionService.java

`analyze()` appelle desormais `apiClient.call(...)` au lieu de construire sa
propre requete HTTP. Un `catch (CallNotPermittedException e)` specifique
distingue "circuit ouvert" (message explicite : service temporairement
suspendu) d'un echec reseau classique — meme pattern que le `catch`
generique deja en place pour les autres erreurs.

## Test : AnthropicApiClientResilienceTest.java (nouveau)

Contrairement au reste des tests de ce repo (unitaires purs, sans contexte
Spring), verifier que `@Retry`/`@CircuitBreaker` fonctionnent **reellement**
exige un vrai proxy AOP — donc un contexte Spring minimal
(`ApplicationContextRunner`, sans base de donnees ni RabbitMQ), avec le
transport HTTP simule par `MockRestServiceServer` (aucun appel reseau reel).
Trois scenarios :

1. **Retry puis succes** : 2 reponses 500 suivies d'une reponse 200 ->
   `call()` retourne le resultat de la 3e tentative sans lever d'exception.
2. **Pas de retry sur 4xx** : une seule reponse 400 programmee ; si un retry
   se declenchait a tort, le 2e appel echouerait faute de reponse
   programmee et `server.verify()` le detecterait.
3. **Ouverture du circuit** : avec une fenetre reduite
   (`minimum-number-of-calls=4`), un premier `call()` epuise ses 3
   tentatives en 500 (echec final `HttpServerErrorException`), un 2e
   `call()` ajoute un 4e echec cumule qui fait basculer le circuit -> sa 2e
   tentative de retry est refusee immediatement (`CallNotPermittedException`)
   sans toucher le reseau.

**Piege trouve en ecrivant ce test** : une premiere version utilisait une
fenetre de circuit breaker tres etroite (`sliding-window-size=2`,
`minimum-number-of-calls=2`) directement dans le test "retry puis succes".
Resultat : les 2 premiers echecs (tentatives 1 et 2, avant le succes de la
tentative 3) suffisaient a eux seuls a faire basculer le circuit OPEN, et la
tentative 3 (censee reussir) se faisait refuser par le circuit avant meme
d'atteindre le mock HTTP configure en succes. Le test "retry puis succes"
utilise donc une fenetre large (`minimum-number-of-calls=10`) pour isoler ce
qu'il teste reellement, et seul le test dedie au circuit breaker retrecit
volontairement la fenetre.

## Comment le voir en vrai

Pas de moyen simple de provoquer un vrai 5xx cote Anthropic a la demande.
Verification faite via le test automatise ci-dessus (HTTP simule) plutot
qu'en conditions reelles cette fois — contrairement a la Phase 5, ou
provoquer les pannes reelles (compte sans credit, bug de parsing) etait
possible et plus instructif.
