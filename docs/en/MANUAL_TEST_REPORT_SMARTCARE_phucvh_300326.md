# MANUAL TEST REPORT - SMARTCARE CAPTURE

**Test Date:** March 12, 2026  
**Overall Status:** PASSED  
**Testers:** Antigravity (AI Assistant) & USER  

---

## 1. Test Specification
### 1.1 Device Configuration

| Specification | Details |
| :--- | :--- |
| **Model** | Samsung Galaxy Tab A 8.0 (2019) |
| **Model Code** | SM-T295 |
| **Operating System** | Android 9/10/11 (Optimized for Scoped Storage) |
| **Processor** | Snapdragon 429 |
| **RAM** | 2 GB |
| **Display** | 8.0 inches, 800 x 1280 pixels |
| **Connectivity** | Bluetooth 4.2, Wi-Fi, 4G LTE |
---
### 1.2 File Validation Criteria
- **File Name**: `SmartCareCsv_<patient>_dd.MM.yyyy.hh.mm.ss_dd.MM.yyyy.hh.mm.ss.csv` must be generated according to connect and disconnect events.
- **File Timestamp**: The timestamps in the file name must match the first and last data records +- 2 seconds within the file.
- **File Length**: The number of data rows must correspond to the duration of the recording (ideal rate: 1 record/sec) +- 5%.
- **Sampling Rate**: Total length of PLETH values (an array of integers per rows) must be consistent with the recording duration (ideal rate: 100 values/sec) +- 5%.

### 1.3 PPG shape validation
- **PPG signal shape**: must follow the shape of double peak PPG signal with no sharp abnormal peak during increasment / decreasement.  
## 2. Test Scenarios and Results

### A. Bluetooth Connection & Button Logic (Fixed Bug)
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| BT-01 | Initial Connection | The "Connect" button switches to "Disconnect" (Stop) once data streaming begins. | Success |
| BT-02 | Automatic Reconnection | When the oximeter is turned off and back on, the app reconnects and forces the button state to "Disconnect". | Success |
| BT-03 | Button State Failsafe | Even if receiving data while the disconnect logic is pending, the button must correctly display "Disconnect". | Success |

### B. Chunk File Configuration
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| CF-01 | Activate Chunk Files | Successfully enable "Chunk File Creation" switch in the configuration popup. | Success |
| CF-02 | 3-Minute Cycle | A new `SmartCareCsvChunk_*.csv` file is generated in the `StreamModel` folder every 3 minutes of continuous recording. | Success |
| CF-03 | Directory Verification | Files are correctly stored in the `Android/data/.../files/OximeterData/StreamModel` directory. | Success |

### C. Annotation System
| ID | Test Case | Expected Result | Status |
| :--- | :--- | :--- | :--- |
| AN-01 | Add New Annotation | Successfully save notes regarding invalid data or missed collection with patient codes. | Success |
| AN-02 | Local History Storage | The `Annotations.json` file is updated correctly (maximum of 5 notes per patient). | Success |
| AN-03 | Server Upload Files | Individual JSON files are created in the `OximeterData` folder for server synchronization. | Success |

### D. 24/7 Keep-Alive Mechanism
| ID    | Test Case | Expected Result | Status |
|:------| :--- | :--- | :--- |
| KA-01 | CPU Persistence (WakeLock) | CPU remains active even when the screen is off (Indefinite WakeLock). | Success |
| KA-02 | Service Priority | Foreground service notification is set to "High Priority" to prevent system termination under low RAM. | Success |
| KA-03 | Background Scanning | App continues to scan and reconnect to Bluetooth devices even after 15+ minutes of screen inactivity. | Success |
| KA-04 | Screen Timeout Data Collection | Wear >30 mins in settings to screen off, continue wearing for >30 mins more from turn off timepoint | Validate with file validation criteria | Success |
| KA-05 | Intermittent Device Connection | Same setting as above: 30 mins screen on, 30 mins screen off, device off for 15 mins, device on again for 5 mins | Validate with file validation criteria | Success |
| KA-06 | Multiple BLE connections concurrent | While collecting data from smartcare, switch to vivalink/shimmer app to connect and get vivalink data| Validate with file validation criteria. Check the ppg signal shape during and after vivalink synchronization. | Success |


---

## 3. Recommended Device Settings for SM-T295
To ensure long-term stability for 24/7 monitoring, the following settings must be applied:
1.  **Battery Optimization:** Set SmartCare Capture to **"Unrestricted"**.
2.  **App Pinning:** Use Android's **"Pin the app"** feature to dedicate system resources to the data collection process.
3.  **Scoped Storage Access:** Use a PC link or specialized file managers (e.g., ZArchiver) to access the `Android/data` folder due to OS restrictions.

---
**Conclusion:** The system is fully verified and ready for continuous 24/7 data collection in the field.
