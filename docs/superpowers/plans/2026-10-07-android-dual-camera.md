# S23 Ultra Dual-camera APK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Deliver a GitHub Actions-built APK that tests two simultaneous rear physical-camera streams and exports timing evidence on the Samsung Galaxy S23 Ultra.

**Architecture:** Native Camera2 opens one logical camera with two explicitly routed YUV outputs. A small Android-free timing class pairs observations; the activity renders previews and controls runs, while local reports retain diagnostic evidence.

**Tech Stack:** Java 17, Android platform APIs, Android Gradle Plugin 8.9.2, Gradle 8.11.1, compile/target SDK 35, build tools 35.0.0. No runtime third-party dependencies; GitHub Actions installs the build tools.

**Spec:** `docs/superpowers/specs/2026-10-07-android-dual-camera-design.md`

## Global Constraints

- Require Android 9/API 28 or later; discover actual physical IDs and record device/OS/build/app/source versions.
- Two same-size YUV ImageReader outputs must belong to one rear logical camera; no alternating or duplicate-stream fallback.
- Bound verification runs to 30 seconds and pairing queues to 120 records per stream; initial adjustable maximum pairing difference is 5 ms.
- Retain all observed frame records and failure reasons locally; no stored participant imagery or network permission.
- Lens identity requires operator confirmation; timestamp agreement never independently establishes exposure synchronization or stereo suitability.
- Camera disconnect, stream stall, backgrounding, and user stop produce saved reports with explicit reasons.
- Push tested milestones to `develop/android-dual-camera`; CI build success is separate from hardware verification.

## Review Focus

- Missing physical-camera metadata must stay missing, without silently substituting logical exposure timestamps.
- Out-of-order, duplicate, unequal-rate observations must not reuse frames or fabricate timing evidence.
- Permission denial, failed configuration, and one stalled stream must preserve exportable diagnostics.
- Backgrounding or restarting must release resources without overwriting retained reports or mixing callbacks between runs.
- Approximate synchronization and identical reported timestamps must not display a hardware synchronization pass.

## File responsibilities

- `android/settings.gradle`, `android/build.gradle`, `android/app/build.gradle`, `android/app/src/main/AndroidManifest.xml`: minimal platform application and reproducible build configuration.
- `android/app/src/main/java/ai/reachsense/dual/MainActivity.java`: native accessible controls, previews, permission flow, saved-report selection and document-picker export.
- `android/app/src/main/java/ai/reachsense/dual/DualCapture.java`: camera inventory, session routing, image/metadata callbacks, timeouts, lifecycle cleanup.
- `android/app/src/main/java/ai/reachsense/dual/TimingEvidence.java`: bounded one-to-one timing pairing and summary statistics independent of Android.
- `android/app/src/main/java/ai/reachsense/dual/RunReport.java`: JSON records, atomic app-private saves, retained-run listing/export.
- `android/checks/TimingEvidenceCheck.java`: one standalone assertion-based behavioral check.
- `.github/workflows/android.yml`, `android/README.md`, `.gitignore`: CI build/signature verification and installation/device-test instructions.

### Task 1: Timing evidence and its executable check

**Interfaces:** `TimingEvidence(long maximumDifferenceNs, int queueLimit)`; `observe(int stream, long timestampNs)`; `finish()`; summaries expose matched count, unmatched counts, overflow counts, and observed image-timestamp differences. Each observation gets a unique sequence index, and pairing identifies both indices. Invalid/nonpositive timestamps are retained as rejected evidence, not matched.

- [x] Write `TimingEvidenceCheck.main(String[] args)` before the implementation. Assert one-to-one matches at an inclusive 5,000,000 ns boundary, rejection beyond it, unequal stream rates, duplicate/nonmonotonic timestamps, queue overflow, and final unmatched accounting. Assert invalid threshold and queue-size inputs fail.
- [x] Compile the check and class with `javac`; observe failure before the class exists.
- [x] Implement ordered bounded queues. Pair monotonically delivered observations by earliest timestamps within the threshold; reject nonmonotonic observations explicitly rather than inventing order. Mark evicted/remaining observations unmatched. Keep statistical labels as image timestamps, not exposure synchronization.
- [x] Run `java -ea ... TimingEvidenceCheck`; require a zero exit code and the printed success message.
- [x] Commit and push the passing timing milestone.

### Task 2: Capture, native UI, and retained reports

**Interfaces:** `DualCapture(Activity activity, Listener listener)`; `discover()` supplies pair candidates with physical IDs, lens/FOV metadata, common sizes, and synchronization characteristics; `start(Pair pair, long maximumDifferenceNs)` begins a new run; `stop(String reason)` saves and releases it. Listener receives inventory, transient preview images, status, and finalized report. `RunReport` accepts capability findings, observations, physical metadata, association/rejection reasons and timings; `save(File directory)` writes a new atomic JSON file. `MainActivity` owns capture lifetime and exports a selected saved file.

- [x] Add the minimal Gradle project and manifest with camera permission, exported launcher activity, API 28 minimum, SDK 35 target, and no internet/storage permissions. Configure source-commit build information and Java 17.
- [x] Implement inventory and pair selection; enumerate all eligible distinct rear physical pairs instead of assuming the shortest two focal lengths are the requested lenses. Prefer a modest common size and report the selected dimensions. Show ID/FOV labels until cover-lens confirmation is supplied.
- [x] Implement one logical Camera2 session and two physical YUV outputs. Use a camera handler thread, close every acquired Image, and render throttled previews from those outputs with correct stride/orientation handling. Record every image timestamp before preview throttling; expose acquisition/queue failures.
- [x] Record physical capture-result timestamps and frame numbers; associate images conservatively by documented timestamp, preserving ambiguous and absent matches. Retain original image and metadata records independently so callback order cannot destroy evidence.
- [x] Implement RunReport atomic saves and retained-run selection; export via ACTION_CREATE_DOCUMENT. Save capability/configuration failures as well as completed/interrupted runs. Report export errors without deleting the original.
- [x] Implement 30-second completion, a 3-second per-stream stall watchdog, Stop, disconnect/error/background cleanup, and run-generation guards against stale callbacks. Clear lens confirmation when selection changes; preserve interruption reasons.
- [x] Implement native controls for pair selection, positive finite threshold entry, previews, stream counts/timing labels, Start/Stop, both lens confirmations, and saved-report export. Label approximate/unknown exposure synchronization as unverified regardless of matching timestamps.
- [x] Build and lint using `gradle -p android assembleDebug lintDebug`; run the timing check again. Inspect lifecycle paths for permission denial, session rejection, metadata absence, export failure, and a stopped run receiving late callbacks.
- [x] Commit and push the functioning capture milestone with its build workflow in Task 3 if a local Android SDK is unavailable.

### Task 3: GitHub APK build and concrete artifact delivery

**Interfaces:** workflow artifact `ReachSenseAI-android-debug` contains the signed APK, SHA-256 digest, source/build identity, signature/manifest evidence, and timing-check output. `android/README.md` supplies Windows-friendly download/install and device-validation instructions.

- [x] Add push/PR path filters for Android sources and the workflow; use Java 17, Gradle 8.11.1, SDK 35/build tools 35.0.0, and read-only repository permissions. Run the standalone check before `assembleDebug lintDebug`.
- [x] Verify the APK with SDK `apksigner verify --verbose --print-certs` and `aapt dump badging`; assert package `ai.reachsense.dual`, minimum SDK 28, and CAMERA permission. Generate SHA-256 and upload the actual APK/evidence with missing-file failures enabled.
- [x] Document development signing: default runner-generated debug keys may change across builds, requiring uninstall; export retained reports first. Keep production signing and Play Store publication outside this capture-verification scope.
- [x] Push and observe the Actions run to completion; fix actual test/build/lint failures and rerun before claiming success. Download the produced APK into ignored `android/artifacts/` and check its archive integrity and digest against CI evidence.
- [x] Perform one independent whole-branch review, address material findings, then rerun the affected checks. Preserve the review decisions with the plan.
- [x] Update this checklist with actual results, commit and push final documentation, and give the user a direct local APK link plus Actions run link.

## Physical S23 Ultra acceptance (researcher-operated)

- [ ] Install the APK; record OS/build and select a candidate wide/ultrawide pair. Deny permission once and check the recoverable message and exportable diagnostics.
- [ ] Run both previews simultaneously. Cover each selected rear lens individually; verify only its assigned preview is obstructed and confirm both identities. Record the finding in the exported run.
- [ ] Capture for 30 seconds; inspect both sustained delivery counts, cadence, unmatched/overflow records, physical metadata availability and synchronization classification.
- [ ] Repeat with motion or a controlled flashing timing target; inspect actual exposure disagreement before setting a validated stereo timing threshold. This external test is required for approximate/unknown synchronization.
- [ ] Stop early, background/reopen, and attempt an unsupported pair where available; verify saved reasons, camera release and no false pass. Export multiple retained runs and confirm none was overwritten.
- [ ] Keep hardware status pending until the researcher supplies actual device results; successful CI alone never checks these boxes.

## Execution evidence — 2026-10-07

- Software tasks completed on `develop/android-dual-camera`. The signed development APK source is `d15a6c0ba3dc9cfa1170128b537b4f017383ac84`.
- [Successful build run 37545350657](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37545350657): standalone timing/report checks, `assembleDebug lintDebug`, APK signature and manifest verification all passed. Artifact `ReachSenseAI-android-debug`, ID `11450881264`, includes the APK and evidence.
- Downloaded APK: `android/artifacts/ReachSenseAI-dual-camera.apk`, 79,597 bytes. SHA-256: `fce31c7582e79b5477850d847a52b106defed9854adb038b5d1ae710c8fef171`. Archive integrity, digest and build identity verified after download. Its small size reflects native platform code without bundled camera/vision libraries or media.
- Test-first checks cover one-to-one pairing, thresholds, timestamp disorder/duplicates, bounded queues, missing/ambiguous/late metadata, even-sample medians, physical timestamp differences, approximate-sync classification, report persistence, completion at a stalled-stream boundary, and consecutive failed saves. Both checks passed locally and in CI. Existing Python tests (11) and web core tests (4) passed; the configured Python environment was required for OpenCV.
- One independent read-only whole-branch review found no Critical or Minor issues and two Important issues. Both were fixed: completion shares the watchdog freshness check, and failed saves retain every report in a process-wide queue that survives Activity recreation. New checks failed before the fixes and passed afterward. Pending exports block new checks; failed exports preserve the pending copy. No review findings were deferred.
- First CI attempt failed during Gradle configuration due to a version-code DSL expression; subsequent builds passed after explicit assignment. No Android SDK or security-policy changes were made to the Windows host; a checksum-verified portable Java runtime ran the local assertions.
- The requested branch was pushed at implementation milestones and remains separate. No S23 Ultra was connected to this workstation. All physical device acceptance items above remain pending; the APK makes no stereo-depth or clinical accuracy claim.
