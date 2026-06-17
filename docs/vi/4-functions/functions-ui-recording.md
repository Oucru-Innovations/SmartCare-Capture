# Functions — UI / Recording

> **Tầng 4.** Tra cứu chi tiết hàm theo class cho module UI/Recording. Xem tổng quan module ở
> [Tầng 3 — module-ui-recording.md](../3-modules/module-ui-recording.md).
> Quy ước: `(static)` = method tĩnh; `(private)` = nội bộ nhưng quan trọng để hiểu luồng.

---

## RecordActivity
`app/src/main/java/com/smartcare/oximetry/sample/RecordActivity.java`

### Vòng đời & menu
| Method | Mô tả |
|---|---|
| `onCreate(Bundle)` | Inflate UI, dựng plot, popup mã BN, BLE scan, nối `OximeterDataService`, `SmartCareHandler`; buộc nhập mã BN trước khi mở khóa control. |
| `onResume()` | Resume chuẩn (hiện không có logic thêm). |
| `onStop()` | Log dừng màn hình (khi không phải config change). |
| `onDestroy()` | Dọn handler, BLE receiver, auto-reconnect, interceptor; unbind hoặc giữ service tùy `isRecordingSessionActive`. |
| `onCreateOptionsMenu(Menu)` | Inflate `R.menu.main_menu`. |
| `onOptionsItemSelected(MenuItem)` | Định tuyến tới popup thiết bị, FileManager, cấu hình chunk, lịch sử ghi chú. |

### Quyền
| Method | Mô tả |
|---|---|
| `checkAppPermissions()` | Kiểm tra/xin Bluetooth (theo API), Location, POST_NOTIFICATIONS (API 33+); true nếu đủ. |
| `onRequestPermissionsResult(...)` | Xử lý `REQUEST_STORAGE_PERMISSION` và `REQUEST_REQUIRED_PERMISSIONS` (auto-connect khi đủ quyền). |
| `requestBluetoothPermissions()` (private) | Xin `BLUETOOTH_SCAN`/`CONNECT` (API 31+) hoặc quyền Bluetooth cũ. |
| `requestReadStorageForExportsIfNeeded()` (private) | Xin `READ_EXTERNAL_STORAGE` trên API 29–32. |

### Kết nối / ngắt / phiên
| Method | Mô tả |
|---|---|
| `connectToPulseOximeter()` (private) | Điểm vào khi user Start; kiểm tra quyền rồi `ensureServiceAndConnect(null)`. |
| `ensureServiceAndConnect(String)` (private) | Start/bind service nếu cần, rồi chạy `Connector` với MAC tùy chọn. |
| `stopDisconnectExit()` (private) | User Stop — kết thúc cả phiên (`endEntireSession=true`). |
| `stopDisconnectExit(boolean)` (private) | Chạy `Disconnector`; `false`=chỉ ngắt BLE (24h tiếp tục), `true`=tắt hẳn. |
| `persistRecordingSessionState()` (private) | Lưu phiên active vào `RecordingSessionPrefs`. |
| `splitCsvOnBlePause()` (private) | Flush buffer + cắt CSV khi BLE rớt mà phiên còn active. |
| `restoreRecordingSessionFromPrefsIfNeeded()` (private) | Khôi phục phiên bị gián đoạn sau khi nạp lại mã BN. |
| `bindRecordingServiceIfNeeded()` (private) | Re-bind service cho phiên khôi phục. |
| `recoverWriterAfterServiceBind()` (private) | Hoãn mở lại writer tới gói dữ liệu kế (tránh file rỗng). |
| `reconnectToService()` (private) | Re-bind service khi handler phát hiện service không sẵn sàng. |

### UI
| Method | Mô tả |
|---|---|
| `initialiseViews()` (private) | Nối plot, nhãn HR/SpO₂/pin, nút record, toggle plot. |
| `initialisePlot()` / `initialiseSeries()` (private) | Cấu hình series pleth AndroidPlot. |
| `togglePlotVisibility()` (private) | Ẩn/hiện vùng đồ thị PPG. |
| `initializePatientInfo()` / `updatePatientInfoDisplay()` (private) | Dựng `PatientInfoPopup`, hiển thị research code. |
| `checkAndRequestPatientCode()` (private) | Popup khởi động không hủy được; khóa record/annotate tới khi có mã. |

### Bluetooth discovery & reconnect
| Method | Mô tả |
|---|---|
| `initializeBluetoothScanning()` (private) | Đăng ký receiver `ACTION_FOUND`/`ACL_DISCONNECTED`; auto-connect thiết bị trong hàng đợi. |
| `startBluetoothBackgroundScan()` (private) | Quét định kỳ 5s (sau khi xác nhận mã BN). |
| `scheduleAutoReconnect(String, long)` (private) | Vòng reconnect 30s đầu / 3 phút sau. |
| `stopAutoReconnect()` (private) | Hủy runnable reconnect. |
| `showConnectedDevicesPopup()` (private) | Dialog xem/đổi tên/xóa thiết bị đã lưu & quét/nối mới. |

### Inner: `SmartCareHandler extends Handler`
Weak-ref handler nhận message từ `ConnectionManager`/`OximeterSDKAdapter`.
- `handleMessage(Message)` — phân nhánh theo `msg.what`. Danh sách message: xem
  [flow-02 mục 3](../2-architecture/flow-02-bluetooth-connection.md#3-các-loại-message-từ-sdk--sdk-messages).

### Inner: `Connector extends AsyncTask`
| Method | Mô tả |
|---|---|
| `doInBackground(...)` | Hủy discovery; gọi `connectionManager.connect()`/`connect(mac)`. |
| `onPostExecute(Boolean)` | Thành công: `startRecording()`, cập nhật MAC, gán Stop → `stopDisconnectExit()`; thất bại: về Start. |

### Inner: `Disconnector extends AsyncTask`
| Method | Mô tả |
|---|---|
| `doInBackground(...)` | Gọi `connectionManager.disconnect()`. |
| `onPostExecute(Boolean)` | Partial: lên lịch reconnect, giữ phiên. Full: xóa prefs, tắt service async, đẩy upload, reset UI. |
| `finishEndEntireSessionUi(...)` (private) | Hoàn tất UI sau khi CSV đóng; lên lịch upload. |

### Inner: `serviceConnection` (anonymous ServiceConnection)
- `onServiceConnected(...)` — bind service; chạy `Connector` đang chờ. `onServiceDisconnected(...)` — đánh dấu service chết khi đang ghi.

---

## FileManagerActivity
`app/src/main/java/com/smartcare/oximetry/sample/FileManagerActivity.java`

| Method | Mô tả |
|---|---|
| `onCreate(Bundle)` | Dựng tab, adapter, trigger truy cập storage + load lần đầu. |
| `onResume()` / `onPause()` | Đăng ký/hủy observer `MediaStore.Downloads` (refresh nhẹ nếu >1.2s). |
| `onDestroy()` | Tắt executor, gỡ callback SAF. |
| `onActivityResult(...)` | Lưu URI cây SAF từ folder picker. |
| `onRequestPermissionsResult(...)` | Query lại MediaStore sau khi cấp `READ_EXTERNAL_STORAGE`. |
| `onBackPressed()` | Về `RecordActivity`. |
| `refreshFileLists()` / `refreshFileListsQuiet()` (private) | Query nền qua `OximeterDataFileIndex`; cập nhật 2 tab. |
| `openCsvFile(CsvFile)` (private) | Mở file qua `ACTION_VIEW` + FileProvider/content URI. |
| `deleteUploadedFile(CsvFile, int)` (private) | Xác nhận + xóa mục đã upload. |
| `getOximeterDataPath()` | Trả path thư mục pending. |
| `ensureStorageAccessThenLoad()` (private) | Load file + xin quyền đọc trên API 29–32. |
| `openOximeterDataFolderPicker()` (private) | Fallback SAF `ACTION_OPEN_DOCUMENT_TREE`. |

**Inner:** `CsvFileAdapter` (pending, nút upload → `PendingFileUploadCoordinator.uploadSessionFile`),
`UploadedFileAdapter` (uploaded, nút xóa), `CsvFile` (`getName`, `getUploadPath`, `getContentUri`, `delete`,
`getFormattedSize/Date`).

---

## ShowAnnotate
`app/src/main/java/com/smartcare/oximetry/sample/ShowAnnotate.java`

| Method | Mô tả |
|---|---|
| `ShowAnnotate(Context, String patientCode)` | Gắn context + research code. |
| `showAnnotateMenu(View)` | Dialog "Add Note" với validate + trường lý do theo điều kiện. |
| `showAnnotationHistory(View)` | Ủy quyền cho `AnnotationHistory.showAnnotationHistory`. |
| `updateAnnotation(int, String)` / `updateAnnotationByEntry(...)` | Sửa ghi chú theo index / theo entry. |
| `deleteAnnotation(int)` / `deleteAnnotation(AnnotationEntry)` | Xóa ghi chú. |
| `startAnnotationSession/stopAnnotationSession/stopAllSessions` (static) | No-op (legacy API JSON). |
| `handleAnnotation()` (private) | Nạp/ghi JSON, giới hạn 5 note/BN, ghi `Annotation_{code}_{ts}_.json`, upload qua `FileUploader`. |

**Inner:** `AnnotationEntry` (`timestamp`, `patientCode`, `note`, `heartRate`, `spO2` + `setNote`).

---

## PatientInfoPopup
`app/src/main/java/com/smartcare/oximetry/sample/PatientInfoPopup.java`

| Method | Mô tả |
|---|---|
| `PatientInfoPopup(Context)` | Nạp prefs; theo dõi trạng thái first-run. |
| `show(View)` | Inflate popup, làm mờ nền, hiện dropdown lịch sử mã; hiện lại nếu đóng mà chưa nhập mã. |
| `setCancelable(boolean)` | Điều khiển khả năng đóng (khởi động dùng `false`). |
| `setOnPatientInfoSavedListener(...)` | Callback khi submit mã hợp lệ. |
| `isCodeSaved()` / `isPatientCodeEmpty()` | Kiểm tra đã có mã chưa. |
| `clearSavedCode()` / `resetPatientCode()` | Xóa mã đã lưu. |
| `requestEdit()` | Đặt cờ yêu cầu nhập lại. |
| `getSavedPatientInfo()` | Trả `PatientInfo` từ prefs. |

**Inner:** `OnPatientInfoSavedListener`, `PatientInfo` (researchCode/locationCode/serialNumber), `TextChangeListener`.

---

## ChunkFileConfigPopup
`app/src/main/java/com/smartcare/oximetry/sample/ChunkFileConfigPopup.java`

| Method | Mô tả |
|---|---|
| `ChunkFileConfigPopup(Context)` | Mở `ChunkFileConfigPrefs`. |
| `show(View)` | Hiện switch + ô interval; lưu khi submit. |
| `isChunkFileEnabled()` | Cờ bật (mặc định `false`). |
| `getTimeInterval()` | Interval phút (mặc định `3`). |

---

## CodeHistoryAdapter
`app/src/main/java/com/smartcare/oximetry/sample/CodeHistoryAdapter.java`

| Method | Mô tả |
|---|---|
| `CodeHistoryAdapter(List<String>, OnCodeClickListener, OnCodeDeleteListener)` | Nối list mã + callback. |
| `onCreateViewHolder/onBindViewHolder` | Inflate `item_code_history`; bind click/delete. |
| `getItemCount()` | Kích thước list. |
| `updateCodes(List<String>)` | Thay list + refresh. |
| `removeCode(int)` | Xóa mục có animation. |

**Inner:** `OnCodeClickListener`, `OnCodeDeleteListener`, `CodeViewHolder`.

---

## SystemBarInsetsHelper
`app/src/main/java/com/smartcare/oximetry/sample/SystemBarInsetsHelper.java`

| Method | Mô tả |
|---|---|
| `apply(Activity)` (static) | Đặt `decorFitsSystemWindows`, áp inset (status/nav/cutout) làm padding cho root; bỏ inset top khi có action bar. |

---

## SmartCareApplication
`app/src/main/java/com/smartcare/oximetry/sample/SmartCareApplication.java`

| Method | Mô tả |
|---|---|
| `onCreate()` | Khởi tạo logging, crash handler, lifecycle callbacks, log process start, lịch upload. |
| `onTrimMemory(int)` | Log cảnh báo khi áp lực bộ nhớ ≥ `TRIM_MEMORY_RUNNING_CRITICAL`. |
| `onLowMemory()` | Log snapshot low-memory toàn hệ thống. |
| `onTerminate()` | Log terminate (chủ yếu emulator/test). |
| `installCrashHandler()` (private) | Bọc default handler để flush log crash trước khi rethrow. |

**Inner:** `ActivityLifecycleCallbacks` (log created/started/stopped/resumed/paused/destroyed; `onActivityPostCreated` áp inset).
