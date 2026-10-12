# ADR-0006 — Chiffrement du numéro d'identité et index aveugle

- Statut : acceptée
- Date : 2026-10-12

## Contexte

Le numéro de CIN est une donnée sensible. Il doit être stocké chiffré, mais la banque doit pouvoir
détecter qu'un même CIN est déjà enregistré.

## Décision

- Chiffrement **AES-256-GCM** (chiffrement authentifié, IV aléatoire) via Spring Security Crypto
  (`Encryptors.delux`), appliqué par un `AttributeConverter` JPA auto-appliqué au type `NationalId` :
  le domaine ignore la cryptographie.
- Détection des doublons par un **index aveugle** HMAC-SHA256 avec une clé secrète distincte : sans
  la clé, l'empreinte ne permet pas de retrouver un CIN par force brute.
- Le CIN est toujours restitué masqué (`******56`), y compris dans `toString()` (journaux), et n'est
  publié sur aucun topic.
- Clés fournies par l'environnement, sans valeur par défaut ; le service refuse de démarrer sans elles.

## Conséquences

- La recherche par CIN en clair est impossible (seule l'égalité est possible, via l'empreinte).
- La rotation de clé impose une migration de rechiffrement ; en production, les clés doivent venir
  d'un KMS ou d'un coffre (Vault).
