#!/usr/bin/env bash
# Final checks before opening the submission merge request (guide 08 §2).
# Run from UNIX_CLUB/ in a clone of the hackathon repository:
#   scripts/submission-check.sh [base]            base defaults to origin/main (git fetch first)
#   scripts/submission-check.sh [base] --build    also builds and tests a fresh clone of HEAD (~5 min)
set -uo pipefail

BASE="${1:-origin/main}"
BUILD="${2:-}"
cd "$(dirname "$0")/.."
TEAM_DIR="$(basename "$PWD")"
ROOT="$(git rev-parse --show-toplevel)"
failures=0
pass() { printf '  \033[32mok\033[0m    %s\n' "$1"; }
fail() { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; failures=$((failures + 1)); }

echo "Checking $TEAM_DIR at $(git rev-parse --short HEAD) against $BASE"

# 1. The merge request may only touch our directory.
outside=$(git -C "$ROOT" diff --name-only "$(git -C "$ROOT" merge-base "$BASE" HEAD)" HEAD | grep -v "^$TEAM_DIR/" || true)
if [ -z "$outside" ]; then pass "only $TEAM_DIR/ changes"; else fail "changes outside $TEAM_DIR/:"; echo "$outside" | sed 's/^/          /'; fi

# 2. No secrets in git: the real .env files stay local.
tracked_env=$(git ls-files -- .env .env.prodtest deploy/seaweedfs/s3.prod.json)
if [ -z "$tracked_env" ]; then pass ".env and rendered credentials are not tracked"; else fail "tracked secret files: $tracked_env"; fi
leaks=$(git grep -nIE 're_[A-Za-z0-9]{20,}|s-s4t2ud-[a-f0-9]{20,}|(FT_CLIENT_SECRET|DB_PASSWORD|S3_SECRET_KEY|SMTP_PASSWORD)=[^[:space:]$]+' -- . ':!*.md' \
  | grep -vE '\.env\.example:|change-me|not-configured' || true)
if [ -z "$leaks" ]; then pass "no secret-looking values in tracked files"; else fail "possible secrets:"; echo "$leaks" | sed 's/^/          /'; fi

# 3. The README the jury reads first.
if [ -s README.md ] && grep -q "Live demo" README.md && grep -q "## Run it" README.md && grep -q "## Tech stack" README.md; then
  pass "README.md has the demo URL, how to run it and the tech stack"
else
  fail "README.md misses the live demo, \"## Run it\" or \"## Tech stack\""
fi
missing=$(grep -oE '\]\(([^)#:]+)\)' README.md | sed -E 's/^\]\((.*)\)$/\1/' | while read -r path; do [ -e "$path" ] || echo "$path"; done)
if [ -z "$missing" ]; then pass "every image and link in README.md exists"; else fail "README.md points to missing files:"; echo "$missing" | sed 's/^/          /'; fi
if grep -q "| … | … |" README.md; then fail "README.md still has the Team placeholder"; else pass "README.md names the team"; fi

# 4. Optional: a fresh clone builds and passes its tests.
if [ "$BUILD" = "--build" ]; then
  tmp=$(mktemp -d)
  git clone -q --no-hardlinks "$ROOT" "$tmp/repo" && git -C "$tmp/repo" checkout -q "$(git rev-parse HEAD)"
  if (cd "$tmp/repo/$TEAM_DIR/backend" && ./mvnw -B -q verify >"$tmp/backend.log" 2>&1); then pass "fresh clone: backend verify"; else fail "fresh clone: backend verify (log: $tmp/backend.log)"; fi
  if (cd "$tmp/repo/$TEAM_DIR/frontend" && npm ci --silent && npm run -s lint && npm test && npm run -s build) >"$tmp/frontend.log" 2>&1; then
    pass "fresh clone: frontend lint, test, build"
  else
    fail "fresh clone: frontend (log: $tmp/frontend.log)"
  fi
fi

if [ "$failures" -eq 0 ]; then echo "All checks passed."; else echo "$failures check(s) failed."; exit 1; fi
