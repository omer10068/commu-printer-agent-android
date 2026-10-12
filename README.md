# Commu Printer Agent — Android

An Android printer-spooler agent for the Communicate AI take-home assignment.

> **Project status:** Initial Android project scaffold. Features described below are **requirements and implementation plans**, not claims of completed functionality. Update the checklist and test evidence as work is completed.

## Overview

The app connects a local Android device to an existing POS/demo print queue. It discovers and configures network receipt printers, maps them to backend printers, receives print jobs, sends them over the local network, and reports job outcomes.

```text
Communicate AI demo backend (HTTP API, port 5100)
                |
      Authenticated agent API
                |
       Android Printer Agent
         |             |
    UI / setup     Background worker
                       |
                Per-printer routing
                    /      \
          TCP :9100      TCP :9100
          Printer A      Printer B
```

**Scope:** Build the **Android agent**, not a replacement backend, demo web console, or Windows EXE. The supplied Windows agent is a behavioral reference, not code to bundle or port verbatim. The demo backend remains external to this repository.

## Assignment requirements

- [ ] Connect using **Backend URL**, **Tenant ID**, and **Agent Token**.
- [ ] Discover reachable LAN printers; display scan status and errors.
- [ ] Name, save, and synchronize printers with the demo backend.
- [ ] Configure and **verify an actual static IP on supported printer hardware**; do not confuse this with storing an IP locally.
- [ ] Route print jobs to **at least two** configured printers, without interleaving jobs on an individual printer.
- [ ] Render and print receipt content, including Hebrew / right-to-left text.
- [ ] Report job lifecycle (`claimed`, `printed`, `failed`) to the demo queue.
- [ ] Remain useful and responsive while running in the background; recover from connectivity interruptions.
- [ ] Restore configured operation after device reboot, within Android's background-execution restrictions.
- [ ] Use an intentionally **old-school, technical/industrial** Android UI with informative status indicators.
- [ ] Deliver a runnable APK, source code, setup instructions, test evidence, and known limitations.

## Planned stack

| Area | Technology |
|---|---|
| Android | Kotlin, Jetpack Compose, Material 3 components styled for a retro technical UI |
| Architecture | UI / domain / data separation; ViewModels and StateFlow |
| Concurrency | Kotlin coroutines and structured cancellation |
| HTTP & SSE | OkHttp (SSE optional; polling must work independently) |
| Printing | Java/Kotlin TCP sockets on port 9100; ESC/POS raster printing |
| Settings | DataStore for non-sensitive settings; Android Keystore-backed solution for token encryption |
| Agent lifecycle | Foreground service where allowed, boot receiver / supported restart path, reconnection policy |
| Tests | JVM unit tests, HTTP mock tests, device integration tests |

Final dependency and Android service-type choices should be validated against the project's SDK levels and the device OS version before implementation.

## Demo backend — verified contract

The separately supplied `printer-spooler-kit` exposes a development server on port `5100`:

```powershell
# Run from the separate printer-spooler-kit directory
npm install
npm start
# Open http://localhost:5100 on the development PC
npm run smoke  # in a separate terminal, with the server running
```

Configure the Android app with a **LAN-reachable** URL, such as `http://<PC_LAN_IP>:5100`; `localhost` on the phone refers to the phone itself. The PC firewall must allow the phone to access the port.

Create an agent token in the demo console under **סוכני מדפסת** (printer agents). The token starts with `pa_` and is displayed only once. The app sends it as:

```http
Authorization: Bearer pa_<token>
```

The Tenant ID must match the one used when generating the token. For the local demo, authentication for manager-only console operations is separate from agent authentication.

### Agent API

All tenant-scoped endpoints are relative to the configured base URL:

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/health` | Confirm demo server is reachable |
| `POST` | `/printers/tenant/{tenantId}/agent-sync` | Synchronize printer names and flags |
| `GET` | `/printers/tenant/{tenantId}/active` | Retrieve active backend printers |
| `GET` | `/printers/tenant/{tenantId}/print-jobs/agent-queue?printerIds=1,2&limit=25` | Poll queued jobs, oldest first |
| `PATCH` | `/printers/tenant/{tenantId}/print-jobs/{jobId}/status` | Update job state |
| `GET` | `/printers/tenant/{tenantId}/print-jobs/agent-stream` | Optional SSE wake-up events |

Example sync body:

```json
{
  "activePrinterNames": ["Kitchen", "Bar"],
  "cashDrawerPrinterNames": [],
  "mainPrinterName": "Kitchen"
}
```

Example print job (representative):

```json
{
  "id": 7,
  "tenantId": "demo",
  "printerId": 1,
  "printerName": "Kitchen",
  "status": "queued",
  "printableText": "Order #123\n..."
}
```

For each job: discover queued work, request `claimed`, route using the saved **backend-printer ID → local printer endpoint** mapping, send the bytes, then report `printed` or `failed`. Capture errors and timings without logging credentials.

**Reliability caveat:** The provided demo backend's status update is not a compare-and-swap / atomic claim. It does not guarantee exactly-once delivery across multiple agents or automatically recover an abandoned `claimed` job. Avoid duplicates within this agent; document remaining server-side limits. A successful socket write does **not** prove the paper physically printed. Record the strongest status evidence that the printer actually exposes.

## Planned Android modules / responsibilities

The project may begin as a **single Gradle `app` module** with clear packages:

```text
app/src/main/java/com/commu/printeragent/
├── ui/             # Compose screens and ViewModels
├── data/           # API client, settings, printer mapping
├── domain/         # Job and printer models, orchestration
├── printing/       # LAN discovery, TCP, ESC/POS, receipt rasterization
└── agent/          # Queue polling, routing, lifecycle and background work
```

This is a **target package structure**, not a claim those packages exist today. Keep the print engine and queue processing independent of Activity lifecycle and Compose.

## UI workflow

1. **Connection:** Server URL, Tenant ID, Agent Token, connection test.
2. **Printers:** Scan subnet with bounded timeouts/concurrency; allow manual IP entry, naming, local save and backend sync.
3. **Static IP:** Show current and proposed settings, provide a supported vendor-specific configuration path, then verify the new address by reconnecting. Unsupported devices must show a clear explanation, not a fake success.
4. **Operational status:** Connected/disconnected, last successful poll, printer availability, last print result, actionable errors and retry state.

The UI should intentionally resemble a practical legacy control panel rather than a consumer-style design.

## Networking and hardware notes

- Receipt printers are expected to expose a reachable TCP print service (commonly RAW port `9100`). Discovery should not treat an open port as definitive proof of printer identity.
- Physical test device: **Samsung Galaxy S22+**.
- Available printer used in the preliminary PC test: **SNBC BTP-R880NPII**, previously reached at `192.168.1.21:9100`. This IP is an **example from the test setup**, not an application default.
- PC-side printing via TCP and the supplied Windows EXE was observed to work. **Android-to-printer printing has not yet been validated.**
- The PC's Wi-Fi LAN and the printer's Ethernet LAN have been on **different subnets**. Resolve routing / Wi-Fi access before claiming Android direct discovery or printing works.
- Static-IP configuration is **printer/model specific**. Back up original network settings and provide a recovery procedure before attempting a change.
- Test at least two configured printers; a simulated second TCP endpoint is useful, but document that it does not replace a two-physical-printer test.

## Local Android development

Prerequisites: Android Studio, Android SDK/Gradle configured, and an Android device with USB debugging enabled.

```powershell
# Run from the repository root (Windows)
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Build output, after a successful build: `app/build/outputs/apk/debug/app-debug.apk`.

For a local HTTP demo server, configure **debug-only** cleartext access as narrowly as feasible using Android network security config (or document a necessary development manifest setting). Prefer HTTPS for deployment. Do not hardcode a bearer token or local IP into committed source.

## Verification plan

- [ ] Builds from a clean checkout (`assembleDebug`).
- [ ] Installs and launches on Samsung Galaxy S22+.
- [ ] Can authenticate and sync printers to the demo tenant.
- [ ] Finds/reaches the physical printer from the **phone**.
- [ ] Prints readable Hebrew and numbers from demo receipt payloads.
- [ ] Routes separate jobs to two configured printers.
- [ ] Updates queued → claimed → printed/failed appropriately.
- [ ] Recovers after backend/printer disconnection without crashing or uncontrolled retries.
- [ ] Continues expected work with Activity closed and screen locked, within OS policy.
- [ ] Recovers operation after reboot (test actual device behavior and user consent requirements).
- [ ] Verifies static-IP changes on supported printer hardware.
- [ ] Documents limitations and shows a short reproducible end-to-end demo.

## Security and sharing

- Never commit actual Agent Tokens, passwords, `.env` files, keystores, debug dumps, or customer data.
- Do not include vendor-provided Windows EXE / backend source archives in this repository without explicit authorization.
- Keep this repository private unless the evaluator explicitly requests different sharing permissions.
- Minimize logs containing receipt text; never log `Authorization` headers.

## Known limitations / to resolve

- Current project is the Android template; the app behavior above is **not implemented yet**.
- Need to determine hardware-specific SNBC static-IP configuration mechanism.
- Need LAN connectivity from phone to both the PC-hosted demo API and the printer(s).
- Printer acknowledgment semantics vary by device; paper output is not provable by TCP write alone.
- Android background-start and boot restrictions vary by OS/device; validate on the actual Galaxy S22+.
- Two-printer physical validation depends on hardware availability.

## References

- Supplied assignment instructions and mobile UI flow.
- `printer-spooler-kit/README.md`, `server/index.js`, `server/store.js`, `scripts/smoke.js` in the **separately supplied** demo archive.
- Supplied Windows Agent executable / source as a behavioral reference.

Update this README as features are implemented, tested, and demonstrated.
