#!/usr/bin/env bash
# Redeploys the demo from the latest UNIX_CLUB/main (09-deployment §11.2, guide 06).
# Run it in the demo's OWN clone, never in a working copy you develop in: it switches that clone to UNIX_CLUB/main.
# Option B (dedicated connector): COMPOSE_PROFILES=tunnel ./deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."                       # → UNIX_CLUB/
[ -f .env ] || { echo "No .env here: copy .env.example and fill it in (E16-S02 story guide)." >&2; exit 1; }
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "This clone has uncommitted changes: refusing to deploy (is this really the demo clone?)." >&2; exit 1
fi
git fetch origin
git switch UNIX_CLUB/main
git pull --ff-only origin UNIX_CLUB/main
deploy/seaweedfs/render-s3-config.sh
# Own project name: compose.yaml says "vclubroom", which is also the dev stack on the same machine.
compose() { docker compose -p vclubroom-demo -f compose.yaml -f compose.prod.yaml "$@"; }
compose up -d --build --remove-orphans
compose restart proxy                         # nginx re-resolves the backend replicas
compose ps
