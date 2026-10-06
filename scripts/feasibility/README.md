# Feasibility observation tools

Python 3.10+ is sufficient; there are no third-party dependencies.

```sh
python -m scripts.feasibility.inspect_frame path/to/frame.json
python -m scripts.feasibility.inspect_frame path/to/frame.json --extract-dir data/raw/frame-001
```

This decodes the iOS export, verifies format/units/dimensions, retains invalid sensor values in the raw binary, and reports robust center-region depth statistics. Optional extraction writes lossless RGB, contiguous float32 little-endian depth, and capture metadata into a new directory.

Only add `--reference-is-axial-plane` when the entered independent reference is the axial camera depth of a planar target that fills the analyzed region. The default report deliberately gives no bias estimate. Relative sensor depth never produces a metric-bias claim. Spatial depth SD within one image is not temporal sensor repeatability or fingertip measurement accuracy.

Collect repeated physical observations at candidate distances, angles, and surfaces. Use recorded timestamps/device IDs/calibration to investigate synchronization, invalid-depth coverage, bias, and remount effects. No sample export or fabricated validation dataset is supplied.
# Marker experiments

Install the pinned OpenCV tool dependency in a virtual environment:

```sh
python -m venv .venv
# Windows: .venv\Scripts\activate; macOS/Linux: source .venv/bin/activate
python -m pip install -r scripts/feasibility/requirements.txt
python -m scripts.feasibility.markers generate --output-dir hardware/generated/artwork
python -m scripts.feasibility.markers detect observation.json \
  --expected-ids 21 22 23 31 32 33
```

Generation writes an A4 SVG and an artwork manifest. The default candidate uses `DICT_4X4_100`, IDs 21/22/23 and 31/32/33, a 10 mm black marker edge (including its black border), and a 2 mm white quiet zone on every side. Dictionary, IDs and dimensions are configurable for experiments. IDs must remain unique across both jigs and the eventual body reference. The checked-in [candidate sheet](../../hardware/prototypes/marker-sheet.svg) is ready for printing at 100% scale; measure the actual black edges afterward. Quiet zones must remain white and unobstructed. This artwork is not measured fixture calibration.

Mount each marker with its canonical top pointing toward fixture +Y, as assumed by the candidate geometry. Confirm the ID, dictionary, orientation and measured face transform in calibration; do not infer them from a rendered illustration or printed label.

Detection accepts an RGB PNG/JPEG or one native feasibility JSON export. It reports subpixel-refined canonical corners in order TL/TR/BR/BL, marker area, minimum edge length, expected-ID flags, duplicate IDs and rejected quadrilaterals. Corner coordinates refer to the raw distorted RGB image; a rotated marker's canonical TL is not necessarily the image's top-left corner. These are image observations, without pose estimation or depth fusion. `measurement_valid` is always false. Detector defaults are not validated research thresholds, and no visible-tag count establishes reliable rigid-body observability.

By default the report prints to stdout without storing another image. Explicit `--output-dir NEW_DIRECTORY` saves a JSON report and an RGB annotation; use only authorized bench/consented images. Existing output directories are refused. Reports include OpenCV/NumPy versions, source SHA-256, and the original frame ID for native exports. Retain the source export to recover its calibration and sensor metadata.

For fixture testing, record distance, lighting, incidence angle, hand configuration, blur and occlusion along with the observations. Determine supported conditions experimentally. Digital marker checks do not establish physical detection performance, image/depth registration, endpoint rigidity or instrument accuracy.

Marker generation, canonical corner ordering and detection follow [OpenCV's ArUco documentation](https://docs.opencv.org/4.13.0/d5/dae/tutorial_aruco_detection.html).
