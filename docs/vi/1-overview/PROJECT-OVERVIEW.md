# Tầng 1 — Project Overview / Tổng quan dự án

> **Mục tiêu của tài liệu này:** Giúp người mới (lập trình viên, người bàn giao dự án, nghiên cứu viên kỹ thuật)
> hiểu **app này dùng để làm gì** chỉ trong ~10 phút đọc, trước khi đi sâu vào kiến trúc và code.
>
> Đây là **Tầng 1** trong bộ tài liệu 4 tầng. Xem [`docs/README.md`](../README.md) để biết toàn bộ cấu trúc.

---

## 1. App này là gì? / What is it?

**SmartCare Capture** là một **ứng dụng Android** dùng trong **nghiên cứu lâm sàng**, có nhiệm vụ:

1. **Kết nối** với máy đo oxy đầu ngón tay (**pulse oximeter**) **SmartCare** qua **Bluetooth (BLE)**.
2. **Thu** liên tục các tín hiệu sinh lý: **SpO₂** (độ bão hòa oxy), **nhịp tim (HR)**, **dạng sóng PPG** (pleth),
   **chỉ số tưới máu (perfusion index)**, **mức pin** của thiết bị.
3. **Ghi** dữ liệu ra **file CSV** trên bộ nhớ máy (qua MediaStore, thư mục `Download/OximeterData`).
4. **Upload** file CSV lên **server** nghiên cứu (OUCRU hoặc Nepal tùy study).
5. Cho phép nhân viên **ghi chú (annotation)** các sự kiện trong lúc đo (vd: ho, cử động, ghi chú lâm sàng).

Nói ngắn gọn: **app biến một chiếc oximeter Bluetooth thành một thiết bị thu dữ liệu lâu dài, chống mất dữ liệu, tự upload lên server.**

---

## 2. Bài toán nó giải quyết / The problem it solves

Trong nghiên cứu, bệnh nhân cần được theo dõi **liên tục nhiều giờ đến nhiều ngày**. Yêu cầu đặc thù:

| Yêu cầu | Vì sao quan trọng |
|---|---|
| **Không được mất dữ liệu** | Dữ liệu bệnh nhân không thể thu lại được. Mất một đoạn = hỏng dữ liệu nghiên cứu. |
| **Chạy nền liên tục** | Phải ghi kể cả khi màn hình tắt, app bị đẩy xuống nền, hoặc bị hệ thống kill. |
| **Sống sót khi BLE rớt** | Bluetooth hay rớt; app phải tự kết nối lại và ghi tiếp mà không cần thao tác tay. |
| **Tự upload, có retry** | Mạng ở hiện trường chập chờn; upload phải tự thử lại, không chặn việc ghi. |
| **Dễ thay phần cứng** | Khi hãng đổi SDK/đời máy, chỉ nên sửa một chỗ, không đập lại toàn bộ app. |

Toàn bộ kiến trúc (trông phức tạp hơn một app thường) tồn tại để đáp ứng 5 yêu cầu trên. Đây là điều cần ghi nhớ
khi đọc các tầng sau: **"phức tạp ở đây là có chủ đích, để không mất dữ liệu bệnh nhân".**

---

## 3. Ai dùng app? / Who uses it?

- **Nghiên cứu viên / điều dưỡng tại hiện trường:** gắn oximeter cho bệnh nhân, nhập **mã nghiên cứu (research code)**,
  bấm bắt đầu ghi, thỉnh thoảng thêm annotation, rồi dừng/đẩy upload.
- **Kỹ thuật viên / dev bàn giao:** build, cấu hình server cho từng study, theo dõi log, xử lý sự cố.

---

## 4. Một phiên làm việc điển hình / A typical session

```
1. Mở app  ──►  bắt buộc nhập Mã nghiên cứu (Patient/Research code)
2. Bấm Start  ──►  app quét & kết nối oximeter qua BLE
3. App hiển thị sóng PPG + HR + SpO₂ + pin theo thời gian thực
4. Dữ liệu được ghi nền ra file CSV (kể cả khi tắt màn hình)
5. (Tùy chọn) Thêm annotation khi có sự kiện
6. Khi đủ thời gian: bấm Stop  ──►  file đóng lại và được đẩy vào hàng đợi upload
7. App tự upload lên server (có retry). File đã upload chuyển sang thư mục Uploaded/
8. Mỗi ngày 17:00: một tác vụ nền tự dọn & upload nốt phần còn tồn
```

---

## 5. Khái niệm cốt lõi (glossary) / Core concepts

| Thuật ngữ | Ý nghĩa trong dự án này |
|---|---|
| **Oximeter / pulse oximeter** | Máy đo SpO₂ + nhịp tim qua đầu ngón tay; ở đây là máy **SmartCare** kết nối **BLE**. |
| **SpO₂** | Độ bão hòa oxy trong máu (%). |
| **HR (Heart Rate)** | Nhịp tim (bpm). |
| **PPG / pleth** | Dạng sóng quang thể tích (photoplethysmography) — sóng nhấp nhô bạn thấy trên màn hình. |
| **Perfusion index (PI)** | Chỉ số tưới máu, phản ánh độ mạnh của tín hiệu mạch. |
| **Research code / Patient code** | Mã định danh bệnh nhân/phiên đo. **Bắt buộc** nhập trước khi ghi. |
| **Session (phiên ghi)** | Một lần thu dữ liệu liên tục. Có thể kéo dài nhiều giờ và được **cắt** thành nhiều file CSV. |
| **Segment / split** | Một file CSV con. App tự **cắt file** theo chu kỳ (mặc định 6 giờ) hoặc lúc 17:00 để giới hạn thiệt hại nếu lỗi. |
| **Annotation** | Ghi chú sự kiện lâm sàng trong lúc đo, lưu dạng JSON. |
| **Foreground service** | Dịch vụ Android chạy nền có thông báo, để ghi không bị gián đoạn. |
| **Wake lock** | Cơ chế giữ CPU thức khi màn hình tắt, để vẫn ghi được. |
| **MediaStore** | API lưu file của Android hiện đại (Scoped Storage); vì vậy đường dẫn file thường là `content://...`. |
| **Adapter (thiết bị)** | Lớp trung gian cô lập SDK của hãng, giúp đổi phần cứng chỉ sửa một file. |

---

## 6. Tech stack / Công nghệ sử dụng

| Hạng mục | Lựa chọn |
|---|---|
| Nền tảng | Android — **minSdk 29 (Android 10)**, **targetSdk 35 (Android 14)** |
| Ngôn ngữ | Java |
| SDK thiết bị | `scolib-2.0.0.aar` (thư viện của hãng, đặt ở `app/libs/`) |
| Vẽ đồ thị | AndroidPlot `androidplot-core:0.6.1` |
| Xử lý tín hiệu | jDSP `0.2.0` (detrending) |
| Mạng / upload | Retrofit `2.9.0` + OkHttp `4.9.0` |
| Tác vụ nền định kỳ | AndroidX WorkManager `2.7.1` |
| Lưu trữ file | Android **MediaStore** (Scoped Storage) |

---

## 7. Cài đặt & build nhanh / Quick setup & build

> Phần này gộp từ tài liệu Deployment cũ — đủ để một dev mới chạy được app. Chi tiết môi trường xem mục 8.

**Yêu cầu:** Android Studio (bản mới), JDK 8 hoặc 11, Android SDK (API 29 trở lên, khuyến nghị API 35).

```bash
# 1. Clone
git clone https://github.com/Oucru-Innovations/SmartCare-Capture.git
cd SmartCare-Capture

# 2. Tạo/sửa local.properties (file này bị git-ignore)
#    sdk.dir=<đường dẫn Android SDK>
#    API_TOKEN=<token của bạn>

# 3. Build debug
./gradlew assembleDebug      # output: app/build/outputs/apk/debug/app-debug.apk

# 4. Build release
./gradlew assembleRelease    # cần keystore (xem mục 8)
./gradlew bundleRelease      # tạo .aab cho Play Store
```

Trong Android Studio: `File → Open` thư mục dự án → Sync Gradle → chọn thiết bị (bật USB debugging) → Run.

---

## 8. Cấu hình & server / Configuration

**Nguyên tắc:** Mọi giá trị môi trường (URL server, token) **không hardcode** trong code nghiệp vụ, mà chảy theo:

```
local.properties (git-ignored)
        │  API_TOKEN, BASE_URL  (khai báo trong app/build.gradle)
        ▼
   BuildConfig.*          ← sinh khi build / generated at build time
        ▼
ApiConfig (BASE_URL, API_TOKEN)      NetworkConfig (timeouts, MAX_RETRY=3, RETRY_DELAY_MS=10s)
        ▼
RetrofitClient / FileUploader
```

**Cấu hình theo study** (đổi server chỉ bằng cách đổi biến build, không sửa code):

| Study | Server | Ghi chú |
|---|---|---|
| 54, 39 | OUCRU (`http://vital-ei.oucru.org/` mặc định) | Đang chạy, sẵn sàng |
| 62 | Nepal | Cần đổi `BASE_URL` trước khi deploy |
| 24c (legacy) | — | Dùng lại APK cũ ở nhánh `android-12-branch` |

**File nhạy cảm — KHÔNG commit:** `local.properties`, `keystore.properties`, `*.keystore`, `*.jks`.

---

## 9. Hai loại phần cứng oximeter — lưu ý quan trọng / Two hardware types

Có **hai loại máy SmartCare** với ràng buộc tương thích khác nhau (đây là **ràng buộc phần cứng, không phải bug**):

1. **Nhãn bạc (silver label)** — máy có **tem thông tin màu bạc** ở mặt sau → **chỉ chạy ổn trên Android ≤ 10**.
   Trên Android mới hơn có thể gặp lỗi kết nối.
2. **In trực tiếp (printed label)** — thông tin **in thẳng** lên mặt sau → **tương thích mọi phiên bản Android**.

Trước khi dùng, hãy kiểm tra phiên bản Android của máy và cấp đủ quyền (Bluetooth, Location, Notifications).

---

## 10. Quyền (permissions) app yêu cầu / Required permissions

- **Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` trên API 31+; quyền Bluetooth cũ trên bản thấp hơn)
- **Location** (bắt buộc để quét BLE)
- **Storage** (đọc file để upload thủ công trên API 29–32)
- **Notifications** (`POST_NOTIFICATIONS` trên API 33+ — cho foreground service)
- **Battery optimization exemption** (xin miễn tối ưu pin để chạy nền lâu dài)

---

## 11. Đọc tiếp gì? / Where to go next

| Bạn muốn… | Đọc tầng |
|---|---|
| Hiểu **hệ thống chạy thế nào**, các luồng dữ liệu | **Tầng 2** — [`docs/2-architecture/`](../2-architecture/ARCHITECTURE-OVERVIEW.md) |
| Biết **một chức năng nằm ở file/class nào** | **Tầng 3** — [`docs/3-modules/`](../3-modules/MODULES-INDEX.md) |
| Tra **chi tiết từng hàm** | **Tầng 4** — [`docs/4-functions/`](../4-functions/) |
