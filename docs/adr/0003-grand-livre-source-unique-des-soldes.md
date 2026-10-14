# ADR-0003 — Le grand livre, source unique des soldes

- Statut : acceptée (révisée le 2026-10-13 : caisse en partie double, réconciliation, moindre privilège)
- Date : 2026-10-12

## Contexte

Un virement touche deux comptes. Répartir le solde entre plusieurs services imposerait une
transaction distribuée (2PC, inadaptée) ou une saga avec compensations, sujette à des états
intermédiaires visibles (argent débité mais pas encore crédité).

Les opérations espèces posent un second problème : si un versement ne fait que créditer le compte
du client, l'argent apparaît sans contrepartie. Les livres ne s'équilibrent plus, et un versement
fictif saisi par un agent devient indétectable comptablement.

## Décision

- Comptes et écritures appartiennent au même service (account-service) : débit et crédit d'un virement
  interne sont écrits dans **une seule transaction ACID locale**.
- **Partie double pour toutes les opérations.** Chaque ordre débite un compte et en crédite un autre du
  même montant. Versements et retraits ont pour contrepartie la **caisse de l'agence** : un compte
  interne `TELLER_CASH` par devise (`CASH-<agence>-<devise>`), ouvert à la première opération espèces.
  - Le solde d'un compte client se lit côté crédit (ce que la banque doit), celui de la caisse côté
    débit (les espèces détenues). La somme des soldes clients égale donc les espèces encaissées.
  - La caisse ne peut jamais remettre plus d'espèces qu'elle n'en détient (`INSUFFICIENT_CASH`).
  - Un compte interne n'appartient à aucun client. Son numéro n'est pas un IBAN : il ne peut figurer
    dans aucun ordre de l'API, et le grand livre refuse de toute façon qu'un ordre le nomme. Il ne peut
    être ni gelé ni clôturé par l'API.
- Le solde n'est modifié que par `Account.debit` / `Account.credit`, qui produisent chacun une écriture
  portant le solde résultant.
- Les comptes, caisse comprise, sont verrouillés en écriture (`PESSIMISTIC_WRITE`, soit
  `SELECT … FOR UPDATE`) dans l'**ordre croissant des numéros** : deux virements croisés ne peuvent pas
  s'interbloquer.
- Le plafond journalier est calculé sous ce verrou, il n'y a donc pas de dépassement par concurrence.
- Un **reçu d'imputation** par ordre, vérifié sous verrou, rend la consommation idempotente ; un
  ordre rejeté ne peut jamais réussir plus tard.
- Un rejet métier annule toute la tentative et est enregistré dans une transaction séparée.
- Garde-fous en base :
  - contrainte `balance >= -overdraft_limit` ;
  - écritures et reçus en ajout seul (triggers sur `UPDATE`, `DELETE` **et** `TRUNCATE`) ;
  - unicité `(transaction_id, account_id)`.
- **Moindre privilège** : les migrations tournent avec le propriétaire du schéma, le service avec un
  rôle distinct qui n'a que `SELECT`/`INSERT` sur le journal et les reçus. Un service compromis ne peut
  ni réécrire l'historique, ni désactiver un trigger, ni modifier le schéma.
- **Réconciliation** (`LedgerReconciliation`, toutes les heures et sur demande de l'administrateur) :
  chaque solde doit égaler la somme de ses écritures, chaque transaction doit être équilibrée. Tout
  écart est journalisé en erreur et exposé par la jauge `atlas.ledger.discrepancies`.

## Conséquences

- Aucune incohérence possible des soldes, vérifiée par un test de virements concurrents croisés et
  par la réconciliation.
- Le débit d'un compte est sérialisé : le partitionnement Kafka par compte débité aligne le
  parallélisme sur ce verrou. Les opérations espèces d'une même devise sont sérialisées sur la caisse
  de l'agence, comme au guichet.
- La caisse démarre vide : un retrait n'est possible qu'à hauteur des espèces encaissées. Un
  approvisionnement de caisse (transfert depuis la réserve centrale) reste à ajouter.
- Les virements vers d'autres banques nécessiteront un compte technique de compensation et un
  processus asynchrone dédié, sur le même modèle que la caisse.
