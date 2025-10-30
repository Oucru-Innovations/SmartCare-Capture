

## SMARTCARE CAPTURE
This is an Android application for recording, displaying, and uploading physiological signals from SmartCare.

## File Structure

### Main Package: `com.smartcare.oximetry.sample`

#### Core Activities
- **`RecordActivity.java`** - Main activity for recording and displaying oximeter data
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

#### File Operations
- **`FileUploader.java`** - Handles file upload to server
  - Uploads CSV files and annotation data
  - Manages upload progress and callbacks
  - Configurable server URL

- **`FileLogger.java`** - Application logging system
  - Logs app events to file for debugging
  - Provides different log levels (INFO, WARNING, ERROR)

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

#### Annotations
- **`AnnotationHistory.java`** - Displays annotation history
- **`CodeHistoryAdapter.java`** - Adapter for annotation history list
- **`AnnotationUploadScheduler.java`** - Schedules daily annotation uploads
- **`UploadAnnotationWorker.java`** - Background worker for uploading annotations

#### Bluetooth
- **`BluetoothAutoConnector.java`** - Automatic Bluetooth device discovery and connection

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
- Notifications (foreground service)
- Battery optimization exemption

## Dependencies
- AndroidPlot - Chart/graph library for waveform display
- jDSP - Signal processing (detrending)
- SmartCare Oximetry Library (`scolib-2.0.0.aar`) - Oximeter connection management

## Building the Project

1. Open the project in Android Studio
2. Sync Gradle dependencies
3. Connect an Android device or start an emulator
4. Run the application


