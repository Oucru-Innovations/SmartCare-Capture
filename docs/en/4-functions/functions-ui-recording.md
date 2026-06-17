# Functions — UI / Recording

> **Tier 4.** Specific function reference per class for the UI/Recording module. Overview in [Tier 3 — module-ui-recording.md](../3-modules/module-ui-recording.md).
> Convention: `(static)` = static method; `(private)` = local/private but critical to understanding the flow.

---

## RecordActivity
`app/src/main/java/com/smartcare/oximetry/sample/RecordActivity.java`

### Lifecycle & Menu
| Method | Description |
|---|---|
| `onCreate(Bundle)` | Inflates UI, configures plot, prompts patient code popup, starts BLE scan, binds `OximeterDataService` and `SmartCareHandler`; forces patient code entry before unlocking controls. |
| `onResume()` | Standard resume lifecycle event (currently empty). |
| `onStop()` | Logs screen stop (excluding configuration changes). |
| `onDestroy()` | Cleans up handler, BLE receivers, auto-reconnect schedules, and interceptors; unbinds or retains service depending on `isRecordingSessionActive`. |
| `onCreateOptionsMenu(Menu)` | Inflates `R.menu.main_menu`. |
| `onOptionsItemSelected(MenuItem)` | Routes to device popup, FileManager, chunk configuration, or annotation history. |

### Permissions
| Method | Description |
|---|---|
| `checkAppPermissions()` | Checks/requests Bluetooth (API dependent), Location, and `POST_NOTIFICATIONS` (API 33+) permissions; returns true if granted. |
| `onRequestPermissionsResult(...)` | Handles `REQUEST_STORAGE_PERMISSION` and `REQUEST_REQUIRED_PERMISSIONS` (auto-connects if permissions are granted). |
| `requestBluetoothPermissions()` (private) | Requests `BLUETOOTH_SCAN`/`CONNECT` (API 31+) or legacy Bluetooth permissions. |
| `requestReadStorageForExportsIfNeeded()` (private) | Requests `READ_EXTERNAL_STORAGE` on API levels 29–32. |

### Connection / Session
| Method | Description |
|---|---|
| `connectToPulseOximeter()` (private) | Entry point when user clicks Start; checks permissions then calls `ensureServiceAndConnect(null)`. |
| `ensureServiceAndConnect(String)` (private) | Starts/binds service if needed, then executes `Connector` with optional MAC. |
| `stopDisconnectExit()` (private) | User Stop — ends the entire session (`endEntireSession=true`). |
| `stopDisconnectExit(boolean)` (private) | Executes `Disconnector`; `false` = only disconnects BLE (24h session continues), `true` = terminates session. |
| `persistRecordingSessionState()` (private) | Persists active session state to `RecordingSessionPrefs`. |
| `splitCsvOnBlePause()` (private) | Flushes buffer + splits CSV when BLE drops while session is active. |
| `restoreRecordingSessionFromPrefsIfNeeded()` (private) | Restores interrupted session after patient code is loaded. |
| `bindRecordingServiceIfNeeded()` (private) | Re-binds service for restored session. |
| `recoverWriterAfterServiceBind()` (private) | Defers writer reopening until next data packet (avoids empty CSVs). |
| `reconnectToService()` (private) | Re-binds service if the handler detects the service is not ready. |

### UI
| Method | Description |
|---|---|
| `initialiseViews()` (private) | Binds plot views, HR/SpO₂/battery labels, record button, and plot visibility toggle. |
| `initialisePlot()` / `initialiseSeries()` (private) | Configures AndroidPlot pleth series. |
| `togglePlotVisibility()` (private) | Toggles PPG plot visibility. |
| `initializePatientInfo()` / `updatePatientInfoDisplay()` (private) | Constructs `PatientInfoPopup`, updates research code layout. |
| `checkAndRequestPatientCode()` (private) | Non-cancellable launch popup; locks record/annotate until code is entered. |

### Bluetooth Discovery & Reconnect
| Method | Description |
|---|---|
| `initializeBluetoothScanning()` (private) | Registers receivers for `ACTION_FOUND`/`ACL_DISCONNECTED`; auto-connects queued devices. |
| `startBluetoothBackgroundScan()` (private) | Triggers periodic 5s scanning (after patient code confirmation). |
| `scheduleAutoReconnect(String, long)` (private) | Reconnection loop: first attempt at 30s / subsequent at 3 minutes. |
| `stopAutoReconnect()` (private) | Cancels the reconnection runnable. |
| `showConnectedDevicesPopup()` (private) | Dialog to view/rename/delete saved devices & scan/connect new ones. |

### Inner Class: `SmartCareHandler extends Handler`
Weak-ref handler receiving messages from `ConnectionManager`/`OximeterSDKAdapter`.
- `handleMessage(Message)` — dispatches based on `msg.what`. List of messages: see [flow-02 section 3](../2-architecture/flow-02-bluetooth-connection.md#3-sdk-messages).

### Inner Class: `Connector extends AsyncTask`
| Method | Description |
|---|---|
| `doInBackground(...)` | Cancels BLE discovery; calls `connectionManager.connect()`/`connect(mac)`. |
| `onPostExecute(Boolean)` | Success: calls `startRecording()`, updates MAC, sets button Stop → `stopDisconnectExit()`; failure: resets button to Start. |

### Inner Class: `Disconnector extends AsyncTask`
| Method | Description |
|---|---|
| `doInBackground(...)` | Calls `connectionManager.disconnect()`. |
| `onPostExecute(Boolean)` | Partial disconnect: schedules reconnection, retains session. Full disconnect: clears prefs, stops service asynchronously, triggers uploads, resets UI. |
| `finishEndEntireSessionUi(...)` (private) | Finalizes UI after CSV is closed; schedules uploads. |

### Inner Class: `serviceConnection` (anonymous ServiceConnection)
- `onServiceConnected(...)` — binds background service; runs pending `Connector`. `onServiceDisconnected(...)` — marks service disconnected during active recording.

---

## FileManagerActivity
`app/src/main/java/com/smartcare/oximetry/sample/FileManagerActivity.java`

| Method | Description |
|---|---|
| `onCreate(Bundle)` | Configures tabs, list adapters, triggers storage check + initial load. |
| `onResume()` / `onPause()` | Registers/unregisters `MediaStore.Downloads` observer (throttled refresh if >1.2s). |
| `onDestroy()` | Shuts down executor, removes SAF tree callback. |
| `onActivityResult(...)` | Stores persisted SAF tree URI from folder picker. |
| `onRequestPermissionsResult(...)` | Re-queries MediaStore after `READ_EXTERNAL_STORAGE` is granted. |
| `onBackPressed()` | Returns to `RecordActivity`. |
| `refreshFileLists()` / `refreshFileListsQuiet()` (private) | Background query via `OximeterDataFileIndex`; updates the two tabs. |
| `openCsvFile(CsvFile)` (private) | Opens CSV via `ACTION_VIEW` + FileProvider/content URI. |
| `deleteUploadedFile(CsvFile, int)` (private) | Confirms and deletes the selected uploaded file entry. |
| `getOximeterDataPath()` | Returns directory path of pending files. |
| `ensureStorageAccessThenLoad()` (private) | Loads files + requests storage permission on API 29–32. |
| `openOximeterDataFolderPicker()` (private) | SAF tree fallback picker (`ACTION_OPEN_DOCUMENT_TREE`). |

**Inner Classes:** `CsvFileAdapter` (pending list, upload button → `PendingFileUploadCoordinator.uploadSessionFile`), `UploadedFileAdapter` (uploaded list, delete button), `CsvFile` (wrapper around `FileEntry`: `getName`, `getUploadPath`, `getContentUri`, `delete`, `getFormattedSize/Date`).

---

## ShowAnnotate
`app/src/main/java/com/smartcare/oximetry/sample/ShowAnnotate.java`

| Method | Description |
|---|---|
| `ShowAnnotate(Context, String patientCode)` | MONITORS context and sets active patient/research code. |
| `showAnnotateMenu(View)` | Opens structured "Add Note" dialog with validation and context-sensitive fields. |
| `showAnnotationHistory(View)` | Delegates to `AnnotationHistory.showAnnotationHistory`. |
| `updateAnnotation(int, String)` / `updateAnnotationByEntry(...)` | Edits note by index / by entry. |
| `deleteAnnotation(int)` / `deleteAnnotation(AnnotationEntry)` | Deletes note. |
| `startAnnotationSession/stopAnnotationSession/stopAllSessions` (static) | No-op (legacy JSON API). |
| `handleAnnotation()` (private) | Loads/writes JSON, enforces 5 notes limit, writes `Annotation_{code}_{ts}_.json`, uploads via `FileUploader`. |

**Inner Class:** `AnnotationEntry` (`timestamp`, `patientCode`, `note`, `heartRate`, `spO2` + `setNote`).

---

## PatientInfoPopup
`app/src/main/java/com/smartcare/oximetry/sample/PatientInfoPopup.java`

| Method | Description |
|---|---|
| `PatientInfoPopup(Context)` | Loads preferences, tracks first-run states. |
| `show(View)` | Inflates popup, blurs background, shows recent code history dropdown; re-opens if dismissed without valid entry. |
| `setCancelable(boolean)` | Toggles close validation (set to `false` on launch). |
| `setOnPatientInfoSavedListener(...)` | Callback invoked upon valid info submission. |
| `isCodeSaved()` / `isPatientCodeEmpty()` | Checks if patient code is configured. |
| `clearSavedCode()` / `resetPatientCode()` | Clears active patient code. |
| `requestEdit()` | Flags request for code modification. |
| `getSavedPatientInfo()` | Returns `PatientInfo` struct from preferences. |

**Inner Classes:** `OnPatientInfoSavedListener`, `PatientInfo` (researchCode/locationCode/serialNumber), `TextChangeListener`.

---

## ChunkFileConfigPopup
`app/src/main/java/com/smartcare/oximetry/sample/ChunkFileConfigPopup.java`

| Method | Description |
|---|---|
| `ChunkFileConfigPopup(Context)` | Opens `ChunkFileConfigPrefs`. |
| `show(View)` | Displays toggle switch + interval input; saves on submit. |
| `isChunkFileEnabled()` | Toggle flag (default `false`). |
| `getTimeInterval()` | Interval in minutes (default `3`). |

---

## CodeHistoryAdapter
`app/src/main/java/com/smartcare/oximetry/sample/CodeHistoryAdapter.java`

| Method | Description |
|---|---|
| `CodeHistoryAdapter(List<String>, OnCodeClickListener, OnCodeDeleteListener)` | Binds history entries + callbacks. |
| `onCreateViewHolder/onBindViewHolder` | Inflates `item_code_history`; binds click/delete. |
| `getItemCount()` | History size. |
| `updateCodes(List<String>)` | Replaces history + refreshes. |
| `removeCode(int)` | Deletes entry with slide animation. |

**Inner Classes/Interfaces:** `OnCodeClickListener`, `OnCodeDeleteListener`, `CodeViewHolder`.

---

## SystemBarInsetsHelper
`app/src/main/java/com/smartcare/oximetry/sample/SystemBarInsetsHelper.java`

| Method | Description |
|---|---|
| `apply(Activity)` (static) | Disables `decorFitsSystemWindows`, applies status/navigation/cutout insets as root layout padding; offsets top inset if action bar exists. |

---

## SmartCareApplication
`app/src/main/java/com/smartcare/oximetry/sample/SmartCareApplication.java`

| Method | Description |
|---|---|
| `onCreate()` | Initializes logging, crash handler, lifecycle callbacks, logs process start, daily upload schedule. |
| `onTrimMemory(int)` | Logs memory pressure warnings when level ≥ `TRIM_MEMORY_RUNNING_CRITICAL`. |
| `onLowMemory()` | Logs system-wide low memory event summaries. |
| `onTerminate()` | Logs terminate (mostly emulator/test context). |
| `installCrashHandler()` (private) | Wraps default uncaught exception handler to flush logs before rethrow. |

**Inner Class:** `ActivityLifecycleCallbacks` (logs created/started/stopped/resumed/paused/destroyed; `onActivityPostCreated` applies system bar insets).
