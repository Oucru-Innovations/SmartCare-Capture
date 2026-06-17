# Flow 06 — File Management

> **Tier 2 · Flow 6.** The `FileManagerActivity` screen browses recorded files (pending) and uploaded files (uploaded), handles manual uploads, and performs open/delete actions, along with fallback mechanisms to list files on strict devices.

---

## 1. Summary

- `FileManagerActivity` consists of **2 tabs**: **Pending** (not yet uploaded) and **Uploaded** (successfully uploaded), reading from the `Download/OximeterData` directory.
- The file list is aggregated from **multiple sources** via `OximeterDataFileIndex` to ensure no files are missed on devices where MediaStore returns incomplete results (especially Samsung devices and over MTP).
- A MediaStore **observer** tracks real-time file updates while recording is active.

---

## 2. Sequence diagram

```mermaid
sequenceDiagram
    participant U as User
    participant FMA as FileManagerActivity
    participant IDX as OximeterDataFileIndex
    participant COORD as PendingFileUploadCoordinator
    participant SAF as OximeterDataTreeAccess

    U->>FMA: Open screen
    FMA->>FMA: ensureStorageAccessThenLoad()
    FMA->>IDX: queryPendingFiles() / queryUploadedFiles()
    IDX-->>FMA: List of FileEntries (merged sources)
    alt List is empty
        FMA->>SAF: openOximeterDataFolderPicker() (SAF fallback)
        SAF-->>FMA: persistTreeUri()
    end
    U->>FMA: Click Upload on a row
    FMA->>COORD: uploadSessionFile(uri)
    U->>FMA: Click file name → open / click delete
```

![File Management Flow Diagram](../../vi/2-architecture/flow06-file-startup.png)

---

## 3. Where the file list comes from

`OximeterDataFileIndex` **merges** several data sources to compile the final file list:
- Direct disk scanning (`Download/OximeterData`, including the misspelled `OximetaData` folder).
- MediaStore Downloads + MediaStore Files volumes.
- `AppRecordingRegistry` (a registry of recorded file URIs — acts as a fallback when MediaStore lacks `RELATIVE_PATH`).
- Storage Access Framework (SAF) document tree (`OximeterDataTreeAccess`) if the user has granted directory access.

Only files that belong to the application are listed: `SmartCareCsv*.csv` or `Annotation_*.json` (excluding the master `Annotations.json` file).

---

## 4. Operations

| Operation | Method |
|---|---|
| Reload file list (background) | `refreshFileLists()` / `refreshFileListsQuiet()` |
| Manually upload 1 file | Pending row → `PendingFileUploadCoordinator.uploadSessionFile()` |
| Open file | `openCsvFile()` (via `ACTION_VIEW` + FileProvider/content URI) |
| Delete uploaded file | `deleteUploadedFile()` |
| Observe changes during recording | `MediaStore.Downloads` observer (registered in `onResume`, unregistered in `onPause`) |
| Directory permission fallback | `openOximeterDataFolderPicker()` (SAF `ACTION_OPEN_DOCUMENT_TREE`) |

---

## 5. Where to read the code

| Task | File · method |
|---|---|
| Screen lifecycle | `FileManagerActivity.onCreate/onResume/onPause/onDestroy` |
| List files (merged sources) | `OximeterDataFileIndex.queryPendingFiles()/queryUploadedFiles()` |
| List adapters | inner `CsvFileAdapter` (pending), `UploadedFileAdapter` (uploaded), `CsvFile` (wrapper) |
| SAF permissions | `OximeterDataTreeAccess.getPersistedTreeUri()/persistTreeUri()` |
| File registry | `AppRecordingRegistry` |

---

## 6. Related

- [flow-05 — File upload](flow-05-file-upload.md)
- Tier 3: [Data/Storage module](../3-modules/module-data-storage.md), [UI/Recording module](../3-modules/module-ui-recording.md)
