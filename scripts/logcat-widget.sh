#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK_ROOT/platform-tools/adb"
LOG_TAG="LastTimeIWidget"
SAVE_MODE="${1:-}"

if [[ ! -x "$ADB" ]]; then
  echo "adb not found at $ADB" >&2
  exit 1
fi

if [[ "$SAVE_MODE" == "--save" ]]; then
  mkdir -p "$ROOT_DIR/logs"
  OUTPUT_FILE="$ROOT_DIR/logs/logcat-$(date +%Y%m%d-%H%M%S).log"
  echo "Saving logcat to $OUTPUT_FILE"
  "$ADB" logcat -v time "$LOG_TAG:D" "System.err:W" "AndroidRuntime:E" "*:S" | tee "$OUTPUT_FILE"
  exit 0
fi

"$ADB" logcat -v color "$LOG_TAG:D" "System.err:W" "AndroidRuntime:E" "*:S"
