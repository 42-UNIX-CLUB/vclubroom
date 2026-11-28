#!/usr/bin/env bash
# Tags the next release (E19-S03). The version follows SemVer from the Conventional Commits since the last release
# (cliff.toml): feat → minor, fix or perf → patch, ! or BREAKING CHANGE → major; other types alone release nothing.
# Afterwards: deploy (the changelog page shows the release), then close its milestone in Forgejo, so linked tickets
# say "Released in …".
# Run in your clone, on main as it is on GitHub:   scripts/release.sh   (it shows what's in it and asks first)
set -euo pipefail
cd "$(dirname "$0")/.."
cliff() { docker run --rm --user "$(id -u):$(id -g)" -v "$PWD":/app -w /app orhunp/git-cliff:2.14.2 "$@"; }

git fetch --quiet --tags origin
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || { echo "Release from main as it is on GitHub: git switch main && git pull" >&2; exit 1; }
last=$(git describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)
[ -n "$last" ] || { echo "No release yet: tag the first one, v1.0.0 (E19-S03 story guide)." >&2; exit 1; }
next=$(cliff --bumped-version 2>/dev/null)
[ "$next" != "$last" ] || { echo "Nothing to release since $last: no feat, fix, perf or breaking change." >&2; exit 1; }

echo "Next release: $next (after $last). What users will see:"
git log --format='  %s' "$last..HEAD" | grep -E '^  (feat|fix|perf)(\(|!|:)' || true
read -rp "Tag $next on $(git rev-parse --short HEAD) and push it? [y/N] " answer
[ "$answer" = y ] || { echo "Nothing done."; exit 0; }
git tag -a "$next" -m "Virtual Clubroom $next"
git push origin "$next"
echo "Tagged $next. Now deploy (deploy/deploy.sh on the server), then close the $next milestone in Forgejo."
