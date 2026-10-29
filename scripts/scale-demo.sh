#!/usr/bin/env bash
# Scaling demo (E16-S04, 09-deployment §11.3): several backend instances behind nginx, one chat room.
# Run from the repository root with the production stack up:   scripts/scale-demo.sh [instances, default 3]
# Then open one chat channel in two browsers as two people: their messages appear instantly in both, while the
# log below shows their live connections held by different instances (Redis relays between them, no sticky sessions).
set -euo pipefail
cd "$(dirname "$0")/.."
compose() { docker compose -f compose.yaml -f compose.prod.yaml "$@"; }
instances=${1:-3}

echo "Scaling the backend to $instances instances..."
compose up -d --no-recreate --scale backend="$instances" backend
echo "Waiting for them to be healthy..."
until [ "$(compose ps backend --format '{{.Health}}' | grep -c '^healthy$')" -ge "$instances" ]; do sleep 2; done
echo "Restarting the proxy: nginx resolves the instances when it starts..."
compose restart proxy
compose ps backend

echo
echo "Open the same chat channel in two browsers, logged in as two people. Live connections (Ctrl+C to stop):"
# Production logs are JSON (ECS): keep the replica's name and the message.
compose logs -f --since 1s backend 2>/dev/null | grep --line-buffered "Live connection" \
  | sed -u -E 's/^([^|]+)\|.*"message":"([^"]*)".*/\1| \2/' || true
