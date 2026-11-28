#!/usr/bin/env bash
# Deploys Virtual Clubroom on the server (09-deployment §11.2; E22-S02). Run it in the demo's OWN clone, never in a
# working copy you develop in: it switches that clone to the version it deploys.
#
#   deploy/deploy.sh v1.2.0           deploy a release: the images CI built for that tag (.github/workflows/release.yml)
#   deploy/deploy.sh v2.0.0 --major   a new major version: refused without --major, since a major release goes to
#                                     production only after its review (CONTRIBUTING: release-prod-v<MAJOR> branches)
#   deploy/deploy.sh rollback         go back to the previous version (run it again to return)
#   deploy/deploy.sh status [--json]  what's live, what came before, and the last deploys
#   deploy/deploy.sh [main]           build and deploy the latest main on this server (the way before releases)
#   --by <login>                      who asked for the deploy, recorded in the history instead of the Unix user
#
# Every deploy backs up the data first (scripts/backup.sh), then waits for the backends to report healthy. If they
# don't, it puts the previous version back by itself. A database migration isn't undone by going back: releases only
# add to the schema, and drop things in a later release (expand, then contract), so the previous version still runs.
#
# Exit codes: 0 live and healthy · 1 failed, but nothing changed or the previous version is live again · 2 rejected
# (bad arguments, unknown version, --major missing, not a deployable clone), nothing changed · 3 another deploy is
# running · 4 failed AND the previous version couldn't be put back: someone must look now.
# Progress, docker's included, goes to stdout, one line at a time and without colours when it isn't a terminal;
# the final error line goes to stderr.
#
# Settings (environment): DEPLOY_PROJECT (vclubroom-demo), DEPLOY_STATE_DIR (~/vclubroom-deploys: live version,
# previous version, history), IMAGE_REPO (ghcr.io/42-unix-club/vclubroom; set it in .env rather than here, so
# scripts/scale-demo.sh and by-hand compose commands use it too), HEALTH_TIMEOUT seconds (300),
# SKIP_BACKUP=1 to skip the backup. Option B (dedicated connector): COMPOSE_PROFILES=tunnel in .env.
set -euo pipefail

PROJECT=${DEPLOY_PROJECT:-vclubroom-demo}    # own project name: compose.yaml's "vclubroom" is the dev stack
STATE=${DEPLOY_STATE_DIR:-$HOME/vclubroom-deploys}
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-300}
VERSION_RE='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
LOGIN_RE='^[a-z0-9][a-z0-9-]{0,38}$'
COMPOSE_OUTPUT=()
[ -t 1 ] || COMPOSE_OUTPUT=(--ansi never --progress plain)   # plain lines for a caller that streams them
BY=""

say() { printf '%s\n' "$*"; }
fail() { local code=$1; shift; printf '✖ %s\n' "$*" >&2; exit "$code"; }
reject() { fail 2 "$@"; }
compose() { docker compose "${COMPOSE_OUTPUT[@]}" -p "$PROJECT" -f compose.yaml -f compose.prod.yaml "$@"; }
step() { compose "$@" 2>&1; }                 # compose reports progress on stderr: a step's progress belongs on stdout
live() { cat "$STATE/live" 2>/dev/null || true; }
previous() { cat "$STATE/previous" 2>/dev/null || true; }
major() { [[ $1 =~ $VERSION_RE ]] && printf '%s' "${BASH_REMATCH[1]}"; }
who() { printf '%s' "${BY:-$(id -un)}"; }
log() { printf '%s  %-10s %-16s → %-16s %s\n' "$(date -Is)" "$(who)" "${1:-none}" "$2" "$3" >> "$STATE/history.log"; }

usage() {
  sed -n '5,11p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

# One backend's state: healthy, starting, unhealthy, or the container's state (exited, restarting, …) if not running.
container_state() {
  local s
  s=$(docker inspect -f '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$1")
  case $s in running/*) printf '%s' "${s#running/}" ;; *) printf '%s' "${s%%/*}" ;; esac
}

# A service's overall state: the first replica that isn't healthy (or running, for a service without a health
# check) decides it; "down" when it has no containers.
service_state() {
  local ids id s worst=""
  ids=$(compose ps -q -a "$1" 2>/dev/null || true)
  [ -n "$ids" ] || { printf 'down'; return; }
  for id in $ids; do
    s=$(container_state "$id")
    [ "$s" = none ] && s=running
    case $s in healthy|running) worst=${worst:-$s} ;; *) worst=$s; break ;; esac
  done
  printf '%s' "$worst"
}

json_str() {                                   # a JSON string, or null for an empty value
  if [ -z "$1" ]; then printf 'null'; return; fi
  local s=${1//\\/\\\\}; s=${s//\"/\\\"}; s=${s//$'\t'/ }
  printf '"%s"' "$s"
}

status() {
  if [ "${1:-}" = --json ]; then
    local backend proxy healthy=false last="" time user from to result
    backend=$(service_state backend); proxy=$(service_state proxy)
    [ "$backend" = healthy ] && [ "$proxy" = running ] && healthy=true
    if [ -s "$STATE/history.log" ]; then
      read -r time user from _ to result < <(tail -n 1 "$STATE/history.log")
      [ "$from" = none ] && from=""
      last=$(printf '{"time":%s,"user":%s,"from":%s,"to":%s,"result":%s}' "$(json_str "$time")" "$(json_str "$user")" \
        "$(json_str "$from")" "$(json_str "$to")" "$(json_str "$result")")
    fi
    printf '{"live":%s,"previous":%s,"healthy":%s,"services":{"backend":%s,"proxy":%s},"last":%s}\n' \
      "$(json_str "$(live)")" "$(json_str "$(previous)")" "$healthy" "$(json_str "$backend")" "$(json_str "$proxy")" "${last:-null}"
    return
  fi
  say "Live:     $(live)"
  say "Previous: $(previous)"
  say "Running images:"
  compose ps --format '  {{.Service}}  {{.Image}}  {{.Status}}' backend proxy 2>/dev/null || say "  (stack not running)"
  if [ -s "$STATE/history.log" ]; then say "Last deploys:"; tail -n 10 "$STATE/history.log" | sed 's/^/  /'; fi
}

# The backends report healthy (readiness), and the proxy answers through its published port.
wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT)) ids id state ok addr
  say "Waiting for the backends to report healthy (up to ${HEALTH_TIMEOUT}s)…"
  while :; do
    ids=$(compose ps -q -a backend); ok=1      # -a: a replica that crashed and exited must count too
    [ -n "$ids" ] || ok=0
    for id in $ids; do
      state=$(container_state "$id")
      [ "$state" = unhealthy ] && { say "A backend is unhealthy."; return 1; }
      [ "$state" = healthy ] || ok=0
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
  deploy/seaweedfs/render-s3-config.sh 2>&1 || return 1
  if [[ $target =~ $VERSION_RE ]]; then
    grep -q 'APP_VERSION' compose.prod.yaml || { say "$target predates release images (E22-S02): it can't be deployed this way."; return 1; }
    export APP_VERSION=$target
    say "Pulling the $target images…"
    step pull -q backend proxy || { say "Couldn't pull the $target images: did the release workflow finish? (If the packages are private: docker login ghcr.io)"; return 1; }
    step up -d --no-build --remove-orphans || return 1
  else
    unset APP_VERSION                          # → the :local tag, built from this checkout
    step up -d --build --remove-orphans || return 1
  fi
  step restart proxy || return 1            # nginx re-resolves the backend replicas
}

deploy() {
  local target=$1 is_revert=${2:-} from head
  from=$(live); head=$(git rev-parse HEAD)
  if [ -z "$is_revert" ] && [ "${SKIP_BACKUP:-}" != 1 ]; then
    if [ -n "$(compose ps -q postgres 2>/dev/null)" ]; then
      BACKUP_PROJECT=$PROJECT scripts/backup.sh 2>&1 || fail 1 "The backup failed: nothing was changed."
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
    step ps backend proxy
    say "✓ $target is live."
    return 0
  fi
  log "$from" "$target" "failed"
  [ -z "$is_revert" ] || return 1
  if [ -n "$from" ]; then
    say "✖ $target didn't come up healthy: putting $from back."
    deploy "$from" revert && fail 1 "$target failed; $from is live again."
    fail 4 "$target failed, and so did putting $from back: check \`docker compose -p $PROJECT logs backend\` now."
  fi
  git switch -q --detach "$head" 2>/dev/null || true
  fail 4 "$target didn't come up healthy, and there's no earlier version to put back: check the backend's logs now."
}

main() {
  cd "$(dirname "$0")/.."                      # → the repository root
  local cmd=main major_ok="" json="" target from_major
  if [ $# -gt 0 ] && [[ $1 != --* ]]; then cmd=$1; shift; fi   # options alone mean main: deploy.sh --by x
  while [ $# -gt 0 ]; do
    case $1 in
      --major) major_ok=1 ;;
      --json)  json=1 ;;
      --by)    if [ $# -lt 2 ] || ! [[ $2 =~ $LOGIN_RE ]]; then reject "--by needs a login (lowercase letters, digits, dashes)."; fi
               BY=$2; shift ;;
      *)       usage ;;
    esac
    shift
  done
  case $cmd in
    -h|--help) usage ;;
    status) status ${json:+--json}; return ;;
  esac
  [ -z "$json" ] || usage
  [ -f .env ] || reject "No .env here: copy .env.example and fill it in (E16-S02 story guide)."
  if ! git diff --quiet || ! git diff --cached --quiet; then
    reject "This clone has uncommitted changes: refusing to deploy (is this really the demo clone?)."
  fi
  mkdir -p "$STATE"
  exec 9>"$STATE/lock"
  flock -n 9 || fail 3 "Another deploy is running."
  git fetch -q --tags --force origin || fail 1 "Couldn't fetch from origin: nothing was changed."
  case $cmd in
    rollback)
      target=$(previous)
      [ -n "$target" ] || reject "No previous version recorded yet."
      ;;
    main) target=main ;;
    *)
      [[ $cmd =~ $VERSION_RE ]] || usage
      git rev-parse -q --verify "refs/tags/$cmd^{commit}" >/dev/null || reject "No tag $cmd in the repository."
      target=$cmd
      from_major=$(major "$(live)" || true)
      if [ -n "$from_major" ] && [ "$(major "$target")" != "$from_major" ] && [ -z "$major_ok" ]; then
        reject "$target is a different major version than the live $(live): deploy it with --major once it's been reviewed."
      fi
      ;;
  esac
  deploy "$target"
}

# Called on the last line: bash has read the whole file before main switches the checkout under it.
main "$@"; exit $?
