#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
STUDIO_APP="$HOME/Applications/Android Studio.app"

if [[ ! -d "$STUDIO_APP" ]]; then
  echo "Android Studio was not found at $STUDIO_APP" >&2
  exit 1
fi

open -a "$STUDIO_APP" "$ROOT_DIR"
