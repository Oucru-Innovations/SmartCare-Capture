# SmartCare Capture — Documentation

> 🌐 **Language / Ngôn ngữ:** [Tiếng Việt (Vietnamese Edition)](../README.md) | **English**

> **Documentation entry point.** The documentation is organized into **4 tiers**, from overview to detail. Please read the tiers in order depending on your goal.

---

## Read by goal

| You are... / You want to... | Start from |
|---|---|
| A complete beginner, want to understand what the app does | **Tier 1** → [1-overview/PROJECT-OVERVIEW.md](1-overview/PROJECT-OVERVIEW.md) |
| Want to understand how the system runs, its flows | **Tier 2** → [2-architecture/ARCHITECTURE-OVERVIEW.md](2-architecture/ARCHITECTURE-OVERVIEW.md) |
| Want to know "which file/class function X is located in" | **Tier 3** → [3-modules/MODULES-INDEX.md](3-modules/MODULES-INDEX.md) |
| Want to look up details of each function | **Tier 4** → [4-functions/](#tier-4--function-documentation) |

---

## The 4 tiers

### Tier 1 — Project Overview
*What does the app do?*
- [1-overview/PROJECT-OVERVIEW.md](1-overview/PROJECT-OVERVIEW.md) — goals, problem statement, glossary, tech stack, setup & configuration.

### Tier 2 — Architecture Flow
*How does the system operate, what are the individual flows?*
- [2-architecture/ARCHITECTURE-OVERVIEW.md](2-architecture/ARCHITECTURE-OVERVIEW.md) — 5-tier architecture + list of flows
- [flow-01-app-startup.md](2-architecture/flow-01-app-startup.md) — app startup
- [flow-02-bluetooth-connection.md](2-architecture/flow-02-bluetooth-connection.md) — BLE connection & SDK hot swap
- [flow-03-data-recording.md](2-architecture/flow-03-data-recording.md) — data recording (most critical flow)
- [flow-04-annotation.md](2-architecture/flow-04-annotation.md) — event annotation
- [flow-05-file-upload.md](2-architecture/flow-05-file-upload.md) — upload & retry & 17:00 schedule
- [flow-06-file-management.md](2-architecture/flow-06-file-management.md) — file management

### Tier 3 — Module / Class Reference
*Where is the code for this feature located?*
- [3-modules/MODULES-INDEX.md](3-modules/MODULES-INDEX.md) — class-to-module map + directory tree
- [module-ui-recording.md](3-modules/module-ui-recording.md)
- [module-device-bluetooth.md](3-modules/module-device-bluetooth.md)
- [module-data-storage.md](3-modules/module-data-storage.md)
- [module-network-upload.md](3-modules/module-network-upload.md)

### Tier 4 — Function Documentation
*Detail of each function (with Javadoc in code).*
- [4-functions/functions-ui-recording.md](4-functions/functions-ui-recording.md)
- [4-functions/functions-device-bluetooth.md](4-functions/functions-device-bluetooth.md)
- [4-functions/functions-data-storage.md](4-functions/functions-data-storage.md)
- [4-functions/functions-network-upload.md](4-functions/functions-network-upload.md)

> In addition to the 4 tiers, **Javadoc has been added to the `.java` source code** for important classes/methods — read directly in the code.

---

## Test reports
- [MANUAL_TEST_REPORT.md](MANUAL_TEST_REPORT.md)
- [MANUAL_TEST_REPORT_SMARTCARE_phucvh_300326.md](MANUAL_TEST_REPORT_SMARTCARE_phucvh_300326.md)

---

## Known issues / Technical debt

Points to note during maintenance (consolidated from old TODOs):

- **Large `RecordActivity` (~1900 lines)** — carries many responsibilities (BLE, UI, permissions, data). The SDK part has been separated into `OximeterSDKAdapter`; could be further refactored into a manager/controller.
- **Static variables for vitals annotation** (`annotationHeartRate`, `annotationSpO2`) — easily cause stale values between sessions; should use state with lifecycles (ViewModel/SavedState).
- **Scattered permissions** in `RecordActivity` — should be consolidated in one place based on API levels.
- **UI updates on every data packet** — throttled to 500ms; consider batching more.
- **Unused legacy classes:** `OximeterDataListener`, `OximeterDataPacket`, `BluetoothAutoConnector` — candidates for cleanup.
- **No unit tests yet** — should start with `OximeterDataBuffer`, `FileUploader`, `OximeterDataWriterFunctions`.

---

## Maintenance / Update conventions

- When **architecture** changes → update Tier 2.
- When **adding/changing classes** → update Tier 3 (MODULES-INDEX + module files).
- When **changing function signature/behavior** → update Tier 4 + Javadoc in code.
- Setup/deployment by study → see [Tier 1 Section 7–8](1-overview/PROJECT-OVERVIEW.md#7-quick-setup--build).
