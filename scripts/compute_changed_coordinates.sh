#!/usr/bin/env bash
# Build coordinate dumps for HEAD and its merge-base, then write a JSON file
# containing only coordinates introduced on the current branch.
#
# This script is used by the coordinate preview GitHub workflow so the logic for
# worktree setup, base-task detection, and output generation stays out of YAML.
# The coordinate dump is produced entirely by a Python script that reads the
# transport .tsv files directly, so no JDK or Gradle invocation is needed.

set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: compute_changed_coordinates.sh <base-ref> <github-output>" >&2
  exit 1
fi

BASE_REF="$1"
GITHUB_OUTPUT_PATH="$2"

MERGE_BASE="$(git merge-base "origin/${BASE_REF}" HEAD)"
BASE_DIR="$(mktemp -d)"
BASE_COORDINATES_PATH="$(mktemp)"

cleanup() {
  git worktree remove --force "$BASE_DIR" >/dev/null 2>&1 || true
  rm -f "$BASE_COORDINATES_PATH"
}
trap cleanup EXIT

git worktree add "$BASE_DIR" "$MERGE_BASE" >/dev/null

# Dump coordinates at HEAD and at the merge-base.  The Python dumper reads the
# .tsv files directly so it works against any revision regardless of whether a
# Gradle task exists there.
python3 scripts/dump_transport_coordinates.py \
  --output build/head-coordinates.json

python3 scripts/dump_transport_coordinates.py \
  --base-dir "$BASE_DIR/src/main/resources/transports" \
  --output "$BASE_COORDINATES_PATH"

python3 scripts/diff_coordinate_json.py \
  "$BASE_COORDINATES_PATH" \
  build/head-coordinates.json \
  build/changed-coordinates.json

# The workflow only needs a boolean output for conditional steps. The actual
# changed-coordinate payload is written to build/changed-coordinates.json.
if [ "$(cat build/changed-coordinates.json)" = "[]" ]; then
  echo "has_changes=false" >> "$GITHUB_OUTPUT_PATH"
else
  echo "has_changes=true" >> "$GITHUB_OUTPUT_PATH"
fi
