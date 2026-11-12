#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
if [[ -z "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}" ]]; then
  echo "Note: Install Android SDK platform 37 and set ANDROID_SDK_ROOT/ANDROID_HOME." >&2
fi
chmod +x gradlew
./gradlew --no-daemon testDevDebugUnitTest assembleDevDebug --stacktrace
find app/build/outputs/apk/dev/debug -maxdepth 1 -name '*.apk' -print
