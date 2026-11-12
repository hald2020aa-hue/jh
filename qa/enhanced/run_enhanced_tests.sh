#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/source/health/SourceHealthRules.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/DuplicateLibraryTitles.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/insights/LibraryInsightMetrics.kt" \
  "$ROOT/qa/enhanced/EnhancedUiRegression.kt" \
  -include-runtime -d "$TMP/enhanced.jar"
java -jar "$TMP/enhanced.jar"
