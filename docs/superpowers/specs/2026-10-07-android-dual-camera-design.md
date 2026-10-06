# S23 Ultra dual-camera capture APK

## Approved scope

Build an installable Android application through GitHub Actions, targeting the researcher's Samsung Galaxy S23 Ultra. The first APK tests simultaneous rear wide and ultrawide capture and collects timing evidence for future stereo matching. The user approved this scope on 2026-10-07. This is a new Android capture subsystem on `develop/android-dual-camera`; existing iOS and browser implementations remain available.

Stereo reconstruction, fixture tracking, virtual fingertip measurement, and clinical scores are subsequent work. Existing anatomical endpoint, calibration, trial, and validation definitions in `GLOSSARY.md` and `docs/design-decisions.md` still govern that future work. Dual RGB capture does not supply TrueDepth measurements or establish measurement accuracy.

## Capture and device discovery

Use native Android Camera2 and platform UI, with no camera abstraction library. Require Android 9/API 28 or later; record the actual Android version, model, build fingerprint, app version, and source commit in each report.

Enumerate rear logical cameras and their physical camera IDs. Display candidate lenses using focal lengths, sensor dimensions, and estimated field of view. Do not hardcode S23 camera IDs or equate two arbitrary cameras with wide and ultrawide. The researcher selects a pair and confirms lens identity by covering each physical lens while observing both labeled previews. Until confirmed, identify the outputs by physical ID rather than asserting lens identity.

Open one logical camera with two same-size YUV ImageReader outputs, each explicitly routed to a different selected physical ID through OutputConfiguration. Render both previews from those outputs rather than adding extra camera streams. Choose a modest common supported resolution and record its actual dimensions. Probe configuration support where available, then require successful session configuration and sustained frame delivery from both outputs. Stream presence alone is not proof of synchronized exposure.

If no suitable logical grouping, common size, or working simultaneous session exists, display the specific failure and retain an exportable capability report. Never silently replace dual capture with alternating cameras, duplicate one output, or declare support from the phone model alone.

## Timing evidence

Run a bounded 30-second verification capture, with an earlier Stop action. Keep every observed frame's stream ID, image timestamp, callback arrival time, sequence information when available, and associated physical capture metadata. Include sensor timestamps, exposure duration, frame duration, rolling-shutter skew, timestamp source, and logical-camera synchronization type. Represent missing metadata explicitly; callback arrival times are diagnostics, not exposure times.

Join image buffers to capture results using their documented timestamps; preserve ambiguous, missing, late, and unmatched associations as such. Pair frames one-to-one, without reusing a frame. Report matched and unmatched counts, per-stream cadence, observed gaps, capture failures, queue overflows, and timestamp differences. Distinguish observed gaps from proven sensor drops. Use bounded queues; retain overflow counters and rejection reasons instead of silently discarding evidence.

Expose a positive finite maximum pairing difference in milliseconds, initially 5 ms, and store the selected value with the report. This is an exploratory filter, not a validated stereo acceptance threshold. Same-request image timestamps can be identical even with approximate synchronization: a low image-timestamp difference must not produce an exposure-synchronization claim. Physical metadata differences are reported separately when available and comparable.

Show separate results for dual-stream delivery, operator-confirmed lens identity, and timing evidence. Report approximate/unknown synchronization as requiring an external exposure-timing test. Even calibrated synchronization and low measured skew establish only the reported capture evidence; future stereo suitability requires optical timing checks with motion or a controlled flashing target, calibration, and bench validation.

## UI, storage, and lifecycle

Provide device/pair selection, two labeled live previews, a timing summary, Start/Stop, lens-confirmation controls, and Export report. Use accessible native controls and clear camera permission failures. Stop and release camera resources on backgrounding; preserve an interrupted run with its reason. A stalled stream or camera disconnect ends the run as unsuccessful rather than displaying a pass.

Persist capability findings, settings, observed frame records, metadata, and failure reasons locally in app-private storage. Export JSON through Android's document picker without network access. Write completed/interrupted reports before replacing them; retain saved runs until explicitly deleted. The first APK does not persist participant imagery: previews remain transient. Any future image recording requires a separate explicit consent and recording workflow consistent with the project decisions.

## GitHub build and acceptance

Add an Android-only GitHub Actions workflow triggered by pushes to this branch and Android pull requests. Use pinned compatible Java, Gradle, and Android build tooling. Build a signed development APK, verify its signature and manifest, and upload the APK plus test/build evidence as artifacts. CI establishes build correctness, not S23 Ultra hardware verification. Document development signing and any upgrade limitations; users must export retained research reports before an uninstall required by a signing-key change.

Leave one small runnable timing-logic check covering one-to-one pairing, threshold boundaries, unequal frame rates, out-of-order/duplicate timestamps, and missing timing evidence. CI must run it and compile the APK. A device test then checks both covered-lens identities, sustained simultaneous delivery, interruption/stall handling, and exported report completeness.

No release is described as device-verified until the researcher runs the APK on the S23 Ultra and reviews the resulting report. No stereo or clinical accuracy claim follows from a successful build or a timestamp-only check.

## Primary API references

- [Camera2 multi-camera capture](https://developer.android.com/media/camera/camera2/multi-camera)
- [Synchronization and timestamp characteristics](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LOGICAL_MULTI_CAMERA_SENSOR_SYNC_TYPE)
- [Physical capture results](https://developer.android.com/reference/android/hardware/camera2/TotalCaptureResult#getPhysicalCameraResults())
