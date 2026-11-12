#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
kotlinc \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionPackageSelection.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/data/database/models/ManhwaTagClassification.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/AvifHeader.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/extension/api/LimitedInputStream.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/data/download/StreamingCbz.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/data/library/LibrarySelectionStore.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/util/system/BoundedRead.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/DuplicateLibraryTitles.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/LibrarySearchAst.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/LibrarySearchLexer.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/LibrarySearchParser.kt" \
  "$ROOT/qa/RegressionTest.kt" -include-runtime -d "$TEMP_DIR/tests.jar"
java -jar "$TEMP_DIR/tests.jar"

# Compile the real search evaluator with lightweight model stubs to check
# field exhaustiveness and actual dup:yes/no evaluation without Android SDK.
kotlinc -nowarn \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/LibrarySearchAst.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/LibrarySearchLexer.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/LibrarySearchParser.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/DuplicateLibraryTitles.kt" \
  "$ROOT/app/src/main/java/eu/kanade/tachiyomi/ui/library/search/QueryNodeExtensions.kt" \
  "$ROOT/qa/SearchEvaluationStub.kt" \
  "$ROOT/qa/SearchEvaluationCheck.kt" \
  -include-runtime -d "$TEMP_DIR/search-tests.jar"
java -jar "$TEMP_DIR/search-tests.jar"
