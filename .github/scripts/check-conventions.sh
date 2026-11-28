#!/usr/bin/env bash
# PR conventions (E22-S01): the branch name, every commit message and the PR title follow CONTRIBUTING, and nothing
# names an AI assistant as co-author or generator. Run by the "conventions" job; the patterns are the hooks' own.
#   check-conventions.sh <base-sha> <head-sha> <branch> <pr-title>
set -euo pipefail
base=$1 head=$2 branch=$3 title=$4
types='feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert'
id='E[0-9]{2}(-S[0-9]{2}(-T[0-9]+)?)?'
branch_pattern="^(${types})/(${id})-[a-z0-9]+(-[a-z0-9]+)*$"
header_pattern="^(${types})\((${id})\)!?: [^A-Z].{0,99}$"
ai='(claude|anthropic|opus|fable|mythos|sonnet|haiku|chatgpt|openai|copilot|gemini)'
failed=0
fail() { echo "::error::$1"; failed=1; }

[[ "$branch" =~ $branch_pattern ]] || fail "Branch '$branch' doesn't match <type>/<ID>-<short-desc> (CONTRIBUTING §2), e.g. feat/E04-S01-club-creation-request"
[[ "$title" =~ $header_pattern ]] || fail "PR title '$title' doesn't match <type>(<ID>): <description> (it becomes the squash commit's message)"
if grep -Eiq "$ai" <<<"$title"; then fail "The PR title names an AI assistant"; fi

while read -r sha; do
  header=$(git log -1 --format=%s "$sha")
  [[ "$header" =~ ^(Merge\ |Revert\ \"|fixup!\ |squash!\ ) ]] && continue
  [[ "$header" =~ $header_pattern ]] || fail "Commit ${sha:0:7} '$header' doesn't match <type>(<ID>): <description> (CONTRIBUTING §3)"
  if git log -1 --format=%B "$sha" | grep -Eiq "^(co-authored-by|generated-by|assisted-by):.*$ai|generated (with|by) .*$ai"; then
    fail "Commit ${sha:0:7} names an AI assistant as co-author or generator"
  fi
done < <(git rev-list --no-merges "$base..$head")

exit $failed
