# ADR-0007 — Ordres de paiement asynchrones, clé d'idempotence et contrôle en 4 yeux

- Statut : acceptée (révisée le 2026-10-13 : prises d'ordre sérialisées par initiateur, 4 yeux du personnel)
- Date : 2026-10-12

## Contexte

Un client mobile peut perdre la réponse à une demande de virement (timeout réseau) et la renvoyer. Le
système ne doit jamais débiter deux fois, ni bloquer le client pendant l'imputation.

Un client ne peut débiter que ses propres comptes. Un agent, lui, agit sur n'importe quel compte :
virement, versement ou retrait. Sans contrôle, un seul agent pouvait vider le compte d'un client ou
créditer un complice par des versements fictifs.

## Décision

- Tout ordre exige l'en-tête **`Idempotency-Key`** (16 à 64 caractères), unique par initiateur.
- Une empreinte SHA-256 du contenu canonique est stockée :
  - même clé et même contenu : l'ordre d'origine est renvoyé avec `Idempotent-Replayed: true` ;
  - même clé, autre contenu : refus `422 IDEMPOTENCY_KEY_REUSED`, pour signaler une erreur du client
    au lieu de la masquer.
- **Les prises d'ordre d'un même initiateur sont sérialisées** (verrou consultatif PostgreSQL le temps
  de la transaction). Deux requêtes simultanées avec la même clé ne s'excluent donc plus sur la
  contrainte d'unicité : la seconde trouve la première et la rejoue. La contrainte reste en dernier
  rempart, et toute violation d'unicité résiduelle répond `409 DUPLICATE_RESOURCE`, jamais 500.
- La prise d'ordre répond **`202 Accepted`** avec un `Location` relatif vers l'ordre ; le client suit
  l'état de l'ordre.
- **4 yeux pour les ordres du personnel** (`atlas.transactions.staff-controls`, par devise) :
  - un agent peut passer seul un ordre jusqu'à un seuil (10 000 MAD), dans la limite d'un cumul
    journalier (50 000 MAD) ;
  - au-delà, l'ordre est accepté en `AWAITING_APPROVAL` et n'atteint le grand livre qu'après
    l'approbation d'un **autre** employé (`POST /{id}/approval`), comparé sur l'identifiant immuable
    `sub` et pas sur le login ;
  - un ordre peut aussi être refusé (`POST /{id}/refusal`, code `REFUSED_BY_REVIEWER`) ;
  - la base de données interdit qu'un ordre soumis à approbation avance sans approbateur distinct
    (contrainte `ck_payment_order_four_eyes`) ;
  - l'approbation voyage dans l'ordre signé (ADR-0008), et le grand livre refuse une approbation
    donnée par l'initiateur lui-même (`FOUR_EYES_VIOLATION`).
- Le plafond par ordre et les limites du personnel sont contrôlés à la prise d'ordre ; provision,
  propriété et plafond journalier du compte le sont au grand livre, sous verrou.

## Conséquences

- Retenter est toujours sûr, y compris après un repli 503 de la gateway ou en parallèle.
- Les clients doivent gérer l'état asynchrone (interrogation ou notification).
- Les ordres en attente d'approbation n'expirent pas encore : une échéance (refus automatique en fin de
  journée) reste à ajouter.
