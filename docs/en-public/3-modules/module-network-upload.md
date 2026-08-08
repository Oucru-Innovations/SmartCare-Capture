# Module — Network / Upload

> **Tier 3.** Network & upload module: upload coordination + retries, HTTP client, build-time configuration, daily 17:00 schedule, file logging, and local annotation history.
> For upload **behavior**, see [flow-05](../2-architecture/flow-05-file-upload.md). API URLs and credentials are **not** listed in this public documentation.

**12 classes** (see [MODULES-INDEX](MODULES-INDEX.md)): coordinator, uploader, Retrofit stack, config helpers, WorkManager scheduler, `FileLogger`, etc.

---

## Responsibilities (public summary)

| Area | Primary classes | Role |
|---|---|---|
| Upload orchestration | `PendingFileUploadCoordinator` | Queue session files, retry (max 3), skip active writer URI, move success to `Uploaded/` |
| HTTP worker | `FileUploader` | Multipart upload of CSV/logs via Retrofit (single attempt per call; retries are in the coordinator) |
| Client | `ApiService`, `RetrofitClient` | Declares upload contract; base URL from build configuration |
| Build-time config | `ApiConfig`, `NetworkConfig` | Base URL, timeouts, retry constants (values set at build — internal) |
| Connectivity | `NetworkHelper` | Checks network before upload |
| Daily maintenance | `AnnotationUploadScheduler`, `UploadAnnotationWorker` | 17:00 local split + upload pending files |
| Diagnostics | `FileLogger` | Daily rotating text logs on device |

---

## Related flows & code map

| Task | Where to read |
|---|---|
| End-to-end upload paths | [flow-05-file-upload.md](../2-architecture/flow-05-file-upload.md) |
| Retry policy | `PendingFileUploadCoordinator` |
| Bug-report log upload | `BugReportDialog` → `FileUploader` |
| File manager manual upload | `FileManagerActivity` → coordinator |

**Deployment note:** Exact endpoint paths, owner fields, and authentication headers are defined in source and internal deployment docs — not reproduced here.

---

## Related

- Tier 2: [flow-05 — File upload](../2-architecture/flow-05-file-upload.md) · [flow-06 — File management](../2-architecture/flow-06-file-management.md)
- Tier 1: [Configuration & deployment](../1-overview/PROJECT-OVERVIEW.md#configuration--deployment)
