#!/usr/bin/env bash
# Sets up the ticket tracker (E17-S01) once the production stack runs: your admin account, the bot the backend
# uses, the private vclubroom/support repository with its labels (deploy/forgejo/labels.txt) and issue templates
# (deploy/forgejo/issue_template/), and the bot's token in .env. Safe to run again: it adds what is missing and
# updates the templates.
# Run from the repository root:   scripts/tickets-setup.sh <your-username> <your-email>
set -euo pipefail
cd "$(dirname "$0")/.."
[ $# -eq 2 ] || { echo "Usage: scripts/tickets-setup.sh <your-username> <your-email>" >&2; exit 1; }
[ -f .env ] || { echo "No .env here: run it from the repository root of the deployed clone." >&2; exit 1; }
admin=$1 email=$2 org=vclubroom repo=support bot=vclubroom-bot

# Same project as deploy/deploy.sh. Forgejo's CLI and API are used from inside its container.
compose() { docker compose -p "${PROJECT:-vclubroom-demo}" -f compose.yaml -f compose.prod.yaml "$@"; }
fj() { compose exec -T forgejo forgejo "$@"; }
users() { fj admin user list | awk 'NR > 1 { print $2 }'; }

# api METHOD PATH [JSON]: sets $code and $body.
api() {
  local out
  if [ $# -ge 3 ]; then
    out=$(printf '%s' "$3" | compose exec -T -e TOKEN="$token" forgejo sh -c 'curl -s -w "\n%{http_code}" -X "$1" \
      -H "Authorization: token $TOKEN" -H "Content-Type: application/json" --data-binary @- "http://127.0.0.1:3000/api/v1$2"' sh "$1" "$2")
  else
    out=$(compose exec -T -e TOKEN="$token" forgejo sh -c 'curl -s -w "\n%{http_code}" -X "$1" \
      -H "Authorization: token $TOKEN" "http://127.0.0.1:3000/api/v1$2"' sh "$1" "$2")
  fi
  code=${out##*$'\n'} body=${out%$'\n'*}
}
expect() { for c in "$@"; do [ "$code" = "$c" ] && return 0; done; echo "Forgejo answered $code: $body" >&2; exit 1; }
json() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }

echo "Waiting for Forgejo…"
for _ in $(seq 60); do compose exec -T forgejo wget -q -O /dev/null http://127.0.0.1:3000/api/healthz 2>/dev/null && break; sleep 2; done
compose exec -T forgejo wget -q -O /dev/null http://127.0.0.1:3000/api/healthz || { echo "Forgejo is not up: compose ps forgejo" >&2; exit 1; }

if users | grep -qx "$admin"; then
  echo "✓ account $admin"
else
  password=$(fj admin user create --username "$admin" --email "$email" --admin --random-password --random-password-length 20 \
    | sed -n "s/.*generated random password is '\(.*\)'.*/\1/p")
  echo "✓ account $admin created, temporary password: $password   (you choose a new one at the first login)"
fi

# A token for this run only. Forgejo only lets a password delete tokens: remove "setup-…" in Settings › Applications.
token=$(fj admin user generate-access-token --username "$admin" --token-name "setup-$(date +%Y%m%d-%H%M%S)" --scopes all --raw | tail -1)

if users | grep -qx "$bot"; then
  echo "✓ bot $bot"
else
  # Restricted: it only sees the repositories it is added to. Nobody ever logs in with its password.
  fj admin user create --username "$bot" --fullname "Virtual Clubroom" --email "$bot@noreply.invalid" \
    --random-password --must-change-password=false --restricted >/dev/null
  echo "✓ bot $bot created"
fi

api GET "/orgs/$org"
if [ "$code" = 404 ]; then
  api POST /orgs "{\"username\":\"$org\",\"full_name\":\"Virtual Clubroom\",\"visibility\":\"private\"}"; expect 201
fi
echo "✓ organisation $org"

api GET "/repos/$org/$repo"
if [ "$code" = 404 ]; then
  api POST "/orgs/$org/repos" "{\"name\":\"$repo\",\"description\":\"Support tickets from the Virtual Clubroom app\",\"private\":true,\"auto_init\":true,\"default_branch\":\"main\"}"
  expect 201
fi
# Project management on, code features off: nothing is ever pushed here.
api PATCH "/repos/$org/$repo" '{"has_issues":true,"has_projects":true,"has_wiki":true,"has_pull_requests":false,"has_releases":false,"has_packages":false,"has_actions":false}'
expect 200
api PUT "/repos/$org/$repo/collaborators/$bot" '{"permission":"write"}'; expect 204
echo "✓ repository $org/$repo, $bot can write to it"

api GET "/repos/$org/$repo/labels?limit=50"; expect 200
existing=$body added=0
while IFS='|' read -r name color exclusive description; do
  case "$name" in ''|'#'*) continue ;; esac
  grep -qF "\"name\":\"$(json "$name")\"" <<<"$existing" && continue
  api POST "/repos/$org/$repo/labels" "{\"name\":\"$(json "$name")\",\"color\":\"$color\",\"exclusive\":$exclusive,\"description\":\"$(json "$description")\"}"
  expect 201; added=$((added + 1))
done < deploy/forgejo/labels.txt
echo "✓ labels ($added added)"

for file in deploy/forgejo/issue_template/*.yaml; do
  path=".forgejo/issue_template/$(basename "$file")" content=$(base64 -w0 "$file")
  api GET "/repos/$org/$repo/contents/$path?ref=main"
  if [ "$code" = 404 ]; then
    api POST "/repos/$org/$repo/contents/$path" "{\"content\":\"$content\",\"message\":\"Add $path\",\"branch\":\"main\"}"; expect 201
  elif ! grep -qF "\"content\":\"$content\"" <<<"$body"; then
    sha=$(sed -n 's/^{"name":"[^"]*","path":"[^"]*","sha":"\([0-9a-f]*\)".*/\1/p' <<<"$body")
    api PUT "/repos/$org/$repo/contents/$path" "{\"content\":\"$content\",\"sha\":\"$sha\",\"message\":\"Update $path\",\"branch\":\"main\"}"; expect 200
  fi
done
echo "✓ issue templates"

if grep -q '^TICKETS_FORGEJO_TOKEN=.' .env; then
  echo "✓ TICKETS_FORGEJO_TOKEN already in .env"
else
  bot_token=$(fj admin user generate-access-token --username "$bot" --token-name "backend-$(date +%Y%m%d-%H%M%S)" \
    --scopes write:issue,read:repository,read:user --raw | tail -1)
  if grep -q '^TICKETS_FORGEJO_TOKEN=' .env; then
    sed -i "s/^TICKETS_FORGEJO_TOKEN=.*/TICKETS_FORGEJO_TOKEN=$bot_token/" .env
  else
    printf '\nTICKETS_FORGEJO_TOKEN=%s\n' "$bot_token" >> .env
  fi
  echo "✓ TICKETS_FORGEJO_TOKEN written to .env"
fi

echo
echo "Done. Open $(grep '^TICKETS_PUBLIC_URL=' .env | cut -d= -f2-)$org/$repo and log in as $admin."