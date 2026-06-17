# Functions — Network / Upload

> **Tier 4.** Specific function reference for the Network/Upload module. Overview in [Tier 3 — module-network-upload.md](../3-modules/module-network-upload.md).

---

## ApiService (Retrofit interface)
`app/src/main/java/com/smartcare/oximetry/sample/network/ApiService.java`

| Method | Description |
|---|---|
| `Call<UploadResponse> uploadFile(@Part MultipartBody.Part file, @Part("owner") RequestBody owner)` | `POST api/v1/file/upload` — sends the file part and the `owner` field; returns `UploadResponse`. |

---

## RetrofitClient
`app/src/main/java/com/smartcare/oximetry/sample/network/RetrofitClient.java`

| Method | Description |
|---|---|
| `getInstance()` (static) | Returns (lazily creates) `Retrofit` using `ApiConfig.BASE_URL` and `NetworkConfig` timeouts (connect 15s, read/write 20s). |

---

## UploadResponse (DTO)
`app/src/main/java/com/smartcare/oximetry/sample/network/UploadResponse.java`

| Method | Description |
|---|---|
| `getMessage()` / `setMessage(String)` | Monitored server response message (success determined when message is `"File uploaded successfully!"` or `"File uploaded successfully"`). |
| `getFile()` / `setFile(FileInfo)` | Nested file metadata object. |
| `FileInfo.getId()/setId`, `FileInfo.getFilename()/setFilename` | Server-assigned file ID and filename. |

---

## ApiConfig
`app/src/main/java/com/smartcare/oximetry/sample/config/ApiConfig.java`

| Field | Description |
|---|---|
| `BASE_URL` (static final) | From `BuildConfig.BASE_URL` — defaults to `http://vital-ei.oucru.org/`. |
| `API_TOKEN` (static final) | From `BuildConfig.API_TOKEN` (`local.properties`), defaults to `YOUR_TOKEN_HERE`. Currently **not sent** in active calls. |

---

## NetworkConfig
`app/src/main/java/com/smartcare/oximetry/sample/config/NetworkConfig.java`

| Field | Value | Used by |
|---|---|---|
| `CONNECT_TIMEOUT_MS` | 15,000 | RetrofitClient |
| `READ_TIMEOUT_MS` | 20,000 | RetrofitClient |
| `WRITE_TIMEOUT_MS` | 20,000 | RetrofitClient |
| `MAX_RETRY` | 3 | **Documentation only** (coordinator redefines locally as `MAX_ATTEMPTS=3`) |
| `RETRY_DELAY_MS` | 10,000 | **Documentation only** (coordinator redefines locally) |

---

## FileUploader
`app/src/main/java/com/smartcare/oximetry/sample/FileUploader.java`

| Method | Description |
|---|---|
| `setAppContext(Context)` (static) | Saves app context for background uploads/content-URIs. |
| `moveFileToUploadedFolder(File)` (static) | Renames file into the `Uploaded/` subdirectory and updates its mtime. |
| `moveFileToUploadedFolderFromUri(Context, String uriPath)` (static) | Copies MediaStore URI → `Download/OximeterData/Uploaded`, deletes the source, and invokes `AppRecordingRegistry.markUploaded()`. |
| `uploadFile(Activity, String, UploadCallback)` (static) | Uploads with a Toast notification; handles `content://` URIs using a temporary cache. |
| `uploadFile(String, UploadCallback)` (static) | Background upload (no Toast); requires `setAppContext()` to be called first. |
| `calculateMD5(Context, String)` (static) | MD5 hex hash for content URI/path (calculated but not sent in current active API calls). |
| `getBaseUrl()` (static) | Returns `ApiConfig.BASE_URL`. |

**Inner Interface:** `UploadCallback { void onUploadComplete(boolean success, String message); }`.
**Details:** The `owner` parameter is hardcoded to `"Vyyy"`; **does not retry internally** (retries are handled by the caller); successfully uploaded destination is `Download/OximeterData/Uploaded`.

---

## NetworkHelper
`app/src/main/java/com/smartcare/oximetry/sample/NetworkHelper.java`

| Method | Description |
|---|---|
| `isNetworkAvailable(Context)` (static) | Returns true if an internet connection is available (uses `NetworkCapabilities` on API 23+, legacy `NetworkInfo` on lower versions); false if context, ConnectivityManager, or network is missing. |

---

## PendingFileUploadCoordinator
`app/src/main/java/com/smartcare/oximetry/sample/PendingFileUploadCoordinator.java`

| Method (static) | Description |
|---|---|
| `uploadSessionFile(Context, String fileUri)` | Queues upload for a single file (no Activity/Toast). |
| `uploadSessionFile(Context, Activity, String fileUri)` | Same as above with Toast notifications; **skips** if URI matches `getCurrentOpenFileUriString()`. |
| `uploadAllPending(Context)` | Iterates through `OximeterDataFileIndex.queryPendingFiles()`, skips active/empty files, queues each file; returns count of queued files. |
| `shouldSkipPendingEntry(...)` (private) | Skips currently active files; skips 0-byte `*_unknown` files; allows non-empty unknown files. |

**Retry Policies:** `MAX_ATTEMPTS=3` (tracked per URI in `ConcurrentHashMap`), `RETRY_DELAY_MS=10000` (`Handler.postDelayed`), `IN_FLIGHT` set to block duplicate uploads, checks connectivity via `NetworkHelper`, skips active files, and moves on success via `FileUploader.moveFileToUploadedFolderFromUri()`. Diagnostic log events: `UPLOAD_SKIP_OPEN_FILE`, `PENDING_BATCH_QUEUED`, `UPLOAD_WAIT_NETWORK`...

---

## UploadAnnotationWorker
`app/src/main/java/com/smartcare/oximetry/sample/UploadAnnotationWorker.java`

| Method | Description |
|---|---|
| `UploadAnnotationWorker(Context, WorkerParameters)` | Standard WorkManager constructor. |
| `doWork()` | (1) First run (`getRunAttemptCount() == 0`): broadcasts `ACTION_SPLIT_RECORDING` + sleeps 3s; (2) calls `uploadAllPending()`; (3) calls `scheduleDailyUpload()` for tomorrow; returns `Result.success()`. |

---

## AnnotationUploadScheduler
`app/src/main/java/com/smartcare/oximetry/sample/AnnotationUploadScheduler.java`

| Method | Description |
|---|---|
| `scheduleDailyUpload(Context)` (static) | Calculates delay to the next **17:00:00.000** local time (shifts to tomorrow if passed), enqueues unique work `smartcare_daily_17h_maintenance` (using `ExistingWorkPolicy.REPLACE`, `NetworkType.CONNECTED`). |

**Initialization entry points:** `SmartCareApplication.onCreate()`, `RecordActivity.onCreate()`, and `UploadAnnotationWorker.doWork()` (self-chaining).

---

## FileLogger
`app/src/main/java/com/smartcare/oximetry/sample/FileLogger.java`

| Method (static) | Description |
|---|---|
| `getInstance(Context)` | Initializes singleton; triggers cleanup of old log files. |
| `writeLog(LogLevel, tag, message)` | Core append-to-file logic with timestamps. |
| `v/d/i/w(String tag, String message)` | Logs message at corresponding level. |
| `e(tag, message)` / `e(tag, message, Throwable)` | ERROR level log (includes stack trace). |
| `logEvent(category, event, detail)` / `(... , Context)` | Structured INFO event (context version includes RAM snapshot). |
| `logWarningEvent(...)` | Structured WARN event. |
| `logCrash(Thread, Throwable)` | Multi-line crash block + `flushToDisk()`. |
| `flushToDisk()` | Forces file sync to disk (for crash/critical logs). |
| `isInitialized()` | Checks if singleton/context is ready. |
| `getFilteredLogs(LogLevel min)` | Reads current log file filtered by minimum LogLevel. |
| `getLogFilePath()` | Returns path of active log file. |
| `forceCleanup()` | Manually deletes old logs (keeps most recent archives). |

**Enum:** `LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }`. **Configurations:** `MAX_FILE_SIZE_BYTES=10MB`; rolls over to archive files matching `smartcare_app_log_{yyyyMMdd_HHmmss}.txt`.

---

## AnnotationHistory
`app/src/main/java/com/smartcare/oximetry/sample/AnnotationHistory.java`

| Method | Description |
|---|---|
| `showAnnotationHistory(Context, String patientCode)` (static) | Displays an `AlertDialog` with a table of notes filtered by `patientCode`, ordered newest first; clicking a row triggers edit/delete dialogs. |

**Private Methods:** `loadAnnotations`, `showAnnotationOptions`, `showEditDialog`, `showDeleteConfirmation`, `getAnnotationFile` (manipulates local JSON via `ShowAnnotate`). **Storage Location:** `{Downloads}/OximeterData/Annotation/Annotations.json`.
**Note:** This is a local JSON viewer UI; it does **not** belong to the HTTP upload stack.
