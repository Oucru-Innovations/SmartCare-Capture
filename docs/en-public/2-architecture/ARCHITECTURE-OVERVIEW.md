# Tier 2 — Architecture Overview

> **Goal:** Explain **how the system operates** at a high level, then guide you to each detailed **flow**.
> This is **Tier 2**. If you have not read [Tier 1 — Project Overview](../1-overview/PROJECT-OVERVIEW.md), please read it first.

---

## 1. The 5 layers

The app is divided into **5 layers**, with data flowing from device → UI/Service → file → server.

```mermaid
flowchart TD
    subgraph DEV["Device Layer"]
        OX["SmartCare Oximeter<br/>(BLE hardware)"]
        SCO["Vendor BLE library<br/>(proprietary)"]
        ADP["OximeterSDKAdapter<br/>(implements IDeviceAdapter)"]
        PST["PacketStatsTracker<br/>(device counter gaps)"]
        OX -->|BLE packets| SCO
        SCO -->|android.os.Message| ADP
    end

    subgraph UI["UI Layer"]
        RA["RecordActivity<br/>(SmartCareHandler)"]
        FMA["FileManagerActivity"]
        POP["Popups<br/>(PatientInfo, ChunkConfig, Annotate, BugReport)"]
    end

    subgraph SVC["Service Layer"]
        ODS["OximeterDataService<br/>(foreground service + wake lock)"]
        BUF["OximeterDataBuffer<br/>(accumulates 100 samples)"]
    end

    subgraph DATA["Data Layer"]
        WRT["OximeterDataWriterFunctions<br/>(CSV writer + split)"]
        CSV["CSV files<br/>(Download/OximeterData via MediaStore)"]
        IDX["OximeterDataFileIndex<br/>AppRecordingRegistry"]
    end

    subgraph NET["Network Layer"]
        COORD["PendingFileUploadCoordinator<br/>(retry, move to Uploaded/)"]
        UP["FileUploader"]
        RC["RetrofitClient + ApiService"]
        SRV["Remote research server<br/>(study-specific)"]
    end

    ADP -->|Message via Handler| RA
    RA -->|onPacketReceived| PST
    PST -->|minute summaries| LOG["FileLogger"]
    RA -->|addDataPoint| ODS
    ODS --> BUF
    BUF -->|ProcessedData every 100 samples| WRT
    WRT --> CSV
    WRT --> IDX
    RA -->|start/bind, patientCode| ODS
    POP --> RA
    CSV --> COORD
    IDX --> COORD
    COORD --> UP --> RC -->|HTTPS multipart| SRV
    FMA --> COORD
    POP -->|bug-report logs| UP
```

---

## 2. Responsibility per layer

| Layer | Primary Files | Responsibility |
|---|---|---|
| **Device** | `device/OximeterSDKAdapter`, `device/IDeviceAdapter`, `device/OximeterDataPacket`, `device/OximeterDataListener`, `PacketStatsTracker` | Isolates the vendor BLE library; translates callbacks into Handler messages; tracks device packet-counter gaps for diagnostics. |
| **UI** | `RecordActivity`, `FileManagerActivity`, `PatientInfoPopup`, `ChunkFileConfigPopup`, `ShowAnnotate`, `AnnotationHistory`, `BugReportDialog` | Renders PPG/HR/SpO₂/battery waves; inputs patient codes; controls start/stop; manages & uploads files manually; bug-report log upload. |
| **Service** | `OximeterDataService`, `OximeterDataBuffer`, `RecordingSessionPrefs`, `RecordingConstants` | Runs continuously in the background (foreground service + wake lock); batches data; survives process termination. |
| **Data** | `OximeterDataWriterFunctions`, `OximeterDataFileIndex`, `AppRecordingRegistry`, `OximeterDataTreeAccess` | Writes CSV via MediaStore; splits files periodically; tracks file state (pending/uploaded). |
| **Network** | `PendingFileUploadCoordinator`, `FileUploader`, `network/RetrofitClient`, `network/ApiService`, `config/ApiConfig`, `config/NetworkConfig` | Uploads CSV (multipart) with retries; moves uploaded files to `Uploaded/`; build-time URL/timeouts (internal). |
| **App** | `SmartCareApplication`, `FileLogger`, `MemoryDiagnostics`, `AnnotationUploadScheduler`, `UploadAnnotationWorker` | Initializes logging + crash handler; schedules daily cleanup/upload at 17:00. |

> For detailed class definitions by module, see **Tier 3** — [`MODULES-INDEX.md`](../3-modules/MODULES-INDEX.md).

---

## 3. Two key design principles

### 3.1. UI and file writing are TWO separate branches

`RecordActivity` (Activity) is only responsible for **rendering graphs**. The **actual data recording** is handled by `OximeterDataService` (background service).
Thanks to this decoupling, the app **continues to record even when the UI is hidden** (screen turned off, app put in background).

### 3.2. Isolating the vendor library behind an Adapter

The vendor pulse oximeter library is accessed only through `OximeterSDKAdapter` (which implements `IDeviceAdapter`).
When changing hardware or library versions, **only the adapter and Gradle dependencies need to change**, leaving `RecordActivity` untouched.
See [flow-02 — Hardware / vendor library changes](flow-02-bluetooth-connection.md#5-hardware--vendor-library-changes).

---

## 4. The flows

Each flow is described in its own file, complete with sequence/state diagrams and code references:

| # | Flow | File | Summary |
|---|---|---|---|
| 1 | **App startup** | [flow-01-app-startup.md](flow-01-app-startup.md) | App launches, initializes logging/crash handler, forces patient code input, restores sessions if any. |
| 2 | **Bluetooth connection** | [flow-02-bluetooth-connection.md](flow-02-bluetooth-connection.md) | Scan, connect, auto-reconnect, filter unexpected BLE events, vendor adapter. |
| 3 | **Data recording** | [flow-03-data-recording.md](flow-03-data-recording.md) | A single sample flows from device → buffer (batches 100) → CSV; file splitting; background execution. |
| 4 | **Annotation** | [flow-04-annotation.md](flow-04-annotation.md) | Create/edit/delete notes, save JSON, upload. |
| 5 | **File upload** | [flow-05-file-upload.md](flow-05-file-upload.md) | Upload queue, 3-try retry, move to `Uploaded/`, 17:00 daily scheduler, bug-report log upload path. |
| 6 | **File management** | [flow-06-file-management.md](flow-06-file-management.md) | Browse pending/uploaded files, manual upload, open/delete files, SAF fallback. |

---

## 5. Resilience at a glance

This is why the architecture may seem complex. The main mechanisms (detailed in each flow) include:

- **Foreground service + wake lock** → continue recording when the screen is off.
- **`START_STICKY` + `RecordingSessionPrefs`** → if the system kills the process, the service automatically restarts and **continues** the previous session.
- **Time-based CSV splitting** → a crash or error only corrupts a small segment, not the entire session.
- **BLE pause closes CSV without empty reopen** → `splitFileOnBlePause()` finalizes the current segment (≥ 60s) and does **not** reopen; the next CSV opens lazily on the first packet after reconnect.
- **`onTaskRemoved` keeps the service alive** when the user swipes the app out of Recents (allowing BLE to reconnect).
- **Crash handler** writes crash logs to a file before the app terminates.
- **Upload skips the currently open writer URI** → never touches the active recording file, avoiding writer corruption.

---

## 6. Configuration

Server URL, credentials, and vendor library artifacts are supplied at **build time** via git-ignored local configuration — not documented in this public manual.
For a summary, see [Tier 1 §8 — Configuration & deployment](../1-overview/PROJECT-OVERVIEW.md#8-configuration--deployment).

---

*Keep this updated when the architecture changes.*
