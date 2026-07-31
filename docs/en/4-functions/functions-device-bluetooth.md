# Functions — Device / Bluetooth

> **Tier 4.** Specific function reference for the Device/Bluetooth module. Overview in [Tier 3 — module-device-bluetooth.md](../3-modules/module-device-bluetooth.md).

---

## IDeviceAdapter (interface)
`app/src/main/java/com/smartcare/oximetry/sample/device/IDeviceAdapter.java`

| Method | Description |
|---|---|
| `setListener(OximeterDataListener)` | Registers callback (unused in the current implementation). |
| `connect()` | Connects to the default/first matching device. |
| `connect(String macAddress)` | Connects by specific MAC address. |
| `disconnect()` | Disconnects the active device. |
| `isRecording()` | Checks if currently recording. |
| `getMacAddress()` | Returns MAC address of the active connected device. |
| `getOximeterModelNumber()` | Returns model string from the SDK. |
| `getSampleRate()` | Returns sample rate (samples/second). |
| `startRecording()` | Starts recording on the active connection. |
| `onDestroy()` | Tears down connection, releases SDK resources. |

---

## OximeterSDKAdapter (implements IDeviceAdapter)
`app/src/main/java/com/smartcare/oximetry/sample/device/OximeterSDKAdapter.java`

| Method | Description |
|---|---|
| `OximeterSDKAdapter(Context, Handler)` | Instantiates adapter, applies **reflection to bypass device name filtering**, creates `ConnectionManager`. |
| `setListener(...)` | **No-op** — data is routed via the Handler. |
| `connect()` / `connect(String)` | Delegates to `connectionManager.connect(...)`. |
| `disconnect()` | Disconnects **only if** recording is active (avoids redundant calls). |
| `isRecording()` | Delegates to `connectionManager.isRecording()`. |
| `getMacAddress()` | MAC from the SDK or `null`. |
| `getOximeterModelNumber()` | Model from the SDK or `"Unknown"`. |
| `getSampleRate()` | Sample rate from the SDK or `0`. |
| `startRecording()` | Delegates to `connectionManager.startRecording()`. |
| `onDestroy()` | Disconnects if recording is active, then nulls `connectionManager`. |

**Workaround (constructor):** uses reflection on obfuscated class `com.smartcare.oximetry.library.a`, field `d` (`String[]`), setting `names[0] = ""` to match all Bluetooth devices instead of hardcoded names (e.g., FC0392). **Review when upgrading `.aar`.**

---

## OximeterDataPacket (POJO, legacy/unused)
`app/src/main/java/com/smartcare/oximetry/sample/device/OximeterDataPacket.java`

- Full-args constructor: `(int pleth, int pulse, double spo2, int deviceId, double battery, int counter, int spo2Status, long redAdc, long irAdc, double perfusionIndex, long packageReceived, int startTime)`.
- Public fields: `pleth, pulse, spo2, deviceId, battery, counter, spo2Status, redAdc, irAdc, perfusionIndex, packageReceived, startTime`. Contains no other methods.

---

## OximeterDataListener (interface, legacy/unused)
`app/src/main/java/com/smartcare/oximetry/sample/device/OximeterDataListener.java`

`onDataReceived`, `onDeviceConnected`, `onDeviceDisconnected`, `onConnectionLost`, `onDeviceNotFound`, `onBluetoothOff`, `onBluetoothUnavailable`, `onPermissionRequired(boolean isFineLocation)`, `onUnknownDevice`, `onNoDataAvailable`.
→ Replaced by `ConnectionManager` + Handler in `RecordActivity`.

---

## OximeterDataService (foreground Service)
`app/src/main/java/com/smartcare/oximetry/sample/OximeterDataService.java`

| Method | Description |
|---|---|
| `onBind(Intent)` | Returns `LocalBinder`. |
| `onStartCommand(Intent, int, int)` | Starts foreground, acquires wake lock, arms buffer; handles null intent (process restart) and `START_FLAG_REDELIVERY`; returns `START_STICKY` if prefs active, else `START_NOT_STICKY`. |
| `onTaskRemoved(Intent)` | If session active → keeps alive; else calls `shutdownRecordingAsync(null)`. |
| `startDataCollection(long, String, boolean, int)` | Arms session + opens CSV for the first time when writer is inactive. |
| `ensureRecordingActive(long, String, boolean, int)` | Reopens writer after BLE pause/recovery; returns true if writer is active. |
| `addDataPoint(double spo2, int spo2Status, long redAdc, double battery, int hr, long irAdc, double pi, int counter, String mac, long pkgRecv, int pleth, long ts)` | Pushes sample to buffer when `isRunning`; logs drops with a throttle (once every 10s). |
| `flushBufferedData()` | Flushes batch < 100 samples before split/close. |
| `stopDataCollection()` | Completes: flush, `endRecording()`, releases wake lock, clears buffer; idempotent via `recordingFinalized`. |
| `shutdownRecordingAsync(Runnable)` | Stops on IO thread, then detaches foreground + calls `stopSelf()` on main thread. |
| `getCompletedRecordFilePath()` | Returns most recent completed file path (`endRecordFilePath`) or `null`. |
| `isRunning()` | Checks if service is actively collecting. |
| `restoreSessionAfterProcessRestart()` (private) | Loads `RecordingSessionPrefs.Session`; restores fields, recreates buffer, re-enters foreground, re-acquires wake lock. Does NOT reopen CSV immediately (deferred to next packet). |
| `acquireWakeLock()` / `releaseWakeLockQuietly()` (private) | `PARTIAL_WAKE_LOCK` tagged `"OximeterDataService::WakeLock"`, reference counting disabled. |

**Inner Class:** `LocalBinder extends Binder` → `getService()`.
**Constants/Fields:** `ACTION_SPLIT_RECORDING` (broadcast for 17:00 split), `public String endRecordFilePath`.
**Notes:** Lazy file creation (no empty CSVs generated); foreground service type `CONNECTED_DEVICE` (API 29+); `shutdownInProgress` (AtomicBoolean) prevents duplicate shutdown attempts.

---

## BluetoothEventInterceptor
`app/src/main/java/com/smartcare/oximetry/sample/BluetoothEventInterceptor.java`

| Method | Description |
|---|---|
| `BluetoothEventInterceptor(Context, Queue<String> macQueue)` | Registers receiver with `SYSTEM_HIGH_PRIORITY`; restores `connectedMacAddress` from prefs. |
| `setConnectedMacAddress(String)` | Updates MAC "our device" (in-memory + persisted). |
| `getConnectedMacAddress()` | Returns monitored MAC. |
| `cleanup()` | Unregisters receiver (called on activity destroy). |

**Workaround:** `abortBroadcast()` for disconnect/connect from devices that do not match our MAC; persists MAC (`BluetoothInterceptorPrefs`/`KEY_CURRENT_MAC`) to survive restarts; if `connectedMacAddress == null` and foreign MAC → allow it (logs "allowing").

---

## PacketStatsTracker
`app/src/main/java/com/smartcare/oximetry/sample/PacketStatsTracker.java`

Singleton that tracks packets received from the device and logs BLE gaps via `KEY_DATA_COUNTER`. Used by `RecordActivity.packetStats`. Main-thread only (no extra synchronization).

**Constants (private):** `MINUTE_MS = 60_000`, `MAX_PLAUSIBLE_GAP = 1000` (larger gaps treated as counter reset/wrap, not loss), `GAP_LOG_THROTTLE_MS = 2_000` (limits detailed gap log spam).

| Method | Description |
|---|---|
| `getInstance()` (static synchronized) | Returns/creates the singleton (survives activity recreation). |
| `start()` | Starts per-minute summary ticker (idempotent); logs `TRACKER_STARTED`. |
| `stop()` | Stops ticker and logs totals (`TRACKER_STOPPED`). |
| `onPacketReceived(int deviceCounter)` | Increments receive counters; if counter jumps by 1…`MAX_PLAUSIBLE_GAP`, counts BLE loss and may log `BLE_PACKET_GAP` (throttled); implausible/backwards counter → `COUNTER_RESYNC` (not counted as loss). Returns counted missing packets (for waveform gaps). |
| `onConnectionReset(String reason)` | Clears device-counter baseline so the first packet after reconnect is not treated as a huge gap; logs `COUNTER_RESYNC_ON_RECONNECT`. |
| `getTotalReceived()` / `getTotalLostBle()` | Lifetime totals since process start of the singleton. |

**Events logged:** `MINUTE_SUMMARY` (per-minute received/lostBle/lossPct + totals; silent when window has no traffic), `BLE_PACKET_GAP`, `COUNTER_RESYNC`, `COUNTER_RESYNC_ON_RECONNECT`. Distinct from CSV queue drops (`OximeterDataBuffer`).

---

## BluetoothAutoConnector (legacy/unused)
`app/src/main/java/com/smartcare/oximetry/sample/BluetoothAutoConnector.java`

| Method | Description |
|---|---|
| `BluetoothAutoConnector(Context, Queue<String>, AutoConnectCallback)` | Registers ACL receiver, loads blocked MAC list, calls `initialize()`. |
| `initialize()` | Resets state, clears blocked MACs, starts auto-connect scan loop. |
| `manualDisconnect(String)` | Blocks MAC, closes socket, pauses reconnect 3s, restarts scanning. |
| `manualConnect(String)` | Unblocks MAC, stops scanning, sets MAC, calls `callback.connectToDevice`. |
| `requestPauseAutoReconnect(long)` | Pauses reconnection temporarily. |
| `cleanup()` | Stops scanning, clears handler, closes socket, unregisters receiver. |

**Inner Interface:** `AutoConnectCallback` (`onDeviceFound`, `checkAppPermissions`, `connectToDevice`).
**Notes:** Uses reflection for `setHighPriorityConnection`; intentionally does **not** open parallel SPP socket (avoids BLE drop); 2s scan interval. **Not referenced by any class.**
