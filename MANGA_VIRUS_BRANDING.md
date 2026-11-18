# MANGA VIRUS — V17 branding

- English app name: **MANGA VIRUS**
- Arabic app name: **فيروس مانجا**
- Full approved logo: `app/src/main/res/drawable-nodpi/manga_virus_logo.webp`
- Legacy launcher icons: `app/src/main/res/mipmap-*`
- Adaptive launcher icon: `app/src/main/res/mipmap-anydpi-v26/`
- Debug and devDebug adaptive overlays are synchronized under `app/src/debug/` and `app/src/devDebug/`
- Android themed monochrome icon: `@mipmap/ic_launcher_monochrome`
- Legacy splash screen: `app/src/main/res/drawable/splash_background.xml`
- Android 12+ splash screen: `app/src/main/res/values/themes.xml`

## Preserved application identity and data

This branding change does not edit Kotlin source, database schemas, source IDs,
reader state, download logic, extension logic, authentication URI schemes,
`applicationId`, or Gradle build variants. The existing launcher resource names
are retained so the manifest continues to resolve the same resources.

## Update and signing safety

Keeping the same `applicationId` does not guarantee an in-place update.
Android also requires the signing certificate to match the installed APK, and
the new `versionCode` must be higher. Never commit a private signing key or
password to Git. A CI debug APK is not an installable update unless its signing
certificate matches the installed app.

## Validation

Run `python3 qa/v17/validate_manga_virus_branding.py`, then the existing
`quality_gate.yml` workflow. Device validation is still required for launcher
masking, Android 12+ splash rendering, Arabic labels, and update-signature
compatibility.
