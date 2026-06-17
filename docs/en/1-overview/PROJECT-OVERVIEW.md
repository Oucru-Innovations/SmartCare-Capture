# Tier 1 — Project Overview

> **Goal of this document:** Help newcomers (developers, hand-over engineers, technical researchers) understand **what this app is used for** in about 10 minutes of reading, before diving deeper into the architecture and code.
>
> This is **Tier 1** of the 4-tier documentation. See [`docs/README.md`](../README.md) to understand the complete structure.

---

## 1. What is this app?

**SmartCare Capture** is an **Android application** used in **clinical research** with the following tasks:

1. **Connect** to the **SmartCare finger pulse oximeter** via **Bluetooth (BLE)**.
2. **Continuously collect** physiological signals: **SpO₂** (oxygen saturation), **heart rate (HR)**, **PPG waveform** (pleth), **perfusion index (PI)**, and the device's **battery level**.
3. **Write** data to a **CSV file** on the device's local storage (via MediaStore, inside the `Download/OximeterData` directory).
4. **Upload** CSV files to the research **server** (OUCRU or Nepal depending on the study).
5. Allow staff to add **annotations** for events during measurement (e.g., coughing, movement, clinical notes).

In short: **the app turns a Bluetooth oximeter into a long-term data collection device, preventing data loss, and automatically uploading data to the server.**

---

## 2. The problem it solves

In research, patients need to be monitored **continuously from several hours to multiple days**. Specific requirements include:

| Requirement | Why it is important |
|---|---|
| **No data loss** | Patient data cannot be re-collected. Losing a segment = ruining the research data. |
| **Continuous background execution** | Must record even when the screen is off, the app is in the background, or terminated by the system. |
| **Survive BLE disconnections** | Bluetooth often drops; the app must automatically reconnect and resume recording without manual intervention. |
| **Auto upload with retry** | Network connectivity in the field can be unstable; upload must automatically retry without blocking the recording process. |
| **Easy hardware replacement** | When the manufacturer changes the SDK/device model, we should only need to modify one place without rewriting the entire app. |

The entire architecture (which looks more complex than a typical app) exists to meet the 5 requirements above. Keep this in mind when reading subsequent tiers: **"complexity here is intentional, to ensure patient data is never lost."**

---

## 3. Who uses the app?

- **Field researchers / nurses:** attach the oximeter to the patient, enter the **research code (patient code)**, press start recording, occasionally add annotations, then stop/trigger upload.
- **Technicians / hand-over developers:** build, configure servers for each study, monitor logs, and troubleshoot issues.

---

## 4. A typical session

```
1. Open app  ──►  Mandatorily enter Patient/Research code
2. Press Start  ──►  App scans & connects to the oximeter via BLE
3. App displays real-time PPG wave + HR + SpO₂ + battery level
4. Data is recorded in the background to a CSV file (even when the screen is off)
5. (Optional) Add annotations when events occur
6. When finished: press Stop  ──►  File is closed and pushed into the upload queue
7. App automatically uploads to the server (with retries). Uploaded files are moved to the Uploaded/ folder
8. Daily at 17:00: a background task automatically cleans up and uploads remaining files
```

---

## 5. Core concepts (glossary)

| Term | Meaning in this project |
|---|---|
| **Oximeter / pulse oximeter** | A device measuring SpO₂ + heart rate via the fingertip; here it is the **SmartCare** device connected via **BLE**. |
| **SpO₂** | Oxygen saturation level in blood (%). |
| **HR (Heart Rate)** | Heart rate (bpm). |
| **PPG / pleth** | Photoplethysmography waveform — the undulating wave displayed on the screen. |
| **Perfusion index (PI)** | Perfusion index, reflecting the strength of the pulse signal. |
| **Research code / Patient code** | Identifier for the patient/recording session. **Mandatory** before starting a record. |
| **Session** | A single continuous data collection run. Can last several hours and is **split** into multiple CSV files. |
| **Segment / split** | A child CSV file. The app automatically **splits files** periodically (default is 6 hours) or at 17:00 to limit damage in case of errors. |
| **Annotation** | Clinical event notes recorded during measurement, saved as JSON. |
| **Foreground service** | An Android background service with a persistent notification to ensure recording is not interrupted. |
| **Wake lock** | A mechanism keeping the CPU awake when the screen is off to maintain recording. |
| **MediaStore** | Modern Android file storage API (Scoped Storage); file URIs usually look like `content://...`. |
| **Adapter (device)** | An intermediate layer isolating the manufacturer's SDK, allowing hardware changes by modifying only one file. |

---

## 6. Tech stack

| Category | Selection |
|---|---|
| Platform | Android — **minSdk 29 (Android 10)**, **targetSdk 35 (Android 14)** |
| Language | Java |
| Device SDK | `scolib-2.0.0.aar` (manufacturer's library, located at `app/libs/`) |
| Graphing | AndroidPlot `androidplot-core:0.6.1` |
| Signal Processing | jDSP `0.2.0` (detrending) |
| Network / upload | Retrofit `2.9.0` + OkHttp `4.9.0` |
| Periodic background tasks | AndroidX WorkManager `2.7.1` |
| File storage | Android **MediaStore** (Scoped Storage) |

---

## 7. Quick setup & build

> This section is merged from the old Deployment document — sufficient for a new developer to run the app. For detailed environment specifications, see Section 8.

**Requirements:** Android Studio (latest version), JDK 8 or 11, Android SDK (API 29 or higher, recommended API 35).

```bash
# 1. Clone
git clone https://github.com/Oucru-Innovations/SmartCare-Capture.git
cd SmartCare-Capture

# 2. Create/edit local.properties (this file is git-ignored)
#    sdk.dir=<path to Android SDK>
#    API_TOKEN=<your token>

# 3. Build debug
./gradlew assembleDebug      # output: app/build/outputs/apk/debug/app-debug.apk

# 4. Build release
./gradlew assembleRelease    # requires keystore (see Section 8)
./gradlew bundleRelease      # creates .aab for Play Store
```

In Android Studio: `File → Open` project folder → Sync Gradle → select device (enable USB debugging) → Run.

---

## 8. Configuration & server

**Principle:** All environment variables (server URL, token) are **not hardcoded** in business logic but flow through:

```
local.properties (git-ignored)
        │  API_TOKEN, BASE_URL  (declared in app/build.gradle)
        ▼
   BuildConfig.*          ← generated at build time
        ▼
ApiConfig (BASE_URL, API_TOKEN)      NetworkConfig (timeouts, MAX_RETRY=3, RETRY_DELAY_MS=10s)
        ▼
RetrofitClient / FileUploader
```

**Configuration by study** (change server only by changing build variables, without modifying code):

| Study | Server | Notes |
|---|---|---|
| 54, 39 | OUCRU (`http://vital-ei.oucru.org/` by default) | Running, ready |
| 62 | Nepal | Need to change `BASE_URL` before deploying |
| 24c (legacy) | — | Re-use old APK on `android-12-branch` |

**Sensitive files — DO NOT commit:** `local.properties`, `keystore.properties`, `*.keystore`, `*.jks`.

---

## 9. Two hardware types — important note

There are **two types of SmartCare devices** with different compatibility constraints (these are **hardware constraints, not bugs**):

1. **Silver label** — device with a **silver information sticker** on the back → **only runs stably on Android ≤ 10**. Connect errors may occur on newer Android versions.
2. **Printed label** — information is **printed directly** on the back → **compatible with all Android versions**.

Before using, check the Android version of the phone and grant sufficient permissions (Bluetooth, Location, Notifications).

---

## 10. Required permissions

- **Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` on API 31+; legacy Bluetooth permissions on lower versions)
- **Location** (mandatory for BLE scanning)
- **Storage** (read files for manual upload on API 29–32)
- **Notifications** (`POST_NOTIFICATIONS` on API 33+ — for the foreground service)
- **Battery optimization exemption** (request to exclude from battery optimization for long-term background execution)

---

## 11. Where to go next

| You want to... | Read tier |
|---|---|
| Understand **how the system runs**, data flows | **Tier 2** — [`docs/2-architecture/`](../2-architecture/ARCHITECTURE-OVERVIEW.md) |
| Know **which file/class a feature is located in** | **Tier 3** — [`docs/3-modules/`](../3-modules/MODULES-INDEX.md) |
| Look up **details of each function** | **Tier 4** — [`docs/4-functions/`](../4-functions/) |
