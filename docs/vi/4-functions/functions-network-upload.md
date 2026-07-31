# Functions — Network / Upload

> **Tầng 4.** Tra cứu chi tiết hàm cho module Network/Upload. Tổng quan ở
> [Tầng 3 — module-network-upload.md](../3-modules/module-network-upload.md).

---

## ApiService (Retrofit interface)
`app/src/main/java/com/smartcare/oximetry/sample/network/ApiService.java`

| Method | Mô tả |
|---|---|
| `Call<UploadResponse> uploadFile(@Part MultipartBody.Part file, @Part("owner") RequestBody owner)` | `POST api/v1/file/upload` — gửi phần file + trường `owner`; trả `UploadResponse`. |

---

## RetrofitClient
`app/src/main/java/com/smartcare/oximetry/sample/network/RetrofitClient.java`

| Method | Mô tả |
|---|---|
| `getInstance()` (static) | Trả (hoặc tạo lười) `Retrofit` dùng `ApiConfig.BASE_URL` + timeout `NetworkConfig` (connect 15s, read/write 20s). |

---

## UploadResponse (DTO)
`app/src/main/java/com/smartcare/oximetry/sample/network/UploadResponse.java`

| Method | Mô tả |
|---|---|
| `getMessage()` / `setMessage(String)` | Message server (success khi `"File uploaded successfully!"` hoặc `"File uploaded successfully"`). |
| `getFile()` / `setFile(FileInfo)` | Metadata file lồng nhau. |
| `FileInfo.getId()/setId`, `FileInfo.getFilename()/setFilename` | ID + tên file server gán. |

---

## ApiConfig
`app/src/main/java/com/smartcare/oximetry/sample/config/ApiConfig.java`

| Thành viên | Mô tả |
|---|---|
| `BASE_URL` (static final) | Từ `BuildConfig.BASE_URL` — mặc định `http://vital-ei.oucru.org/`. |
| `API_TOKEN` (static final) | Từ `BuildConfig.API_TOKEN` (`local.properties`), mặc định `YOUR_TOKEN_HERE`. Hiện **không gửi** trên call active. |

---

## NetworkConfig
`app/src/main/java/com/smartcare/oximetry/sample/config/NetworkConfig.java`

| Thành viên | Giá trị | Dùng bởi |
|---|---|---|
| `CONNECT_TIMEOUT_MS` | 15.000 | RetrofitClient |
| `READ_TIMEOUT_MS` | 20.000 | RetrofitClient |
| `WRITE_TIMEOUT_MS` | 20.000 | RetrofitClient |
| `MAX_RETRY` | 3 | **Chỉ tài liệu** (coordinator định nghĩa lại `MAX_ATTEMPTS=3`) |
| `RETRY_DELAY_MS` | 10.000 | **Chỉ tài liệu** (coordinator định nghĩa lại) |

---

## FileUploader
`app/src/main/java/com/smartcare/oximetry/sample/FileUploader.java`

| Method | Mô tả |
|---|---|
| `setAppContext(Context)` (static) | Lưu app context cho upload nền/content-URI. |
| `moveFileToUploadedFolder(File)` (static) | Đổi tên file vào `Uploaded/` cùng cấp + cập nhật mtime. |
| `moveFileToUploadedFolderFromUri(Context, String uriPath)` (static) | Copy URI MediaStore → `Download/OximeterData/Uploaded`, xóa nguồn, gọi `AppRecordingRegistry.markUploaded()`. |
| `uploadFile(Activity, String, UploadCallback)` (static) | Upload có Toast; xử lý `content://` qua temp cache. Ủy quyền overload với `uploadFileName=null` (dùng tên local/display). |
| `uploadFile(Activity, String filePath, String uploadFileName, UploadCallback)` (static) | Như trên, nhưng tên multipart có thể khác tên trên đĩa (dùng cho upload báo lỗi). `uploadFileName` rỗng/`null` → fallback tên local/display. |
| `uploadFile(String, UploadCallback)` (static) | Upload nền (không Toast); cần `setAppContext()` trước. |
| `calculateMD5(Context, String)` (static) | MD5 hex cho content URI/path (tính nhưng không gửi trên API active). |
| `getBaseUrl()` (static) | Trả `ApiConfig.BASE_URL`. |

**Inner:** `UploadCallback { void onUploadComplete(boolean success, String message); }`.
**Chi tiết:** owner hardcode `"Vyyy"`; **không tự retry**; đích sau success `Download/OximeterData/Uploaded`.

---

## NetworkHelper
`app/src/main/java/com/smartcare/oximetry/sample/NetworkHelper.java`

| Method | Mô tả |
|---|---|
| `isNetworkAvailable(Context)` (static) | True nếu có mạng internet (API 23+ `NetworkCapabilities`, legacy `NetworkInfo`); false nếu thiếu context/cm/network. |

---

## PendingFileUploadCoordinator
`app/src/main/java/com/smartcare/oximetry/sample/PendingFileUploadCoordinator.java`

| Method (static) | Mô tả |
|---|---|
| `uploadSessionFile(Context, String fileUri)` | Queue upload 1 file (không Activity/Toast). |
| `uploadSessionFile(Context, Activity, String fileUri)` | Như trên có Toast; **bỏ qua** nếu URI == `getCurrentOpenFileUriString()`. |
| `uploadAllPending(Context)` | Duyệt `OximeterDataFileIndex.queryPendingFiles()`, bỏ file đang ghi/rỗng, queue từng file; trả số đã queue. |
| `shouldSkipPendingEntry(...)` (private) | Bỏ file đang ghi; bỏ `*_unknown` 0 byte; cho phép unknown không rỗng. |

**Chính sách retry:** `MAX_ATTEMPTS=3` (đếm theo URI trong `ConcurrentHashMap`), `RETRY_DELAY_MS=10000`
(`Handler.postDelayed`), `IN_FLIGHT` set chống upload trùng, kiểm tra `NetworkHelper`, không upload file đang ghi,
thành công → `FileUploader.moveFileToUploadedFolderFromUri()`. Sự kiện log: `UPLOAD_SKIP_OPEN_FILE`,
`PENDING_BATCH_QUEUED`, `UPLOAD_WAIT_NETWORK`…

---

## UploadAnnotationWorker
`app/src/main/java/com/smartcare/oximetry/sample/UploadAnnotationWorker.java`

| Method | Mô tả |
|---|---|
| `UploadAnnotationWorker(Context, WorkerParameters)` | Constructor WorkManager chuẩn. |
| `doWork()` | (1) lần đầu (`getRunAttemptCount()==0`): broadcast `ACTION_SPLIT_RECORDING` + sleep 3s; (2) `uploadAllPending()`; (3) `scheduleDailyUpload()` cho ngày mai; trả `Result.success()`. |

---

## AnnotationUploadScheduler
`app/src/main/java/com/smartcare/oximetry/sample/AnnotationUploadScheduler.java`

| Method | Mô tả |
|---|---|
| `scheduleDailyUpload(Context)` (static) | Tính delay tới **17:00:00.000** local kế tiếp (sang mai nếu đã qua), enqueue unique work `smartcare_daily_17h_maintenance` (`ExistingWorkPolicy.REPLACE`, `NetworkType.CONNECTED`). |

**Điểm khởi tạo:** `SmartCareApplication.onCreate()`, `RecordActivity.onCreate()`, và chính `UploadAnnotationWorker.doWork()` (nối tiếp).

---

## FileLogger
`app/src/main/java/com/smartcare/oximetry/sample/FileLogger.java`

Log bền vững dưới `{externalFilesDir}/Logs`: một file (hoặc nhiều part) mỗi ngày theo lịch tablet.

| Method (static) | Mô tả |
|---|---|
| `getInstance(Context)` | Init singleton; chạy purge log ngày hết hạn. |
| `writeLog(LogLevel, tag, message)` | Lõi append-to-file kèm timestamp. |
| `v/d/i/w(String tag, String message)` | Ghi theo mức. |
| `e(tag, message)` / `e(tag, message, Throwable)` | ERROR (kèm stack trace). |
| `logEvent(category, event, detail)` / `(... , Context)` | Dòng INFO có cấu trúc (bản context kèm snapshot RAM). |
| `logWarningEvent(...)` | Dòng WARN có cấu trúc. |
| `appendUserReportToFile(File logFile, String detail, Context)` | Append dòng `USER`/`BUG_REPORT` vào một file log cụ thể (trước khi upload). |
| `logCrash(Thread, Throwable)` | Khối crash nhiều dòng + `flushToDisk()`. |
| `flushToDisk()` | Ép sync file (crash/critical/bug-report). |
| `isInitialized()` | Singleton/context sẵn sàng chưa. |
| `getFilteredLogs(LogLevel min)` | Đọc log hiện tại lọc theo mức tối thiểu. |
| `listAvailableLogFiles()` | Liệt kê file log theo format ngày dạng `LogFileInfo` (mới nhất trước); `displayName` có thể kèm `(today)` và kích thước KB. |
| `getLogFilePath()` | Path file log đang dùng. |
| `formatBugReportUploadTimestamp(File)` / `(File, long uploadTimeMs)` | Tạo `dd.MM.yyyy.HH.mm.ss` từ ngày trong tên file log + giờ phút giây lúc upload. |
| `forceCleanup()` | Chạy purge retention (`purgeExpiredLogs`) — xóa log ngày cũ hơn `RETENTION_DAYS`. |

**Enum:** `LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }`.
**Inner:** `LogFileInfo` (`file`, `displayName`, `toString()` → displayName) — cho picker báo lỗi.
**Config:** `MAX_FILE_SIZE_BYTES=30MB` mỗi part; `RETENTION_DAYS=15`; đặt tên theo ngày
`smartcare_app_log_dd.MM.yyyy.txt`, thêm `_N` khi một ngày vượt 30MB (vd
`smartcare_app_log_25.06.2025_2.txt`). File legacy không khớp pattern không bị đổi tên hay xóa. Day rollover / part
rotation log `LOG_DAY_ROLLOVER` / `LOG_FILE_PART_ROTATION`.

---

## AnnotationHistory
`app/src/main/java/com/smartcare/oximetry/sample/AnnotationHistory.java`

| Method | Mô tả |
|---|---|
| `showAnnotationHistory(Context, String patientCode)` (static) | Hiện `AlertDialog` bảng ghi chú theo `patientCode`, mới nhất trước; chạm dòng → sửa/xóa. |

**Private:** `loadAnnotations`, `showAnnotationOptions`, `showEditDialog`, `showDeleteConfirmation`, `getAnnotationFile`
(thao tác JSON cục bộ qua `ShowAnnotate`). **Lưu trữ:** `{Downloads}/OximeterData/Annotation/Annotations.json`.
**Lưu ý:** đây là UI JSON cục bộ, **không** thuộc stack HTTP upload.
