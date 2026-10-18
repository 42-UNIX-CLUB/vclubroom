#!/usr/bin/env bash
# Usage: scripts/api.sh login <user> <password> | scripts/api.sh <METHOD> <path> [json-body]
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
JAR="${JAR:-/tmp/vclubroom-cookies.txt}"
xsrf() { awk '$6=="XSRF-TOKEN"{print $7}' "$JAR" | tail -1; }
usage() {
  echo "Usage: $0 login <user> <password>      log in once (session kept in $JAR)" >&2
  echo "       $0 <METHOD> <path> [json-body]  e.g. $0 GET /api/me" >&2
  exit 1
}

[[ $# -ge 1 ]] || usage

if [[ "$1" == "login" ]]; then
  [[ $# -eq 3 ]] || usage
  rm -f "$JAR"
  curl -s -c "$JAR" -b "$JAR" "$BASE/api/auth/csrf" -o /dev/null
  curl -s -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $(xsrf)" -H 'Content-Type: application/json' \
       -d "{\"username\":\"$2\",\"password\":\"$3\"}" "$BASE/api/auth/login" | jq
  exit 0
fi

[[ $# -ge 2 ]] || usage
method="$1"; path="$2"; body="${3:-}"
args=(-s -c "$JAR" -b "$JAR" -X "$method" -H "X-XSRF-TOKEN: $(xsrf)")
[[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
curl "${args[@]}" "$BASE$path" | jq