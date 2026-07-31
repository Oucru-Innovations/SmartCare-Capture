# Tier 3 — Modules Index

> **Goal:** Answer the question **"Where is the code for this feature located?"**. This is **Tier 3** — mapping features to modules and classes. For specific function details, see **Tier 4** ([`docs/4-functions/`](../4-functions/)).

All **38 classes** (within the package `com.smartcare.oximetry.sample` and its sub-packages) are grouped into **4 modules**:

| Module | Detailed Document | Class Count | Role |
|---|---|---|---|
| UI / Recording | [module-ui-recording.md](module-ui-recording.md) | 9 | Screens, popups, adapters, app lifecycle |
| Device / Bluetooth | [module-device-bluetooth.md](module-device-bluetooth.md) | 8 | BLE connection, vendor library wrapper, background service |
| Data / Storage | [module-data-storage.md](module-data-storage.md) | 9 | Sample aggregation, CSV writing, file indexing, session restore |
| Network / Upload | [module-network-upload.md](module-network-upload.md) | 12 | Upload, retry, configurations, 17:00 schedule, file logger |

---

## Quick lookup: "Where is feature X located?"

| I want to find/modify... | Primary Class | Module |
|---|---|---|
| Recording screen, PPG plotting, receiving BLE data | `RecordActivity` (+ `SmartCareHandler`) | UI/Recording |
| Enter/change patient code | `PatientInfoPopup`, `CodeHistoryAdapter` | UI/Recording |
| Chunk file configuration | `ChunkFileConfigPopup` | UI/Recording |
| Event annotations | `ShowAnnotate`, `AnnotationHistory` | UI/Recording |
| Bug report (multi-day log upload) | `BugReportDialog` | UI/Recording |
| File Manager screen | `FileManagerActivity` | UI/Recording |
| App initialization, crash handler, upload schedule | `SmartCareApplication` | UI/Recording |
| Connect/disconnect oximeter, vendor adapter | `OximeterSDKAdapter`, `IDeviceAdapter` | Device/Bluetooth |
| Background recording, wake lock, session recovery | `OximeterDataService` | Device/Bluetooth |
| Filter unexpected BLE events | `BluetoothEventInterceptor` | Device/Bluetooth |
| Packet loss / per-minute BLE stats | `PacketStatsTracker` | Device/Bluetooth |
| Accumulate 100 samples, calculate median | `OximeterDataBuffer` | Data/Storage |
| Write CSV, split files | `OximeterDataWriterFunctions` | Data/Storage |
| List pending/uploaded files | `OximeterDataFileIndex`, `AppRecordingRegistry` | Data/Storage |
| Annotation MediaStore I/O | `AnnotationStorage` | Data/Storage |
| Save session for recovery | `RecordingSessionPrefs` | Data/Storage |
| Constants (split interval, etc.) | `RecordingConstants` | Data/Storage |
| Upload + retry orchestration | `PendingFileUploadCoordinator`, `FileUploader` | Network/Upload |
| HTTP client (upload contract) | `network/ApiService`, `network/RetrofitClient` | Network/Upload |
| Build-time URL/timeouts | `config/ApiConfig`, `config/NetworkConfig` | Network/Upload |
| Daily 17:00 upload schedule | `AnnotationUploadScheduler`, `UploadAnnotationWorker` | Network/Upload |
| Write log to file (daily parts) | `FileLogger` | Network/Upload |

---

## Source tree

```
app/src/main/java/com/smartcare/oximetry/sample/
├── device/                          # Device/Bluetooth Module (partial)
│   ├── IDeviceAdapter.java          #   Adapter interface
│   ├── OximeterSDKAdapter.java      #   Vendor BLE library wrapper
│   ├── OximeterDataListener.java    #   (legacy, unused)
│   └── OximeterDataPacket.java      #   (legacy POJO, unused)
├── network/                         # Network Module (HTTP)
│   ├── ApiService.java
│   ├── RetrofitClient.java
│   └── UploadResponse.java
├── config/                          # Network Module (configuration)
│   ├── ApiConfig.java
│   └── NetworkConfig.java
│
├── RecordActivity.java              # UI/Recording — main screen
├── FileManagerActivity.java         # UI/Recording — file manager
├── ShowAnnotate.java                # UI/Recording — annotations
├── AnnotationHistory.java           # UI/Recording — annotation history
├── BugReportDialog.java             # UI/Recording — bug report / log upload
├── PatientInfoPopup.java            # UI/Recording — patient code input
├── ChunkFileConfigPopup.java        # UI/Recording — file splitting config
├── CodeHistoryAdapter.java          # UI/Recording — recent code adapter
├── SystemBarInsetsHelper.java       # UI/Recording — system bar inset utility
├── SmartCareApplication.java        # UI/Recording — app lifecycle & init
│
├── OximeterDataService.java         # Device/Bluetooth — background service
├── BluetoothAutoConnector.java      # Device/Bluetooth — (legacy, unused)
├── BluetoothEventInterceptor.java   # Device/Bluetooth — ACL event filtering
├── PacketStatsTracker.java          # Device/Bluetooth — BLE packet-loss stats
│
├── OximeterDataBuffer.java          # Data/Storage — batches 100 samples
├── OximeterDataWriterFunctions.java # Data/Storage — CSV writing & splitting
├── OximeterDataFileIndex.java       # Data/Storage — file list indexing
├── OximeterDataTreeAccess.java      # Data/Storage — SAF document tree
├── AppRecordingRegistry.java        # Data/Storage — completed file registry
├── AnnotationStorage.java           # Data/Storage — annotation MediaStore I/O
├── RecordingSessionPrefs.java       # Data/Storage — session persistence
├── RecordingConstants.java          # Data/Storage — constants
├── MemoryDiagnostics.java           # Data/Storage — RAM diagnostics
│
├── PendingFileUploadCoordinator.java# Network/Upload — upload & retry coordinator
├── FileUploader.java                # Network/Upload — HTTP upload worker
├── NetworkHelper.java               # Network/Upload — connectivity check
├── FileLogger.java                  # Network/Upload — daily text logger
├── AnnotationUploadScheduler.java   # Network/Upload — 17:00 daily scheduler
└── UploadAnnotationWorker.java      # Network/Upload — 17:00 daily worker
```

> **Note on "legacy/unused":** `OximeterDataListener`, `OximeterDataPacket`, and `BluetoothAutoConnector` remain in the repository but **are not referenced** by other classes (replaced by the Handler/Message mechanism and custom reconnect logic inside `RecordActivity`). Kept for reference; candidates for future cleanup.
