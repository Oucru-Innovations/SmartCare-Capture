# Module — UI / Recording

> **Tier 3.** UI module: recording screen, file manager, popups, event annotation, adapters, and app initialization.
> For details on each function, see [Tier 4 — functions-ui-recording.md](../4-functions/functions-ui-recording.md).

**8 classes:** `RecordActivity`, `FileManagerActivity`, `ShowAnnotate`, `AnnotationHistory`*, `PatientInfoPopup`, `ChunkFileConfigPopup`, `CodeHistoryAdapter`, `SystemBarInsetsHelper`, `SmartCareApplication`.

> *`AnnotationHistory` is listed in detail under the [Network/Upload module](module-network-upload.md) based on analysis, but essentially belongs to the UI layer. It is treated as UI here since it only operates on local JSON files.

---

## 1. `RecordActivity`
`app/.../RecordActivity.java` — **The primary screen** (~1900 lines). Connects to the BLE oximeter, displays real-time PPG waves, HR, SpO₂, and battery, pushes samples to `OximeterDataService` for CSV writing, manages BLE scans/reconnects, gates patient codes, and controls session life cycles (splitting CSV on BLE pause vs. stopping sessions entirely).

- **Notable Static States:** `MacAddress` (active connected MAC), `isRecordingSessionActive` (app-wide session active state), `annotationHeartRate`/`annotationSpO2` (latest vitals used for annotations).
- **Function Groups:**
  - **Lifecycle:** `onCreate`, `onStop`, `onDestroy`, option menu callbacks (`onCreateOptionsMenu`, `onOptionsItemSelected`).
  - **Permissions:** `checkAppPermissions`, `onRequestPermissionsResult`, `requestBluetoothPermissions`.
  - **Connection/Session:** `connectToPulseOximeter`, `ensureServiceAndConnect`, `stopDisconnectExit`, `persistRecordingSessionState`, `splitCsvOnBlePause`, `restoreRecordingSessionFromPrefsIfNeeded`, `reconnectToService`.
  - **UI:** `initialiseViews`, `initialisePlot`, `togglePlotVisibility`, `checkAndRequestPatientCode`.
  - **BLE Scanning:** `initializeBluetoothScanning`, `startBluetoothBackgroundScan`, `scheduleAutoReconnect`, `showConnectedDevicesPopup`.
- **Inner Classes:** `SmartCareHandler` (receives Messages from SDK — see [flow-02](../2-architecture/flow-02-bluetooth-connection.md#3-sdk-messages)), `Connector`/`Disconnector` (AsyncTask for connection/disconnection), `serviceConnection` (binds the background service).

---

## 2. `FileManagerActivity`
`app/.../FileManagerActivity.java` — Two-tab file browser (**pending** / **uploaded**) reading from `Download/OximeterData`. Handles storage permissions, SAF fallback, MediaStore observer (live updates during recording), manual uploads, and open/delete actions.

- **Function Groups:** lifecycle (`onCreate/onResume/onPause/onDestroy/onActivityResult`), files (`refreshFileLists`, `openCsvFile`, `deleteUploadedFile`), storage (`ensureStorageAccessThenLoad`, `openOximeterDataFolderPicker`).
- **Inner Classes:** `CsvFileAdapter` (pending), `UploadedFileAdapter` (uploaded), `CsvFile` (wrapper around `FileEntry`).
- Detailed flow: [flow-06](../2-architecture/flow-06-file-management.md).

---

## 3. `ShowAnnotate`
`app/.../ShowAnnotate.java` — Collects clinical notes during measurements. Displays a structured dialog, saves notes in `Annotations.json`, exports individual JSON notes for upload, and appends current HR/SpO₂.
- **Primary Methods:** `showAnnotateMenu`, `showAnnotationHistory`, `updateAnnotation`, `deleteAnnotation`.
- **Inner Class:** `AnnotationEntry` (model: timestamp, patientCode, note, heartRate, spO2). Details: [flow-04](../2-architecture/flow-04-annotation.md).

---

## 4. `PatientInfoPopup`
`app/.../PatientInfoPopup.java` — Popup for inputting/selecting the **research code** (along with location/serial). Persists in `PatientInfoPrefs`, keeps up to 5 recent codes, and **locks the app** until a valid code is entered on launch.
- **Primary Methods:** `show`, `setCancelable`, `setOnPatientInfoSavedListener`, `isCodeSaved`, `clearSavedCode`, `getSavedPatientInfo`.
- **Inner Classes:** `OnPatientInfoSavedListener`, `PatientInfo` (researchCode/locationCode/serialNumber), `TextChangeListener`.

---

## 5. `ChunkFileConfigPopup`
`app/.../ChunkFileConfigPopup.java` — Configuration popup to toggle and set the splitting interval for StreamModel chunk files. Persisted in `ChunkFileConfigPrefs`; values are forwarded to `OximeterDataService` upon connection.
- **Primary Methods:** `show`, `isChunkFileEnabled` (default false), `getTimeInterval` (default 3 minutes).

---

## 6. `CodeHistoryAdapter`
`app/.../CodeHistoryAdapter.java` — RecyclerView adapter for the list of recent patient codes in `PatientInfoPopup`. Supports click-to-fill and row removal.
- **Primary Methods:** `onCreateViewHolder`, `onBindViewHolder`, `updateCodes`, `removeCode`.
- **Inner Classes/Interfaces:** `OnCodeClickListener`, `OnCodeDeleteListener`, `CodeViewHolder`.

---

## 7. `SystemBarInsetsHelper`
`app/.../SystemBarInsetsHelper.java` — Utility layout padder based on status bar / navigation bar / cutout to prevent UI obstruction.
- **Methods:** `static apply(Activity)`. Called from `SmartCareApplication.onActivityPostCreated`.

---

## 8. `SmartCareApplication`
`app/.../SmartCareApplication.java` — Application singleton. Initializes `FileLogger`, `FileUploader`, global crash handler, activity lifecycle logs, tracks foreground/background state, applies system bar insets, configures the daily 17:00 upload schedule, and diagnostics for low-memory events.
- **Primary Methods:** `onCreate`, `onTrimMemory`, `onLowMemory`, `installCrashHandler` (private).
- Details: [flow-01](../2-architecture/flow-01-app-startup.md).

---

## Interaction Diagram

```mermaid
flowchart TB
    App[SmartCareApplication] -->|inset + logging| RA[RecordActivity]
    RA -->|patient code| PIP[PatientInfoPopup]
    PIP --> CHA[CodeHistoryAdapter]
    RA -->|annotation| SA[ShowAnnotate]
    RA -->|chunk config| CFC[ChunkFileConfigPopup]
    RA -->|menu| FMA[FileManagerActivity]
    RA -->|BLE packets| H[SmartCareHandler]
    H -->|write CSV| Svc[OximeterDataService]
    RA -->|connect/disconnect| Conn[Connector / Disconnector]
```

![UI Recording Module Diagram](../../vi/3-modules/module-ui-recording.png)
