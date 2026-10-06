# Native sensor prototype

This is Milestone 1 software for front TrueDepth feasibility work. It is not yet the full measurement app and produces no flexibility score.

## Build on a Mac

Requires Xcode with the iOS 17+ SDK and XcodeGen 2.43 or newer. The app uses the local `ReachSenseCore` Swift package.

```sh
brew install xcodegen
cd ios
xcodegen generate
open ReachSenseAI.xcodeproj
```

Set your signing team in Xcode and run the `ReachSenseAI` scheme on the initial iPhone 15 Pro Max. Other models in the requested iPhone 15 Pro Max–iPhone 18 range remain candidates until exact-device runtime support and capture quality are verified. There is no rear-camera fallback. The simulator can check the build/UI but cannot supply TrueDepth observations.

## First-device acceptance

1. Grant camera permission and start capture. Confirm front RGB and depth views change together, unmirrored, with compatible raw orientation.
2. Verify hardware ID, dimensions, timestamp skew, received/dropped counts, calibration delivery, and valid depth coverage.
3. Inspect a known-distance bench target at 300, 400, 500, and 600 mm; these are candidate validation distances, not claimed operating guarantees.
4. Explicitly authorize bench/consented RGB-depth recording, enter independently measured reference distance and target/angle notes, and save repeated paired frames.
5. Share selected JSON files to a research workstation. They contain lossless RGB PNG, contiguous float32 little-endian depth, timestamps, intrinsics, extrinsics, forward/inverse lens-distortion data, and sensor accuracy/quality labels.
6. Deny permission, interrupt/background capture, stop/restart, and cover/remove targets. Verify stale/rejected previews clear, capture does not resume automatically after interruption, a pending permission grant cannot restart stopped capture, no stale frame is saved, and no invalid depth becomes a 3D point.

The point estimate uses reference-dimension-scaled intrinsics and inverse radial mapping from an observed distorted pixel to a rectilinear ray. Stored calibration must be verified against known geometry; sensor observations and software tests do not establish measurement accuracy. Depth filtering is disabled to retain invalid samples/noise for characterization. The 5 ms pair-skew limit is an acquisition diagnostic, not a bench-derived research-QC threshold.

Apple extrinsics describe camera-to-reference-camera geometry: translation is in millimeters and rotation is unitless; the reference camera is not assumed to be RGB. Exported depth and point coordinates are in meters.

Raw frames are saved under Documents/Feasibility with no automatic deletion. The app requests system backup exclusion on both the directory and each saved file; Apple treats this flag as backup guidance, not a guarantee against every backup method. There are no automatic app uploads. Researchers must control media consent and explicit exports. No recording happens merely by opening the camera preview.

## Automated verification

```sh
swift test --package-path ..
xcodegen generate
xcodebuild -project ReachSenseAI.xcodeproj -scheme ReachSenseAI \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

CI exercises the core behavior and compiles both device and simulator targets; physical-camera acceptance remains mandatory.
