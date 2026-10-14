# ADR-0005 — Keycloak, JWT validé partout, rattachement client par claim

- Statut : acceptée (révisée le 2026-10-13 : permissions fines du compte de service, MFA du personnel)
- Date : 2026-10-12

## Contexte

L'authentification bancaire exige MFA, politique de mot de passe, détection de force brute,
vérification d'e-mail et gestion des sessions. Développer ces fonctions soi-même est risqué.

Customer-service crée les comptes de connexion des clients. Avec le rôle `manage-users`, son compte
de service pouvait administrer **tous** les utilisateurs du realm : réinitialiser le mot de passe d'un
administrateur, s'ajouter au groupe `administrators`, s'attribuer le rôle `ADMIN`. Cela a été vérifié
sur un Keycloak réel. Compromettre ce service revenait à prendre le contrôle de la banque.

## Décision

- **Keycloak** (OIDC / OAuth2) est le fournisseur d'identité ; les services sont des *resource servers*.
- Chaque service valide lui-même le jeton (signature JWKS, émetteur, audience `atlas-api`,
  expiration), en plus de la gateway (*zero trust*). Le mapping des rôles est purement déclaratif
  (propriétés Spring Boot `authorities-claim-name`, `authority-prefix`, `principal-claim-name`).
- Rôles `CUSTOMER`, `EMPLOYEE`, `ADMIN`. La hiérarchie `ADMIN > EMPLOYEE` est déclarée dans les
  services, et dans Keycloak (`ADMIN` est un rôle composite qui inclut `EMPLOYEE`), afin que les
  politiques du personnel s'appliquent aussi aux administrateurs.
- L'utilisateur est rattaché à son dossier par le claim `customer_id`, issu d'un attribut que seuls les
  administrateurs (et le compte de service de customer-service) peuvent écrire.
- Les comptes clients sont **provisionnés par customer-service** via l'API d'administration
  (*client credentials*) quand le dossier est validé, et désactivés à la suspension. La banque ne fixe
  jamais de mot de passe : Keycloak envoie le lien d'activation.
- **Permissions fines d'administration (FGAP v2)** : le compte de service n'a plus que `query-users`.
  Une permission du client `admin-permissions` lui accorde `view`, `view-members`, `manage-members` et
  `manage-membership` sur le **seul groupe `/customers`**. Sans `view`, Keycloak 26.8 ne résout le
  chemin `/customers` d'un nouvel utilisateur que lorsque son cache a déjà été rempli par un
  administrateur : la création échouait en 403 après chaque redémarrage (constaté puis corrigé sur la
  pile complète). Concrètement, le compte de service peut :
  - créer un utilisateur, à condition qu'il soit membre de `/customers` ;
  - activer ou désactiver un client et lui envoyer les actions requises ;
  - rechercher les utilisateurs, avec des résultats limités aux clients.
  Tout le reste lui est refusé (vérifié) : personnel, groupes du personnel, rôles, mots de passe.
- **Second facteur obligatoire pour le personnel** : le flux navigateur `atlas browser` exige un code
  à usage unique (OTP) de tout utilisateur ayant le rôle `EMPLOYEE` (donc aussi `ADMIN`), configuré à
  la première connexion. Les clients gardent le second facteur conditionnel standard.
- **Séparation des tâches sur les sanctions** : un agent peut suspendre un client ou geler un compte,
  mais seul un `ADMIN` peut lever la sanction, et jamais celui qui l'a décidée.
- **Les règles des 4 yeux comparent le `sub`** (identifiant immuable émis par Keycloak), jamais le
  login, qu'un administrateur peut renommer : validation KYC, levée de sanction, ordres du personnel.

## Conséquences

- Les contrôles de propriété ne nécessitent aucun appel réseau.
- Un customer-service compromis ne peut agir que sur les comptes de connexion des clients, pas sur le
  personnel ni sur les droits.
- Le client OAuth2 `atlas-dev-cli` (mot de passe direct, sans OTP tant que l'utilisateur n'en a pas
  configuré) n'existe que pour les tests locaux et doit être supprimé des realms de production.
- Le groupe `/customers` a un identifiant fixe dans le fichier du realm, auquel la permission fait
  référence ; il ne doit pas être recréé à la main.
