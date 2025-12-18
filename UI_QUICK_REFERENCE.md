# 🚀 QUICK REFERENCE - UI Files

## 📱 MÀN HÌNH CHÍNH

### RecordActivity (Màn hình ghi dữ liệu)
- **Layout:** `res/layout/activity_main.xml`
- **Java:** `RecordActivity.java`
- **Icons:** `ic_chart.png`, `ic_zoom_in.png`, `ic_devices.png`

---

## 🎯 POPUP ĐẦU TIÊN (Patient Info)

### PatientInfoPopup
- **Layout:** `res/layout/patient_info_popup.xml`
- **Java:** `PatientInfoPopup.java`
- **Item layout:** `res/layout/item_code_history.xml`
- **Adapter:** `CodeHistoryAdapter.java`
- **Backgrounds:**
  - Popup: `drawable/popup_background.xml`
  - Input: `drawable/edit_text_background.xml`
  - Item: `drawable/rounded_button_white.xml` (hoặc blue)
- **Kích thước:** `values/dimens.xml` (phone), `values-sw600dp/dimens.xml` (tablet)

---

## 📁 FILE MANAGER

### FileManagerActivity
- **Layout:** `res/layout/activity_file_manager.xml`
- **Java:** `FileManagerActivity.java`
- **Item layouts:**
  - Pending: `res/layout/item_csv_file.xml`
  - Uploaded: `res/layout/item_uploaded_file.xml`
- **Icons:** `file_icon.png`, `csv_icon.png`, `upload.png`

---

## 🎨 CÁC FILE QUAN TRỌNG

### Layouts
```
res/layout/
├── activity_main.xml              ← Màn hình chính
├── activity_file_manager.xml      ← File Manager
├── patient_info_popup.xml          ← Popup đầu tiên
├── item_code_history.xml          ← Item danh sách codes
├── item_csv_file.xml               ← Item file pending
└── item_uploaded_file.xml          ← Item file uploaded
```

### Drawables
```
res/drawable/
├── popup_background.xml            ← Background popup
├── edit_text_background.xml        ← Background input
├── rounded_button_white.xml         ← Button/item trắng
├── rounded_button_blue.xml          ← Button xanh
├── rounded_button_selector.xml      ← Button với effect
└── ic_*.png                        ← Icons
```

### Values
```
res/values/
├── dimens.xml                      ← Kích thước (phone)
├── strings.xml                     ← Văn bản
└── styles.xml                      ← Theme, màu sắc

res/values-sw600dp/
└── dimens.xml                      ← Kích thước (tablet)
```

---

## 🔧 KHI CẦN SỬA GÌ?

| Muốn sửa | File cần sửa |
|----------|--------------|
| **Màu nền popup** | `drawable/popup_background.xml` |
| **Màu nền input** | `drawable/edit_text_background.xml` |
| **Màu nền item code** | `drawable/rounded_button_white.xml` hoặc trong `item_code_history.xml` |
| **Kích thước chữ** | `values/dimens.xml` hoặc trong layout file |
| **Padding/Margin** | `values/dimens.xml` hoặc trong layout file |
| **Icon** | Thay file trong `drawable/` và cập nhật trong layout |
| **Văn bản** | `values/strings.xml` hoặc trong layout file |
| **Màu sắc theme** | `values/styles.xml` |
| **Layout màn hình** | `layout/activity_xxx.xml` |
| **Layout popup** | `layout/xxx_popup.xml` |
| **Layout item** | `layout/item_xxx.xml` |

---

## 📍 VỊ TRÍ FILE

```
app/src/main/
├── res/
│   ├── layout/                     ← Tất cả layout files
│   ├── drawable/                   ← Backgrounds, icons, buttons
│   ├── values/                     ← Dimens, strings, styles (phone)
│   ├── values-sw600dp/             ← Dimens (tablet)
│   └── menu/                       ← Menu files
└── java/com/smartcare/oximetry/sample/
    ├── RecordActivity.java         ← Activity chính
    ├── PatientInfoPopup.java       ← Popup đầu tiên
    ├── FileManagerActivity.java    ← File Manager
    └── CodeHistoryAdapter.java     ← Adapter codes
```

---

## ⚡ TIPS

1. **Layout files** → Sửa giao diện, bố cục
2. **Drawable files** → Sửa màu sắc, background, icon
3. **Dimens files** → Sửa kích thước (có riêng cho tablet)
4. **Strings files** → Sửa văn bản
5. **Styles files** → Sửa theme, màu sắc toàn app

---

**💡 Lưu ý:** Xem file `UI_MAP.md` để có bản đồ chi tiết đầy đủ hơn!

