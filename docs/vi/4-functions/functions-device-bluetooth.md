# Functions — Device / Bluetooth

> **Tầng 4.** Tra cứu chi tiết hàm cho module Device/Bluetooth. Tổng quan ở
> [Tầng 3 — module-device-bluetooth.md](../3-modules/module-device-bluetooth.md).

---

## IDeviceAdapter (interface)
`app/src/main/java/com/smartcare/oximetry/sample/device/IDeviceAdapter.java`

| Method | Mô tả |
|---|---|
| `setListener(OximeterDataListener)` | Đăng ký callback (không dùng trong cài đặt hiện tại). |
| `connect()` | Kết nối thiết bị mặc định/đầu tiên khớp. |
| `connect(String macAddress)` | Kết nối theo MAC cụ thể. |
| `disconnect()` | Ngắt thiết bị đang nối. |
| `isRecording()` | Có đang recording không. |
| `getMacAddress()` | MAC thiết bị đang nối. |
| `getOximeterModelNumber()` | Chuỗi model từ SDK. |
| `getSampleRate()` | Sample rate (mẫu/giây). |
| `startRecording()` | Bắt đầu ghi trên kết nối đã lập. |
| `onDestroy()` | Tháo kết nối, giải phóng tài nguyên SDK. |

---

## OximeterSDKAdapter (implements IDeviceAdapter)
`app/src/main/java/com/smartcare/oximetry/sample/device/OximeterSDKAdapter.java`

| Method | Mô tả |
|---|---|
| `OximeterSDKAdapter(Context, Handler)` | Dựng adapter, áp **reflection bỏ lọc tên thiết bị**, tạo `ConnectionManager`. |
| `setListener(...)` | **No-op** — data đi qua Handler. |
| `connect()` / `connect(String)` | Ủy quyền `connectionManager.connect(...)`. |
| `disconnect()` | Ngắt **chỉ khi** đang recording (tránh gọi thừa). |
| `isRecording()` | Ủy quyền `connectionManager.isRecording()`. |
| `getMacAddress()` | MAC từ SDK hoặc `null`. |
| `getOximeterModelNumber()` | Model từ SDK hoặc `"Unknown"`. |
| `getSampleRate()` | Sample rate từ SDK hoặc `0`. |
| `startRecording()` | Ủy quyền `connectionManager.startRecording()`. |
| `onDestroy()` | Ngắt nếu đang recording, rồi null `connectionManager`. |

**Workaround (constructor):** reflection trên class obfuscated `com.smartcare.oximetry.library.a`, field `d` (`String[]`),
set `names[0] = ""` để khớp mọi thiết bị Bluetooth thay vì tên hardcode (vd FC0392). **Xem lại khi nâng `.aar`.**

---

## OximeterDataPacket (POJO, legacy/không dùng)
`app/src/main/java/com/smartcare/oximetry/sample/device/OximeterDataPacket.java`

- Constructor đầy đủ tham số: `(int pleth, int pulse, double spo2, int deviceId, double battery, int counter,
  int spo2Status, long redAdc, long irAdc, double perfusionIndex, long packageReceived, int startTime)`.
- Field public: `pleth, pulse, spo2, deviceId, battery, counter, spo2Status, redAdc, irAdc, perfusionIndex,
  packageReceived, startTime`. Không có method khác.

---

## OximeterDataListener (interface, legacy/không dùng)
`app/src/main/java/com/smartcare/oximetry/sample/device/OximeterDataListener.java`

`onDataReceived`, `onDeviceConnected`, `onDeviceDisconnected`, `onConnectionLost`, `onDeviceNotFound`, `onBluetoothOff`,
`onBluetoothUnavailable`, `onPermissionRequired(boolean isFineLocation)`, `onUnknownDevice`, `onNoDataAvailable`.
→ Đã thay bằng `ConnectionManager` + Handler trong `RecordActivity`.

---

## OximeterDataService (foreground Service)
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataService.java`

| Method | Mô tả |
|---|---|
| `onBind(Intent)` | Trả `LocalBinder`. |
| `onStartCommand(Intent, int, int)` | Start foreground, acquire wake lock, arm buffer; xử lý null intent (process restart) và `START_FLAG_REDELIVERY`; trả `START_STICKY` nếu prefs active, ngược lại `START_NOT_STICKY`. |
| `onTaskRemoved(Intent)` | Nếu phiên active → giữ sống; ngược lại `shutdownRecordingAsync(null)`. |
| `startDataCollection(long, String, boolean, int)` | Arm phiên + mở CSV lần đầu khi writer chưa active. |
| `ensureRecordingActive(long, String, boolean, int)` | Mở lại writer sau BLE pause/recovery; trả writer có active không. |
| `addDataPoint(double spo2, int spo2Status, long redAdc, double battery, int hr, long irAdc, double pi, int counter, String mac, long pkgRecv, int pleth, long ts)` | Đẩy mẫu vào buffer khi `isRunning`; log drop có throttle (1 lần/10s). |
| `flushBufferedData()` | Flush lô <100 mẫu trước split/close. |
| `stopDataCollection()` | Hoàn tất: flush, `endRecording()`, release wake lock, clear buffer; idempotent qua `recordingFinalized`. |
| `shutdownRecordingAsync(Runnable)` | Stop trên IO thread, rồi detach foreground + `stopSelf()` trên main. |
| `getCompletedRecordFilePath()` | Path file hoàn tất gần nhất (`endRecordFilePath`) hoặc `null`. |
| `isRunning()` | Service có đang thu không. |
| `restoreSessionAfterProcessRestart()` (private) | Nạp `RecordingSessionPrefs.Session`; khôi phục field, tạo lại buffer, re-enter foreground, re-acquire wake lock. **Không** mở lại CSV ngay (hoãn tới gói kế). |
| `acquireWakeLock()` / `releaseWakeLockQuietly()` (private) | `PARTIAL_WAKE_LOCK` tag `"OximeterDataService::WakeLock"`, không đếm tham chiếu. |

**Inner:** `LocalBinder extends Binder` → `getService()`.
**Hằng/field:** `ACTION_SPLIT_RECORDING` (broadcast cắt 17:00), `public String endRecordFilePath`.
**Lưu ý:** tạo file lười (không sinh CSV rỗng); foreground type `CONNECTED_DEVICE` (API 29+); `shutdownInProgress`
(AtomicBoolean) chống shutdown trùng.

---

## BluetoothEventInterceptor
`app/src/main/java/com/smartcare/oximetry/sample/BluetoothEventInterceptor.java`

| Method | Mô tả |
|---|---|
| `BluetoothEventInterceptor(Context, Queue<String> macQueue)` | Đăng ký receiver `SYSTEM_HIGH_PRIORITY`; khôi phục `connectedMacAddress` từ prefs. |
| `setConnectedMacAddress(String)` | Cập nhật MAC "máy của ta" (in-memory + persisted). |
| `getConnectedMacAddress()` | Trả MAC đang theo dõi. |
| `cleanup()` | Hủy đăng ký receiver (gọi khi activity destroy). |

**Workaround:** `abortBroadcast()` cho disconnect/connect từ thiết bị không phải MAC của ta; persist MAC
(`BluetoothInterceptorPrefs`/`KEY_CURRENT_MAC`) sống sót qua restart; nếu `connectedMacAddress == null` và MAC lạ →
cho qua (log "allowing").

---

## PacketStatsTracker
`app/src/main/java/com/smartcare/oximetry/sample/PacketStatsTracker.java`

Singleton theo dõi packet nhận từ thiết bị và ghi gap BLE qua `KEY_DATA_COUNTER`. Dùng bởi `RecordActivity.packetStats`. Chỉ gọi trên main thread (không cần đồng bộ thêm).

**Hằng (private):** `MINUTE_MS = 60_000`, `MAX_PLAUSIBLE_GAP = 1000` (gap lớn hơn coi là reset/wrap bộ đếm, không tính mất), `GAP_LOG_THROTTLE_MS = 2_000` (giới hạn spam log gap chi tiết).

| Method | Mô tả |
|---|---|
| `getInstance()` (static synchronized) | Trả/tạo singleton (sống sót qua recreate activity). |
| `start()` | Bắt đầu ticker tổng hợp mỗi phút (idempotent); log `TRACKER_STARTED`. |
| `stop()` | Dừng ticker và log tổng (`TRACKER_STOPPED`). |
| `onPacketReceived(int deviceCounter)` | Tăng bộ đếm nhận; nếu counter nhảy 1…`MAX_PLAUSIBLE_GAP` thì tính mất BLE và có thể log `BLE_PACKET_GAP` (throttle); counter lệch bất thường/ngược → `COUNTER_RESYNC` (không tính mất). Trả số packet vừa mất (cho khoảng trống waveform). |
| `onConnectionReset(String reason)` | Xóa mốc bộ đếm thiết bị để packet đầu sau reconnect không bị tính gap khổng lồ; log `COUNTER_RESYNC_ON_RECONNECT`. |
| `getTotalReceived()` / `getTotalLostBle()` | Tổng lifetime kể từ khi singleton tồn tại. |

**Sự kiện log:** `MINUTE_SUMMARY` (received/lostBle/lossPct mỗi phút + totals; im lặng khi cửa sổ không có lưu lượng), `BLE_PACKET_GAP`, `COUNTER_RESYNC`, `COUNTER_RESYNC_ON_RECONNECT`. Tách biệt với drop hàng đợi CSV (`OximeterDataBuffer`).

---

## BluetoothAutoConnector (legacy/không dùng)
`app/src/main/java/com/smartcare/oximetry/sample/BluetoothAutoConnector.java`

| Method | Mô tả |
|---|---|
| `BluetoothAutoConnector(Context, Queue<String>, AutoConnectCallback)` | Đăng ký ACL receiver, nạp MAC bị chặn, gọi `initialize()`. |
| `initialize()` | Reset state, xóa MAC chặn, bắt đầu vòng quét auto-connect. |
| `manualDisconnect(String)` | Chặn MAC, đóng socket, pause reconnect 3s, quét lại. |
| `manualConnect(String)` | Bỏ chặn, dừng quét, set MAC, gọi `callback.connectToDevice`. |
| `requestPauseAutoReconnect(long)` | Tạm ngừng reconnect. |
| `cleanup()` | Dừng quét, clear handler, đóng socket, hủy receiver. |

**Inner:** `AutoConnectCallback` (`onDeviceFound`, `checkAppPermissions`, `connectToDevice`).
**Ghi chú:** reflection cho `setHighPriorityConnection`; cố ý **không** mở socket SPP song song (tránh BLE drop);
chu kỳ quét 2s. **Không được class nào dùng.**
