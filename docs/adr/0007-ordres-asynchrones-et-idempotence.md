# ADR-0007 — Ordres de paiement asynchrones et clé d'idempotence

- Statut : acceptée
- Date : 2026-10-12

## Contexte

Un client mobile peut perdre la réponse à une demande de virement (timeout réseau) et la renvoyer. Le
système ne doit jamais débiter deux fois, ni bloquer le client pendant l'imputation.

## Décision

- Tout ordre exige l'en-tête **`Idempotency-Key`** (16 à 64 caractères), unique par initiateur.
- Une empreinte SHA-256 du contenu canonique est stockée :
  - même clé et même contenu : l'ordre d'origine est renvoyé avec `Idempotent-Replayed: true` ;
  - même clé, autre contenu : refus `422 IDEMPOTENCY_KEY_REUSED`, pour signaler une erreur du client
    au lieu de la masquer ;
  - deux requêtes simultanées avec la même clé : la contrainte d'unicité en tranche une, l'autre
    reçoit `409`.
- La prise d'ordre répond **`202 Accepted`** avec un `Location` relatif vers l'ordre ; le client suit
  l'état `PENDING → COMPLETED | REJECTED` (motif fourni).
- Le plafond par ordre est contrôlé à la prise d'ordre ; provision, propriété et plafond journalier
  le sont au grand livre, sous verrou.

## Conséquences

- Retenter est toujours sûr, y compris après un repli 503 de la gateway.
- Les clients doivent gérer l'état asynchrone (interrogation ou notification).
