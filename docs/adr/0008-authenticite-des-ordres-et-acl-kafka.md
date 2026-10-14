# ADR-0008 — Ordres signés et moindre privilège sur Kafka

- Statut : acceptée
- Date : 2026-10-13

## Contexte

Le grand livre décide de ce qui est permis selon l'**initiateur** d'un ordre : un client ne débite que
ses comptes, alors qu'un agent agit sur tous les comptes. Cet initiateur voyageait en clair dans un
message Kafka, et Kafka acceptait n'importe quel client, sans authentification ni autorisation.

N'importe quel conteneur du réseau pouvait donc publier un ordre « agent » : créer de l'argent par un
versement, ou vider un compte par un virement. La revalidation du grand livre protégeait contre une
erreur de transaction-service, pas contre un faux message.

## Décision

Deux protections indépendantes, chacune suffisant à bloquer un faux ordre :

1. **Ordres signés (application).**
   - transaction-service signe chaque `TransactionRequested` en Ed25519 (fournisseur du JDK). La
     signature couvre tout ce qui décide de l'imputation : identifiant, nature, comptes, montant,
     devise, libellé, initiateur et approbation.
   - L'encodage est sans ambiguïté (chaque valeur est préfixée par sa longueur) et versionné
     (`atlas-order-v1`).
   - Le schéma vit dans le module de contrats (`OrderSignatures`), partagé par le signataire et le
     vérificateur.
   - Seul transaction-service détient la clé privée ; le grand livre fait confiance à une ou plusieurs
     clés publiques (`key-id` + `previous-keys` pendant une rotation).
   - Un ordre non signé, signé par une clé inconnue ou altéré n'est **jamais** imputé. Il lève
     `UntrustedMessageException` :
     - aucun reçu n'est écrit, pour qu'un faux ne puisse pas « consommer » l'identifiant d'un ordre
       authentique ;
     - aucun retry n'est tenté, et le message part directement sur le topic `.dlt` ;
     - un journal `SECURITY` signale l'incident.
2. **Kafka authentifié et cloisonné (infrastructure).**
   - SASL sur tous les listeners, contrôleur compris, et autorisation `StandardAuthorizer` avec refus
     par défaut.
   - Un principal par service, des ACL par topic (`deploy/kafka/init-acls.sh`) : seul
     transaction-service écrit les ordres, seul account-service écrit les résultats et les événements de
     compte, seul customer-service écrit les snapshots clients.
   - Chaque service ne lit que ses topics, avec ses groupes de consommateurs, et n'écrit que dans les
     `.dlt` de ce qu'il consomme.

## Conséquences

- Compromettre un autre service ou accéder au réseau ne suffit plus pour faire bouger de l'argent.
- Les autres événements (snapshots, résultats) ne sont protégés que par les ACL. Les signer aussi
  serait la prochaine étape si le réseau n'est pas de confiance.
- En local, SASL/PLAIN circule en clair sur le réseau Docker. En production : SASL_SSL ou mTLS,
  identifiants et clé de signature dans un coffre (Vault, KMS), et rotation de la clé via `previous-keys`.
- Les tests d'intégration s'exécutent sans SASL (Testcontainers) mais avec la signature, grâce à une
  paire de clés réservée aux tests (`TestOrderSigning`).
