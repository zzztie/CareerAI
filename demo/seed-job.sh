#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sudo docker compose -f compose.yaml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" --default-character-set=utf8mb4 "$MYSQL_DATABASE"' < demo/seed-demo-job.sql