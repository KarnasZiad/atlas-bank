# ADR-0004 — Outbox transactionnelle, état porté par les événements, JSON sans type

- Statut : acceptée
- Date : 2026-10-12

## Contexte

Publier sur Kafka après un commit peut perdre l'événement (crash entre les deux) ; publier avant peut
annoncer un fait qui sera finalement annulé. Les services ont aussi besoin de données d'autres
domaines (statut d'un client) sans appel synchrone.

## Décision

- **Transactional outbox** fournie par Spring Modulith : l'événement est enregistré dans la table
  `event_publication` dans la même transaction que le changement métier, puis externalisé vers Kafka
  après le commit. Les publications non livrées sont resoumises périodiquement
  (`EventPublicationResubmitter`) et au redémarrage.
- **Event-carried state transfer** pour les clients : un snapshot complet et versionné sur un topic
  compacté. Chaque consommateur tient une projection locale et ignore les snapshots plus anciens que le
  sien. Cela tolère le désordre et le rejeu.
- **Minimisation des données personnelles** : le snapshot d'un client clôturé ne porte plus ni nom, ni
  e-mail, ni téléphone. Dernier enregistrement de sa clé, il remplace les précédents, que la
  compaction efface au plus tard après `max.compaction.lag.ms` (7 jours par défaut,
  `atlas.kafka.max-compaction-lag`). notification-service efface alors les coordonnées de sa projection
  et cesse d'écrire au client. La base de customer-service reste le registre conservé pour les
  obligations légales.
- Les changements de coordonnées sont publiés à part (`atlas.customer.contact-changed.v1`), sans les
  nouvelles valeurs : notification-service prévient le client à son adresse e-mail, qui n'a pas changé.
- **Consommateurs idempotents** partout : reçu d'imputation (grand livre), clé unique
  (événement, client, type) pour les notifications, transitions d'état idempotentes pour les ordres.
- Sérialisation : JSON produit et lu par un unique convertisseur, **sans en-tête de type** ; le
  consommateur lie le message au type de son paramètre. Aucun nom de classe ne transite.
- Erreurs : retries à back-off exponentiel, puis **dead-letter topic** contenant le message d'origine.

## Conséquences

- Livraison *au moins une fois*, effets *exactement une fois*.
- Cohérence à terme entre services (quelques millisecondes en fonctionnement normal) ; un ordre est
  `PENDING` jusqu'à son imputation, ce que l'API expose explicitement (202 + suivi).
