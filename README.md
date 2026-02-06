# ASMR Player (Android)

THIS REPOSITORY AND ITS CONTENT WERE GENERATED 100% BY AI.

## Overview

ASMR Player (Android) is an Android audio player project built with Jetpack Compose and Media3. It includes a local library experience plus app-level features like playlists, lyrics, downloads, and settings.

This repository is provided as-is and may be incomplete or experimental.

## Features

- Audio playback powered by Media3 (ExoPlayer)
- Modern UI with Jetpack Compose + Material 3
- Local library browsing and album/track views
- Playlists management
- Lyrics parsing/loading and on-screen lyric UI
- Downloads screen and background work via WorkManager
- Local persistence with Room + Paging 3
- Network layer with Retrofit + OkHttp (includes HTML parsing via Jsoup for some sources)

## Tech Stack

- Kotlin
- Jetpack Compose (Material 3, Navigation)
- Media3 (ExoPlayer, Media Session)
- Room, DataStore, WorkManager, Paging 3
- Hilt + KSP
- Retrofit, OkHttp, Jsoup
- Coil + Palette

## Project Setup

### Prerequisites

- Android Studio (recent stable)
- JDK 17 (required by Android Gradle Plugin 8.x)
- Android SDK (compileSdk/targetSdk: 34, minSdk: 24)

### Open and Run

1. Open this project folder in Android Studio.
2. Let Gradle sync finish.
3. Run the `app` configuration on a device/emulator.

### Build from CLI

```bash
./gradlew :app:assembleDebug
```

### Build Output Location

This project is configured to redirect Gradle build outputs into a custom build directory:

- Default: `<repo>/.build_asmr_player_android/`
- Optional override: set environment variable `TRAE_BUILD_ROOT` to customize the build root

## Configuration Notes

- `local.properties` is intentionally excluded from version control. Android Studio will generate it automatically (it contains machine-specific SDK paths).
- Do not commit keystores or signing configs (`*.jks`, `*.keystore`, `keystore.properties`).

## Disclaimer

- This project is not an official product and is not affiliated with any platform, store, or brand that may be referenced by the code or UI.
- This repository may contain mistakes, incomplete implementations, or security issues. Review the code carefully before using it in production.
- You are responsible for complying with all applicable laws and the terms of service of any third-party services you interact with using this software.
- No warranties are provided. Use at your own risk.

## AI Generation Notice

This repository (including documentation and code changes in this publishing step) is marked as 100% AI-generated as requested. Human review is recommended before any real-world use.
