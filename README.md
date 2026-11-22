# WasteCrush

Android 1v1 match-3 race across Tunisia's landmarks (Kotlin, Jetpack Compose, Firebase).
Match recyclables to defeat trash monsters and watch the place clean up as you win.

## Setup after cloning

Three files are kept out of Git on purpose. Add them before building:

| File | Where | What it is |
|---|---|---|
| `local.properties` | project root | `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` (Android Studio creates it) |
| `debug.keystore` | project root | Shared debug signing key (ask the team), so builds install over each other |
| `google-services.json` | `app/` | Firebase config from the Firebase console (also provides `default_web_client_id`) |

Then:

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Build modes

- **Debug** (`LOCAL_TEST_MODE = true`): fully offline local guest. No sign-in, AI rivals,
  progress and replays saved on the device. Set it to `false` in `app/build.gradle.kts` to test
  Firebase online play, private rooms and the edge relay from a debug build.
- **Release**: anonymous Firebase sign-in, online 1v1 matchmaking and private rooms.

## Online sync

- Firestore (`liveMatches`) is the source of truth for live matches and results.
- An optional edge WebSocket relay (`ws://` or `wss://`, set in Settings) only speeds up the
  rival's on-screen state. It never decides who wins. A private-room guest only uses their own
  relay setting, never one chosen by the host.
- Release builds allow unencrypted `ws://` only to `localhost`, `127.0.0.1` and `10.0.2.2`;
  use `wss://` for real relays. Debug builds allow `ws://` on the local network for demos.

## Firestore rules

`firestore.rules` with tests in `firestore.test.js` (run against the Firestore emulator).
