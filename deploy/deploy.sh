#!/usr/bin/env bash
# Deploys Virtual Clubroom on the server (09-deployment §11.2; E22-S02). Run it in the demo's OWN clone, never in a
# working copy you develop in: it switches that clone to the version it deploys.
#
#   deploy/deploy.sh v1.2.0           deploy a release: the images CI built for that tag (.github/workflows/release.yml)
#   deploy/deploy.sh v2.0.0 --major   a new major version: refused without --major, since a major release goes to
#                                     production only after its review (CONTRIBUTING: release-prod-v<MAJOR> branches)
#   deploy/deploy.sh rollback         go back to the previous version (run it again to return)
#   deploy/deploy.sh status           what's live, what came before, and the last deploys
#   deploy/deploy.sh [main]           build and deploy the latest main on this server (the way before releases)
#
# Every deploy backs up the data first (scripts/backup.sh), then waits for the backends to report healthy. If they
# don't, it puts the previous version back by itself. A database migration isn't undone by going back: releases only
# add to the schema, and drop things in a later release (expand, then contract), so the previous version still runs.
#
# Settings (environment): DEPLOY_PROJECT (vclubroom-demo), DEPLOY_STATE_DIR (~/vclubroom-deploys: live version,
# previous version, history), IMAGE_REPO (ghcr.io/42-unix-club/vclubroom), HEALTH_TIMEOUT seconds (300),
# SKIP_BACKUP=1 to skip the backup. Option B (dedicated connector): COMPOSE_PROFILES=tunnel in .env.
set -euo pipefail

PROJECT=${DEPLOY_PROJECT:-vclubroom-demo}    # own project name: compose.yaml's "vclubroom" is the dev stack
STATE=${DEPLOY_STATE_DIR:-$HOME/vclubroom-deploys}
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-300}
VERSION_RE='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'

say() { printf '%s\n' "$*"; }
die() { printf '✖ %s\n' "$*" >&2; exit 1; }
compose() { docker compose -p "$PROJECT" -f compose.yaml -f compose.prod.yaml "$@"; }
live() { cat "$STATE/live" 2>/dev/null || true; }
previous() { cat "$STATE/previous" 2>/dev/null || true; }
major() { [[ $1 =~ $VERSION_RE ]] && printf '%s' "${BASH_REMATCH[1]}"; }
log() { printf '%s  %-10s %-16s → %-16s %s\n' "$(date -Is)" "$(id -un)" "${1:-none}" "$2" "$3" >> "$STATE/history.log"; }

usage() {
  sed -n '5,10p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

status() {
  say "Live:     $(live || true)"
  say "Previous: $(previous || true)"
  say "Running images:"
  compose ps --format '  {{.Service}}  {{.Image}}  {{.Status}}' backend proxy 2>/dev/null || say "  (stack not running)"
  if [ -s "$STATE/history.log" ]; then say "Last deploys:"; tail -n 10 "$STATE/history.log" | sed 's/^/  /'; fi
}

# The backends report healthy (readiness), and the proxy answers through its published port.
wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) ids id health ok addr
  say "Waiting for the backends to report healthy (up to ${HEALTH_TIMEOUT}s)…"
  while :; do
    ids=$(compose ps -q -a backend); ok=1      # -a: a replica that crashed and exited must count too
    [ -n "$ids" ] || ok=0
    for id in $ids; do
      health=$(docker inspect -f '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id")
      health=${health#running/}                 # anything not running stays "exited/…" and never counts as healthy
      [ "$health" = unhealthy ] && { say "A backend is unhealthy."; return 1; }
      [ "$health" = healthy ] || ok=0
    done
    [ "$ok" = 1 ] && break
    [ "$SECONDS" -lt "$deadline" ] || { say "Timed out."; return 1; }
    sleep 5
  done
  addr=$(compose port proxy 80 2>/dev/null || true)
  if [ -n "$addr" ] && command -v curl >/dev/null; then
    curl -fsS -o /dev/null --max-time 10 "http://$addr/" || { say "The proxy doesn't answer on $addr."; return 1; }
  fi
}

# Puts TARGET (a vX.Y.Z tag, or main@<commit> for a build on this server) in place and starts it.
# It runs as an `if` condition, where set -e doesn't apply: every step returns on failure explicitly.
switch_to() {
  local target=$1
  case $target in
    main)   git switch -q main && git pull -q --ff-only origin main || return 1 ;;
    main@*) git switch -q --detach "${target#main@}" || return 1 ;;
    *)      git switch -q --detach "refs/tags/$target" || return 1 ;;
  esac
  deploy/seaweedfs/render-s3-config.sh || return 1
  if [[ $target =~ $VERSION_RE ]]; then
    grep -q 'APP_VERSION' compose.prod.yaml || { say "$target predates release images (E22-S02): it can't be deployed this way."; return 1; }
    export APP_VERSION=$target
    say "Pulling the $target images…"
    compose pull -q backend proxy || { say "Couldn't pull the $target images: did the release workflow finish? (If the packages are private: docker login ghcr.io)"; return 1; }
    compose up -d --no-build --remove-orphans || return 1
  else
    unset APP_VERSION                          # → the :local tag, built from this checkout
    compose up -d --build --remove-orphans || return 1
  fi
  compose restart proxy || return 1            # nginx re-resolves the backend replicas
}

deploy() {
  local target=$1 is_revert=${2:-} from head
  from=$(live); head=$(git rev-parse HEAD)
  if [ -z "$is_revert" ] && [ "${SKIP_BACKUP:-}" != 1 ]; then
    if [ -n "$(compose ps -q postgres 2>/dev/null)" ]; then
      BACKUP_PROJECT=$PROJECT scripts/backup.sh || die "The backup failed: nothing was changed."
    else
      say "The stack isn't running: no data to back up yet."
    fi
  fi
  say "Deploying $target (live: ${from:-none})…"
  if switch_to "$target" && wait_healthy; then
    [ "$target" = main ] && target="main@$(git rev-parse --short HEAD)"
    if [ "$from" != "$target" ]; then printf '%s\n' "$from" > "$STATE/previous"; fi
    printf '%s\n' "$target" > "$STATE/live"
    log "$from" "$target" "${is_revert:+reverted }ok"
    compose ps backend proxy
    say "✓ $target is live."
    return 0
  fi
  log "$from" "$target" "failed"
  [ -z "$is_revert" ] || return 1
  if [ -n "$from" ]; then
    say "✖ $target didn't come up healthy: putting $from back."
    deploy "$from" revert && die "$target failed; $from is live again."
    die "$target failed, and so did putting $from back: check \`compose logs backend\` now."
  fi
  git switch -q --detach "$head" 2>/dev/null || true
  die "$target didn't come up healthy (no earlier version to put back): check the backend's logs."
}

main() {
  cd "$(dirname "$0")/.."                      # → the repository root
  local cmd=${1:-main} target
  case $cmd in -h|--help) usage ;; status) status; return ;; esac
  [ -f .env ] || die "No .env here: copy .env.example and fill it in (E16-S02 story guide)."
  if ! git diff --quiet || ! git diff --cached --quiet; then
    die "This clone has uncommitted changes: refusing to deploy (is this really the demo clone?)."
  fi
  mkdir -p "$STATE"
  exec 9>"$STATE/lock"
  flock -n 9 || die "Another deploy is running."
  git fetch -q --tags --force origin
  case $cmd in
    rollback)
      target=$(previous)
      [ -n "$target" ] || die "No previous version recorded yet."
      ;;
    main) target=main ;;
    *)
      [[ $cmd =~ $VERSION_RE ]] || usage
      git rev-parse -q --verify "refs/tags/$cmd^{commit}" >/dev/null || die "No tag $cmd in the repository."
      target=$cmd
      local from_major; from_major=$(major "$(live)" || true)
      if [ -n "$from_major" ] && [ "$(major "$target")" != "$from_major" ] && [ "${2:-}" != --major ]; then
        die "$target is a different major version than the live $(live): deploy it with --major once it's been reviewed."
      fi
      ;;
  esac
  deploy "$target"
}

# Called on the last line: bash has read the whole file before main switches the checkout under it.
main "$@"; exit $?
