# ADR-0005 — Keycloak, JWT validé partout, rattachement client par claim

- Statut : acceptée
- Date : 2026-10-12

## Contexte

L'authentification bancaire exige MFA, politique de mot de passe, détection de force brute,
vérification d'e-mail et gestion des sessions. Développer ces fonctions soi-même est risqué.

## Décision

- **Keycloak** (OIDC / OAuth2) est le fournisseur d'identité ; les services sont des *resource servers*.
- Chaque service valide lui-même le jeton (signature JWKS, émetteur, audience `atlas-api`,
  expiration), en plus de la gateway (*zero trust*). Le mapping des rôles est purement déclaratif
  (propriétés Spring Boot `authorities-claim-name`, `authority-prefix`, `principal-claim-name`).
- Rôles `CUSTOMER`, `EMPLOYEE`, `ADMIN`, avec la hiérarchie `ADMIN > EMPLOYEE` déclarée une seule fois.
- L'utilisateur est rattaché à son dossier par le claim `customer_id`, issu d'un attribut que seuls les
  administrateurs (et le compte de service de customer-service) peuvent écrire.
- Les comptes clients sont **provisionnés par customer-service** via l'API d'administration
  (*client credentials*, rôle `manage-users` uniquement) quand le dossier est validé, et désactivés à
  la suspension. La banque ne fixe jamais de mot de passe : Keycloak envoie le lien d'activation.

## Conséquences

- Les contrôles de propriété ne nécessitent aucun appel réseau.
- Le client OAuth2 `atlas-dev-cli` (mot de passe direct) n'existe que pour les tests locaux et doit
  être supprimé des realms de production.
