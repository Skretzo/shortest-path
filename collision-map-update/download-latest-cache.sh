#!/usr/bin/env bash
# Requires: curl, jq, unzip
# Usage:   chmod +x download-latest-cache.sh && ./download-latest-cache.sh

set -euo pipefail

URL="https://archive.openrs2.org/caches.json"

echo "▶ Fetching cache metadata…"
CACHE_ID=$(curl -s "$URL" | jq -r '
  map(select(.game=="oldschool" and .environment=="live"))
  | sort_by(.timestamp)
  | .[-1].id
')

if [[ -z "$CACHE_ID" ]]; then
  echo "❌ Could not determine latest cache ID." >&2
  exit 1
fi

echo "Latest OSRS live cache id: $CACHE_ID"

BASE="https://archive.openrs2.org/caches/runescape/$CACHE_ID"

echo "⬇ Downloading cache.zip…"
curl -fL "$BASE/disk.zip" -o cache.zip

echo "🧹 Cleaning previous extract dir…"
rm -rf cache
mkdir cache

echo "📂 Unzipping cache.zip…"
unzip -q cache.zip -d ./

echo "⬇ Downloading keys.json…"
curl -fL "$BASE/keys.json" -o keys.json

echo "✅ Done."
