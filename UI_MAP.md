# 🗺️ BẢN ĐỒ CÁC FILE UI - SmartCare Capture App

## 📋 MỤC LỤC
1. [Layout Files (Giao diện chính)](#1-layout-files)
2. [Drawable Files (Hình ảnh, Background, Icons)](#2-drawable-files)
3. [Values Files (Kích thước, Màu sắc, Văn bản)](#3-values-files)
4. [Menu Files](#4-menu-files)
5. [Java Files liên quan đến UI](#5-java-files-liên-quan-đến-ui)
6. [Hướng dẫn sử dụng](#6-hướng-dẫn-sử-dụng)

---

## 1. LAYOUT FILES
**Đường dẫn:** `app/src/main/res/layout/`

### 1.1. Activity Layouts (Màn hình chính)

| File | Mô tả | Activity sử dụng |
|------|-------|------------------|
| `activity_main.xml` | Màn hình chính - Hiển thị đồ thị PPG, HR, SpO2, nút Connect/Disconnect | `RecordActivity.java` |
| `activity_file_manager.xml` | Màn hình quản lý file - Danh sách CSV files, upload files | `FileManagerActivity.java` |

### 1.2. Popup/Dialog Layouts

| File | Mô tả | Sử dụng trong |
|------|-------|--------------|
| `patient_info_popup.xml` | Popup nhập thông tin bệnh nhân (Patient Code) - **POPUP ĐẦU TIÊN KHI MỞ APP** | `PatientInfoPopup.java` |
| `chunk_file_config_popup.xml` | Popup cấu hình chunk files | `ChunkFileConfigPopup.java` |
| `popup_connected_devices.xml` | Popup hiển thị danh sách thiết bị đã kết nối | `RecordActivity.java` (menu) |
| `annotation_dialog.xml` | Dialog thêm annotation | `ShowAnnotate.java` |
| `annotation_history_layout.xml` | Layout hiển thị lịch sử annotations | `AnnotationHistory.java` |
| `dialog_list.xml` | Dialog danh sách (dùng chung) | Nhiều nơi |

### 1.3. Item Layouts (Item trong RecyclerView/ListView)

| File | Mô tả | Sử dụng trong |
|------|-------|--------------|
| `item_code_history.xml` | **Item hiển thị danh sách Patient Codes (Recent Codes)** - Tối đa 5 codes | `CodeHistoryAdapter.java` |
| `item_csv_file.xml` | Item hiển thị file CSV trong File Manager (tab Pending) | `FileManagerActivity.java` |
| `item_uploaded_file.xml` | Item hiển thị file đã upload (tab Uploaded) | `FileManagerActivity.java` |
| `list_item.xml` | Item danh sách (dùng chung) | Nhiều nơi |

---

## 2. DRAWABLE FILES
**Đường dẫn:** `app/src/main/res/drawable/`

### 2.1. Background Drawables

| File | Mô tả | Sử dụng trong |
|------|-------|--------------|
| `popup_background.xml` | Background của popup | `patient_info_popup.xml`, các popup khác |
| `edit_text_background.xml` | Background của EditText | Tất cả EditText trong app |
| `dialog_background.xml` | Background của dialog | Các dialog |
| `item_background.xml` | Background của item trong list | Các RecyclerView items |
| `list_background.xml` | Background của list | Các ListView/RecyclerView |
| `border_background.xml` | Background có border | Nhiều nơi |
| `border.xml` | Border đơn giản | Nhiều nơi |

### 2.2. Button Drawables

| File | Mô tả | Sử dụng trong |
|------|-------|--------------|
| `rounded_button_selector.xml` | Button với hiệu ứng press (selector) | Nút Save, các button chính |
| `rounded_button_blue.xml` | Button màu xanh dương | Nhiều nơi |
| `rounded_button_blue_ripple.xml` | Button xanh với ripple effect | Nhiều nơi |
| `rounded_button_white.xml` | Button màu trắng (mới tạo) | Item code history |
| `rounded_button_green.xml` | Button màu xanh lá | Nhiều nơi |
| `rounded_button_orange.xml` | Button màu cam | Nhiều nơi |
| `rounded_button_pressed.xml` | Button khi được nhấn | Dùng trong selector |

### 2.3. Icons (PNG)

| File | Mô tả | Sử dụng trong |
|------|-------|--------------|
| `ic_chart.png` | Icon biểu đồ | Toggle plot button |
| `ic_zoom_in.png` | Icon zoom in | Toggle plot button |
| `ic_devices.png` | Icon thiết bị | Menu, buttons |
| `ic_file_management.png` | Icon quản lý file | Menu |
| `ic_menu_options.png` | Icon menu options | Menu |
| `ic_patient_info.png` | Icon thông tin bệnh nhân | Menu, buttons |
| `file_icon.png` | Icon file | File Manager |
| `csv_icon.png` | Icon CSV | File Manager |
| `upload.png` | Icon upload | File Manager |

---

## 3. VALUES FILES
**Đường dẫn:** `app/src/main/res/values/` và `app/src/main/res/values-sw600dp/`

### 3.1. Dimensions (Kích thước)
**File:** `values/dimens.xml` (Phone) và `values-sw600dp/dimens.xml` (Tablet)

| Tên | Mô tả | Giá trị Phone | Giá trị Tablet |
|-----|-------|---------------|----------------|
| `popup_padding` | Padding bên trong popup | 20dp | 48dp |
| `popup_margin` | Margin bên ngoài popup | 10dp | 96dp |
| `title_text_size` | Kích thước chữ tiêu đề | 20sp | 36sp |
| `label_text_size` | Kích thước chữ label | 16sp | 27sp |
| `input_padding` | Padding trong EditText | 12dp | 24dp |
| `button_padding` | Padding trong Button | 10dp | 21dp |

### 3.2. Strings (Văn bản)
**File:** `values/strings.xml`

| Tên | Giá trị | Sử dụng trong |
|-----|---------|--------------|
| `app_name` | "SmartCare Capture" | Tên app |
| `record_start` | "Connect" | Nút kết nối |
| `record_stop` | "Disconnect" | Nút ngắt kết nối |
| `menu_show_devices` | "Show Devices" | Menu item |
| `menu_manage_files` | "File Manager" | Menu item |
| `menu_patient_info` | "Patient Info" | Menu item |
| `menu_config_info` | "Configuration" | Menu item |
| `unknown_device` | "Oximeter" | Tên thiết bị mặc định |

### 3.3. Styles (Theme, Style)
**File:** `values/styles.xml`

| Style | Mô tả | Sử dụng |
|-------|-------|---------|
| `CustomTheme` | Theme chính của app | AndroidManifest.xml |
| `MyActionBar` | Style của ActionBar | CustomTheme |
| `MyMenuText` | Style text trong menu | CustomTheme |

**Màu sắc trong styles.xml:**
- ActionBar background: `#006A67` (xanh đậm)
- Menu text color: `#3DC2EC` (xanh nhạt)

---

## 4. MENU FILES
**Đường dẫn:** `app/src/main/res/menu/`

| File | Mô tả | Sử dụng trong |
|------|-------|--------------|
| `main_menu.xml` | Menu chính (3 chấm) | `RecordActivity.java` |
| `annotate_menu.xml` | Menu annotation | `ShowAnnotate.java` |

---

## 5. JAVA FILES LIÊN QUAN ĐẾN UI

**Đường dẫn:** `app/src/main/java/com/smartcare/oximetry/sample/`

### 5.1. Activity Files

| File | Layout sử dụng | Mô tả |
|------|----------------|-------|
| `RecordActivity.java` | `activity_main.xml` | Activity chính - Ghi dữ liệu oximeter |
| `FileManagerActivity.java` | `activity_file_manager.xml` | Quản lý files CSV |

### 5.2. Popup/Dialog Files

| File | Layout sử dụng | Mô tả |
|------|----------------|-------|
| `PatientInfoPopup.java` | `patient_info_popup.xml` | **Popup nhập Patient Code - HIỂN THỊ ĐẦU TIÊN** |
| `ChunkFileConfigPopup.java` | `chunk_file_config_popup.xml` | Popup cấu hình chunk files |
| `ShowAnnotate.java` | `annotation_dialog.xml` | Dialog thêm annotation |
| `AnnotationHistory.java` | `annotation_history_layout.xml` | Hiển thị lịch sử annotations |

### 5.3. Adapter Files (RecyclerView/ListView)

| File | Item Layout | Mô tả |
|------|-------------|-------|
| `CodeHistoryAdapter.java` | `item_code_history.xml` | **Adapter hiển thị danh sách Patient Codes** |

---

## 6. HƯỚNG DẪN SỬ DỤNG

### 6.1. Khi muốn chỉnh sửa UI của một màn hình:

1. **Xác định màn hình cần sửa:**
   - Màn hình chính → `activity_main.xml`
   - Popup Patient Info → `patient_info_popup.xml`
   - File Manager → `activity_file_manager.xml`

2. **Tìm file layout tương ứng** trong `app/src/main/res/layout/`

3. **Nếu cần chỉnh màu, kích thước:**
   - Màu sắc → `values/styles.xml` hoặc trong layout file
   - Kích thước → `values/dimens.xml` (phone) hoặc `values-sw600dp/dimens.xml` (tablet)
   - Văn bản → `values/strings.xml`

4. **Nếu cần chỉnh background, icon:**
   - Background → `drawable/` (các file `.xml` hoặc `.png`)
   - Icon → `drawable/` (các file `.png`)

### 6.2. Các thay đổi UI thường gặp:

#### Thay đổi màu sắc:
- **Background:** Sửa trong layout file (`android:background`) hoặc drawable file
- **Text color:** Sửa trong layout file (`android:textColor`)
- **Theme:** Sửa trong `values/styles.xml`

#### Thay đổi kích thước:
- **Text size:** Sửa trong layout file (`android:textSize`) hoặc `dimens.xml`
- **Padding/Margin:** Sửa trong layout file hoặc `dimens.xml`
- **Width/Height:** Sửa trong layout file

#### Thay đổi hình ảnh/icon:
- **Icon:** Thay file trong `drawable/` và cập nhật tên trong layout
- **Background:** Sửa drawable file hoặc thay file mới

### 6.3. Lưu ý khi chỉnh sửa:

1. **Tablet support:**
   - Nếu có `values-sw600dp/dimens.xml`, tablet sẽ dùng kích thước lớn hơn
   - Layout files dùng chung cho cả phone và tablet (trừ khi có `layout-sw600dp/`)

2. **Naming convention:**
   - Layout: `activity_xxx.xml`, `item_xxx.xml`, `xxx_popup.xml`
   - Drawable: `rounded_button_xxx.xml`, `ic_xxx.png`
   - Dimen: `xxx_padding`, `xxx_text_size`

3. **Testing:**
   - Test trên cả phone và tablet
   - Kiểm tra các màn hình khác nhau
   - Đảm bảo không bị lỗi khi build

---

## 📌 QUAN TRỌNG - CÁC FILE THƯỜNG SỬA NHẤT

### Khi chỉnh UI popup Patient Info (popup đầu tiên):
1. `layout/patient_info_popup.xml` - Layout chính
2. `layout/item_code_history.xml` - Item danh sách codes
3. `drawable/rounded_button_white.xml` - Background item
4. `drawable/edit_text_background.xml` - Background input
5. `drawable/popup_background.xml` - Background popup
6. `values/dimens.xml` - Kích thước (phone)
7. `values-sw600dp/dimens.xml` - Kích thước (tablet)

### Khi chỉnh UI màn hình chính:
1. `layout/activity_main.xml` - Layout màn hình chính
2. `drawable/` - Các icon, background
3. `values/styles.xml` - Theme, màu sắc
4. `values/strings.xml` - Văn bản

### Khi chỉnh UI File Manager:
1. `layout/activity_file_manager.xml` - Layout chính
2. `layout/item_csv_file.xml` - Item file pending
3. `layout/item_uploaded_file.xml` - Item file uploaded
4. `drawable/` - Icons, backgrounds

---

**📝 Ghi chú:** File này sẽ được cập nhật khi có thay đổi trong cấu trúc UI của app.

