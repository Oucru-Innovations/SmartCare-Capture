

## SMARTCARE CAPTURE
This is an Android application for recording, displaying, and uploading physiological signals from SmartCare.

## 📚 Documentation

Tài liệu được tổ chức theo **4 tầng** (tổng quan → chi tiết). Điểm vào: **[docs/README.md](docs/README.md)**.

- **Tầng 1 — [Project Overview](docs/1-overview/PROJECT-OVERVIEW.md)** — App dùng để làm gì; bài toán, glossary, tech stack, cài đặt & cấu hình. **Đọc trước tiên / Start here.**
- **Tầng 2 — [Architecture Flow](docs/2-architecture/ARCHITECTURE-OVERVIEW.md)** — Hệ thống hoạt động thế nào: kiến trúc 5 tầng + 6 flow chi tiết (startup, BLE, recording, annotation, upload, file management).
- **Tầng 3 — [Module / Class Reference](docs/3-modules/MODULES-INDEX.md)** — "Chức năng X nằm ở file/class nào?" — bản đồ 38 class theo 4 module.
- **Tầng 4 — [Function Documentation](docs/4-functions/)** — Chi tiết từng hàm; mã nguồn `.java` cũng đã được bổ sung Javadoc.

## ⚠️ Important Notice

There are **two types of SmartCare oximeters** to be aware of:

1. **Silver Label Type** – Devices with a **silver information sticker** on the back are **only compatible with older Android versions** (up to Android 10 or below). These devices may experience connection issues on newer Android versions.  
2. **Printed Label Type** – Devices with **printed information directly on the back** are **fully compatible with all Android versions** and do not have such limitations.

Before using the application, please **check your device’s Android version** and **grant the required permissions** according to the version’s security requirements to ensure stable operation.

## File Structure

### Main Package: `com.smartcare.oximetry.sample`

#### Core Activities
- **`RecordActivity.java`** - Main activity for recording and displaying oximeter data (~2600 lines)
  - Manages Bluetooth connection to pulse oximeters
  - Displays real-time PPG waveforms using AndroidPlot
  - Shows heart rate, SpO₂, and battery status
  - Handles patient information and data recording sessions

- **`FileManagerActivity.java`** - Manages recorded data files
  - Displays CSV files and uploaded files
  - Handles file upload operations
  - Provides file management interface

#### Services
- **`OximeterDataService.java`** - Foreground service for continuous data collection
  - Runs in background to ensure uninterrupted recording
  - Writes oximeter data to CSV files
  - Manages data buffering and file chunking

#### Data Management
- **`OximeterDataBuffer.java`** - Buffers incoming oximeter data
- **`OximeterDataWriterFunctions.java`** - Handles CSV file writing operations
  - Creates new recording sessions
  - Writes data points to CSV files
  - Manages file chunking based on time intervals

#### File Operations & Networking
- **`config/`** - Contains `ApiConfig` and `NetworkConfig` mappings derived from `BuildConfig` for centralized API base URL and tokens.
- **`network/`** - Uses Retrofit and OkHttp for resilient network communication (`ApiService`, `RetrofitClient`).
- **`FileUploader.java`** - Orchestrates file upload to server utilizing the `ApiService`.
  - Uploads CSV files and annotation data
  - Manages upload progress and callbacks

- **`FileLogger.java`** - Application logging system with **daily file rotation**
  - Writes one log file (or multiple parts) per calendar day under `{externalFilesDir}/Logs`
  - Provides different log levels (INFO, WARNING, ERROR)
  - Supports bug-report selection/upload of daily log files

#### UI Components
- **`PatientInfoPopup.java`** - Patient information entry dialog
  - Collects patient/research codes
  - Validates and saves patient identifiers

- **`ChunkFileConfigPopup.java`** - Configuration for file chunking
  - Enables/disables file splitting
  - Sets time interval for chunks

- **`ShowAnnotate.java`** - Annotation menu system
  - Allows marking events during recording
  - Manages annotation history

- **`BugReportDialog.java`** - Bug report dialog
  - Lets staff describe an issue and upload selected daily log files to the server

#### Annotations
- **`AnnotationHistory.java`** - Displays annotation history
- **`CodeHistoryAdapter.java`** - Adapter for annotation history list
- **`AnnotationUploadScheduler.java`** - Schedules daily annotation uploads
- **`UploadAnnotationWorker.java`** - Background worker for uploading annotations

#### Bluetooth
- **`BluetoothAutoConnector.java`** - Legacy / unused; automatic Bluetooth discovery helper kept for reference (connection/reconnect logic lives in `RecordActivity`)

## Key Functionalities

### 1. Device Connection
- Auto-discovery of SmartCare oximeters
- Manual device pairing and connection
- Supports up to 2 saved devices
- Auto-reconnection capability

### 2. Data Recording
- Real-time PPG waveform display
- Records: SpO₂, heart rate, plethysmography, perfusion index, battery level
- Continuous CSV file generation
- Optional file chunking by time interval

### 3. Data Upload
- Uploads recorded CSV files to server
- Batch upload of annotation data
- Progress tracking and error handling

### 4. Patient Management
- Patient/research code entry
- Code validation and formatting
- Persistent storage of patient information

### 5. Annotations
- Event marking during recording (e.g., cough, movement)
- Annotation history viewing

### 6. Background Operation
- Foreground service ensures recording continues
- Battery optimization exemption requested
- Handles service disconnection recovery

### 7. Bug Report
- Staff can report issues from the menu and upload selected daily log files

## Resource Files

### Layouts (`res/layout/`)
- `activity_main.xml` - Main recording interface
- `activity_file_manager.xml` - File management screen
- `patient_info_popup.xml` - Patient info dialog
- Various item layouts for lists and dialogs

### Drawables (`res/drawable/`)
- UI backgrounds, borders, buttons
- Icons for menu items and actions

### Values (`res/values/`)
- `strings.xml` - Text resources
- `styles.xml` - UI styling
- `dimens.xml` - Dimensions

## Permissions Required
- Bluetooth (SCAN, CONNECT)
- Location (for Bluetooth scanning)
- Storage (for file operations)
- MANAGE_EXTERNAL_STORAGE (All files access; preferred on API 30+)
- Notifications (foreground service)
- Battery optimization exemption

## Dependencies
- AndroidPlot - Chart/graph library for waveform display
- jDSP - Signal processing (detrending)
- SmartCare Oximetry Library (`scolib-2.3.0.aar`) - Oximeter connection management

## Building the Project

1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Connect an Android device or start an emulator
4. Run the application

