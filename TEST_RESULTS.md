# Final offline test report — R1/R2/R3 development snapshot

| Check | Result | Meaning |
|---|---|---|
| Kotlin logic regression tests | **64 / 64 PASS** | Extension packages, image headers, download ZIP, durable library selection, backup stream bounds, duplicate-title matching/search parsing |
| Search evaluator integration with stubs | **5 / 5 PASS** | Compiled actual parser and matching extension with substitute Android data models |
| XML / SQL / structure tests | **10 / 10 PASS** | English and Arabic resources, new stats layout, SQLite counters, Gradle wrapper and license |
| Git diff whitespace | **PASS** | `git diff --check` |
| Android JUnit | **NOT RUN** | Needs Gradle and Android SDK |
| Dev APK assembly | **BLOCKED BEFORE COMPILATION** | Gradle 9.6.1 cannot be downloaded due UnknownHostException |
| Android emulator/device tests | **NOT RUN** | No Android runtime/SDK |
| Signed production APK | **NOT AVAILABLE** | Signing/test gate not cleared |

Full build attempt log: `qa/gradle-release-build-attempt.log`.
Commands: `bash qa/run_local_tests.sh`, `python3 qa/static_integration_checks.py`, `./gradlew --offline :app:testDevDebugUnitTest :app:assembleDevDebug --no-daemon --stacktrace`.

**Limitations:** All green tests are offline checks of a subset of the code. They are not evidence that the Android application compiles or runs.
