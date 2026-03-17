# SmartCare Capture - Release & Deployment Guide

## Current Active Studies

### Study 54 & Study 39
- **Status**: **Active** - Running from latest commit
- **Branch**: `main`
- **Server**: OUCRU (`<OUCRU_SERVER_URL>`)
- **Configuration**: Standard OUCRU setup

### Study 62 (Nepal)
- **Status**: **Configuration Required**
- **Branch**: `main`
- **Server**: Nepal (`<NEPAL_SERVER_IP_OR_URL>`)
- **Action Needed**: Change URL before deployment

### Study 24c (Legacy)
- **Status**: **Use Existing APK**
- **Branch**: `android-12-branch` or existing archives
- **Note**: Reuse old APK in Team installation guide

#### Clinical Notes Integration (Future)
For models requiring clinical notes, discuss implementation with:
```json
{
  "ablett": 2,           
  "comorbidities": "yes", 
  "ventilation": "no",     
  "age": 65              
}
```
> **Development Note**: Switch to `server-version` branch for continuous development with clinical note templates. Logic flow design still pending.
## Server Configurations by Study

These settings are no longer hardcoded in `FileUploader.java` and instead managed via environment variables/local configuration that injects into `BuildConfig` and subsequently `ApiConfig`.

### OUCRU Server (Studies 54, 39)
```properties
# config.properties or local.properties
BASE_URL="<OUCRU_SERVER_URL>"
API_TOKEN="<YOUR_API_TOKEN>"
```

### Nepal Server (Study 62)
```properties
# config.properties or local.properties
BASE_URL="<NEPAL_SERVER_IP_OR_URL>"
API_TOKEN="<YOUR_API_TOKEN>"
```

> **Important**: When building for Nepal (Study 62) or other studies, provide the appropriate variables injected into `BuildConfig` to correctly route traffic cleanly without altering code logic.

## APK Build Matrix

| Study | Branch | Server | API Route | Status | Action Required |
|-------|--------|--------|-----------|--------|-----------------|
| **54** | `main` | OUCRU | `smapp/upload` (via config) | **Active** | None - Ready |
| **39** | `main` | OUCRU | `smapp/upload` (via config) | **Active** | None - Ready |
| **62** | `main` | Nepal | `upload` (via config)   | **Config Needed** | Provide Build Variables |
| **24c** | `android-12-branch` | Various | Various | **Use Existing** | Reuse APK |

---
