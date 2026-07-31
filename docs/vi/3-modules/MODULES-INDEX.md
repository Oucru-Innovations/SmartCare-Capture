# Tầng 3 — Modules Index / Bản đồ module & class

> **Mục tiêu:** Trả lời câu hỏi **"code của chức năng này nằm ở đâu?"**. Đây là **Tầng 3** — bản đồ từ chức năng →
> module → class. Muốn biết chi tiết từng hàm, xem **Tầng 4** ([`docs/4-functions/`](../4-functions/)).

Toàn bộ **38 class** (package `com.smartcare.oximetry.sample` và sub-package) được gom thành **4 module**:

| Module | File chi tiết | Số class | Vai trò |
|---|---|---|---|
| UI / Recording | [module-ui-recording.md](module-ui-recording.md) | 9 | Màn hình, popup, adapter, vòng đời app |
| Device / Bluetooth | [module-device-bluetooth.md](module-device-bluetooth.md) | 8 | Kết nối BLE, bọc SDK, service nền |
| Data / Storage | [module-data-storage.md](module-data-storage.md) | 9 | Gom mẫu, ghi CSV, index file, khôi phục phiên |
| Network / Upload | [module-network-upload.md](module-network-upload.md) | 12 | Upload, retry, cấu hình, lịch 17:00, log |

---

## Tra nhanh: "Chức năng X nằm ở đâu?" / Quick lookup

| Tôi muốn tìm/sửa… | Class chính | Module |
|---|---|---|
| Màn hình ghi, đồ thị PPG, nhận dữ liệu BLE | `RecordActivity` (+ `SmartCareHandler`) | UI/Recording |
| Nhập/đổi mã bệnh nhân | `PatientInfoPopup`, `CodeHistoryAdapter` | UI/Recording |
| Cấu hình cắt file (chunk) | `ChunkFileConfigPopup` | UI/Recording |
| Ghi chú sự kiện | `ShowAnnotate`, `AnnotationHistory` | UI/Recording |
| Báo lỗi (upload log theo ngày) | `BugReportDialog` | UI/Recording |
| Màn hình quản lý file | `FileManagerActivity` | UI/Recording |
| Khởi tạo app, crash handler, lịch | `SmartCareApplication` | UI/Recording |
| Kết nối/ngắt oximeter, bọc SDK | `OximeterSDKAdapter`, `IDeviceAdapter` | Device/Bluetooth |
| Chạy nền, wake lock, khôi phục sau kill | `OximeterDataService` | Device/Bluetooth |
| Chặn sự kiện BLE thiết bị lạ | `BluetoothEventInterceptor` | Device/Bluetooth |
| Thống kê mất packet BLE | `PacketStatsTracker` | Device/Bluetooth |
| Gom 100 mẫu, tính median | `OximeterDataBuffer` | Data/Storage |
| Ghi CSV, cắt file | `OximeterDataWriterFunctions` | Data/Storage |
| Liệt kê file pending/uploaded | `OximeterDataFileIndex`, `AppRecordingRegistry` | Data/Storage |
| I/O MediaStore cho annotation | `AnnotationStorage` | Data/Storage |
| Lưu phiên để khôi phục | `RecordingSessionPrefs` | Data/Storage |
| Hằng số (chu kỳ cắt…) | `RecordingConstants` | Data/Storage |
| Upload + retry | `PendingFileUploadCoordinator`, `FileUploader` | Network/Upload |
| Endpoint/HTTP | `network/ApiService`, `network/RetrofitClient` | Network/Upload |
| Cấu hình URL/token/timeout | `config/ApiConfig`, `config/NetworkConfig` | Network/Upload |
| Lịch upload 17:00 | `AnnotationUploadScheduler`, `UploadAnnotationWorker` | Network/Upload |
| Ghi log ra file (theo ngày) | `FileLogger` | Network/Upload |

---

## Sơ đồ thư mục mã nguồn / Source tree

```
app/src/main/java/com/smartcare/oximetry/sample/
├── device/                          # Module Device/Bluetooth (một phần)
│   ├── IDeviceAdapter.java          #   Hợp đồng adapter
│   ├── OximeterSDKAdapter.java      #   Bọc scolib SDK
│   ├── OximeterDataListener.java    #   (legacy, không dùng)
│   └── OximeterDataPacket.java      #   (legacy POJO, không dùng)
├── network/                         # Module Network (HTTP)
│   ├── ApiService.java
│   ├── RetrofitClient.java
│   └── UploadResponse.java
├── config/                          # Module Network (cấu hình)
│   ├── ApiConfig.java
│   └── NetworkConfig.java
│
├── RecordActivity.java              # UI/Recording — màn hình chính
├── FileManagerActivity.java         # UI/Recording — quản lý file
├── ShowAnnotate.java                # UI/Recording — ghi chú
├── AnnotationHistory.java           # UI/Recording — lịch sử ghi chú
├── BugReportDialog.java             # UI/Recording — báo lỗi / upload log
├── PatientInfoPopup.java            # UI/Recording — nhập mã BN
├── ChunkFileConfigPopup.java        # UI/Recording — cấu hình cắt file
├── CodeHistoryAdapter.java          # UI/Recording — adapter mã gần đây
├── SystemBarInsetsHelper.java       # UI/Recording — inset thanh hệ thống
├── SmartCareApplication.java        # UI/Recording — khởi tạo app
│
├── OximeterDataService.java         # Device/Bluetooth — service nền
├── BluetoothAutoConnector.java      # Device/Bluetooth — (legacy, không dùng)
├── BluetoothEventInterceptor.java   # Device/Bluetooth — chặn sự kiện lạ
├── PacketStatsTracker.java          # Device/Bluetooth — thống kê mất packet BLE
│
├── OximeterDataBuffer.java          # Data/Storage — gom 100 mẫu
├── OximeterDataWriterFunctions.java # Data/Storage — ghi CSV + split
├── OximeterDataFileIndex.java       # Data/Storage — index file
├── OximeterDataTreeAccess.java      # Data/Storage — SAF tree
├── AppRecordingRegistry.java        # Data/Storage — sổ đăng ký file
├── AnnotationStorage.java           # Data/Storage — I/O MediaStore annotation
├── RecordingSessionPrefs.java       # Data/Storage — lưu phiên
├── RecordingConstants.java          # Data/Storage — hằng số
├── MemoryDiagnostics.java           # Data/Storage — chẩn đoán RAM
│
├── PendingFileUploadCoordinator.java# Network/Upload — điều phối + retry
├── FileUploader.java                # Network/Upload — upload HTTP
├── NetworkHelper.java               # Network/Upload — kiểm tra mạng
├── FileLogger.java                  # Network/Upload — log theo ngày
├── AnnotationUploadScheduler.java   # Network/Upload — lịch 17:00
└── UploadAnnotationWorker.java      # Network/Upload — worker 17:00
```

> **Ghi chú "legacy/không dùng":** `OximeterDataListener`, `OximeterDataPacket`, `BluetoothAutoConnector` còn trong
> repo nhưng **không được class nào khác gọi** (đã bị thay bằng cơ chế Handler/Message và logic reconnect trong
> `RecordActivity`). Giữ lại để tham khảo, có thể là ứng viên dọn dẹp sau này.
