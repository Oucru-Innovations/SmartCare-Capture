# Flow 06 — File Management / Quản lý file

> **Tầng 2 · Flow 6.** Màn hình `FileManagerActivity` để duyệt file đã ghi (pending) và đã upload (uploaded),
> upload thủ công, mở/xóa file, kèm các cơ chế fallback để liệt kê file trên thiết bị "khó tính".

---

## 1. Tóm tắt / Summary

- `FileManagerActivity` có **2 tab**: **Pending** (chưa upload) và **Uploaded** (đã upload), đọc từ
  `Download/OximeterData`.
- Danh sách file được tổng hợp từ **nhiều nguồn** qua `OximeterDataFileIndex` (gộp `AppRecordingRegistry` + MediaStore +
  đĩa + SAF) để chắc chắn không sót file trên các máy mà MediaStore trả thiếu (đặc biệt Samsung + MTP).
- Có **observer** theo dõi MediaStore để cập nhật live khi đang ghi.
- **Ước lượng thời lượng CSV** trên UI: `sizeBytes / BYTES_PER_RECORDING_HOUR` với
  `BYTES_PER_RECORDING_HOUR = 10 MiB` ≈ 1 giờ.
- Upload từ màn hình này **bỏ qua URI writer đang mở** (cùng open-file guard với coordinator).

---

## 2. Sequence diagram

```mermaid
sequenceDiagram
    participant U as User
    participant FMA as FileManagerActivity
    participant IDX as OximeterDataFileIndex
    participant COORD as PendingFileUploadCoordinator
    participant SAF as OximeterDataTreeAccess

    U->>FMA: mở màn hình
    FMA->>FMA: ensureStorageAccessThenLoad()
    Note over FMA: Thứ tự khôi phục quyền:<br/>READ_EXTERNAL_STORAGE → MANAGE_EXTERNAL_STORAGE<br/>(All files) settings → SAF folder picker
    FMA->>IDX: queryPendingFiles() / queryUploadedFiles()
    Note over IDX: Gộp AppRecordingRegistry + MediaStore + đĩa + SAF
    IDX-->>FMA: danh sách FileEntry (gộp đa nguồn)
    alt Danh sách rỗng
        FMA->>FMA: prompt MANAGE_EXTERNAL_STORAGE (All files)
        opt Từ chối / không khả dụng
            FMA->>SAF: openOximeterDataFolderPicker() (SAF fallback)
            SAF-->>FMA: persistTreeUri()
        end
    end
    U->>FMA: bấm Upload trên 1 dòng
    FMA->>COORD: uploadSessionFile(uri)
    Note over COORD: Bỏ qua nếu URI == writer đang mở
    U->>FMA: chạm tên file → mở / bấm xóa
```
![alt text](flow06-file-startup.png)
---

## 3. Nguồn dữ liệu liệt kê file / Where the file list comes from

`OximeterDataFileIndex` **gộp** nhiều nguồn để ra danh sách cuối:
- `AppRecordingRegistry` (sổ đăng ký URI đã ghi — cứu khi MediaStore thiếu `RELATIVE_PATH`).
- MediaStore Downloads + MediaStore Files volume.
- Quét đĩa trực tiếp (`Download/OximeterData`, kể cả thư mục typo `OximetaData`).
- Cây tài liệu SAF (`OximeterDataTreeAccess`) nếu user đã cấp quyền thư mục.

Chỉ nhận các file là dữ liệu app: `SmartCareCsv*.csv` hoặc `Annotation_*.json` (loại trừ `Annotations.json` tổng).

### Thứ tự khôi phục quyền / Access recovery order
Khi danh sách pending rỗng / liệt kê đĩa thiếu, File Manager leo thang theo thứ tự:
1. Xin **`READ_EXTERNAL_STORAGE`** (API 29–32) qua `ensureStorageAccessThenLoad()`.
2. Prompt **`MANAGE_EXTERNAL_STORAGE`** (All files access) trên API 30+.
3. Fallback **SAF folder picker** (`OximeterDataTreeAccess` / `openOximeterDataFolderPicker()`).

### Ước lượng thời lượng / Duration estimate
Với mỗi dòng CSV, thời lượng ước lượng = `sizeBytes / RecordingConstants.BYTES_PER_RECORDING_HOUR`
(`10 MiB` ≈ 1 giờ).

---

## 4. Các thao tác / Operations

| Thao tác | Method |
|---|---|
| Tải lại danh sách (nền) | `refreshFileLists()` / `refreshFileListsQuiet()` |
| Upload thủ công 1 file | dòng Pending → `PendingFileUploadCoordinator.uploadSessionFile()` (bỏ qua URI writer đang mở) |
| Mở file | `openCsvFile()` (qua `ACTION_VIEW` + FileProvider/content URI) |
| Xóa file đã upload | `deleteUploadedFile()` |
| Quan sát thay đổi khi đang ghi | observer `MediaStore.Downloads` (đăng ký ở `onResume`, hủy ở `onPause`) |
| Prompt quyền All files | `showManageAllFilesAccessRationaleDialog()` → `openManageAllFilesAccessSettings()` |
| Fallback quyền thư mục | `openOximeterDataFolderPicker()` (SAF `ACTION_OPEN_DOCUMENT_TREE`) |

---

## 5. Đọc code ở đâu / Where to read the code

| Việc | File · method |
|---|---|
| Vòng đời màn hình | `FileManagerActivity.onCreate/onResume/onPause/onDestroy` |
| Liệt kê file (gộp nguồn) | `OximeterDataFileIndex.queryPendingFiles()/queryUploadedFiles()` |
| Adapter danh sách | inner `CsvFileAdapter` (pending), `UploadedFileAdapter` (uploaded), `CsvFile` (wrapper) |
| Quyền SAF | `OximeterDataTreeAccess.getPersistedTreeUri()/persistTreeUri()` |
| Sổ đăng ký file | `AppRecordingRegistry` |

---

## 6. Liên quan / Related

- [flow-05 — File upload](flow-05-file-upload.md)
- Tầng 3: [module Data/Storage](../3-modules/module-data-storage.md), [module UI/Recording](../3-modules/module-ui-recording.md)
