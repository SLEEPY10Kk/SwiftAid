# SwiftAid

Road-safety emergency response system: an Android app that detects crashes and raises an SOS,
a responder app for police and hospital staff, and Firebase Cloud Functions that route each SOS
to nearby responders.

## Repository layout

| Path | What it is |
|---|---|
| `SwiftAid/` | **Citizen app** (Android, `com.example.swiftaid`). Crash detection, SOS overlay, emergency auto-dialer, SMS fallback, maps, Firebase auth. |
| `SwiftAid_Service/` | **Responder app** (Android, `com.example.policeapp`). Police/hospital dashboards, live SOS feed, FCM push. |
| `SwiftAid_Service/functions/` | **Cloud Functions** (Node 20). `enrichSosTargetResponders` targets responders within 20 km of an SOS. |

Both apps target the same Firebase project, `swiftaid-1088b`.

## Prerequisites

- **JDK 17–21.** The Android Gradle Plugin used here (9.1.0) does not support JDK 26.
  The JDK bundled with Android Studio works:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- **Android SDK** with `compileSdk 36`, NDK and CMake (the citizen app has native crash-detection code).
- **Node 20** for the Cloud Functions.

## Setup

Secrets are not committed. Copy the templates and fill them in:

```bash
cp .env.example .env
cp SwiftAid/local.properties.example SwiftAid/local.properties
cp SwiftAid_Service/local.properties.example SwiftAid_Service/local.properties
```

Then set, in `SwiftAid/local.properties`:

| Key | Needed for |
|---|---|
| `sdk.dir` | Android SDK path — **use the path for your own OS** |
| `MAPS_API_KEY` | Google Maps; the map renders blank without it |
| `GOOGLE_WEB_CLIENT_ID` | Google sign-in |
| `API_BASE_URL` | Backend API |
| `KSHITI_API_BASE_URL` | ML crash-classification service |

`SwiftAid_Service/local.properties` only needs `sdk.dir`.

The build reads these at configure time and falls back to defaults with a warning if they are
missing, so the project still compiles without them — the affected features just won't work.

## Build

```bash
cd SwiftAid && ./gradlew :app:assembleDebug
```

```bash
cd SwiftAid_Service && ./gradlew :app:assembleDebug
```

APKs land in `app/build/outputs/apk/debug/`. Install with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test

```bash
cd SwiftAid && ./gradlew :app:testDebugUnitTest
```

```bash
cd SwiftAid_Service && ./gradlew :app:testDebugUnitTest
```

## Cloud Functions

```bash
cd SwiftAid_Service/functions && npm install && npm run lint
```

Deploy (requires Firebase project access):

```bash
cd SwiftAid_Service && npx firebase-tools deploy --only functions --project swiftaid-1088b
```

## Firestore rules

`SwiftAid_Service/firestore.rules` currently allows unauthenticated read/write on
`sos_events`, `sos_responses` and `responders` so the responder app can work without Firebase Auth.
**This is open to anyone with the project ID and must be tightened before any public release.**

Deploy rules:

```bash
cd SwiftAid_Service && npx firebase-tools deploy --only firestore:rules --project swiftaid-1088b
```

## Backend services (not in this repo)

The two Android apps and the Cloud Functions are self-contained here, but the citizen app calls
two HTTP services that live **outside** this tree:

| Setting | Used by | Where the code lives |
|---|---|---|
| `API_BASE_URL` | `AuthApi`, `AuthRepository`, `db/PoiRepository` | `backend_python/` on the `API-Merge`, `devak` and `feature/divy-backend-sync` branches |
| `KSHITI_API_BASE_URL` | `logging/CrashDataUploader` | `fastapi_server/` on the `Kshiti` branch |

Both apps build and run without them. Sign-in and POI lookup fail with a clear on-screen message
until a reachable `API_BASE_URL` is configured; crash-data upload is best-effort and silent.
To run the Python backend, check out one of the branches above into a separate working copy.

## Branches

`main` holds the current working system. The other branches on this remote are earlier,
independent explorations by individual team members (a Kotlin Multiplatform UI prototype, a
FastAPI/ML crash-classification service, standalone Python scripts, and an earlier
`SwiftAidMobile` + `backend_python` split). They are merged into `main`'s history for provenance
but contribute no files to it. Check out a branch directly to see that work.
