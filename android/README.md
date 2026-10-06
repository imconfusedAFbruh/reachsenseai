# ReachSense Dual for Android

This branch builds an Android APK that tests simultaneous rear wide/ultrawide capture on the researcher's Samsung Galaxy S23 Ultra. It uses one Camera2 logical session with two explicitly selected physical-camera YUV outputs. Device support is discovered and tested at runtime; a phone model or successful CI build is not hardware verification.

## Get the APK from Windows

1. Open [Android dual-camera APK runs](https://github.com/imconfusedAFbruh/reachsenseai/actions/workflows/android.yml), selecting the `develop/android-dual-camera` branch.
2. Open a successful run and download its **ReachSenseAI-android-debug** artifact. Extract the ZIP; the installable file is **ReachSenseAI-dual-camera.apk**.
3. Transfer the APK to the S23 Ultra and open it. Allow installation from the app opening the APK when Android requests it, then allow camera permission in ReachSense Dual.

Each artifact includes signature/manifest evidence, the source commit, SHA-256 digest, and timing/report test results. This is a signed development APK. GitHub runners generate debug signing keys; subsequent builds can have a different key and require uninstalling the old app. **Export all retained reports before uninstalling**, which removes app-private storage. Stable private signing is required before relying on in-place upgrades across research builds.

Verified software build: [run 37545350657](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37545350657), source `d15a6c0`, artifact **ReachSenseAI-android-debug**. Build, lint, assertion checks, signature and downloaded-file integrity checks passed. S23 Ultra hardware verification remains pending.

## Run the device check

1. Tap **Check cameras**. Select a candidate pair using the physical IDs and estimated FOV. Choose the main wide and ultrawide rather than either telephoto lens. IDs are discovered, not hardcoded.
2. Start a **30-second test**. Both previews must receive current frames. Previews are downsampled and rendered at up to 5 fps to reduce load; every acquired image's timestamp is retained before preview throttling. The observed capture cadence is reported separately.
3. Cover each rear lens individually. Only its assigned preview should be obstructed. Assign A/B to wide/ultrawide and confirm each covered-lens check while the run is active. Repeat a run if there was insufficient time to confirm.
4. Export the selected JSON report using Android's document picker. Capabilities, failed sessions, interrupted runs and successful captures remain separately selectable. No image pixels are stored or exported.
5. Review both stream counts and cadence, unmatched/overflow/rejected observations, capture failures, physical metadata availability, and synchronization classification. Session rejection, disconnect and a three-second stream stall terminate the run with a reason; an early Stop or backgrounding saves an interrupted run.

The pairing filter starts at 5 ms and is adjustable. It is exploratory, not a validated stereo threshold. Same-request image timestamps may be equal even when actual exposures are not synchronized. Missing physical metadata remains missing, and ambiguous timestamp associations are explicitly rejected. Exposure/frame durations, rolling-shutter skew and physical timestamps are retained when provided. Physical timestamp differences are calculated only when both sensors report the comparable REALTIME timestamp source.

If local storage fails, pending reports remain available across screen rotation and new checks are blocked until they are exported. Keep the app open and export every pending report: these fallback copies are in memory and cannot survive the app process being killed while storage is unavailable. Export failures leave the pending copy available for retry.

Approximate/unknown synchronization requires an optical timing experiment with a controlled flashing target or motion. Even hardware-calibrated synchronization needs optical checks and stereo calibration before declaring stereo suitability. This APK does not reconstruct depth, track fixtures or issue fingertip/clinical scores. It establishes capture evidence for that next stage.

## Build and checks

GitHub Actions requires no Android installation on the researcher's Windows computer. Pushes touching `android/` or its workflow trigger checks, build, lint, signature verification and artifact upload. Manual dispatch may require the workflow to exist on the default branch; branch pushes work for this development branch.

With Java 17 and an Android SDK installed, use Gradle 8.11.1: `gradle -p android assembleDebug lintDebug`. Build configuration pins AGP 8.9.2 and SDK/build tools 35. Runtime code uses only platform APIs, with API 28 as its minimum.

The standalone assertions in `checks/` exercise actual timing pairing and JSON report persistence without a camera or emulator. Their JSON test library is pinned to `org.json:json:20240303` and checksum-verified in CI; the APK uses Android's built-in JSON implementation. Real camera support, lens identity, stream timing and interruption behavior still require the S23 Ultra device checklist in the approved plan.
