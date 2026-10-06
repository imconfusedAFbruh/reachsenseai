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
| 1 — Sensor prototype | In progress | iPhone capture and recorded feasibility dataset pending |
| 2 — Common-pose tracking | Pending | Face dimensions and detection envelope pending |
| 3 — Fusion | Pending | Fixture feasibility gate pending |
| 4 — Physical fixtures | Feasibility deliverables in progress | Fabrication, endpoint drift, comfort, and remount tests pending |
| 5 — Two-body/occlusion tracking | Pending | Occlusion/swap tests pending |
| 6 — Body reference | Pending | Mount registration and drift tests pending |
| 7 — Temporal protocol | Pending | Device integration pending |
| 8 — Calibration | Pending | Empirical calibration and thresholds pending |
| 9 — Bench validation | Pending | Precision-reference experiments pending |
| 10 — Human validation | Pending | Bench acceptance and participant studies pending |

## Evidence

Record commands, CI run URLs, outcomes, pushed commits, and unresolved gates here as work proceeds. No milestone is complete without its specified deliverables.

- Depth geometry RED: [CI 37481921879](https://github.com/imconfusedAFbruh/reachsenseai/actions/runs/37481921879) compiled the package and ran two tests. The valid-point test failed at XCTUnwrap as expected; invalid samples remained rejected. Implements a pinhole calculation with scaled calibration intrinsics, not a claim that unrectified sensor pixels are metrologically valid.
