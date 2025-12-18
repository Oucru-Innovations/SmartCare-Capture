# SmartCare Capture - Release & Deployment Guide

## 📋 Current Active Studies

### Study 54 & Study 39
- **Status**: ✅ **Active** - Running from latest commit
- **Branch**: `main`
- **Server**: OUCRU (`vital-ei.oucru.org`)
- **Configuration**: Standard OUCRU setup

### Study 62 (Nepal)
- **Status**: 🚧 **Configuration Required**
- **Branch**: `main`
- **Server**: Nepal (`103.213.124.230`)
- **Action Needed**: Change URL before deployment

### Study 24c (Legacy)
- **Status**: 📦 **Use Existing APK**
- **Branch**: `android-12-branch` or existing archives
- **Note**: Reuse old APK in Team installation guide

#### Clinical Notes Integration (Future)
For models requiring clinical notes, discuss implementation with:
```json
{
  "ablett": 2,           // Ablett Score: 1-4 assessment scale
  "comorbidities": "yes", // Comorbid conditions: yes/no
  "ventilation": "no",    // Mechanical ventilation: yes/no  
  "age": 65              // Patient age in years
}
```
> **Development Note**: Switch to `server-version` branch for continuous development with clinical note templates. Logic flow design still pending.
## 🔧 Server Configurations by Study

### OUCRU Server (Studies 54, 39)
```java
// FileUploader.java - Production Configuration
BASE_URL = "http://vital-ei.oucru.org/"
API_ENDPOINT = "api/v1/file/smapp/upload"
STATIC_TOKEN = "[configured]"
```

### Nepal Server (Study 62)
```java
// FileUploader.java - Nepal Configuration 
BASE_URL = "http://103.213.124.230/"
API_ENDPOINT = "api/v1/file/upload"  
STATIC_TOKEN = null                   
```

> **⚠️ Important**: When building for Nepal (Study 62), update both URL and API endpoint in `FileUploader.java`

## 🚀 APK Build Matrix

| Study | Branch | Server | API Route | Status | Action Required |
|-------|--------|--------|-----------|--------|-----------------|
| **54** | `main` | OUCRU | `smapp/upload` | ✅ **Active** | None - Ready |
| **39** | `main` | OUCRU | `smapp/upload` | ✅ **Active** | None - Ready |
| **62** | `main` | Nepal | `upload` | 🚧 **Config Needed** | Update URL + Route |
| **24c** | `android-12-branch` | Various | Various | 📦 **Use Existing** | Reuse APK |

---
