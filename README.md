# TFT Bubbles Assistant Starter

This starter repository packages three pieces you can copy straight into a new GitHub project:

1. **Android bubble overlay prototype** (`app/`) – Kotlin sources targeting Android 15 with a `BubbleActivity`, a foreground `ScreenCaptureService`, and a coroutine-driven notification helper that surfaces context-aware strategy tips in a system bubble for phones and tablets.
2. **TFT dataset + auto-sync** (`data/sets/15.19/`) – a curated patch 15.19 sample (champions, items, traits, augments, and multi-step rules) that the prototype ships as assets and refreshes from the network at runtime so recommendations stay current.
3. **Documentation** – this README plus in-app comments call out where to plug in real OCR/computer vision, richer datasets, or an online sync service.

The goal is to give you a reproducible baseline: unzip or clone, open in Android Studio, click *Build ▸ Make Project*, and you get a runnable APK that recognises stage/level/gold from the screen, prioritises actions, and drives a rich bubble conversation. From there you can refine the computer-vision pipeline, expand the rule engine, or swap in live data hosting.

## Project layout

```
tft-bubbles-assistant/
├─ app/
│  ├─ src/main/java/com/example/tft/
│  │  ├─ ui/BubbleActivity.kt              # Hosts the compact bubble UI
│  │  ├─ notif/BubbleNotifier.kt           # Builds notification + bubble metadata
│  │  ├─ data/TftRepository.kt             # Loads JSON from disk (network cache → assets fallback)
│  │  ├─ data/DataSyncer.kt               # Background job that pulls Data Dragon + tips
│  │  ├─ vision/ScreenAnalyzer.kt          # ML Kit OCR + fuzzy match to build detailed game state
│  │  ├─ rules/TipEngine.kt, TipSession.kt # Prioritised proactive/corrective recommendations
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
3. **Run/Build** – `Build ▸ Make Project` or `./gradlew assembleDebug`. Install the resulting `app-debug.apk` via Studio, `adb`, or the device file manager. Narwhal 2024.2+ is fully supported.
4. **Grant permissions** – on first launch the prototype asks for overlay (bubble) and screen-capture consent. The capture flow is stubbed but the permissions wiring is ready.

The app boots even when the device is offline. When a network connection is available it downloads the latest JSON into `files/data/sets/live/`, then the repository prefers that cache without blocking the UI thread.

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

## What the in-game assistant does now

- **Understands more than colour blobs** – `ScreenAnalyzer` streams MediaProjection frames into ML Kit Text Recognition, fuzzy-matches champion/item names, and infers stage, level, gold, lãi, board/bench/shop units, augment offers, and deviations (ví dụ *LEVEL_BEHIND*, *ITEMS_UNUSED*, *NO_FRONTLINE*).
- **Generates step-by-step plans** – the new rule schema supports proactive actions (lên cấp, đi chợ, ghép đồ, chọn lõi) and instant corrective responses when bạn đi lệch hướng.
- **Adapts to phones & tablets** – the bubble layout is responsive, emphasises category/type labels, and keeps controls reachable in landscape/tablet split-screen scenarios.

The JSON inside `data/sets/15.19/` documents the schema so you can extend it with your own comps, items, or augment heuristics.

## Extending the starter

- **Vision** – the default `ScreenAnalyzer` already uses ML Kit text recognition + fuzzy string matching. You can plug in extra detectors (object recognition, minimap parsing) and store the results on `TipState` to unlock richer rules.
- **Rules** – edit `data/sets/15.19/tips-rules.json` to describe new matchers. The `TipEngine` understands stages, level/gold/interest ranges, board/bench/shop membership, deviation flags, carry focus, and augment requirements.
- **Data hosting** – publish the `data/` folder to a CDN and update `tipsRulesUrl` so the built-in sync job writes into `context.filesDir` before you call `TftRepository`.
- **Reports** – persist the `TipSession` objects produced by `TipEngine.evaluate` and export them as Markdown or JSON for after-action reviews.

## License

The underlying Data Dragon content remains © Riot Games. Kotlin sources in this starter follow the original repository’s Apache 2.0 license.
