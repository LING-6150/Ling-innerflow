#!/usr/bin/env bash
# Spawn a dedicated git worktree for a Pattern Engine V1.2 sub-issue.
# Usage: ./docs/issues/spawn-worktree.sh pe-1-eval-harness
#
# Behavior:
#   - Must be run from the MAIN repo root (where .git/ lives), not from inside a worktree.
#   - Creates branch feature/<slug> from epic/pattern-engine-v1.2 (or origin/main if epic branch
#     doesn't exist yet — script will warn).
#   - Creates worktree at .claude/worktrees/<slug>.
#   - Prints the cd command for you to copy.
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <slug>   e.g. $0 pe-1-eval-harness" >&2
  exit 1
fi

SLUG=$1
BRANCH="feature/${SLUG}"
WT_DIR=".claude/worktrees/${SLUG}"
EPIC="epic/pattern-engine-v1.2"

# Sanity: must be in main repo root
if [[ ! -d .git ]]; then
  echo "ERROR: run this from the main repo root (.git/ not found here)" >&2
  exit 1
fi

# Refresh remote refs
git fetch --quiet origin || true

# Determine base ref
if git show-ref --verify --quiet "refs/heads/${EPIC}"; then
  BASE="${EPIC}"
elif git show-ref --verify --quiet "refs/remotes/origin/${EPIC}"; then
  git branch "${EPIC}" "origin/${EPIC}"
  BASE="${EPIC}"
else
  echo "WARNING: ${EPIC} branch not found locally or on origin." >&2
  echo "         Falling back to origin/main. You probably want to create the Epic branch first:" >&2
  echo "             git checkout main && git pull && git checkout -b ${EPIC} && git push -u origin ${EPIC}" >&2
  BASE="origin/main"
fi

if [[ -d "${WT_DIR}" ]]; then
  echo "ERROR: worktree dir ${WT_DIR} already exists. Remove it first with:" >&2
  echo "    git worktree remove ${WT_DIR} && git branch -D ${BRANCH}" >&2
  exit 1
fi

mkdir -p "$(dirname "${WT_DIR}")"
git worktree add -b "${BRANCH}" "${WT_DIR}" "${BASE}"

echo
echo "✅ Worktree ready."
echo "   path:   ${WT_DIR}"
echo "   branch: ${BRANCH}"
echo "   base:   ${BASE}"
echo
echo "Next:"
echo "    cd ${WT_DIR}"
echo "    cat docs/issues/${SLUG^^}*.md   # read the issue spec"
echo
