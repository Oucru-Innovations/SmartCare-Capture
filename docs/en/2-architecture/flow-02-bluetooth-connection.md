# Flow 02 — Bluetooth Connection

> **Tier 2 · Flow 2.** How the app scans, connects, keeps, and auto-reconnects to the oximeter via BLE — and how to swap the hardware SDK without modifying the UI.

---

## 1. Summary

- The app connects via the **manufacturer's SDK** (`ConnectionManager` in `scolib-2.3.0.aar`), wrapped by **`OximeterSDKAdapter`**.
- The SDK reports events/data back to the app in the form of an **`android.os.Message`** sent via a **`Handler`** (`RecordActivity.SmartCareHandler`) — it **does not** use the `OximeterDataListener` interface (that class is still in the repo but has been deprecated/replaced).
- The app implements **background scanning** and **auto-reconnection**, while **filtering BLE events from unexpected devices** using `BluetoothEventInterceptor`.
- **`PacketStatsTracker`** (on `MESSAGE_PACKET`) tracks device `KEY_DATA_COUNTER` gaps, writes minute summaries to `FileLogger`, and ignores implausible gaps above `MAX_PLAUSIBLE_GAP = 1000`.

---

## 2. Sequence diagram — connect & receive data

```mermaid
sequenceDiagram
    participant U as User
    participant RA as RecordActivity
    participant CN as Connector (AsyncTask)
    participant ADP as OximeterSDKAdapter
    participant SDK as ConnectionManager (scolib)
    participant H as SmartCareHandler

    U->>RA: Press Start
    RA->>RA: checkAppPermissions()
    RA->>RA: ensureServiceAndConnect(mac?)
    RA->>CN: execute()
    CN->>ADP: connect() or connect(mac)
    ADP->>SDK: connect()
    SDK-->>H: MESSAGE_RECORDING_STARTED
    CN->>ADP: startRecording()
    loop Every data packet
        SDK-->>H: MESSAGE_PACKET (Bundle: pleth, HR, SpO₂, pin, ADC, PI, counter)
        H->>RA: Update graphs + labels (throttled to 500ms)
        H->>RA: PacketStatsTracker.onPacketReceived(counter)
        H->>RA: addDataPoint(...) → OximeterDataService
    end
    SDK-->>H: MESSAGE_CONNECTION_LOST (on drop)
    H->>RA: splitCsvOnBlePause() + scheduleAutoReconnect()
    Note over RA: splitFileOnBlePause closes segment if ≥ 60s;<br/>does NOT reopen — next CSV opens lazily on first packet
```

![Bluetooth Connection Flow Diagram](../../vi/2-architecture/flow02-bluetooth-connection.png)

---

## 3. SDK messages

`SmartCareHandler.handleMessage()` dispatches based on `msg.what`:

| Message | Action taken by app |
|---|---|
| `MESSAGE_PACKET` | Extracts pleth/HR/SpO₂/battery/ADC/PI; plots graphs (500-sample buffer); updates labels (500ms throttle); records via service; feeds `PacketStatsTracker` with `KEY_DATA_COUNTER`. |
| `MESSAGE_RECORDING_STARTED` | Toasts device model/time/sample rate; enables Stop button; stops scanning. |
| `MESSAGE_RECORDING_ENDED` | Toasts; splits CSV; resets button to Start; restarts scanning. |
| `MESSAGE_CONNECTION_LOST` | Toasts; splits CSV; if session is active → `stopDisconnectExit(false)` (only disconnect BLE); marks as disconnected. |
| `MESSAGE_DEVICE_NOT_FOUND` | Toasts; scans again; partial disconnect if session is active. |
| `MESSAGE_NO_DATA_AVAILABLE` / `MESSAGE_UNKNOWN_OXIMETER` | Warning toasts. |
| `MESSAGE_BLUETOOTH_OFF` / `MESSAGE_BLUETOOTH_UNAVAILABLE` | Toast: Bluetooth off/unavailable. |
| `MESSAGE_CLOCATION_PERMISSION_REQUIRED` / `MESSAGE_FLOCATION_PERMISSION_REQUIRED` | Toast: missing location permission. |

---

## 4. Auto-reconnect & event filtering

### Auto-reconnect
- `scheduleAutoReconnect(mac, delayMs)` — retry loop: **first retry after 30s**, then **every 3 minutes**.
- `startBluetoothBackgroundScan()` — background scanning **every 5s** (enabled only after patient code is entered).
- When BLE drops mid-session: `splitCsvOnBlePause()` → `splitFileOnBlePause()` **closes/finalizes** the current CSV segment if duration ≥ `MIN_SEGMENT_MS` (60s); it does **not** reopen a new file. The next CSV opens **lazily** via `ensureRecordingActive` / `startDataCollection` on the first packet after reconnect. The 24h session **continues**.

### PacketStatsTracker (device/UI diagnostics)
- On each `MESSAGE_PACKET`, `PacketStatsTracker.onPacketReceived(deviceCounter)` inspects gaps in the device `KEY_DATA_COUNTER`.
- Plausible gaps (≤ `MAX_PLAUSIBLE_GAP = 1000`) count as lost packets; larger gaps are treated as counter reset/wrap (logged as resync, not loss).
- Emits **minute summaries** to `FileLogger` while tracking is running.

### Filtering BLE events from unexpected devices
- `BluetoothEventInterceptor` is a high-priority `BroadcastReceiver` (`SYSTEM_HIGH_PRIORITY`) that intercepts `ACTION_ACL_CONNECTED/DISCONNECTED` **before** the SDK receives it, and calls `abortBroadcast()` if the device is **not** our oximeter.
- Purpose: Prevent other Bluetooth devices (headphones, smartwatches, etc.) from triggering connection drop events that would make the SDK think the oximeter has disconnected.

> **Note:** `BluetoothAutoConnector.java` exists but is **not currently used** — `RecordActivity` implements its own scanning and reconnection logic. See [Device/Bluetooth module](../3-modules/module-device-bluetooth.md).

---

## 5. Swapping the device SDK

When the manufacturer provides a new SDK (`.aar`/`.jar`), only **3 steps** are needed, **without touching `RecordActivity`**:

**Step 1 — Replace the library:** place the new library file in `app/libs/`, and update the dependency declaration in `app/build.gradle`.

**Step 2 — Modify `device/OximeterSDKAdapter.java` (the ONLY file to modify):**
1. Update `import` statements for the new SDK.
2. Replace old SDK initialization with the new one.
3. Modify `connect()`, `disconnect()`, and `startRecording()` to call the corresponding methods in the new SDK.

**Step 3 — Map data back via Handler Message:** when the new SDK triggers callbacks (connection / new data), construct a `Message` that mimics the old SDK's structure and send it via the `Handler`:

```java
Message msg = handler.obtainMessage(ConnectionManager.MESSAGE_PACKET);
Bundle data = new Bundle();
// data.putInt(ConnectionManager.KEY_DATA_PULSE, newSdkHeartRate);
// data.putDouble(ConnectionManager.KEY_DATA_SPO2, newSdkSpO2);
// data.putInt(ConnectionManager.KEY_DATA_PLETH, newSdkPlethValue);
msg.setData(data);
handler.sendMessage(msg); // dispatches to RecordActivity exactly as before
```

### Notable Workaround: Reflection to bypass device name filtering
In `OximeterSDKAdapter`'s constructor, the app uses **reflection** to override the hardcoded device name list in the SDK (obfuscated class `com.smartcare.oximetry.library.a`, field `d` of type `String[]`), setting `names[0] = ""` to **match all devices** (to support newer models like FC0392). This is a *workaround dependent on SDK internals* — **must re-verify after any `.aar` upgrade**.

---

## 6. Where to read the code

| Task | File · method |
|---|---|
| Start connection | `RecordActivity.connectToPulseOximeter()` → `ensureServiceAndConnect()` → inner `Connector` |
| Disconnect / Stop | inner `Disconnector`, `RecordActivity.stopDisconnectExit(boolean)` |
| Receive data/events from SDK | `RecordActivity.SmartCareHandler.handleMessage()` |
| Packet counter gap diagnostics | `PacketStatsTracker.onPacketReceived()`, minute summaries → `FileLogger` |
| Wrap vendor SDK | `device/OximeterSDKAdapter` (`connect`, `disconnect`, `startRecording`) |
| Adapter contract | `device/IDeviceAdapter` |
| Filter unexpected BLE events | `BluetoothEventInterceptor` |
| Background scan & reconnect | `RecordActivity.startBluetoothBackgroundScan()`, `scheduleAutoReconnect()` |

---

## 7. Related

- [flow-03 — Data recording](flow-03-data-recording.md) (where the data goes after connecting)
- Tier 3: [Device/Bluetooth module](../3-modules/module-device-bluetooth.md)
- Tier 4: [Device/Bluetooth functions](../4-functions/functions-device-bluetooth.md)
