# Repository Guidelines

## Project Structure & Module Organization
- Single Android app module at `app/`; Gradle version catalogs live in `gradle/libs.versions.toml`.
- Source entry points belong in `app/src/main/java/` (package `app.oneroll.oneroll`) with resources in `app/src/main/res/`.
- JVM unit tests sit in `app/src/test/java/`; device/emulator instrumentation tests in `app/src/androidTest/java/`.
- Gradle and wrapper scripts (`gradlew`, `gradlew.bat`) are in the repo root; avoid invoking system `gradle`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` — compile and package the debug APK.
- `./gradlew test` — run JVM unit tests in `app/src/test/java/`.
- `./gradlew connectedAndroidTest` — run instrumentation tests on a plugged-in device or emulator.
- `./gradlew lint` — run Android Lint; fix issues before submitting.
- Android Studio: use the bundled JDK/SDK; target/compile SDK 36, min SDK 24, Java/Kotlin target 11.

## Coding Style & Naming Conventions
- Kotlin-first codebase (Kotlin 2.0.21, AGP 8.13.x); use Android Studio’s formatter with 4-space indentation and organize imports.
- Package naming: expand under `app.oneroll.oneroll.*`; keep feature packages cohesive (e.g., `app.oneroll.oneroll.feature.login`).
- Resource naming: `snake_case` with prefixes per type (`activity_main.xml`, `ic_share.xml`, `color_primary`).
- Avoid static singletons for state; prefer dependency injection and lifecycle-aware components.

## Testing Guidelines
- Unit tests: place alongside source packages under `app/src/test/java/`; use JUnit4 assertions and Mockito-style fakes/stubs if added.
- Instrumentation/UI tests: mirror package paths in `app/src/androidTest/java/`; rely on AndroidX Test + Espresso.
- Name tests with intent (e.g., `LoginViewModelTest` methods like `loginFailsWithBadPassword`).
- Aim to cover new logic; add regression tests when fixing bugs; keep tests deterministic (no real network/time without fakes).

## Commit & Pull Request Guidelines
- Use small, focused commits with imperative subjects (`Add login form validation`, `Fix null state in profile screen`).
- Include context in body when behavior changes or migrations occur; reference issues/links when available.
- Before opening a PR: run `./gradlew lint test` (and `connectedAndroidTest` if UI changes depend on it), attach relevant screenshots for UI changes, and note any follow-ups or trade-offs.

## Security & Configuration Tips
- Do not commit secrets or API keys; keep them in local `local.properties` or environment variables.
- If adding new build configs, prefer Gradle properties and `BuildConfig` flags over hardcoded constants; document defaults in PR descriptions.

## No Google Play Services Requirement
- The app must run on devices without Google Play Services; do not add `com.google.android.gms` or Play Core dependencies.
- Prefer AndroidX/open-source alternatives (e.g., CameraX, WorkManager) and ensure fallbacks if a library would check for Play Services.
- Validate on a device/emulator without Play Services; if you add dependencies, confirm via `./gradlew :app:dependencies` that none pull in Play Services transitively.
