# atlas-web : l'interface web d'Atlas Bank

Une application **React + TypeScript** (construite avec **Vite**) qui sert deux publics avec le même code :

| Espace | Pour qui | Adresse | Ce qu'on y fait |
|---|---|---|---|
| Banque en ligne | Client (`CUSTOMER`) | `/client` | Voir ses comptes et relevés, faire un virement, suivre ses opérations, lire ses notifications, changer ses coordonnées |
| Back-office | Agent (`EMPLOYEE`) et administrateur (`ADMIN`) | `/agence` | Enrôler un client, valider un KYC, ouvrir et geler des comptes, guichet espèces, approuver les ordres d'un collègue, contrôler les livres (admin) |

Le frontend ne contient **aucune règle de sécurité** : il adapte l'affichage au rôle, mais chaque
service revérifie le jeton et les droits. Cacher un bouton ne protège rien.

---

## Démarrer

Prérequis : la pile Atlas Bank démarrée (`docker compose up -d` à la racine du dépôt) et Node.js 22.

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Le port **5173** n'est pas au hasard : c'est l'origine déjà autorisée par le client Keycloak
`atlas-web` (`deploy/keycloak/atlas-bank-realm.json`) et par le CORS de la gateway. Rien n'est à
modifier côté backend.

| Commande | Effet |
|---|---|
| `npm run dev` | Serveur de développement, rechargement instantané |
| `npm run typecheck` | Vérifie les types TypeScript |
| `npm test` | Tests unitaires (Vitest) |
| `npm run build` | Vérifie les types puis produit le site statique dans `dist/` |

Configuration (facultative, fichier `.env.local`, voir `.env.example`) : adresse de la gateway, du
realm Keycloak et identifiant du client. **Aucun secret** : tout ce qui commence par `VITE_` finit dans
le JavaScript envoyé au navigateur.

### Se connecter

| Compte | Rôle | Remarque |
|---|---|---|
| `agent.amina`, `agent.youssef` | Agent | Mot de passe dans `../.env` (`ATLAS_DEMO_EMPLOYEE_PASSWORD`) |
| `admin.atlas` | Administrateur | `ATLAS_DEMO_ADMIN_PASSWORD` |
| Un client | Client | Créé automatiquement quand un agent valide son KYC ; l'e-mail pour choisir le mot de passe arrive dans Mailpit (http://localhost:8025) |

À sa première connexion dans le navigateur, le personnel doit configurer un **second facteur** : scanner
le QR code avec une application d'authentification (Google Authenticator, FreeOTP, Microsoft
Authenticator…), puis saisir le code à 6 chiffres à chaque connexion.

### Scénario de démonstration (4 yeux)

1. Connecté en **agent.amina** : *Clients → Enrôler un client*.
2. Sur la fiche, *Valider le KYC* : le serveur refuse (**403 SELF_APPROVAL_FORBIDDEN**).
3. Déconnexion, connexion en **agent.youssef** : il valide, le client devient actif.
4. Dans Mailpit, le client reçoit l'e-mail d'activation ; il choisit son mot de passe et se connecte.
5. Youssef ouvre un compte courant ; Amina fait un versement de 1 000 MAD au guichet.
6. Le client fait un virement : l'écran montre le **202 Accepted**, puis le statut passe à *Exécuté*.
7. Amina saisit un versement de 20 000 MAD : il attend l'approbation ; Youssef l'approuve.

---

## Comment c'est construit

```
src/
├── main.tsx            point d'entrée : routeur, authentification, cache des données
├── App.tsx             la carte des pages (/client et /agence, gardées par rôle)
├── config.ts           adresses de la gateway et de Keycloak
├── auth/
│   ├── oidc.ts         connexion OpenID Connect (Authorization Code + PKCE)
│   ├── session.ts      lecture du jeton : rôles, customer_id, sub
│   ├── useSession.ts   la session dans les composants
│   └── RequireRole.tsx garde de route par rôle
├── api/
│   ├── client.ts       le seul endroit qui parle HTTP (jeton, erreurs)
│   ├── problem.ts      erreurs RFC 9457 et leur traduction en français
│   ├── types.ts        les formes JSON des services (copiées des records « …View » Java)
│   └── endpoints.ts    un appel par endpoint de l'API
├── hooks/
│   ├── useOrderSubmission.ts   envoi d'un ordre avec clé d'idempotence
│   └── useOrder.ts             suivi d'un ordre (polling jusqu'au statut final)
├── components/         briques d'interface réutilisables
├── pages/              une page par écran (customer/, staff/, shared/)
└── styles.css          le style, construit sur des variables
```

Les correspondances avec le backend :

| Frontend | Backend |
|---|---|
| `api/endpoints.ts` → `customersApi` | `customer-service` · `CustomerController` |
| `accountsApi` | `account-service` · `AccountController` |
| `transactionsApi` | `transaction-service` · `TransactionController` |
| `notificationsApi` | `notification-service` · `NotificationController` |
| `api/types.ts` | les records `application/view/*View.java` |
| `api/problem.ts` | `ProblemDetailsExceptionHandler` et les enums `*ErrorCode` |
| `auth/session.ts` (hiérarchie ADMIN ⊃ EMPLOYEE) | `AtlasSecurityAutoConfiguration.roleHierarchy` |

---

## Les notions importantes, expliquées

### 1. La connexion : OpenID Connect, Authorization Code + PKCE (`auth/oidc.ts`)

1. Le bouton *Se connecter* redirige le navigateur vers **Keycloak**. L'application ne voit jamais le
   mot de passe.
2. Keycloak vérifie le mot de passe (et l'OTP du personnel), puis revient sur `http://localhost:5173/`
   avec un **code** à usage unique.
3. La bibliothèque `oidc-client-ts` échange ce code contre les **jetons**. Avec **PKCE**, elle prouve
   que c'est bien elle qui a commencé la connexion : un code intercepté ne sert à rien.

Le client `atlas-web` est **public** : une application dans un navigateur ne peut garder aucun secret
(n'importe qui peut lire son JavaScript). PKCE remplace le secret.

Le **jeton d'accès** vit 5 minutes ; `automaticSilentRenew` le renouvelle avec le refresh token avant
qu'il expire.

### 2. Les rôles dans l'interface (`auth/session.ts`)

Le jeton est un JWT : trois parties en Base64 séparées par des points. La partie du milieu contient les
**claims** : `sub`, `preferred_username`, `roles`, `customer_id`. Le frontend la décode pour choisir le
menu et l'espace (`/client` ou `/agence`).

Il **ne vérifie pas la signature** et n'en a pas besoin : ce sont les services qui la vérifient. Le
frontend n'est qu'une commodité ; un attaquant peut appeler l'API sans lui.

### 3. Les appels à l'API (`api/client.ts`)

Chaque appel passe par la **gateway** (`http://localhost:8080`), porte `Authorization: Bearer <jeton>`,
et en cas d'échec lit le corps **RFC 9457** pour produire une `ApiError` avec un `code` stable
(`INSUFFICIENT_FUNDS`, `SELF_APPROVAL_FORBIDDEN`…) et le `traceId` affiché à l'utilisateur pour le
support. Les erreurs de validation (`VALIDATION_FAILED`) sont rangées **par champ** et s'affichent sous
le bon champ du formulaire.

### 4. Le cache des données : React Query

`useQuery` lit une donnée (avec les états chargement / erreur / données), la garde en cache, et la
recharge quand on la déclare périmée (`invalidateQueries`). Exemple : après un virement exécuté, on
invalide `accounts` et `entries`, et les soldes affichés se mettent à jour tout seuls.

### 5. Un virement « comme en vrai » : 202 + idempotence + suivi

- **`hooks/useOrderSubmission.ts`** : une **clé d'idempotence** (UUID) est créée pour la demande et
  envoyée dans l'en-tête `Idempotency-Key`. Si le réseau coupe, *Réessayer* renvoie la **même clé** :
  le serveur reconnaît la demande et renvoie l'ordre d'origine (`Idempotent-Replayed: true`) au lieu
  d'en créer un second. Après un succès, une nouvelle clé est préparée. La clé est affichée à l'écran
  du virement pour qu'on la voie.
- La prise d'ordre répond **202 Accepted** : « reçu, pas encore exécuté ».
- **`hooks/useOrder.ts`** interroge l'ordre toutes les secondes tant qu'il est `PENDING` (en route vers
  le grand livre par Kafka), toutes les 10 secondes s'il attend une approbation, et s'arrête au statut
  final (`COMPLETED` ou `REJECTED`).

### 6. Le contrôle 4 yeux à l'écran

Sur la fiche d'un client en attente de KYC, l'agent qui l'a enrôlé voit un avertissement. Le bouton
reste **volontairement actif** pour voir le serveur refuser (403). Pour les ordres, le bouton *Approuver*
est désactivé pour celui qui a saisi l'ordre. Dans les deux cas, c'est une **aide** : la vraie règle est
dans le backend, qui compare le `sub` (immuable), alors que l'écran ne compare que le login.

### 7. Ce que le client ne voit pas

Les motifs de gel ou de suspension sont des notes internes (elles peuvent concerner une enquête). Le
serveur ne les envoie pas au client ; l'interface ne les affiche qu'au personnel.

---

## Choix et limites

| Choix | Pourquoi | Alternative en production |
|---|---|---|
| React + Vite (application monopage) | Le backend expose déjà une API REST et un client OIDC public prêt pour le port 5173 | Next.js si l'on veut du rendu serveur |
| Jetons dans le `sessionStorage` | Simple, la session disparaît à la fermeture de l'onglet | Un **BFF** (*Backend For Frontend*) : un petit serveur garde les jetons et le navigateur n'a qu'un cookie `HttpOnly`, à l'abri d'une faille XSS. C'est la recommandation actuelle pour les applications bancaires |
| CSS maison avec variables | Aucune dépendance, facile à lire | Une bibliothèque de composants (MUI, shadcn/ui…) |
| Formulaires en `useState` | Peu de champs, lecture facile | `react-hook-form` + `zod` pour de gros formulaires |
| Polling pour suivre un ordre | Aucun changement côté backend | Server-Sent Events ou WebSocket poussés par le serveur |
