# Functions — Data / Storage

> **Tier 4.** Specific function reference for the Data/Storage module. Overview in [Tier 3 — module-data-storage.md](../3-modules/module-data-storage.md).

---

## OximeterDataBuffer
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataBuffer.java`

**Constants:** `BUFFER_SIZE = 100` (samples per batch).

| Method | Description |
|---|---|
| `OximeterDataBuffer()` | Creates empty queue, processing flags, and pre-sized batch list. |
| `addDataPoint(double spo2, int spo2Status, long redAdc, double battery, int hr, long irAdc, double pi, int counter, String mac, long pkgRecv, int pleth, long ts)` | Enqueues a raw sample and triggers queue processing. |
| `flushRemaining()` | Drains the queue + writes incomplete batches (< 100 samples) before closing the segment. |
| `processQueueIfNeeded()` (private) | Drains the queue under the `AtomicBoolean isProcessing` lock. |
| `writeCurrentBatch()` (private) | When 100 samples are accumulated: calculates median HR/SpO₂, packages `ProcessedData`, and invokes the writer. |
| `calculateMedian(List<? extends Number>)` (private) | Sorts and returns the middle element (rounded). |
| `convertBatteryToPercentage(...)` (private) | Converts 3300–4150 mV → 0–100% (ceiling). |

**Inner Classes:** `DataPoint` (raw, private), `ProcessedData` (public: `timestamp`, `deviceId`, `battery`, `hr`, `o2`, `spo2Status`, `pleth` List<Integer>, `red`/`ir` List<Long>, `perfusion` List<Double>).
> Note: `addDataPoint` accepts a `timestamp` parameter, but `DataPoint` currently uses `packageReceived` as its timestamp — the `timestamp` parameter is not yet used.

---

## OximeterDataWriterFunctions
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataWriterFunctions.java`

**Constants/Keys:** `FOLDER_NAME="OximeterData"`, `STREAM_MODEL_FOLDER="StreamModel"`, `DEFAULT_SPLIT_INTERVAL=RecordingConstants.SPLIT_INTERVAL_MS`, `MIN_AUTO_SPLIT_MS=RecordingConstants.MIN_SEGMENT_MS`, `SPLIT_LOCK` (synchronization monitor).
**CSV Columns:** `timestamp, device_id, battery, hr, o2, spo2_status, pleth, red, ir, perfusion`.
**File Name:** `SmartCareCsv_{patientCode}_{startStamp}_{endStamp}.csv` (timestamp format: `dd.MM.yyyy.HH.mm.ss`; open file uses `unknown`).

| Method (all static) | Description |
|---|---|
| `initialize(Context)` | Stores application context for MediaStore access. |
| `setActivity(Activity)` | Retains a weak reference to the Activity (optional UI coupling). |
| `setSplitInterval(int hours)` | Sets the splitting interval (in hours) and recalculates `nextSplitTime`. |
| `isRecordingActive()` | Returns true if there is an active writer and open URI. |
| `getCurrentOpenFileUriString()` | Returns the URI of the currently open segment, or null. |
| `getRecordingSegmentDurationMs()` | Duration in ms elapsed since `startTimestamp`. |
| `splitFileOnBlePause()` | Splits file on BLE pause; returns completed display name or null. |
| `getFileNameFromUri(Uri)` | Resolves display name from MediaStore for the given URI. |
| `startNewRecording(long pkgTs, String patient, boolean chunk, int interval)` | Starts a session: closes previous segment, cleans orphan `_unknown` files, opens new `_unknown` CSV, and flushes headers immediately. |
| `writeProcessedDataPoint(OximeterDataBuffer.ProcessedData)` | Writes 1 row; triggers time-based split if needed; buffers chunks optionally. |
| `splitForDailyCutoff()` | Closes current file + opens new segment at the 17:00 cutoff. |
| `endRecording()` | Completes session + renames segment; returns URI string or null. |
| `getOximeterDataPath()` | Absolute path to `Download/OximeterData`. |
| `getStreamModelPath()` | Absolute path to `Download/OximeterData/StreamModel`. |
| `formatTimestamp(long)` | Formats epoch ms → `dd.MM.yyyy.HH.mm.ss`. |
| `splitFileLocked(...)` (private) | Central splitting logic: closes writer, renames file, registers with registry/index, opens new segment. |

---

## OximeterDataFileIndex
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataFileIndex.java`

**Constants:** `FOLDER_NAME="OximeterData"`, `FOLDER_NAME_TYPO="OximetaData"`, `UPLOADED_SUBDIR="Uploaded"`, `PREFIX_SMARTCARE="SmartCareCsv"`, `PREFIX_ANNOTATION_EXPORT="Annotation_"`, `MASTER_ANNOTATIONS_FILE="Annotations.json"`.
**Inner Class:** `FileEntry` (`uri`, `displayName`, `sizeBytes`, `lastModifiedMs`).

| Method (all static) | Description |
|---|---|
| `isAppDataFileName(String)` | Returns true for `SmartCareCsv*.csv` or `Annotation_*.json` (excluding the master). |
| `queryPendingFiles(Context)` | Merges multiple sources to query files **not** inside `Uploaded/`. |
| `queryUploadedFiles(Context)` | Same as above, querying files inside the `Uploaded/` subdirectory. |
| `refreshMediaStore(Context)` / `refreshMediaStoreDeep(Context)` | Performs a deep MediaStore scan for app files. |
| `tryDiskEntryByDisplayName(Context, String)` | Finds a file by name among pending/uploaded/typo directories. |
| `lookupDownloadEntry(Context, Uri)` | Resolves MediaStore metadata if the entry is app data. |
| `notifyUriUpdated(Context, Uri)` | Calls `ContentResolver.notifyChange`. |
| `notifyRecordingSaved(Context, Uri, String)` | Notifies MediaStore + registers in `AppRecordingRegistry` + refreshes. |
| `getPendingDirectory()` / `getUploadedDirectory()` | Returns `File` references for the two directories. |

---

## OximeterDataTreeAccess
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataTreeAccess.java`

**Constants (private):** `PREFS="oximeter_data_saf_tree"`, `KEY_TREE_URI="tree_uri"`.

| Method (static) | Description |
|---|---|
| `getPersistedTreeUri(Context)` | Returns the persisted SAF tree URI, or null. |
| `persistTreeUri(Context, Uri)` | Persists the tree URI after user directory approval. |
| `clearPersistedTreeUri(Context)` | Clears the persisted tree URI. |

---

## RecordingSessionPrefs
`app/src/main/java/com/smartcare/oximetry/sample/RecordingSessionPrefs.java`

**Prefs file:** `"recording_session_prefs"`. **Keys:** `KEY_ACTIVE`, `KEY_PATIENT`, `KEY_CHUNK`, `KEY_INTERVAL`, `KEY_COLLECTION_START`.
**Inner Class:** `Session` (`active`, `patientCode`, `chunkEnabled`, `chunkInterval`, `collectionStartMs`).

| Method (static) | Description |
|---|---|
| `markActive(Context, String patient, boolean chunk, int interval, long startMs)` | Writes all fields and sets active=true. |
| `clear(Context)` | Clears all session preferences. |
| `isActive(Context)` | Quick check on the active flag. |
| `load(Context)` | Loads session snapshot (default: inactive, chunkInterval=3). |

---

## RecordingConstants
`app/src/main/java/com/smartcare/oximetry/sample/RecordingConstants.java`

| Constant | Value | Purpose |
|---|---|---|
| `SPLIT_INTERVAL_HOURS` | 6 | Interval (in hours) between automatic CSV splits |
| `SPLIT_INTERVAL_MS` | 21,600,000 | Same as above, in milliseconds — used by writer |
| `MIN_SEGMENT_MS` | 60,000 | Minimum segment duration before it can be split |

This class only contains constants (private constructor, no methods).

---

## AppRecordingRegistry
`app/src/main/java/com/smartcare/oximetry/sample/AppRecordingRegistry.java`

**Constants (private):** `PREFS="app_recording_registry"`, `KEY_ENTRIES="entries"` (JSON array `{uri, displayName, savedAt}`).

| Method (static) | Description |
|---|---|
| `registerCompletedRecording(Context, Uri, String)` | Upserts a completed record (called during rename/split/end). |
| `markUploaded(Context, String uriStr)` | Removes the entry after a successful upload. |
| `queryRegistryPending(Context)` | Returns pending files from the registry; prunes stale URIs, falls back to disk by name. |

---

## MemoryDiagnostics
`app/src/main/java/com/smartcare/oximetry/sample/MemoryDiagnostics.java`

| Method (static) | Description |
|---|---|
| `formatDeviceSummary(Context)` | Returns `"device={manufacturer} {model} (API {sdk}) RAM_installed={MB}"`. |
| `formatSnapshot(Context)` | Returns RAM avail/total/threshold/lowMemory + heap used/max (MB). |
| `trimLevelName(int)` | Maps `ComponentCallbacks2` trim level → human-readable string. |
