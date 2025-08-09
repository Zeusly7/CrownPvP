# CrownPvP Monorepo (SQLite Starter v2)

- Paper API: 1.21.4
- DB: SQLite par défaut (plugins/CrownCore/data.db). Bascule possible vers MySQL (PlanetScale) plus tard.

## Build
mvn -U -q clean package

## Déploiement
Le workflow Github Actions déploie crowncore dans /home/container/plugins via SFTP.
