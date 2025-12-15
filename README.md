# OneRoll Android

Lightweight Android camera app for capturing photos after scanning a OneRoll QR configuration and uploading through the OneRoll Broker backend (no WebDAV credentials on-device).

## Build & Install
- Debug APK: `GRADLE_USER_HOME=$(pwd)/.gradle ./gradlew --no-daemon assembleDebug`
- Install on device/emulator: `./gradlew installDebug`
- Run unit tests: `./gradlew test`
- Lint: `./gradlew lint`

## Project Layout
- App module: `app/`
- Entry activity: `CameraActivity` (`app/src/main/java/app/oneroll/oneroll/`)
- QR setup flow: `QrScanActivity`
- Resources and layouts under `app/src/main/res/`
- Gradle wrapper scripts: `./gradlew`, `./gradlew.bat`

## Notes
- Uses edge-to-edge layouts; verify UI on devices with cutouts.
- No Google Play Services dependencies by design.
