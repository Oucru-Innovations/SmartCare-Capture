# SmartCare Oximetry - Deployment Guide

This guide helps you set up and deploy the SmartCare Android application on a new development machine or for production builds.

## Environment Variables

Sensitive variables are currently hardcoded. You should configure them using environment variables in a `config.properties` file instead.

```properties
# Server Configuration
SERVER_BASE_URL=

# Authentication
API_AUTH_TOKEN=

# Upload Configuration
UPLOAD_RETRY_DELAY_MS=10000
UPLOAD_MAX_RETRY_ATTEMPTS=3

# Data Buffer Configuration
DATA_BUFFER_SIZE=100

# File Chunking
FILE_CHUNK_SIZE=10
FILE_SPLIT_INTERVAL_HOURS=6

# Bluetooth Configuration
BLUETOOTH_SPP_UUID=
BLUETOOTH_SCAN_PERIOD_MS=
```

---

## Prerequisites

### Required Software
- **Android Studio**: Latest stable version (Arctic Fox or newer)
- **JDK**: Java Development Kit 8 or 11
- **Android SDK**: API Level 29 (Android 10) minimum, API Level 35 (Android 14) recommended
- **Git**: For version control

### Hardware Requirements
- **RAM**: Minimum 8GB (16GB recommended)
- **Storage**: At least 10GB free space
- **Android Device**: Physical device running Android 10+ or emulator

---

## Initial Setup

### 1. Install Android Studio
1. Download from [developer.android.com/studio](https://developer.android.com/studio)
2. Install with default settings
3. During first launch, download required SDK components when prompted

### 2. Configure Android SDK
1. Open Android Studio → Settings/Preferences → Appearance & Behavior → System Settings → Android SDK
2. Install the following SDK platforms:
   - Android 14.0 (API 35) - Target SDK
   - Android 10.0 (API 29) - Minimum SDK
3. Install SDK Build-Tools version 34.0.0
4. Install SDK Platform-Tools (latest version)

### 3. Clone the Repository
```bash
git clone https://github.com/Oucru-Innovations/SmartCare-Capture.git
cd ProjectSample
```

---

## Configuration Setup

### 1. Create Local Configuration File

Create `local.properties` in the project root (this file is git-ignored):

```properties
# Path to your Android SDK installation
# Windows example:
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk

# macOS/Linux example:
# sdk.dir=/Users/YourUsername/Library/Android/sdk
```

**Note:** Android Studio usually creates this file automatically. Update the path if needed.

### 2. Configure Environment Variables
Sensitive variables are currently hardcoded. You should configure them using environment variables in a `config.properties` file instead.
(See the list of required variables at the top of this document).

## Building the Project

### For Development (Debug Build)

1. **Open the project in Android Studio**
   - File → Open → Select ProjectSample folder

2. **Sync Gradle**
   - File → Sync Project with Gradle Files
   - Wait for sync to complete (may take a few minutes on first run)

3. **Connect Android device or start emulator**
   - Enable USB Debugging on physical device
   - Or: Tools → Device Manager → Create/Start Virtual Device

4. **Build and run**
   - Click the green "Run" button, or
   - Run → Run 'app', or
   - Press `Shift + F10` (Windows/Linux) or `Control + R` (macOS)

### For Production (Release Build)

#### 1. Obtain Signing Key
To build the AAB file for the Google Play Store, obtain the release keystore (keyapp) from the team (located in the `KEYAPP` folder).

#### 4. Build Release APK or Bundle

**APK (Direct Install):**
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

**Bundle (Play Store):**
```bash
./gradlew bundleRelease
```
Output: `app/build/outputs/bundle/release/app-release.aab`

---

## Project Structure

```
ProjectSample/
├── app/
│   ├── src/main/
│   │   ├── java/com/smartcare/oximetry/sample/  # Source code
│   │   ├── res/                                  # Resources (layouts, strings, etc.)
│   │   └── AndroidManifest.xml                   # App manifest
│   ├── libs/
│   │   └── scolib-2.0.0.aar                      # Oximeter library
│   └── build.gradle                              # App-level build config
├── gradle/                                        # Gradle wrapper
├── build.gradle                                   # Project-level build config
├── gradle.properties                              # Gradle configuration
├── settings.gradle                                # Project modules
├── local.properties                               # Local SDK path (git-ignored)
├── config.properties                              # App configuration (git-ignored)
└── keystore.properties                            # Signing config (git-ignored)
```

---

## Troubleshooting

### Gradle Sync Failures
- **Issue**: "SDK location not found"
  - **Solution**: Create or verify `local.properties` with correct SDK path

- **Issue**: "Failed to resolve dependencies"
  - **Solution**: Check internet connection, try File → Invalidate Caches → Restart

### Build Errors
- **Issue**: "Unsupported Java version"
  - **Solution**: File → Project Structure → SDK Location → Set JDK to version 8 or 11

- **Issue**: "BuildConfig cannot be resolved"
  - **Solution**: Ensure `config.properties` exists and Gradle sync is complete

### Runtime Issues
- **Issue**: "App crashes on startup"
  - **Solution**: Check `config.properties` values are correct, especially SERVER_BASE_URL

- **Issue**: "Cannot connect to oximeter"
  - **Solution**: Verify Bluetooth permissions are granted, device is compatible

### Library Issues
- **Issue**: "scolib-2.0.0.aar not found"
  - **Solution**: Ensure the AAR file exists in `app/libs/` directory

---

## Deployment Checklist

### Before Building Release
- [ ] Update version name and code in `app/build.gradle`
- [ ] Verify all configuration values in `config.properties`
- [ ] Test on physical devices (Android 10, 11, 12, 13, 14)
- [ ] Check all permissions are declared in AndroidManifest.xml
- [ ] Review ProGuard rules if enabling minification
- [ ] Test file upload with production server (e.g., OUCRU, Nepal)
- [ ] Verify Bluetooth connectivity with both oximeter types (silver label & printed label)

### After Building Release
- [ ] Test APK/Bundle installation on clean device
- [ ] Verify app signature
- [ ] Document version changes
- [ ] Backup signing keystore securely
- [ ] Update release notes

---

## Security Notes

### Sensitive Files (NEVER commit these)
- `local.properties` - Contains local SDK path
- `config.properties` - Contains API tokens and server URLs
- `keystore.properties` - Contains signing credentials
- `*.keystore`, `*.jks` - Signing keys

### Best Practices
1. Store production keystore in secure location with restricted access
2. Use different API tokens for dev/staging/production
3. Rotate API tokens periodically
4. Keep backup of signing keystore in secure offline storage
5. Document keystore password in secure password manager

## Support & Resources

- **Android Developer Documentation**: https://developer.android.com/docs
- **AndroidPlot Library**: http://androidplot.com/docs/
- **Retrofit Documentation**: https://square.github.io/retrofit/
- **Project README**: See `README.md` for app functionality details
- **TODO List**: See `TODO.md` for development tasks

