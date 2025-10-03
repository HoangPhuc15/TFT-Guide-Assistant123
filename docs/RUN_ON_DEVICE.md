# Running the TFT Guide Assistant on Android phones and tablets

This repository now contains a prototype Android application (`app/`) plus a
floating bubble overlay library (`FloatingBubbleView/`).  The code compiles into
a regular Android APK that can be installed on physical phones and tablets
(Android 8.0/Oreo or newer).  The steps below walk through preparing the data
files, building the project, installing the APK, and granting the runtime
permissions required for screen capture and overlay guidance.

## 1. Prerequisites

- **Android Studio Hedgehog or newer** with the Android Gradle Plugin 8.2+
  (bundled with Studio).
- **Android SDK Platform 34** and build tools (install from the Android Studio
  SDK Manager if they are not present yet).
- **Java 17** runtime (handled automatically by recent Android Studio builds).
- A physical Android device or tablet running **Android 8.0 (API 26)** or newer
  with Google Play services (needed for ML Kit's on-device text recognition).
- A USB cable (for physical deployment) or an emulator image if you only need to
  verify compilation.

> **Tip:** If you prefer command-line builds, install Gradle 8.2+ and run
> `gradle wrapper` once from the repository root so the `gradlew` bootstrapper
> is created with a local copy of `gradle-wrapper.jar`.

## 2. Pull the TFT Data Dragon payloads (set 9 / 9.5)

1. Open a terminal inside the repository root.
2. Run the helper script (network access required):

   ```bash
   python tools/fetch_tft_data.py
   ```

   The script populates `previous/set9/` and `previous/set9update/` with the
   latest JSON files (`tft-champions.json`, `tft-items.json`, etc.) that the app
   expects when generating guidance.

   If you already have the files downloaded elsewhere, copy them into those
   folders instead.

## 3. Import the project in Android Studio

1. Launch Android Studio and choose **Open an Existing Project**.
2. Select the repository root (`TFT-Guide-Assistant123`).
3. Allow Android Studio to perform the initial Gradle sync.
   - When prompted to upgrade or download the Gradle wrapper files, accept the
     recommendation so that `./gradlew` works on your machine.
   - Confirm that both modules (`app` and `FloatingBubbleView`) appear in the
     **Gradle** tool window.

Once the sync completes, you can build the APK via **Build ▸ Make Project** or
from the command line:

```bash
./gradlew assembleDebug
```

The debug APK is produced at
`app/build/outputs/apk/debug/app-debug.apk`.

## 4. Deploy to a physical phone or tablet

1. Enable **Developer options** and **USB debugging** on the device.
2. Connect it to your development machine via USB (or use wireless debugging).
3. In Android Studio, click the **Run** button and choose the connected device.
   - Alternatively, install from the command line:

     ```bash
     ./gradlew installDebug
     ```

     or

     ```bash
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```

4. Wait for the app (`TFT Guide Assistant`) to appear on the device launcher.

## 5. Grant runtime permissions and start the overlay

The assistant relies on two special capabilities:

1. **Display over other apps** (SYSTEM_ALERT_WINDOW) to render the floating
   guide bubble.
2. **Screen capture** via `MediaProjection` to read the TFT board in real time.

On first launch the main activity will guide you through these grants:

- Tap **Request overlay permission** and switch the toggle for the app in the
  system settings page that opens. Navigate back to the app once granted.
- Tap **Request screen capture** and accept the capture prompt. The service runs
  in the foreground (with a notification) while capture is active.

After both permissions are granted, press **Start assistant**. A movable bubble
appears; tap it to expand/collapse the guidance panel. Drag the bubble to a
corner so it does not obstruct TFT gameplay.

## 6. Tablet-specific notes

- Tablets generally require the same steps as phones. The overlay is responsive
  and can be dragged anywhere along the larger display.
- If the system forces the app into freeform/resizable mode, ensure it remains
  in full-screen while playing TFT so the capture resolution matches the
  expected aspect ratio.
- For desktop-class Android tablets (e.g., Samsung DeX), keep the guide bubble
  near the edge of the TFT window to avoid accidental clicks.

## 7. Keeping data and builds up to date

- Re-run `python tools/fetch_tft_data.py` whenever Riot releases a new set or
  balance patch to refresh the JSON payloads.
- Use **Build ▸ Clean Project** followed by **Build ▸ Rebuild Project** if Gradle
  caches become stale after SDK updates.
- When distributing to testers, create a signed release APK via **Build ▸ Generate
  Signed App Bundle / APK** and register your keystore credentials.

Following the checklist above will give you a working TFT assistant app on both
phones and tablets, ready for further development of the in-game guidance logic.
