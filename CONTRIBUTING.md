# Contributing to LiteChat

LiteChat is intentionally a small native Android application. Changes should preserve direct provider communication, local ownership of data and a dependency footprint proportionate to the feature.

## Development setup

- JDK 17 or newer
- Android SDK 35
- Android Studio or the checked-in Gradle wrapper

Create an ignored `local.properties` with `sdk.dir` when `ANDROID_HOME` is not configured, then run:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Instrumentation tests require an API 31+ device or emulator:

```powershell
./gradlew.bat connectedDebugAndroidTest
```

## Pull requests

- Keep changes focused and explain observable behavior, privacy impact and dependency cost.
- Add unit tests for parsers, request mapping and data rules; add instrumentation coverage for Room and Compose behavior.
- Do not include API keys, local databases, attachment contents, generated build directories or signing material.
- Update `CHANGELOG.md` for user-facing changes.

Bug reports should include the Android version, device or emulator, provider protocol, expected behavior and a minimal reproduction. Remove keys and private conversation content from logs and screenshots.
