# Functions — Data / Storage

> **Tầng 4.** Tra cứu chi tiết hàm cho module Data/Storage. Tổng quan ở
> [Tầng 3 — module-data-storage.md](../3-modules/module-data-storage.md).

---

## OximeterDataBuffer
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataBuffer.java`

**Hằng số:** `BUFFER_SIZE = 100` (số mẫu/lô).

| Method | Mô tả |
|---|---|
| `OximeterDataBuffer()` | Tạo queue rỗng, cờ xử lý, list lô pre-sized. |
| `addDataPoint(double spo2, int spo2Status, long redAdc, double battery, int hr, long irAdc, double pi, int counter, String mac, long pkgRecv, int pleth, long ts)` | Enqueue một mẫu thô và kích hoạt xử lý queue. |
| `flushRemaining()` | Drain queue + ghi lô lẻ (<100 mẫu) trước khi đóng segment. |
| `processQueueIfNeeded()` (private) | Drain queue dưới khóa `AtomicBoolean isProcessing`. |
| `writeCurrentBatch()` (private) | Khi đủ 100 mẫu: tính median HR/SpO₂, gói `ProcessedData`, gọi writer. |
| `calculateMedian(List<? extends Number>)` (private) | Sort + trả phần tử giữa (làm tròn). |
| `convertBatteryToPercentage(...)` (private) | 3300–4150 mV → 0–100% (ceiling). |

**Inner:** `DataPoint` (raw, private), `ProcessedData` (public: `timestamp`, `deviceId`, `battery`, `hr`, `o2`,
`spo2Status`, `pleth` List<Integer>, `red`/`ir` List<Long>, `perfusion` List<Double>).
> Lưu ý: `addDataPoint` nhận tham số `timestamp` nhưng `DataPoint` đang dùng `packageReceived` làm timestamp — tham số
> `timestamp` hiện chưa dùng tới.

---

## OximeterDataWriterFunctions
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataWriterFunctions.java`

**Hằng/khóa:** `FOLDER_NAME="OximeterData"`, `STREAM_MODEL_FOLDER="StreamModel"`,
`DEFAULT_SPLIT_INTERVAL=RecordingConstants.SPLIT_INTERVAL_MS`, `MIN_AUTO_SPLIT_MS=RecordingConstants.MIN_SEGMENT_MS`,
`SPLIT_LOCK` (monitor đồng bộ).
**Cột CSV:** `timestamp, device_id, battery, hr, o2, spo2_status, pleth, red, ir, perfusion`.
**Tên file:** `SmartCareCsv_{patientCode}_{startStamp}_{endStamp}.csv` (stamp `dd.MM.yyyy.HH.mm.ss`; mở dùng `unknown`).

| Method (đều static) | Mô tả |
|---|---|
| `initialize(Context)` | Lưu application context cho MediaStore. |
| `setActivity(Activity)` | Giữ weak ref Activity (coupling UI tùy chọn). |
| `setSplitInterval(int hours)` | Đặt chu kỳ cắt (giờ), tính lại `nextSplitTime`. |
| `isRecordingActive()` | Có writer + URI mở không. |
| `getCurrentOpenFileUriString()` | URI segment đang mở, hoặc null. |
| `getRecordingSegmentDurationMs()` | Ms đã trôi từ `startTimestamp`. |
| `splitFileOnBlePause()` | Cắt khi BLE pause; trả tên hiển thị hoàn tất hoặc null. |
| `getFileNameFromUri(Uri)` | Tên hiển thị MediaStore cho URI. |
| `startNewRecording(long pkgTs, String patient, boolean chunk, int interval)` | Bắt đầu phiên: đóng segment trước, dọn orphan `_unknown`, mở CSV `_unknown` + flush header ngay. |
| `writeProcessedDataPoint(OximeterDataBuffer.ProcessedData)` | Ghi 1 dòng; có thể trigger cắt theo thời gian; tùy chọn buffer chunk. |
| `splitForDailyCutoff()` | Đóng file hiện tại + mở segment mới tại mốc 17:00. |
| `endRecording()` | Hoàn tất + đổi tên segment; trả URI string hoặc null. |
| `getOximeterDataPath()` | Path tuyệt đối `Download/OximeterData`. |
| `getStreamModelPath()` | Path `Download/OximeterData/StreamModel`. |
| `formatTimestamp(long)` | Format epoch ms → `dd.MM.yyyy.HH.mm.ss`. |
| `splitFileLocked(...)` (private) | Lõi cắt: đóng writer, đổi tên, đăng ký registry/index, mở segment mới. |

---

## OximeterDataFileIndex
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataFileIndex.java`

**Hằng:** `FOLDER_NAME="OximeterData"`, `FOLDER_NAME_TYPO="OximetaData"`, `UPLOADED_SUBDIR="Uploaded"`,
`PREFIX_SMARTCARE="SmartCareCsv"`, `PREFIX_ANNOTATION_EXPORT="Annotation_"`, `MASTER_ANNOTATIONS_FILE="Annotations.json"`.
**Inner:** `FileEntry` (`uri`, `displayName`, `sizeBytes`, `lastModifiedMs`).

| Method (đều static) | Mô tả |
|---|---|
| `isAppDataFileName(String)` | True cho `SmartCareCsv*.csv` hoặc `Annotation_*.json` (trừ master). |
| `queryPendingFiles(Context)` | Gộp đa nguồn cho file **không** thuộc `Uploaded/`. |
| `queryUploadedFiles(Context)` | Tương tự cho cây `Uploaded/`. |
| `refreshMediaStore(Context)` / `refreshMediaStoreDeep(Context)` | Quét MediaStore sâu các file app. |
| `tryDiskEntryByDisplayName(Context, String)` | Tìm file theo tên từ pending/uploaded/typo. |
| `lookupDownloadEntry(Context, Uri)` | Nạp metadata cho hàng MediaStore nếu là app data. |
| `notifyUriUpdated(Context, Uri)` | `ContentResolver.notifyChange`. |
| `notifyRecordingSaved(Context, Uri, String)` | Notify MediaStore + đăng ký `AppRecordingRegistry` + refresh. |
| `getPendingDirectory()` / `getUploadedDirectory()` | `File` cho 2 thư mục. |

---

## OximeterDataTreeAccess
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataTreeAccess.java`

**Hằng (private):** `PREFS="oximeter_data_saf_tree"`, `KEY_TREE_URI="tree_uri"`.

| Method (static) | Mô tả |
|---|---|
| `getPersistedTreeUri(Context)` | Trả URI cây SAF đã lưu, hoặc null. |
| `persistTreeUri(Context, Uri)` | Lưu URI cây sau khi user cấp quyền thư mục. |
| `clearPersistedTreeUri(Context)` | Xóa URI cây đã lưu. |

---

## RecordingSessionPrefs
`app/src/main/java/com/smartcare/oximetry/sample/RecordingSessionPrefs.java`

**Prefs file:** `"recording_session_prefs"`. **Khóa:** `KEY_ACTIVE`, `KEY_PATIENT`, `KEY_CHUNK`, `KEY_INTERVAL`,
`KEY_COLLECTION_START`. **Inner:** `Session` (`active`, `patientCode`, `chunkEnabled`, `chunkInterval`, `collectionStartMs`).

| Method (static) | Mô tả |
|---|---|
| `markActive(Context, String patient, boolean chunk, int interval, long startMs)` | Ghi mọi field + active=true. |
| `clear(Context)` | Xóa toàn bộ session prefs. |
| `isActive(Context)` | Kiểm tra nhanh cờ active. |
| `load(Context)` | Nạp snapshot phiên (mặc định: inactive, chunkInterval=3). |

---

## RecordingConstants
`app/src/main/java/com/smartcare/oximetry/sample/RecordingConstants.java`

| Hằng số | Giá trị | Ý nghĩa |
|---|---|---|
| `SPLIT_INTERVAL_HOURS` | 6 | Giờ giữa các lần cắt CSV tự động |
| `SPLIT_INTERVAL_MS` | 21.600.000 | Như trên (ms) — dùng bởi writer |
| `MIN_SEGMENT_MS` | 60.000 | Segment tối thiểu trước khi cắt |

Class chỉ chứa hằng số (constructor private, không method).

---

## AppRecordingRegistry
`app/src/main/java/com/smartcare/oximetry/sample/AppRecordingRegistry.java`

**Hằng (private):** `PREFS="app_recording_registry"`, `KEY_ENTRIES="entries"` (JSON array `{uri, displayName, savedAt}`).

| Method (static) | Mô tả |
|---|---|
| `registerCompletedRecording(Context, Uri, String)` | Upsert một bản ghi hoàn tất (gọi khi rename/split/end). |
| `markUploaded(Context, String uriStr)` | Xóa entry sau khi upload thành công. |
| `queryRegistryPending(Context)` | Trả file pending từ registry; prune URI cũ, fallback disk theo tên. |

---

## MemoryDiagnostics
`app/src/main/java/com/smartcare/oximetry/sample/MemoryDiagnostics.java`

| Method (static) | Mô tả |
|---|---|
| `formatDeviceSummary(Context)` | `"device={hãng} {model} (API {sdk}) RAM_installed={MB}"`. |
| `formatSnapshot(Context)` | RAM avail/total/threshold/lowMemory + heap used/max (MB). |
| `trimLevelName(int)` | Map mức trim `ComponentCallbacks2` → tên dễ đọc. |
