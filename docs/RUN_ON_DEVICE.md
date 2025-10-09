# Run the TFT Bubbles Assistant on Android

This guide walks through installing the starter bubble assistant on a phone or tablet. The project is a single Android application module (`app/`) that bundles sample TFT data under `app/src/main/assets/` and exposes a bubble-style UI for quick tips.

## 1. Prerequisites

- **Android Studio Hedgehog (2023.1.1) or newer** with the Android Gradle Plugin 8.2+.
- **Android SDK Platform 34** and Build-Tools 34.0.0 (install via the SDK Manager if missing).
- **JDK 17** (bundled with recent Android Studio builds).
- A device or emulator running **Android 13 (API 33)** or newer for the full bubble experience. The project still compiles against API 34 and runs on API 26+, but bubbles are best supported on Android 11+.
- USB cable (for physical deployment) or a configured emulator.

## 2. Clone and open the project

1. `git clone` (or download) the repository and ensure the `app/src/main/assets/data/sets/15.19/` folder exists. The JSON samples are already included.
2. Launch Android Studio and choose **Open an Existing Project**, selecting the repository root.
3. Wait for Gradle sync to finish. Accept prompts to download the Gradle wrapper if requested.

## 3. Build an APK

- From Android Studio: **Build ▸ Make Project**. The debug APK will land at `app/build/outputs/apk/debug/app-debug.apk`.
- From the command line: `./gradlew assembleDebug`.

Use `Build ▸ Generate Signed App Bundle / APK` later when you want a release build.

## 4. Install on a device

1. Enable **Developer options** and **USB debugging** on the phone/tablet.
2. Connect via USB (or enable wireless debugging).
3. Click **Run** in Android Studio and choose the device. Alternatively, install manually:
   ```bash
   ./gradlew installDebug
   # or
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Look for the app icon labelled “TFT Bubbles Assistant”.

## 5. Grant permissions on first launch

The prototype needs two approvals before it can analyse the screen:

- **Display over other apps** – required for bubbles. The app links to the system overlay settings page. Flip the toggle and return.
- **Screen capture consent** – launching the capture request opens Android’s `MediaProjection` dialog. Choose *Start now* to allow streaming the screen into the foreground service.

A persistent notification appears while capture is active. Use it to stop the assistant when you are done testing.

## 6. Using the bubble

1. After permissions are granted, tap **Start assistant**. A chat-style bubble appears on the edge of the screen.
2. Tap the bubble to open the tip panel. The assistant ranks proactive vs. corrective steps (lên cấp, ghép đồ, chọn lõi) from `tips-rules.json`; dùng **Gợi ý kế tiếp** để duyệt các bước tiếp theo.
3. Drag the bubble to a corner so it doesn’t obstruct TFT gameplay. Use **Ẩn đến hết round** to collapse the panel until the next tip update.

## 7. Updating data & logic

- Riot data now refreshes itself on launch. Bump `ddragonVersion` inside `gradle.properties` when a new patch lands so the runtime downloader pulls the latest champions/items/traits/augments payloads into `files/data/sets/live/`.
- Point `tipsRulesUrl` at a hosted `tips-rules.json` (GitHub raw, S3, etc.) if you want the bubble advice to change without shipping a new APK. Leave it blank to rely on the bundled offline sample.
- Expand `ScreenAnalyzer` with extra detectors (minimap, quân địch). Nó đã sử dụng ML Kit Text Recognition + fuzzy match để xác định stage, level, vàng, đội hình, kho đồ và augment.
- Thêm điều kiện mới trong `tips-rules.json` nếu bạn muốn bắt thêm trạng thái: engine hiện hỗ trợ stage, khoảng level/vàng/lãi, thành phần bench/board/shop, augment, carry, và các cờ sai lệch (`LEVEL_BEHIND`, `ITEMS_UNUSED`, ...).

Following this checklist produces an installable APK that demonstrates the full permission + bubble flow, ready for you to plug in production-grade analysis and guidance.
