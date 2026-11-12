# Independent TachiyomiJ2K development fork — changes

This is an **unofficial, unverified development snapshot**, based on the user's
uploaded `tachiyomiJ2K-master.zip` (TachiyomiJ2K 1.8.1). Original authors and
Apache-2.0 license are retained. All modified source files carry modification notices.

## Source changes

1. **Extension catalog** — materialize private APK metadata once and select by
   package name, avoiding repeated archive parsing. Keep version precedence and
   signature trust handling.
2. **Cold start** — start initial extension scan on `Dispatchers.IO` instead of
   synchronously in `ExtensionManager`'s constructor, and move eager app component
   initialization off the main thread and out of the crash-handler process.
3. **Thread safety** — use a concurrent trust-signature set and avoid blocking
   `runBlocking` in `SourceManager.getOrStub()`.
4. **AVIF reader** — on Android 12+, detect AVIF by container brands and use
   platform `ImageDecoder` on an IO dispatcher with bounded pixel decoding and
   a temporary disk file; old Android retains the native decoder path. Coil
   cover decoding selects the platform implementation for AVIF on 12+.
5. **Library updates** — protect an empty category update, synchronize additions
   to source queues, use concurrency-safe collections, and track dynamically
   created update jobs. Source queues are cleared for unavailable sources.
6. **Large-library memory** — change Coil cover cache target from 40% of heap to
   25% normally and 15% on low-RAM devices.
7. **Download validation** — reject unsupported/nonimage responses, close HTTP
   responses, close cached-file sniff streams, and require a successful filename
   rename before marking the image ready.
8. **CBZ downloads** — store images using two-pass streaming CRC rather than
   reading entire page files into memory; include a minimal `ComicInfo.xml`.
9. **Source repository safety** — bound extension index input/decompression to
   20 MiB and reject cyclic or excessively long `index_v2` pointer chains.
10. **Series labels** — accept `manwha` spelling and normalize Chinese script
    aliases such as `zh-Hans` and `zh-Hant`.
11. **Co-installable development variant** — independent Dev application ID
    suffix and branded labels; Dev builds retain Arabic locales and disable
    Firebase Analytics/Crashlytics collection.
12. **Quality checks** — added self-contained CLI Kotlin regression tests,
    Android JUnit tests, and GitHub Actions debug build/test workflow.

## Compatibility and limits

- The application namespace and original stable `standard` flavor remain intact.
- No database schema migration was introduced; restore behavior was not tested
  on Android hardware.
- Android app compilation, emulator tests, runtime crash tests, and a signed
  APK have **not** been completed in the current environment.
- Avoid enabling auto-update from the original app in any derivative build until
  the fork's update channels and signing identity have been reviewed.
- Retain `LICENSE` and all original attribution in redistributed versions.

## Additional development: R1 → R2 → R3 (2026-11-12)

- **R1:** WorkManager library update selections persist across restarts and retries; hash-based lookup; concurrency-safe source stubs; decompressed backup input bounded to 128 MiB.
- **R2:** Reader layout resets on transition from separating vertical hinge back to regular screen (respecting vertical seekbar); animated image stream input bounded to 64 MiB.
- **R3:** `dup:yes|no` library search field with one-time conservative cross-source duplicate index; normalized Arabic/Latin title search; seven-day chapter last-read counts and distinct reading days in Statistics.
- **QA:** 64 pure Kotlin logic checks, 5 search matcher integration checks, 10 XML/SQLite checks. Added Android JUnit tests and CI structural step (not run on Android).
- **Blocker:** Android build could not start because Gradle 9.6.1 and Android SDK are missing; no APK, runtime verification, security release audit or signed production output.

## Enhanced UI v2 (2026-11-12) — visual/functional iteration

- Distinct development icon, brand palette, modernized main navigation, covers, rows, downloads and reader navigation.
- Dedicated Compose source health screen with explicit tests, HTTP status/latency, bounded concurrency and private-address rejection.
- Dedicated library metrics dashboard backed by the user's actual database, category-safe counting and cross-source duplicate detection.
- Direct library smart filter actions and a reader two-page action linked to existing reading mode settings.
- Download queue summary; existing queue actions and resume/reorder support retained.
- JUnit, pure Kotlin, Android XML and CI regression hooks.
- GitHub Actions is required for the first Android compilation of these new UI additions; local Gradle is network-blocked.
