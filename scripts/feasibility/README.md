# Feasibility observation tools

Python 3.10+ is sufficient; there are no third-party dependencies.

```sh
python -m scripts.feasibility.inspect_frame path/to/frame.json
python -m scripts.feasibility.inspect_frame path/to/frame.json --extract-dir data/raw/frame-001
```

This decodes the iOS export, verifies format/units/dimensions, retains invalid sensor values in the raw binary, and reports robust center-region depth statistics. Optional extraction writes lossless RGB, contiguous float32 little-endian depth, and capture metadata into a new directory.

Only add `--reference-is-axial-plane` when the entered independent reference is the axial camera depth of a planar target that fills the analyzed region. The default report deliberately gives no bias estimate. Relative sensor depth never produces a metric-bias claim. Spatial depth SD within one image is not temporal sensor repeatability or fingertip measurement accuracy.

Collect repeated physical observations at candidate distances, angles, and surfaces. Use recorded timestamps/device IDs/calibration to investigate synchronization, invalid-depth coverage, bias, and remount effects. No sample export or fabricated validation dataset is supplied.
