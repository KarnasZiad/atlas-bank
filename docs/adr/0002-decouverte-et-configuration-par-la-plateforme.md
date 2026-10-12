# ADR-0002 — Découverte et configuration assurées par la plateforme (ni Eureka ni Config Server)

- Statut : acceptée
- Date : 2026-10-12

## Contexte

Les architectures Spring Cloud « classiques » ajoutent un serveur Eureka et un Config Server. En 2026,
les plateformes cibles (Kubernetes, ou Docker Compose en local) fournissent déjà la découverte de
services (DNS) et la configuration (variables d'environnement, ConfigMaps, Secrets). L'environnement de
développement disponible ne dispose par ailleurs que de 3,9 Go de mémoire pour Docker.

## Décision

- Les services s'adressent par leur **nom DNS** (`http://account-service:8082`), résolu par la plateforme.
- La configuration suit le modèle **12-factor** : tout ce qui dépend de l'environnement est une
  variable d'environnement ; les secrets n'ont aucune valeur par défaut.
- Les conventions communes sont fournies par le starter (`atlas/platform-defaults.yml`, chargé par un
  `EnvironmentPostProcessor` avec la priorité la plus faible) : le partage de configuration du Config
  Server, sans serveur à exploiter.

## Conséquences

- Deux JVM et deux points de défaillance en moins.
- Les valeurs par défaut partagées sont versionnées avec le starter ; les surcharges par
  environnement restent possibles et prioritaires.
- Le rechargement à chaud de la configuration n'est pas prévu : un changement se déploie par
  redémarrage progressif, comme sur Kubernetes.
