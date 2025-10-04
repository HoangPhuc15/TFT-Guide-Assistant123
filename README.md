# TFT Bubbles Assistant Starter

This starter repository packages three pieces you can copy straight into a new GitHub project:

1. **Android bubble overlay prototype** (`app/`) – Kotlin sources targeting Android 15 with a `BubbleActivity`, a foreground `ScreenCaptureService`, and a lightweight notification helper that surfaces strategy tips in a system bubble.
2. **TFT dataset + auto-sync** (`data/sets/15.19/`) – a trimmed patch 15.19 sample (champions, items, traits, augments, and JSON rules) that the prototype ships as assets and refreshes from the network at runtime.
3. **Documentation** – this README plus the in-app comments call out where to plug in real OCR/computer-vision, richer datasets, or an online sync service.

The goal is to give you a reproducible baseline: unzip or clone, open in Android Studio, click *Build ▸ Make Project*, and you get a runnable APK that pops a conversation bubble with hard-coded tips. From there you can iterate on the computer-vision pipeline, expand the rules engine, or swap in live data hosting.

## Project layout

```
tft-bubbles-assistant/
├─ app/
│  ├─ src/main/java/com/example/tft/
│  │  ├─ ui/BubbleActivity.kt              # Hosts the compact bubble UI
│  │  ├─ notif/BubbleNotifier.kt           # Builds notification + bubble metadata
│  │  ├─ data/TftRepository.kt             # Loads JSON from disk (network cache → assets fallback)
│  │  ├─ data/DataSyncer.kt               # Background job that pulls Data Dragon + tips
│  │  ├─ vision/ScreenAnalyzer.kt          # Heuristic screen-state placeholder
│  │  ├─ rules/TipEngine.kt, TipSession.kt # Matches states to rule tips
│  │  └─ service/ScreenCaptureService.kt   # Foreground MediaProjection stub
│  ├─ src/main/assets/data/sets/15.19/     # Copied into the APK for offline reads
│  ├─ src/main/res/layout/activity_bubble.xml
│  └─ src/main/AndroidManifest.xml
├─ data/sets/15.19/                        # Same JSON payloads for external hosting
└─ tools/                                   # Existing utility scripts (unchanged)
```

The `data/` directory at the repo root mirrors the assets bundled into the APK so you can:

- host them separately (for example on GitHub Pages) and fetch at runtime, or
- track balance changes in git before deciding whether to ship the updated assets.

## Build the APK

1. **Sync the repo** – clone or copy this starter, then ensure `app/src/main/assets/data/sets/15.19/` is present (already included).
2. **Open in Android Studio** – Hedgehog (2023.1.1) or newer with Android Gradle Plugin 8.2+ and JDK 17. Narwhal 2024.1 works out of the box too.
3. **Run/Build** – `Build ▸ Make Project` or `./gradlew assembleDebug`. Install the resulting `app-debug.apk` via Studio, `adb`, or the device file manager.
4. **Grant permissions** – on first launch the prototype asks for overlay (bubble) and screen-capture consent. The capture flow is stubbed but the permissions wiring is ready.

The app boots even when the device is offline. When a network connection is available it downloads the latest JSON into `files/data/sets/live/` and transparently switches the repository to that cache.

## Configure automatic data refresh

`DataSyncer` runs on every app start (via `androidx.startup`) and attempts to update five files:

| Type       | Source | Default endpoint |
| ---------- | ------ | ---------------- |
| Champions  | Riot Data Dragon | `https://ddragon.leagueoflegends.com/cdn/<ddragonVersion>/data/en_US/tft-champions.json` |
| Items      | Riot Data Dragon | `…/tft-items.json` |
| Traits     | Riot Data Dragon | `…/tft-traits.json` |
| Augments   | Riot Data Dragon | `…/tft-augments.json` |
| Tips rules | Custom | `tipsRulesUrl` (left blank by default) |

Two Gradle properties control these endpoints and compile into `BuildConfig`:

```properties
# gradle.properties
ddragonVersion=15.19.1      # Use the patch you want Narwhal/Gradle to fetch.
tipsRulesUrl=https://raw.githubusercontent.com/<you>/tft-data/main/tips-rules.json
```

Leave `tipsRulesUrl` empty to keep using the bundled offline rules. When you point it to a hosted JSON file, `DataSyncer` caches the download and the `TipEngine` will immediately read the updated strategy list. Failures (for example 404s or timeouts) are logged but never crash the app; the repository falls back to the asset copy so you can always reach the bubble UI.

## Extending the starter

- **Vision** – replace `ScreenAnalyzer`’s color heuristic with ML Kit Text Recognition + template matching, or feed frames to a server model. It currently emits a static `TipState` so the rest of the pipeline can be exercised without real OCR.
- **Rules** – edit `data/sets/15.19/tips-rules.json` to describe new matchers. The `TipEngine` supports string equality and basic numeric comparisons; extend it with fuzzy matching or trait lookups as needed.
- **Data hosting** – publish the `data/` folder to a CDN and update `tipsRulesUrl` so the built-in sync job writes into `context.filesDir` before you call `TftRepository`.
- **Reports** – persist the `TipSession` objects produced by `TipEngine.evaluate` and export them as Markdown or JSON for after-action reviews.

## License

The underlying Data Dragon content remains © Riot Games. Kotlin sources in this starter follow the original repository’s Apache 2.0 license.
