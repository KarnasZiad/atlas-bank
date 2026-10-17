# ADR-0001 — Microservices Spring Boot 4 à architecture hexagonale

- Statut : acceptée
- Date : 2026-10-12

## Contexte

Le système doit évoluer par domaines (clients, comptes, opérations, notifications) avec des équipes
et des rythmes différents, tout en restant maintenable : peu de couplage, peu de duplication,
règles métier testables.

## Décision

- Quatre services métier découpés par **propriété des données**, plus une gateway.
- Java 21, **Spring Boot 4.1** et **Spring Cloud 2025.1** : couple supporté (Spring Cloud 2025.1
  déclare compatibles Boot 4.0 et 4.1 ; Spring Boot 3.5 n'est plus maintenu en open source depuis
  juin 2026). Les montées de Spring, Spring Cloud, Modulith et springdoc arrivent ensemble, en une seule
  PR Dependabot.
- **Threads classiques** dans les services : sous Java 21, un thread virtuel qui attend dans un bloc
  `synchronized` bloque son thread porteur, ce que fait le client Kafka à chaque `poll`. Avec un
  consommateur par partition, les porteurs étaient monopolisés et même les requêtes HTTP n'étaient
  plus servies (constaté en CI). Les threads virtuels seront réactivés avec un runtime Java 24 ou plus
  (JEP 491).
- Dans chaque service : couches `domain` / `application` / `infrastructure` / `web` ; le sens des
  dépendances est **testé** par ArchUnit (`HexagonalArchitectureRules`), pas seulement documenté.
- Hexagonal *pragmatique* : les entités du domaine portent les annotations JPA, ce qui évite un
  double modèle et le code de conversion associé. Le domaine reste libre de toute dépendance à Spring.
- Les repositories Spring Data implémentent directement les ports applicatifs (une interface qui
  étend à la fois `JpaRepository` et le port) : aucun adaptateur écrit à la main.
- Les préoccupations transverses vivent dans un **starter interne** (auto-configurations) plutôt que
  d'être copiées dans chaque service.

## Conséquences

- Ajouter un service consiste surtout à écrire du métier : sécurité, erreurs, audit, Kafka,
  OpenAPI et configuration sont hérités du starter.
- Le starter devient un composant de plateforme versionné ; une évolution incompatible doit être
  coordonnée entre les services.
- Nuance assumée : les agrégats héritent d'`AuditableEntity` (starter), qui porte des annotations
  Spring Data d'audit. Le domaine n'importe aucune classe Spring, et la règle ArchUnit le vérifie, mais il
  dépend de Spring Data à la compilation par cet héritage. Le prix est accepté, en échange de colonnes
  d'audit et d'un verrou optimiste uniformes ; un domaine entièrement portable demanderait un modèle de
  persistance séparé.
