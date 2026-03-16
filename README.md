# Local WiFi Debug

**Local WiFi Debug** is a specialized Android application (Kotlin) designed to automate the synchronization of the **Wireless Debugging** port on Android TV devices (like Chromecast with Google TV) with **Home Assistant**.

## Why this exists
The Android TV (ADB) Integration in Home Assistant often loses connection because:
1.  **Wireless Debugging is disabled on reboot** by the Android system.
2.  **The port is randomly assigned** each time Wireless Debugging is toggled on.

This app allows for a "one-click" sync that:
1.  Enables Wireless Debugging (if not already on).
2.  Discovers the current random port using mDNS.
3.  Reports that port to a Home Assistant webhook.

## Key Features
- **Zero-Touch Execution:** The app performs its task and closes itself immediately upon launch.
- **mDNS Service Discovery:** Uses `NsdManager` to find the `_adb-tls-connect._tcp.` service locally.
- **Quick Settings Tile:** Includes a "WiFi Debug Sync" tile for devices that support it.
- **Visual Feedback:** Provides system-level notifications and Toast messages on the TV.

## Requirements
- Android 11+ (Required for Wireless Debugging).
- ADB access to grant initial secure permissions.
- **Home Assistant Android TV Remote Integration:** Required to remotely launch the app on the TV.
- Home Assistant with an active Webhook automation.

## Building the APK

### Standard Environment
If you are building on a standard x86_64 machine:
```bash
./gradlew assembleDebug
```

### ARM64 Environment (e.g., Raspberry Pi)
If building on an ARM64 Linux host, you must provide a native `aapt2` binary at the project root as the standard SDK binary is x86 only:
1.  Place a native `aapt2` binary in the root directory.
2.  Run the build:
    ```bash
    export ANDROID_HOME=$HOME/android-sdk
    ./gradlew assembleDebug
    ```

## Installation

1.  Connect via ADB to your TV.
2.  Install the APK:
    ```bash
    adb install app/build/outputs/apk/debug/app-debug.apk
    ```
3.  **Grant Critical Permissions:**
    These permissions cannot be granted via the UI and must be applied via ADB:
    ```bash
    # Allow the app to toggle Wireless Debugging
    adb shell pm grant com.enuff.steven.localwifidebug android.permission.WRITE_SECURE_SETTINGS

    # Allow notifications (Android 13+)
    adb shell pm grant com.enuff.steven.localwifidebug android.permission.POST_NOTIFICATIONS
    ```

## Configuration
The following values are currently hardcoded in `MainActivity.kt` and `WiFiDebugTileService.kt`:
- **Webhook URL:** `http://192.168.50.200:8123/api/webhook/ccwgt_port`
- **Device ID:** `ccwgt`

## Home Assistant Integration

### 1. Webhook Automation
Create an automation in HA to handle the incoming port and trigger a reconnection:
```yaml
alias: "Update ADB Port for TV"
description: "Updates the ADB port when the WiFi Debug app reports a new one"
trigger:
  - platform: webhook
    webhook_id: ccwgt_port
    allowed_methods:
      - POST
    local_only: true
action:
  - service: shell_command.reconnect_adb_tv
    data:
      port: "{{ trigger.json.port }}"
```

### 2. Shell Command
Add this to your `configuration.yaml` to allow HA to reconnect to the new port:
```yaml
shell_command:
  reconnect_adb_tv: "adb connect <TV_IP_ADDRESS>:{{ port }}"
```
*(Note: Ensure the `adb` binary is installed and available to the Home Assistant process/container)*

### 3. Launch Script
Add this to `scripts.yaml` to trigger the sync from your HA dashboard. This uses the `android_tv_remote` integration to start the application:
```yaml
sync_local_wifi_debug:
  alias: "Sync Local WiFi Debug"
  sequence:
    - service: remote.turn_on
      target:
        entity_id: remote.steven_tv  # Entity from Android TV Remote integration
      data:
        activity: "com.enuff.steven.localwifidebug/.MainActivity"
```

## License
MIT

## Disclaimer
This project was coded using Gemini CLI. Your mileage may vary.
