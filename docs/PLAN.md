# Atlas Bank — Plan de réalisation

Ce document fixe la stratégie de travail : objectifs, choix structurants, découpage en
lots et critères de « terminé ». Les décisions détaillées sont tracées dans `docs/adr/`.

## 1. Objectif

Un système bancaire (core banking simplifié) en microservices, exploitable comme en
entreprise : sécurité de haut niveau, cohérence des fonds garantie, architecture propre,
faible couplage, configuration externalisée, observabilité et tests automatisés.

## 2. Périmètre fonctionnel

| Domaine | Fonctionnalités |
|---|---|
| Clients (KYC) | Enrôlement par un agent, validation KYC en *4 yeux* (le validateur ≠ le créateur), suspension / réactivation / clôture, historique d'audit, création automatique du compte de connexion (Keycloak) à l'activation |
| Comptes | Ouverture (compte courant / épargne) avec IBAN, gel / dégel / clôture, consultation, relevé paginé (grand livre) |
| Opérations | Virement (client ou agent), versement et retrait espèces (agent), clé d'idempotence obligatoire, plafond par opération, plafond journalier de débit par compte |
| Notifications | E-mails transactionnels (compte ouvert, opération exécutée / rejetée, changement de statut), i18n fr/en, consultation paginée |

## 3. Choix structurants

| Sujet | Choix | Pourquoi |
|---|---|---|
| Stack | Java 21, Spring Boot 4.0.8, Spring Cloud 2025.1.3 | Versions supportées et alignées entre elles |
| Identité | Keycloak 26 (OIDC/OAuth2, JWT) | IdP standard du marché : MFA, politique de mot de passe, anti-bruteforce sans code maison |
| Entrée | Spring Cloud Gateway | Point d'entrée unique : validation JWT, rate limiting Redis, circuit breaker, en-têtes de sécurité |
| Données | PostgreSQL, une base et un utilisateur par service, Flyway, `ddl-auto=validate` | Isolation des données, migrations versionnées |
| Messagerie | Kafka + *transactional outbox* (Spring Modulith) | Aucun événement perdu ni publié pour une transaction annulée |
| Cohérence des fonds | Grand livre (ledger) dans account-service, transaction ACID locale, verrous pessimistes ordonnés, consommateur idempotent | Le solde n'existe qu'à un seul endroit ; pas de transaction distribuée |
| Architecture interne | Hexagonale pragmatique (domain / application / infrastructure / web) vérifiée par ArchUnit | Règles de dépendance testées, pas seulement documentées |
| Découverte / config | DNS de la plateforme (Docker, Kubernetes) + starter interne de configuration partagée | Moins de composants à opérer (pas d'Eureka ni de Config Server), voir ADR-0002 |
| Observabilité | Actuator, Micrometer, OpenTelemetry, logs structurés corrélés par traceId | Diagnostic de bout en bout, y compris à travers Kafka |

## 4. Découpage en lots

1. **Socle** : POM parent, BOMs, modules `platform/*` (commons, contrats d'événements, starter, support de test).
2. **customer-service** : domaine client, chiffrement des données personnelles, provisionnement Keycloak, audit Envers.
3. **account-service** : comptes, IBAN, grand livre, imputation idempotente des opérations, projection clients.
4. **transaction-service** : orchestration des opérations, idempotence HTTP, plafonds, suivi d'état.
5. **notification-service** : projections destinataires, e-mails i18n, consultation.
6. **api-gateway** : routage, sécurité en bordure, rate limiting, agrégation OpenAPI.
7. **Déploiement** : Dockerfile multi-étapes, docker-compose (infra + services), realm Keycloak, init Postgres.
8. **Qualité** : tests unitaires, tests de slices web sécurisés, tests d'intégration Testcontainers, ArchUnit.
9. **Documentation** : README, architecture, ADR, guide d'exploitation, scénario de démonstration.

## 5. État d'avancement (13/10/2026)

| Lot | État | Vérification |
|---|---|---|
| 1. Socle | Terminé | Build Maven complet ; tests de `Money`, de la signature des ordres et du traitement des erreurs |
| 2. customer-service | Terminé | 21 tests unitaires et web + 3 tests d'intégration (PostgreSQL et Kafka réels) |
| 3. account-service | Terminé | 19 tests unitaires et web + 17 tests d'intégration, dont 40 virements croisés concurrents, ordre forgé, caisse, réconciliation |
| 4. transaction-service | Terminé | 11 tests unitaires + 13 tests d'intégration, dont 8 requêtes simultanées avec la même clé et le flux 4 yeux |
| 5. notification-service | Terminé | 8 tests unitaires + 4 tests d'intégration |
| 6. api-gateway | Terminé | 7 tests d'intégration (Redis réel) |
| 7. Déploiement | Terminé | Pile complète démarrée (`docker compose`), `scripts/smoke-test.ps1` passe de bout en bout ; ACL Kafka, rôles PostgreSQL et permissions Keycloak vérifiés sur instances réelles |
| 8. Qualité | Terminé | ArchUnit dans chaque service, CI GitHub Actions, profil d'audit OWASP |
| 9. Documentation | Terminé | README, architecture, ADR 0001 à 0008, scénario `scripts/smoke-test.ps1` |
| 10. Durcissement (revue de sécurité) | Terminé | Ordres signés, Kafka SASL + ACL, 4 yeux du personnel, partie double et réconciliation, moindre privilège PostgreSQL et Keycloak, MFA du personnel, motifs internes masqués, 409 au lieu de 500 sur doublon |

## 6. Définition de « terminé » pour chaque lot

- `mvn verify` passe (tests unitaires et d'intégration).
- Aucun secret dans le code ni dans la configuration versionnée : uniquement des variables d'environnement.
- Endpoints documentés (OpenAPI) et protégés (rôle et propriété de la ressource).
- Erreurs au format RFC 9457 (`application/problem+json`) avec un code métier stable.
- Pagination sur toutes les listes, avec une taille maximale imposée par la configuration.
