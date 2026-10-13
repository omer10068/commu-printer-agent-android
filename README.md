# Commu Printer Agent — Android

An Android take-home assignment for Communicate AI.  
Replaces the functional role of the supplied Windows printer-spooler EXE with a native Android app.

## What it does

- Authenticates with the Communicate AI demo backend using a Tenant ID and Bearer token.
- Discovers ESC/POS thermal printers on the local Wi-Fi subnet (TCP port 9100).
- Names printers and synchronises them with the backend via `agent-sync`.
- Guides the user through static-IP configuration via each printer's own web admin page.
- Verifies physical connectivity with a test print on each saved printer.
- Runs a background polling loop (foreground service) that claims queued jobs, renders Hebrew receipts as 1-bit raster bitmaps, routes them to the correct printer over TCP, and reports `printed` / `failed` to the backend.
- Survives screen lock, task-kill, and device reboot.
- Supports at least two configured printers with per-printer job serialisation.

The application intentionally uses a retro, industrial-style interface — monospace fonts, dark panels, and technical status indicators — in accordance with the assignment's explicit UI requirements.

## Technology stack

| Layer | Library / API |
|---|---|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 (Compose BOM 2026.02.01) |
| Build | AGP 9.4.1, Gradle 9.6.0 (wrapper) |
| HTTP | OkHttp 4.12.0 |
| Storage | DataStore Preferences 1.1.1 (settings); Security Crypto 1.0.0 (token) |
| Background | `foregroundServiceType="connectedDevice"` foreground service + `BOOT_COMPLETED` receiver |
| Printing | Java TCP sockets, ESC/POS, Android Canvas bitmap rasterisation |
| Tests | JUnit 4, OkHttp MockWebServer, kotlinx-coroutines-test 1.7.3 |

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | **17 or higher** — required by Gradle 9.6 and AGP 9.4.1 (JBR 17 bundled with Android Studio satisfies this) |
| Android SDK | API levels 26–37 installed (`sdkmanager "platforms;android-37"`) |
| `ANDROID_HOME` | Must point to the SDK root, or `local.properties` must set `sdk.dir` |
| Android device | API 26+ (Android 8.0+) with USB debugging enabled, for APK installation |

Android Studio is **not required** to build — the Gradle wrapper downloads Gradle 9.6 automatically.  
Ensure `JAVA_HOME` points to a JDK 17+ installation before running the wrapper on the command line.

## Clone and build

```powershell
git clone https://github.com/omer10068/commu-printer-agent-android.git
cd commu-printer-agent-android
.\gradlew.bat assembleDebug          # build APK
.\gradlew.bat testDebugUnitTest      # run 216 JVM unit tests (no device needed)
```

Build output: `app/build/outputs/apk/debug/app-debug.apk` (~14 MB)

### Optional: Android Studio

Open the project root in Android Studio. A version compatible with AGP 9.4.1 is required — use a current release.  
The IDE will sync Gradle automatically.

## Install APK

```powershell
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the device and open it (enable **Install unknown apps** for Files/Chrome).

## Start the demo backend

The demo backend is a **separate supplied project** (`printer-spooler-kit`) — not included in this repository.

```bash
cd printer-spooler-kit
npm install           # Node.js 18+ required
npm start             # listens on port 5100
```

The startup log prints a **LAN address** (e.g., `http://192.168.1.53:5100`).  
Use that address in the app — `localhost` resolves to the phone itself, not the PC.

Verify the server is healthy:

```bash
npm run smoke         # runs a complete agent-protocol walkthrough
```

## Obtain a Tenant ID and agent token

1. Open the console at `http://localhost:5100`.
2. The Tenant ID defaults to `demo` (shown in the URL after logging in).
3. Under **סוכני מדפסת**, enter a name for this agent and press **צור טוקן**.
4. Copy the `pa_…` value — it is shown **once only**. Losing it requires revoking and re-issuing.

## Onboarding — four-step wizard

Every step must succeed before proceeding. The wizard reopens at the first incomplete step.

| Step | What to do |
|---|---|
| **1 · Connect** | Enter the backend LAN URL (`http://<PC-IP>:5100`), Tenant ID, and agent token. Tap **Connect**. |
| **2 · Discover** | Tap **Search for printers**. The app scans the /24 subnet on port 9100. Tap ⊕ next to each printer, give it a name, and save. At least one printer is required. |
| **3 · Static IP** | Tap a printer row — the app opens the printer's web admin interface in the browser. Set a static IP there, return to the app, and confirm **Yes** in the dialog. Repeat for each printer. |
| **4 · Test print** | Tap each printer row to send an ASCII test page. Confirm **Yes** in the dialog once the page prints. |

After all four steps succeed the app opens the **Dashboard** and the background agent starts automatically.

## Printing a test receipt and sending jobs from the backend

1. Ensure the agent is running (green status card on the Dashboard).
2. In the demo console under **עבודות הדפסה**, use:
   - **הדפסת בדיקה** — sends a short alignment test to the first active printer.
   - **קבלה לדוגמה** — queues a multi-line Hebrew order receipt with items, VAT, and totals.
3. The agent claims the job within its polling interval, sends it over TCP, and reports the result.  
   The job status updates in real time in the demo console.

## Running automated tests

```powershell
.\gradlew.bat testDebugUnitTest
```

216 JVM unit tests, no device required, no network required.  
Test HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`

**Coverage areas:**
- Backend HTTP client (MockWebServer) — connection, auth failure, job parsing, PATCH status
- Job processor — routing, PATCH sequencing, per-printer serialisation, failure isolation
- LAN scanner — subnet calculation, IP validation
- ESC/POS encoder — command byte sequences, raster wrapping
- Receipt layout calibration — font calibration math, horizontal inset geometry
- Printer mapping — encode/decode routing table
- Printer and onboarding repository logic — CRUD, state transitions, migration

## Testing evidence and known limitations

**Tested on:** Samsung Galaxy S22+ (Android 14), SNBC BTP-R880NPII thermal printer (80 mm, 203 DPI).

| Feature | Evidence |
|---|---|
| End-to-end print loop | Jobs printed physically on SNBC printer; job status reaches `printed` in demo console |
| Hebrew / RTL rendering | Restaurant receipts with Hebrew product names, prices, and totals printed correctly |
| Two-printer routing | One physical printer (SNBC) + one PC TCP mock (`tools/pc-mock-printer.ps1`); two physical printers not available |
| Background operation | Agent survives screen lock and swipe-to-close; tested on Galaxy S22+ |
| Boot recovery | Agent restarts automatically after reboot without opening the app; verified on Galaxy S22+ |

**Known limitations:**

- **Static IP:** The app opens each printer's web admin page and asks the user to confirm. There is no independent TCP re-verification after confirmation; the app trusts the user's answer. SNBC and similar printers do not expose a standard ESC/POS command to change network settings.
- **Two-printer physical test:** Only one physical ESC/POS printer was available. Per-printer routing and serialisation are validated by JVM TCP integration tests against two real `ServerSocket(0)` endpoints and by the PC mock receiver.
- **Cleartext HTTP:** This debug build permits cleartext HTTP to support the local demo backend. The network security config must be tightened before production deployment.
- **Boot recovery — first device unlock:** The `BOOT_COMPLETED` receiver starts the foreground service automatically on every reboot — no manual app launch required. However, the Android Keystore does not expose credential-encrypted storage until the device is unlocked for the first time after a cold boot. If the device has not yet been unlocked, the service starts in a `NoConfig` state and waits. The moment the user unlocks the screen (no need to open the app), the token becomes readable and the agent resumes on the next poll cycle. This has been verified on Samsung Galaxy S22+ (Android 14).
- **Print confirmation:** A successful TCP `socket.write()` confirms bytes were transmitted to the printer's input buffer. It does not confirm that the paper physically ejected. `printed` status reflects TCP transmission success, not paper output.

## Security notes

- The agent token is stored in `EncryptedSharedPreferences` backed by the Android Keystore (AES-256-SIV key encryption, AES-256-GCM value encryption).
- Tokens, `Authorization` headers, and receipt text are never written to Logcat.
- The demo backend uses HTTP only. This repository is private; do not expose the backend to the public internet.
