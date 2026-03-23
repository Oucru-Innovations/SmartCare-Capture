# SmartCare Oximetry - Development TODO


### Handle Sudden App Closure
**Problem:** Sudden app closure while collecting data causes file loss or corruption when collecting data next time.

**Solution:** Implement robust file closing mechanisms in lifecycle callbacks and add file integrity checks/recovery on app startup.

**Files:** `RecordActivity.java`, `OximeterDataService.java`, `OximeterDataWriterFunctions.java`

---

### Bluetooth Connection Reliability
**Problem:** Silent disconnections occur without user notification. Reconnection logic lacks proper error handling and feedback.

**Solution:** Implement robust connection management with proper lifecycle handling, user feedback, and reconnection strategies.

**Files:** `RecordActivity.java`, `OximeterDataService.java`, `BluetoothAutoConnector.java`

**Status:** PARTIALLY COMPLETED. Reconnection logic improved (2s delay logic after disconnecting device from Vivalink app, and duplicate connect fix).

---

### Data Loss Prevention
**Problem:** Data packets are lost when the service is unavailable or during connection interruptions.

**Solution:** Implement buffering or queuing mechanism to retain data during interruptions and restore it when service becomes available.

**Files:** `OximeterDataBuffer.java`, `OximeterDataService.java`

---

### Annotation State Management
**Problem:** Static variables for `annotationHeartRate` and `annotationSpO2` lead to stale values across sessions.

**Solution:** Use proper state management (ViewModel, SavedState, or similar) with appropriate lifecycle scoping.

**Files:** `RecordActivity.java`, `ShowAnnotate.java`

---

### Code Organization
**Problem:** `RecordActivity.java` (~1900 lines) handles multiple responsibilities: Bluetooth, UI, permissions, and data management.

**Solution:** Refactor into separate focused classes using appropriate design patterns (e.g., managers, controllers, repositories).

**Files:** `RecordActivity.java`

**Status:** PARTIALLY COMPLETED. Refactored SDK Connection out of RecordActivity by introducing the `OximeterSDKAdapter` Proxy Pattern.

---

### Permission Management
**Problem:** Permission checks are scattered across multiple locations. Version-specific logic is mixed with business logic.

**Solution:** Centralize permission handling with clear abstractions for different Android API levels.

**Files:** `RecordActivity.java`

---

### UI Performance
**Problem:** UI updates on every data packet cause performance issues and jank.

**Solution:** Implement update throttling or batching mechanisms to reduce UI refresh frequency.

**Files:** `RecordActivity.java` (SmartCareHandler class)

---

### Memory Management
**Problem:** Static references to Handler and BroadcastReceiver instances risk memory leaks.

**Solution:** Use lifecycle-aware components or WeakReferences, and ensure proper cleanup in lifecycle methods.

**Files:** `RecordActivity.java`, `BluetoothAutoConnector.java`, `AnnotationHistory.java`

---

## Code Quality

### Configuration Management
**Problem:** Server URLs, authentication tokens, UUIDs, and constants are hardcoded across multiple files.

**Solution:** Centralize configuration values using build configuration or dedicated config classes.

**Files:** `FileUploader.java`, `BluetoothAutoConnector.java`, `OximeterDataBuffer.java`, `app/build.gradle`

**Status:** COMPLETED. Centralized configuration using `BuildConfig`, `ApiConfig`, and `NetworkConfig`.

---

### Code Cleanup
**Problem:** Extensive commented-out code blocks reduce readability and maintainability.

**Solution:** Remove obsolete commented code and unused dependencies. Document important information separately if needed.

**Files:** `RecordActivity.java`, `OximeterDataBuffer.java`, `FileUploader.java`, `app/build.gradle`

---

### Logging Consistency
**Problem:** Mixed use of Android Log and FileLogger creates inconsistent logging patterns.

**Solution:** Standardize on a single logging approach with proper levels and sensitive data handling.

**Files:** All `.java` files

### Testing
Implement unit and integration tests for core components. Consider starting with: `OximeterDataBuffer`, `FileUploader`, `OximeterDataWriterFunctions`.

## Notes

- The app currently requires Android 10+ (minSdk 29) and targets Android 14+ (targetSdk 35)
- The main library dependency is `scolib-2.0.0.aar` for oximeter communication
- Chart rendering uses `androidplot-core:0.6.1`
- File uploads use Retrofit 2.9.0 and OkHttp 4.9.0
- Background work uses AndroidX WorkManager 2.7.1
- Signal processing uses jDSP 0.2.0

---

## Device SDK Integration Guide
Whenever the manufacturer provides a new SDK file (e.g., `.aar`), follow these 3 exact steps to replace the hardware layer without modifying any UI or plotting logic in `RecordActivity.java`.

### Step 1: Update the Library
Place the new `.aar` or `.jar` file into the `app/libs` directory. Open `app/build.gradle` and update the dependency name to match the new SDK file. 

### Step 2: Edit `OximeterSDKAdapter.java`
Open `app/src/main/java/com/smartcare/oximetry/sample/device/OximeterSDKAdapter.java`. This is the **ONLY** file you need to modify.
1. Update your `import` statements to load the new SDK.
2. Inside `OximeterSDKAdapter`, replace the instantiation of the old SDK with the new one.
3. Update the `connect()`, `disconnect()` and `startRecording()` methods to trigger the corresponding methods of the new SDK.

### Step 3: Map Data back via Handler Message
When your new SDK triggers a callback reflecting a successful connection or new data (SpO2, heart rate, etc.), you must construct a fake `Message` mimicking the old SDK's structure, and send it through the `Handler`.

Example Template inside `OximeterSDKAdapter.java`:
```java
// On receiving data from the New SDK...
Message msg = handler.obtainMessage(ConnectionManager.MESSAGE_PACKET);
Bundle data = new Bundle();

// Map new SDK values using the REQUIRED standard Keys
// data.putInt(ConnectionManager.KEY_DATA_PULSE, newSdkHeartRate);
// data.putDouble(ConnectionManager.KEY_DATA_SPO2, newSdkSpO2);
// data.putInt(ConnectionManager.KEY_DATA_PLETH, newSdkPlethValue);

// msg.setData(data);
// handler.sendMessage(msg); // This dispatches everything flawlessly to RecordActivity
```
Done! You are not required to open or modify `RecordActivity.java`.


