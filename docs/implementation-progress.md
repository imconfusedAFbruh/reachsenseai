# Implementation ledger — plan: plan.md

The objective is the complete measurement instrument described in `plan.md`, with verified software milestones pushed to GitHub. Code/build success does not certify physical fixtures, sensor accuracy, or research validity.

## Execution decisions

- Ruling: work on `build/sensor-prototype` in the existing clean checkout — no concurrent edits or user changes existed, and milestone pushes are explicitly authorized. Green deliverables will be published; test-first CI commits on the implementation branch are verification work, not completed milestones.
- Ruling: use macOS GitHub Actions for Swift tests and iOS builds — this Windows host has neither Swift nor Xcode. Real-device and hardware acceptance remain open.
- Initial device: iPhone 15 Pro Max. The requested compatibility range extends through iPhone 18; each exact device/capture mode requires runtime capability checks and validation before claiming support.
- Pre-flight: multi-face calibration feeds common-pose estimation; virtual-tip geometry consumes the resulting rigid-body pose; body registration feeds body-frame scoring; the scorer feeds observed-only temporal QC and the three-valid-trial session protocol. All use explicit metric units, persistent physical fixture IDs, and versioned calibration.
- Physical registration gates expanded tracking/fusion software. Minimal native capture/detection tools and fixture experiments proceed first; no invented fixture dimensions or validation data substitute for that gate.

## Milestone status

| Plan milestone | Software evidence | Physical/device evidence |
| --- | --- | --- |
| 1 — Sensor prototype | Viewer/export implemented; 8 core tests, 7 Python checks, and device/simulator builds passed | iPhone capture and recorded feasibility dataset pending |
| 2 — Common-pose tracking | Marker artwork and offline RGB observations available; common-pose solver pending | Measured face geometry and physical detection envelope pending |
| 3 — Fusion | Pending | Fixture feasibility gate pending |
| 4 — Physical fixtures | Adjustable CAD, STL, render and nominal face geometry available | Fabrication, endpoint drift, comfort, and remount tests pending |
| 5 — Two-body/occlusion tracking | Pending | Occlusion/swap tests pending |
| 6 — Body reference | Pending | Mount registration and drift tests pending |
| 7 — Temporal protocol | Pending | Device integration pending |
| 8 — Calibration | Pending | Empirical calibration and thresholds pending |
| 9 — Bench validation | Pending | Precision-reference experiments pending |
| 10 — Human validation | Pending | Bench acceptance and participant studies pending |

## Evidence

Record commands, CI run URLs, outcomes, pushed commits, and unresolved gates here as work proceeds. No milestone is complete without its specified deliverables.

- Depth geometry RED: [CI 37481921879](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37481921879) compiled the package and ran two tests. The valid-point test failed at XCTUnwrap as expected; invalid samples remained rejected. Implements a pinhole calculation with scaled calibration intrinsics, not a claim that unrectified sensor pixels are metrologically valid.
- Depth geometry GREEN: [CI 37482233100](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37482233100) passed the two behavioral tests for commit `206e1c8`.
- Pair/rectification RED: [CI 37482441432](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37482441432) ran five tests; synchronized valid-pair acceptance and radial rectification failed as expected, while geometry and invalid-input checks passed.
- Ruling: hardware-facing AVFoundation integration is verified by device/simulator compilation and explicit real-device acceptance, not synthetic mock-camera assertions — a build cannot prove synchronization or intrinsics delivery on hardware. Those checks stay pending until observed on the selected iPhone.
- The native prototype is feasibility-only: synchronized RGB/depth previews, metadata, distortion-corrected center-point estimates when calibration is available, and explicit local lossless-frame export. No valid shoulder score or calibration thresholds are fabricated.
- Sensor software GREEN: [CI 37483188580](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37483188580) passed 5/5 core tests and compiled unsigned generic iOS device and simulator builds for commit `6adc332`. Actual TrueDepth capture, calibration delivery, image/depth registration, and a recorded feasibility dataset remain pending. Platform builds emitted a nonfunctional AppIntents extraction warning and a full-screen orientation warning; the latter is addressed by the explicit full-screen setting.

- Lifecycle/storage RED: [CI 37484646872](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37484646872) ran eight core tests; the three new cancellation, freshness, and actual-file/backup-exclusion tests failed as expected before implementation.
- Frame inspection and candidate-layout tools: seven Python behavioral tests pass locally, covering corrupt payloads, invalid samples, reference-dependent statistics, and nominal geometry.
- Candidate CAD exported and rendered with official OpenSCAD 2021.01. STL edge inspection found 620 triangles with each undirected edge shared twice and nonzero volume; fabrication and anatomical registration are still pending.
- Fresh sensor review identified stale previews, unintended interruption restart, and missing backup exclusion. Corrections gate permission completions, clear rejected/expired previews, stop interrupted sessions, and request exclusion on saved folders/files. Apple extrinsic translation units and reference direction are now explicit.

- Reviewed sensor/bench-tools software GREEN: [CI 37485981533](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37485981533) passed all 8 Swift core tests, all 7 Python checks on Linux, and unsigned device/simulator builds for `33f8d40`. The first rebuild caught a Windows-encoded status character; source encoding was corrected before publication.
- Published this verified software increment to `main`. Milestone 1 remains open for real iPhone capture/registration evidence and a recorded feasibility dataset; the physical-fixture gate remains open for fabrication, independent endpoint-drift measurements, and comfort/equipment-effect checks.

- Ruling: add pinned headless OpenCV for local marker experiments — it is the planned fiducial implementation, and using its native generator/detector avoids duplicating marker coding. This adds an offline workstation dependency; it does not add an iOS/cloud dependency or bypass the physical feasibility gate.
- Marker experiments RED: [CI 37487516153](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37487516153) installed the dependency successfully and failed all three new behaviors: empty SVG, missing perspective detection and missing duplicate-ID reporting. The same failures were observed locally after installing the official SHA-256-verified wheel.
- Marker experiments local GREEN: ten Python checks pass, including all three new tests. The actual CLI generated the six-ID metric SVG/manifest and detected IDs 21/31 in a digital input while keeping `measurement_valid=false`. These are software/digital-image checks, not physical observability validation.
- Final marker review: independently rasterized the checked-in SVG and detected all six IDs; black edges span 10 mm with 2 mm quiet zones. Found a Windows Unicode-path export failure. A real CLI test using a Thai output directory failed before the fix; PNG encoding now uses OpenCV in memory and Python's path writer.
- Final: fixed Unicode-path export — the CLI regression failed before the fix and passes afterward; all 11 Python tests pass locally on Windows. CI now exercises research tools on both Windows and Linux to preserve the platform-specific regression check.
- Final: minor (deferred): automated artwork assertions verify metric metadata and black-cell count but do not fully reconstruct the SVG geometry. The independent review verified the current artifact's dimensions and decoding; strengthen this regression check before changing artwork layout.
