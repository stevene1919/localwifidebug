# Local WiFi Debug Sync

This project is a native Android (Kotlin) application designed for Google/Android TV devices. It autonomously enables Wireless Debugging, discovers the assigned random port, and reports it to a Home Assistant webhook.

## Core Mandates
- **No Autonomous Sync:** Do NOT sync, push, or commit changes to GitHub unless specifically instructed by the user in the current turn.
- **No Autonomous Builds:** Do NOT run build commands (e.g., `./gradlew assembleDebug`, `gradle build`) unless specifically instructed by the user in the current turn.
- **Security & Secrets:** NEVER upload or commit secure information (keystores, passwords, API tokens, webhooks, or private keys) to GitHub. All sensitive credentials must remain in local, ignored files.

## Objective
Enable a "one-click" synchronization of the Android Wireless Debugging port to Home Assistant, allowing the HA Android TV integration (ADB) to reconnect without manual intervention when the port changes.

## App Details
- **App Name:** Local WiFi Debug Sync
- **Package ID:** `com.enuff.localwifidebug`
- **Main Activity:** `.MainActivity`
- **Webhook Endpoint:** Defined in `local.properties` as `WEBHOOK_URL`.
- **Device ID Reported:** `ccwgt`

## Key Features
- **Auto-Run on Launch:** The app immediately checks the status and sends the port upon launch.
- **Already-Enabled Logic:** If Wireless Debugging is already on, it skips the setup and just syncs the current port.
- **mDNS Discovery:** Uses `NsdManager` to listen for the `_adb-tls-connect._tcp.` service.
- **Visual Feedback:** Shows system notifications and Toast messages on the TV upon success/failure.
- **Auto-Exit:** The app closes itself (calls `finish()`) after a successful report to minimize UI intrusion.

## Installation & Deployment (via ADB)

### 1. Build the APK
Navigate to the project directory and run:
```bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$HOME/gradle/gradle-8.1.1/bin:$PATH
# Ensure the ARM64 AAPT2 patch is applied to the Gradle cache if building on ARM64
./gradlew assembleDebug
```

### 2. Connect & Install
```bash
adb connect 192.168.50.10:5555
adb install /opt/usb/steven/projects/localwifidebug/app/build/outputs/apk/debug/app-debug.apk
```

### 3. Critical Permissions (Manual Grant Required)
Run these commands once after installation to allow the app to function:
```bash
# Allow toggling system settings (Wireless Debugging)
adb shell pm grant com.enuff.localwifidebug android.permission.WRITE_SECURE_SETTINGS

# Allow showing notifications on Android 13+
adb shell pm grant com.enuff.localwifidebug android.permission.POST_NOTIFICATIONS
```

## Home Assistant Integration

### Script
Add this to your `scripts.yaml` to launch the app from HA:
```yaml
local_wifi_debug_sync:
  alias: "Local WiFi Debug Sync"
  description: "Launches the WiFi Debug app to report its random port to HA"
  sequence:
    - service: remote.turn_on
      target:
        entity_id: remote.steven_tv
      data:
        activity: "com.enuff.localwifidebug"
  mode: single
  icon: mdi:wifi-sync
```

### Automation Suggestion
The reported port can be used to update the `media_player` configuration or trigger an ADB reconnection service in Home Assistant.

## Technical Notes (ARM64 Build Environment)
Since this environment is ARM64, the standard Android SDK `aapt2` (x86_64) will fail.
- A native ARM64 `aapt2` binary is required at the project root: `localwifidebug/aapt2` (this binary is excluded from the git repository).
- During builds, this binary is patched into the Gradle cache: `~/.gradle/caches/transforms-3/.../transformed/aapt2-8.1.1-10154469-linux/aapt2`.
- `android.useAndroidX=true` and `android.enableJetifier=true` are required in `gradle.properties`.
- `android:usesCleartextTraffic="true"` is enabled in the manifest for local HTTP communication to Home Assistant.
