#!/usr/bin/env bash
# Redeploys the demo from the latest main (09-deployment §11.2, guide 06).
# Run it in the demo's OWN clone, never in a working copy you develop in: it switches that clone to main.
# Option B (dedicated connector): COMPOSE_PROFILES=tunnel in .env, or COMPOSE_PROFILES=tunnel ./deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."                       # → the repository root
[ -f .env ] || { echo "No .env here: copy .env.example and fill it in (E16-S02 story guide)." >&2; exit 1; }
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "This clone has uncommitted changes: refusing to deploy (is this really the demo clone?)." >&2; exit 1
fi
git fetch origin
git switch main
git pull --ff-only origin main
deploy/seaweedfs/render-s3-config.sh
# The changelog page's data (E19-S03): releases and what's unreleased, from the history. Not worth stopping a deploy for.
docker run --rm --user "$(id -u):$(id -g)" -v "$PWD":/app -w /app orhunp/git-cliff:2.14.2 --context \
  -o deploy/release-notes/changelog.json 2>/dev/null || echo "Changelog not updated (git-cliff failed): the page keeps the last one."
# Own project name: compose.yaml says "vclubroom", which is also the dev stack on the same machine.
compose() { docker compose -p vclubroom-demo -f compose.yaml -f compose.prod.yaml "$@"; }
compose up -d --build --remove-orphans
compose restart proxy                         # nginx re-resolves the backend replicas
compose ps
