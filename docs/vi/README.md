# SmartCare Capture — Documentation

> 🌐 **Language / Ngôn ngữ:** **Tiếng Việt** | [English Edition (Bản tiếng Anh)](en/README.md)

> **Điểm vào tài liệu.** Bộ tài liệu được tổ chức theo **4 tầng**, từ tổng quan → chi tiết. Hãy đọc theo thứ tự
> tầng tùy mục đích của bạn.

---

## Đọc theo mục đích / Read by goal

| Bạn là… / Bạn muốn… | Bắt đầu từ |
|---|---|
| Người mới hoàn toàn, muốn hiểu app làm gì | **Tầng 1** → [1-overview/PROJECT-OVERVIEW.md](1-overview/PROJECT-OVERVIEW.md) |
| Muốn hiểu hệ thống chạy thế nào, các luồng | **Tầng 2** → [2-architecture/ARCHITECTURE-OVERVIEW.md](2-architecture/ARCHITECTURE-OVERVIEW.md) |
| Muốn biết "chức năng X nằm ở file/class nào" | **Tầng 3** → [3-modules/MODULES-INDEX.md](3-modules/MODULES-INDEX.md) |
| Muốn tra chi tiết từng hàm | **Tầng 4** → [4-functions/](#tầng-4--function-documentation) |

---

## Cấu trúc 4 tầng / The 4 tiers

### Tầng 1 — Project Overview
*App dùng để làm gì?*
- [1-overview/PROJECT-OVERVIEW.md](1-overview/PROJECT-OVERVIEW.md) — mục tiêu, bài toán, glossary, tech stack, cài đặt & cấu hình.

### Tầng 2 — Architecture Flow
*Hệ thống hoạt động thế nào, từng luồng ra sao?*
- [2-architecture/ARCHITECTURE-OVERVIEW.md](2-architecture/ARCHITECTURE-OVERVIEW.md) — kiến trúc 5 tầng + danh sách flow
- [flow-01-app-startup.md](2-architecture/flow-01-app-startup.md) — khởi động app
- [flow-02-bluetooth-connection.md](2-architecture/flow-02-bluetooth-connection.md) — kết nối BLE & đổi SDK
- [flow-03-data-recording.md](2-architecture/flow-03-data-recording.md) — ghi dữ liệu (luồng quan trọng nhất)
- [flow-04-annotation.md](2-architecture/flow-04-annotation.md) — ghi chú sự kiện
- [flow-05-file-upload.md](2-architecture/flow-05-file-upload.md) — upload & retry & lịch 17:00
- [flow-06-file-management.md](2-architecture/flow-06-file-management.md) — quản lý file

### Tầng 3 — Module / Class Reference
*Code của chức năng này nằm ở đâu?*
- [3-modules/MODULES-INDEX.md](3-modules/MODULES-INDEX.md) — bản đồ class → module + cây thư mục
- [module-ui-recording.md](3-modules/module-ui-recording.md)
- [module-device-bluetooth.md](3-modules/module-device-bluetooth.md)
- [module-data-storage.md](3-modules/module-data-storage.md)
- [module-network-upload.md](3-modules/module-network-upload.md)

### Tầng 4 — Function Documentation
*Chi tiết từng hàm (kèm Javadoc trong code).*
- [4-functions/functions-ui-recording.md](4-functions/functions-ui-recording.md)
- [4-functions/functions-device-bluetooth.md](4-functions/functions-device-bluetooth.md)
- [4-functions/functions-data-storage.md](4-functions/functions-data-storage.md)
- [4-functions/functions-network-upload.md](4-functions/functions-network-upload.md)

> Ngoài 4 tầng, **mã nguồn `.java` đã được bổ sung Javadoc** cho các class/method quan trọng — đọc trực tiếp trong code.

---

## Báo cáo test / Test reports
- [MANUAL_TEST_REPORT.md](MANUAL_TEST_REPORT.md)
- [MANUAL_TEST_REPORT_SMARTCARE_phucvh_300326.md](MANUAL_TEST_REPORT_SMARTCARE_phucvh_300326.md)

---

## Known issues / Nợ kỹ thuật

Một số điểm cần lưu ý khi bảo trì (gộp từ TODO cũ):

- **`RecordActivity` lớn (~2600 dòng)** — gánh nhiều trách nhiệm (BLE, UI, quyền, dữ liệu). Đã tách phần SDK ra
  `OximeterSDKAdapter`; có thể tách tiếp thành manager/controller. Các helper liên quan hiện có thêm
  `BugReportDialog` (UI báo lỗi + upload log) và `PacketStatsTracker` (chẩn đoán tốc độ gói tin).
- **Biến static cho annotation vitals** (`annotationHeartRate`, `annotationSpO2`) — dễ gây giá trị cũ giữa các phiên;
  nên dùng state có vòng đời (ViewModel/SavedState).
- **Quyền rải rác** trong `RecordActivity` — nên gom về một chỗ theo từng mức API.
- **UI cập nhật mỗi gói dữ liệu** — đã throttle 500ms; cân nhắc batch thêm.
- **Class legacy chưa dùng:** `OximeterDataListener`, `OximeterDataPacket`, `BluetoothAutoConnector` — ứng viên dọn dẹp.
- **Chưa có unit test** — nên bắt đầu từ `OximeterDataBuffer`, `FileUploader`, `OximeterDataWriterFunctions`.

---

## Quy ước cập nhật / Maintenance

- Khi **kiến trúc** thay đổi → cập nhật Tầng 2.
- Khi **thêm/đổi class** → cập nhật Tầng 3 (MODULES-INDEX + file module).
- Khi **đổi chữ ký/hành vi hàm** → cập nhật Tầng 4 + Javadoc trong code.
- Cấu hình/triển khai theo study → xem [Tầng 1 mục 7–8](1-overview/PROJECT-OVERVIEW.md#7-cài-đặt--build-nhanh--quick-setup--build).
