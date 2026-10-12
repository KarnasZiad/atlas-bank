# ADR-0003 — Le grand livre, source unique des soldes

- Statut : acceptée
- Date : 2026-10-12

## Contexte

Un virement touche deux comptes. Répartir le solde entre plusieurs services imposerait une
transaction distribuée (2PC, inadaptée) ou une saga avec compensations, sujette à des états
intermédiaires visibles (argent débité mais pas encore crédité).

## Décision

- Comptes et écritures appartiennent au même service (account-service) : débit et crédit d'un virement
  interne sont écrits dans **une seule transaction ACID locale**.
- Le solde n'est modifié que par `Account.debit` / `Account.credit`, qui produisent chacun une écriture
  portant le solde résultant.
- Les comptes sont verrouillés en écriture (`PESSIMISTIC_WRITE`, soit `SELECT … FOR UPDATE`) dans
  l'**ordre croissant des numéros** : deux virements croisés ne peuvent pas s'interbloquer.
- Le plafond journalier est calculé sous ce verrou, il n'y a donc pas de dépassement par concurrence.
- Un **reçu d'imputation** par ordre, vérifié sous verrou, rend la consommation idempotente ; un
  ordre rejeté ne peut jamais réussir plus tard.
- Un rejet métier annule toute la tentative et est enregistré dans une transaction séparée.
- Garde-fous en base : contrainte `balance >= -overdraft_limit`, écritures immuables (trigger),
  unicité `(transaction_id, account_id)`.

## Conséquences

- Aucune incohérence possible des soldes, vérifiée par un test de virements concurrents croisés.
- Le débit d'un compte est sérialisé : le partitionnement Kafka par compte débité aligne le
  parallélisme sur ce verrou.
- Les virements vers d'autres banques nécessiteront un compte technique de compensation et un
  processus asynchrone dédié.
