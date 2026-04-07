#!/usr/bin/env bash
# Build coordinate dumps for HEAD and its merge-base, then write a JSON file
# containing only coordinates introduced on the current branch.
#
# This script is used by the coordinate preview GitHub workflow so the logic for
# worktree setup, base-task detection, and output generation stays out of YAML.
# It also centralizes the compatibility guard for older merge-base commits that
# do not yet define the `dumpTransportCoordinates` Gradle task.

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
  # The workflow checks out the merge-base in a temporary worktree so we can run
  # the dumper against both revisions without mutating the main checkout.
  git worktree remove --force "$BASE_DIR" >/dev/null 2>&1 || true
  rm -f "$BASE_COORDINATES_PATH"
}
trap cleanup EXIT

chmod +x gradlew
git worktree add "$BASE_DIR" "$MERGE_BASE" >/dev/null

if ! (
  cd "$BASE_DIR"
  chmod +x gradlew
  # Old base commits may not contain the dumper task yet. Probe task output
  # first so we can skip the preview cleanly instead of failing the workflow.
  ./gradlew --no-daemon tasks --all | grep -q '^dumpTransportCoordinates'
); then
  echo "Base commit does not support dumpTransportCoordinates; skipping preview."
  {
    echo "has_changes=false"
    echo "skip_reason=missing_base_dumper"
  } >> "$GITHUB_OUTPUT_PATH"
  exit 0
fi

# Build a complete coordinate dump at the current HEAD and at the merge-base.
# The later Python step reduces this to only coordinates that are new in HEAD.
./gradlew --no-daemon dumpTransportCoordinates --args="--output build/head-coordinates.json"
(
  cd "$BASE_DIR"
  ./gradlew --no-daemon dumpTransportCoordinates --args="--output build/base-coordinates.json"
)
cp "$BASE_DIR/build/base-coordinates.json" "$BASE_COORDINATES_PATH"
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
