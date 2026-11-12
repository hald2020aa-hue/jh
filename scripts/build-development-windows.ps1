$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (-not ($env:ANDROID_HOME -or $env:ANDROID_SDK_ROOT)) {
    Write-Warning 'Install Android SDK API 37 and set ANDROID_HOME or ANDROID_SDK_ROOT.'
}
& .\gradlew.bat --no-daemon testDevDebugUnitTest assembleDevDebug --stacktrace
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed: exit $LASTEXITCODE" }
Get-ChildItem 'app\build\outputs\apk\dev\debug' -Filter '*.apk' | Select-Object FullName, Length
