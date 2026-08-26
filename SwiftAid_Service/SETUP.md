# PoliceApp Setup

## Firebase (`swiftaid-1088b`)

- Place `google-services.json` at `app/google-services.json` (not the project root).
- Package name must be `com.example.policeapp`.
- Debug SHA-1 registered in Firebase: `F8:17:D1:51:75:E4:E6:A0:BD:0C:EE:A5:62:01:29:CF:F3:D5:FC:D5`.

### Firestore rules (required for live SOS data)

PoliceApp reads/writes `sos_events` and `sos_responses` without Firebase Auth. Deploy the rules in this repo:

```bash
cd /Users/pkians/AndroidStudioProjects/PoliceApp
npx -y firebase-tools@latest login
npx -y firebase-tools@latest deploy --only firestore:rules --project swiftaid-1088b
```

Or paste `firestore.rules` into Firebase Console → Firestore → Rules → Publish.

## Android build

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Install from Android Studio (Run) or:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## SwiftAid API (optional for PoliceApp)

PoliceApp uses **Firestore cloud** as its backend. The Python API in `SwiftAid/backend_python` is for the SwiftAid citizen app (auth, POI, etc.).

Run on all interfaces (LAN / emulator `10.0.2.2:8001`):

```bash
cd ../SwiftAid/backend_python
./.venv/bin/uvicorn app3:app --host 0.0.0.0 --port 8001
```

Health: `http://127.0.0.1:8001/health`
