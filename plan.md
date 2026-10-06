# Finger-to-Finger 3D Measurement System Plan

## 1. Project Goal

Build a robust iPhone-based system that measures the signed distance between the two middle-finger endpoints during a behind-the-back shoulder flexibility test.

The system is a jig-assisted 3D measurement instrument. It estimates anatomical endpoints from calibrated rigid-body references, transforms them into a participant-centered coordinate frame, and reports signed body-Y overlap with alignment and measurement-quality evidence. Direct biological fingertip visibility is not required. Physical contact is distinct from zero projected overlap.

The objective is repeatable, research-grade measurement; this is a target, not a demonstrated accuracy claim. The researcher operates the phone on a stand and assigns one hand-up position per participant at enrollment, maintained across visits. Bare-hand reference categories remain deferred until equipment effects and the reference table's provenance and applicability are established.

Illustrative output only:

```text
Signed body-Y overlap: +46.3 mm
Depth alignment offset: 18.1 mm
Quality status: VALID only if all validated checks pass
```

Negative values represent a projected gap, positive values projected overlap, and zero equal body-Y coordinates. None of these alone establishes physical contact.

---

## 2. Recommended Sensor Architecture

Use the iPhone front TrueDepth system, comprising front RGB and structured-light depth sensing, with synchronized observations, intrinsic parameters, RGB/depth calibration information, and timestamps. Front TrueDepth is the selected close-range architecture; confirm capture support on the specific device before progressing beyond feasibility work. The exact iPhone model remains unselected.

The reference hardware comprises two rigid 3D fiducial structures, each with at least three uniquely identified planar markers on differently oriented faces, and a rigid ChArUco body reference mounted on the upper back.

```text
Synchronized RGB + TrueDepth observations
                  +
Calibrated multi-face rigid-body geometry
                  +
Calibrated virtual anatomical fingertip points
                  +
Participant/body reference + temporal constraints
                  ↓
Signed body-Y overlap + quality evidence
```

Tracking and confidence belong to each complete rigid body rather than an individual marker. No MediaPipe or other AI fingertip estimation is part of core V1. TrueDepth is an observation source with experimentally characterized bias and noise, not perfect ground truth.

---

## 3. Fingertip Hardware

### 3.1 Rigid 3D multi-marker fixture

Each middle finger wears a lightweight rigid fixture containing a repeatable fingertip stop, a multi-face fiducial structure, and known rigid geometry. A hand-mounted alternative is acceptable only if anatomical registration preserves the calibrated fingertip-to-body relationship throughout the test; an unconstrained finger can move relative to a hand fixture and invalidate that relationship.

The initial fixture concept is a dorsal finger jig with registration/support at at least two separated regions, such as the middle and distal phalanges, plus the gentle fingertip stop. The supports must resist relative rotation, flexion, and sliding that would change the calibrated endpoint. A fingertip stop alone is insufficient evidence of rigidity. Evaluate DIP/PIP motion and finger rotation to identify which motions alter the endpoint-to-fixture relationship; do not assume that two contacts eliminate all motion. Final contact locations, fit adjustment, retention, and dimensions remain prototype choices.

Each structure contains at least three planar markers with distinct IDs. Candidate geometry has a central face and two faces inclined approximately 30–45 degrees from it. These are prototype candidates, not finalized dimensions.

A single planar marker can become unusable under severe perspective angle, hand rotation, partial occlusion, blur, or palm-to-palm overlap. Differently oriented faces provide alternative observations of the same calibrated rigid body when one face is hidden or poorly viewed. This is the intended robustness advantage; it must be demonstrated in rotation and occlusion tests, and does not guarantee tracking when all usable faces are hidden.

```text
         [A1: central face]
        /                  \
 [A2: inclined]      [A3: inclined]
        \__________________/
                 |
        dorsal rigid finger jig
           |             |
      middle-region  distal-region
        support         support
                           |
                   fingertip seating stop
                           · virtual anatomical endpoint
```

### 3.2 Virtual measurement endpoint

The measured anatomical reference is the furthest middle-finger point along its length, seated gently against the internal stop. Its position is estimated as a calibrated virtual point in the fixture's common rigid-body frame. The printed marker location and any external nub are tracking/calibration geometry, not the anatomical endpoint.

A candidate sphere or hemispherical nub of approximately 8–12 mm diameter may assist physical calibration, but neither that size nor use of a nub is finalized. Distinguish its center/surface from the anatomical stop location in calibration records.

### 3.3 Mechanical registration

Minimize fixture slippage, insertion variability, soft-tissue effects, and obstruction during palm-to-palm overlap. The operator checks gentle fingertip seating before and after each trial. Marker visibility does not prove correct seating. Movement of the fixture relative to the finger invalidates the affected trial; reseat and restart.

Seating checks include engagement of both registration regions and the stop. Independently assess endpoint drift during relevant finger motion, removal/remounting, and palm overlap. Multi-region support may alter natural finger posture or reach; evaluate that equipment effect and comfort alongside registration repeatability before accepting the fixture design.

### 3.4 Physical identity and marker IDs

Assign a persistent physical jig ID distinct from its current upper/lower role. Example marker sets are upper structure A1/A2/A3 = 21/22/23 and lower structure B1/B2/B3 = 31/32/33. These are examples; dictionaries and IDs remain configurable until detection testing. Reserve unique IDs across both structures and the body reference.

For rigid body R and marker M_i, calibrate T_R_Mi, the transform mapping marker coordinates into R. Every marker corner then has a known 3D position in R. Store geometry, virtual endpoint, and calibration version by physical jig ID. Confirm role assignments at setup; an observed within-session swap triggers notification, pause, trial invalidation, and researcher confirmation of assignments and seating before restart.

---

## 4. Surface / IR Design

Do not rely on IR spray as the primary tracking method. Select marker-face and candidate endpoint materials experimentally: matte white, light gray, black, and diffuse IR-reflective materials are candidates. Avoid glossy/specular coatings, uncontrolled retroreflective paint, and surfaces that saturate or destabilize depth observations.

Evaluate valid-depth ratio, depth bias/noise, temporal stability, viewing-angle sensitivity, and RGB marker detection. Test individual faces and transitions between visible faces. Report measured results rather than illustrative material-performance values. Final material and geometry choices must precede bench validation.

---

## 5. Body Reference

Mount a rigid ChArUco board or equivalent calibrated multi-marker reference on the upper back. Its pose establishes the participant-centered frame and accounts for phone tilt and participant orientation while sufficiently observed and correctly registered to the torso.

Define a right-handed frame: +Y superior along the back, +Z outward from the back/reference plane, and +X lateral in the direction completing the right-handed frame (+Y × +Z). Record board orientation and torso registration. A board frame is not automatically an anatomical frame if mounting is misaligned.

```text
              +Y (superior)
                   ↑
         -X ←───────┼───────→ +X
                   ↓
                  -Y
       +Z outward from the back
```

Select secure mounting and visibility through prototypes. Board slippage and soft-tissue motion are uncertainty sources. Reject frames when the body reference lacks a valid current observation; do not silently use an old pose.

Treat the upper-back mount as a calibrated fixture. Prototype a lightweight repeatable harness or multi-contact mounting method with documented contact locations and superior-axis alignment; simple attachment to clothing is not evidence of anatomical registration. Verify remount repeatability and board-to-torso angular drift during the pose. Invalidate an affected trial when mounting validity is lost, and re-register before restarting.

If F denotes the fiducial board frame and B the registered body frame, record the mounting calibration T_F_B and calculate T_C_B = T_C_F T_F_B. Identity is permissible only when documented registration aligns the frames. Include mounting calibration identity/version with each trial; visibility of the board alone does not prove stable torso registration.

---

## 6. Camera Configuration

The researcher operates the iPhone on a fixed stand approximately centered behind the participant. Start feasibility/bench characterization at 300–600 mm camera-to-target distance; this is a provisional test envelope, not a claimed optimal or supported range. Narrow the supported range if validation fails. Record distance definition, viewing angles, lighting, capture mode, and device model.

Require sufficient visibility of each complete hand rigid body and the body reference. Do not require every face or either anatomical fingertip to be visible. The minimum acceptable face visibility and viewing-angle limits must be determined experimentally.

The alignment overlay reports body-reference observation, upper/lower rigid-body confidence, calibrated operating range, torso orientation, depth/lateral alignment, and depth coverage. Block capture until required checks pass.

---

## 7. Software Stack

### Native iOS measurement engine

Recommended:

- Swift;
- ARKit / AVFoundation;
- OpenCV;
- Accelerate / simd;
- Metal only if needed for performance.

### UI

Option A — fully native SwiftUI.

Option B — native measurement engine + web-style UI.

If using React/web technologies, keep all sensor access and geometric processing in the native Swift layer.

Do not depend on Safari/PWA for raw TrueDepth access.

---

## 8. Frame Processing Pipeline

For each synchronized RGB/depth pair, retain timestamps and calibration metadata and perform:

```text
RGB → all visible marker IDs → refined corners
                             ↓
           common rigid-body pose from known 3D corners
                             ↘
Depth → RGB/depth registration → valid 3D samples
                             ↓
           separate surface/plane fits per visible face
                             ↙
        quality-weighted RGB/depth/geometry/temporal fusion
                             ↓
  upper rigid-body pose + lower rigid-body pose + body pose
                             ↓
          calibrated virtual anatomical fingertip points
                             ↓
       body-frame transform → signed ΔY + ΔX/ΔZ QC
                             ↓
      observed-frame validity → continuous stable window
```

The curved/multi-face structure must not be treated as a single plane. Log failures and missing estimates explicitly rather than substituting zeros or purely predicted endpoints.

---

## 9. RGB Pose Estimation

Detect all visible fiducials, identify IDs, refine corners to subpixel precision, and use physically measured dimensions and calibrated face transforms. Group observations by physical rigid body and estimate one common 6-DoF pose using all its valid visible corners with solvePnP or an equivalent calibrated estimator. Do not independently estimate and average several marker poses.

Notation throughout this methodology: T_A_B maps coordinates from frame B into frame A. C denotes camera, R a hand rigid body, F the fiducial board, and B the registered participant/body frame. Thus T_C_R maps rigid-body coordinates into camera coordinates.

For known corner P_j^R = T_R_Mi P_j^Mi and observed pixel p_j:

```text
T_C_R* = argmin_T Σ_j ρ(||p_j - π(K, distortion, T P_j^R)||²)
```

Here π is calibrated projection, K is the intrinsic matrix, and ρ is a robust loss whose implementation is documented. Report contributing IDs, visible-face count, corner quality, reprojection residuals, pose confidence, and ambiguity handling. One face may be usable with reduced confidence only if validation supports it; multiple faces do not automatically guarantee high confidence. Severe tilt, small image area, blur, occlusion, or ambiguous/poorly conditioned pose must reduce confidence or invalidate the observation.

---

## 10. TrueDepth Processing

For each currently visible face, map its RGB region into the depth domain using verified registration. Reject invalid samples and use applicable capture-quality metadata. Convert multiple valid depth samples into calibrated camera-frame 3D points; record depth units and whether conversion uses axial depth or another representation.

Fit each visible planar face robustly, excluding edges, unrelated hand/background surfaces, and mixed-depth pixels. Estimate depth residuals, local variance, plane normal, valid-pixel ratio, and temporal consistency. The face's known orientation in R constrains its predicted geometry under T_C_R. Do not use a single depth pixel or fit one plane across differently oriented faces.

Characterize TrueDepth bias, noise, angle dependence, usable range, and registration error experimentally. Depth samples independently constrain geometry but do not constitute ground truth.

---

## 11. RGB + Depth Fusion

Estimate each common hand pose through quality-weighted fusion of image observations, measured depth, known geometry, and temporal constraints:

```text
T* = argmin_T [w_rgb E_rgb + w_depth E_depth
             + w_plane E_plane + w_rigid E_rigid + w_time E_temporal]
```

- E_rgb: residual between observed corners and projected known rigid-body corners.
- E_depth: residual between measured 3D samples and predicted visible-face geometry.
- E_plane: disagreement between observed local normals and transformed calibrated face normals.
- E_rigid: inconsistency with fixed face geometry if separate face variables are estimated.
- E_temporal: implausible changes relative to previously observed poses.

With a single common pose and fixed geometry, rigidity is enforced by construction; E_rigid need not be a separate nonzero penalty. Document the parameterization, robust losses, residual units/scales, and optimizer. Do not average RGB and depth or require fixed equal weights.

RGB confidence uses reprojection error, marker area, sharpness, corner quality, viewing angle, and visible-face count. Depth confidence uses valid coverage, variance, temporal stability, incidence angle, and RGB/depth disagreement. Reduce the contribution of poor observations; reject frames when critical information is absent or sensor disagreement exceeds bench-derived, versioned limits. Temporal regularization cannot make an unobserved rigid body valid.

---

## 12. Rigid-Body-to-Fingertip Calibration

For every physical jig, calibrate each face transform T_R_Mi and the homogeneous anatomical endpoint:

```text
P_tip^R = [x_tip, y_tip, z_tip, 1]^T
P_tip^C = T_C_R P_tip^R
```

The virtual point corresponds to the agreed distal anatomical endpoint gently registered against the stop. Calibration of rigid jig geometry does not prove repeatable anatomical placement; evaluate insertion, remounting, slippage, and any finger motion relative to the fixture separately.

Store physical jig identity, face IDs/dictionary, measured dimensions, transforms, stop/endpoint definition, units, coordinate conventions, calibration method, version, date, and verification evidence. Block measurement for missing or invalid calibration. Recalibrate after marker movement, damage, mounting changes, or endpoint/stop geometry changes. Calibration remains attached to the physical jig when its assigned hand role changes.

---

## 13. Body Coordinate Transformation

Estimate the currently observed body-reference pose T_C_B using calibrated ChArUco geometry and verified RGB/depth consistency. Apply the convention established in Section 9:

```text
P_tip^B = inverse(T_C_B) P_tip^C
         = inverse(T_C_B) T_C_R P_tip^R
```

Apply this separately to upper- and lower-hand endpoints. Use consistent metric units and timestamps; log both camera-frame and body-frame coordinates. Orientation compensation is valid only within the experimentally supported range and with secure anatomical board registration.

---

## 14. Primary Measurement

Let A denote the upper-hand endpoint and L the lower-hand endpoint, with both expressed in body coordinates. Roles are assignments, not permanent physical marker identities.

```text
ΔX = X_L - X_A
ΔY = Y_L - Y_A
ΔZ = Z_L - Z_A
D = ΔY
D_3D = sqrt(ΔX² + ΔY² + ΔZ²)
```

With +Y superior: D < 0 is projected gap, D > 0 is projected overlap, and D = 0 is equal Y coordinates. For example, -35 mm is a 3.5 cm projected gap and +42 mm a 4.2 cm projected overlap. Zero must not be labeled physical touching solely from this score; lateral/depth offsets may remain nonzero.

Use signed ΔY as the primary flexibility score. Report ΔX and ΔZ as alignment/QC quantities and D_3D as a diagnostic quantity, not the primary score. The trial score is the median of valid observed-frame D values in the required stable window.

Before comparison with any published normative table, establish measurement-definition equivalence: source population, test posture and hand assignment, anatomical endpoints, measurement direction, sign/units, alignment rules, and trial aggregation. Determine whether the published score is projected overlap, an along-finger measurement, or another distance. Test lateral/depth offset cases explicitly: +40 mm projected overlap with separated fingers cannot automatically be interpreted as +40 mm under an external protocol. Use paired protocol measurements to assess agreement; equipment-effect validation alone is insufficient. Until equivalence is supported, retain the jig-assisted body-Y score without normative category labels.

The researcher assigns one hand-up position per participant at enrollment and keeps it consistent across visits. Use one familiarization attempt, then at most five scored attempts to obtain three valid trials. Preserve unsuccessful attempts; the position session score is the median of the three valid trial scores. If fewer than three are valid, mark the position incomplete and issue no position session score. Do not combine left/right scores or label results with unvalidated reference categories.

---

## 15. Depth-Alignment and Occlusion Checks

Reject or pause capture when |ΔZ| or lateral offset exceeds experimentally validated protocol limits. A small image-space gap is insufficient evidence of acceptable 3D alignment. Suggested feedback: "Hands are too far apart in depth. Adjust the hand position and hold still." Do not invent final alignment thresholds.

### Palm-to-palm overlap and complete fingertip occlusion

The biological fingertip may be occluded while its position remains estimable through the calibrated rigid-body reference. If the rigid-body reference itself becomes unobservable, the frame is rejected.

```text
Visible faces on each rigid body
             ↓
Fused currently observed common poses
             ↓
Calibrated rigid-body-to-tip points
             ↓
Estimated anatomical fingertip locations
```

This applies to touch, overlap, and palm-to-palm configurations only while sufficient current fiducial observations and depth/QC evidence exist for both structures and the body reference. It is not measurement through an occluding hand. Temporal predictions can aid detection continuity or a clearly labeled preview; purely extrapolated endpoints never contribute valid measurement frames. Recovered observation starts a new continuous passing window.

---

## 16. Temporal Filtering

Require one continuous second of passing observed-frame quality checks. Validate capture rate and timestamp continuity experimentally; do not assume a fixed 30/60-frame count. Missing or invalid observations, capture discontinuities, excessive motion, sensor disagreement, or any required check failure reset the window.

For every frame retain upper/lower/body poses, estimated virtual endpoints, signed ΔY, ΔX, ΔZ, RGB/depth residuals, quality flags, and hand/body velocities. Calculate the trial score as the median signed overlap in the passing window and retain dispersion such as median absolute deviation as quality evidence, not a validated accuracy bound.

The researcher starts each attempt. Capture automatically when the passing window is complete, then require post-trial seating confirmation. A provisional 15-second acquisition timeout or earlier operator stop creates an unsuccessful attempt counted toward the five-attempt limit. The timeout does not apply to subsequent seating confirmation. Rest duration is at the researcher's discretion; log timestamps and elapsed inter-attempt intervals, which may include setup and adjustments.

---

## 17. Automatic Measurement State Machine

```text
SEARCHING → ALIGNING → READY
                       ↓ researcher starts + pre-trial seating check
                  HOLD STILL → MEASURING (continuous 1 s)
                                      ↓
                     VALIDATING (post-trial seating check)
                                      ↓
                                    RESULT
```

READY requires valid calibration, confirmed physical-jig roles, verified body-reference mounting registration, an observed body reference, sufficient upper/lower rigid-body confidence, supported geometry, adequate depth coverage, and passing RGB/depth alignment checks. MEASURING additionally requires low hand/body motion and continuous valid observations. Operator-reported mounting loss invalidates the affected trial; do not assume optical tracking automatically detects mount-to-torso slippage.

A critical failure resets the stable window and returns to alignment/hold while the acquisition timeout continues. On timeout or early stop, save an unsuccessful outcome. Jig swaps or slippage notify, pause, and invalidate the attempt; confirm assignments/seating before starting a new attempt. A failed post-trial seating check invalidates the captured trial. Researchers may save failed observations but cannot override quality failures to produce valid scores.

---

## 18. Measurement QC

Show live quality for the complete upper/lower rigid bodies and body reference, including contributing marker IDs/counts, reprojection error, depth valid ratio and residuals, plane-normal consistency, sensor disagreement, torso motion/orientation, hand velocity, and ΔX/ΔZ alignment. Report stable elapsed time and observed-frame counts.

Fewer visible faces may lower confidence; all faces hidden on either hand means invalid. Experimental visibility criteria, not face count alone, determine acceptance. Store all thresholds in a versioned bench-derived set and log it with each trial. Missing threshold/calibration verification blocks valid research measurement. Preserve failed attempts and explicit failure reasons; do not mask low completion rates by reporting only accepted-frame accuracy.

Define complete rigid-body observability by pose conditioning/ambiguity, image-space corner geometry and resolution, reprojection residuals, and independent depth support. A visible-marker count is descriptive, not an acceptance rule. Record the conditioning measure, its scaling/units, and experimentally chosen limits. Apply the same principle to the body-reference pose and do not let a temporal prior conceal insufficient current geometry.

---

## 19. Uncertainty

No final millimeter-level accuracy or uncertainty is claimed before experimental validation. Display resolution and stable-window dispersion are not accuracy evidence.

Characterize TrueDepth bias/noise, RGB corner localization, camera/depth registration, marker printing, rigid-body face geometry, virtual-tip calibration, mechanical placement, fingertip-stop repeatability, back-reference registration/movement, soft tissue, temporal motion, and partial occlusion.

If uncertainty is reported, derive it from experimentally characterized error propagation or empirical validation/repeatability for the supported conditions, with assumptions, coverage, and confidence interpretation documented. Do not label unvalidated examples as ±1 mm or ±2 mm performance.

---

## 20. Calibration Procedures

### 20.1 Camera/depth verification

Verify intrinsics, distortion behavior, RGB/depth registration, timestamp alignment, depth representation/scale, bias, and noise across candidate distances and angles. Record device, capture mode, verification procedure, and results.

### 20.2 Printed fiducial dimensions

Measure actual marker dimensions and ChArUco square/marker sizes with physical metrology; nominal printer dimensions are insufficient.

### 20.3 Multi-marker rigid-body calibration

Measure every T_R_Mi, marker-corner coordinates, and face orientation in one common frame per physical jig. Verify rigidity and record geometry tolerances and calibration version.

### 20.4 Virtual anatomical endpoint calibration

Determine P_tip^R at the seating stop using documented fixtures/metrology. Record endpoint definition and distinguish optional nub centers/surfaces. Verify before/after seating checks and examine removal/reinstallation effects.

Verify engagement of both finger registration regions and characterize endpoint drift from flexion, rotation, sliding, fit variation, and remounting independently of camera pose error. Record fixture sizing/configuration and mechanical registration verification evidence.

### 20.5 Body-reference calibration

Verify printed geometry, right-handed coordinate convention, superior-axis alignment, secure mounting, and anatomical registration repeatability.

Calibrate the board-to-body mounting transform T_F_B, document harness/contact geometry and orientation procedure, and quantify angular/positional drift during trials and removal/remounting. Evaluate how those errors propagate into ΔY rather than treating a stable board pose as proof of a stable anatomical frame.

### 20.6 Depth bias and mechanical repeatability

Use known-distance targets; candidate distances include 250, 300, 350, 400, 450, 500, and 600 mm. Values outside 300–600 mm extend characterization rather than establish supported operation. For each condition record bias, SD, valid coverage, incidence angle, and lighting/material effects. Repeat jig removal, insertion, and reference remounting and quantify their effects.

Archive calibration evidence by physical jig and device/configuration. Changed marker, stop, or mounting geometry requires recalibration. Determine acceptance limits and verification frequency through experiments and document them before scored research use.

---

## 21. Validation Plan

Use an independently characterized precision reference, such as a calibrated caliper, gauge blocks, linear stage, or mechanical fixture. Specify reference uncertainty, coordinate registration, endpoint definition, repetitions, and test conditions before validation. TrueDepth is not the validation reference.

### Phase A — Static bench test

Test signed overlaps -100, -75, -50, -25, 0, +25, +50, +75, and +100 mm within the provisional camera-distance envelope of 300–600 mm. Repeat at documented distances, viewing angles, lighting, and materials. Evaluate accepted engineering targets throughout the envelope, report condition-specific failures, and narrow supported conditions if needed.

### Phase B — Hand rigid-body rotation

Rotate each structure through relevant orientations; quantify detection/pose error against known geometry and performance versus visible-face count and angle, including one-face and multi-face configurations. Establish experimentally which observations are usable.

### Phase C — Systematic occlusion

Cover one face, two faces, part of a rigid body, one anatomical fingertip, and both fingertips. Verify preserved accuracy when references remain observable, appropriate confidence reduction, and rejection when either rigid body or the body reference lacks sufficient current observation. Test that extrapolated frames do not enter a valid stable window.

### Phase C.1 — Complete rigid-body observability experiment

Use known rigid-body poses and systematically vary the number and arrangement of observed corners, face-normal diversity, projected target size, viewing angle, partial-face occlusion, blur, and depth coverage. Include three well-resolved faces, two faces with distinct normals, one frontal face, one highly oblique face, and two tiny/partially visible faces. These are test cases, not predetermined PASS/REJECT labels; missing corners must not be fabricated.

Compare estimated pose and resulting endpoint/ΔY error with the precision reference. Record pose-conditioning/ambiguity indicators, reprojection residuals, depth support, and acceptance/rejection outcomes, including apparently low-residual but ambiguous solutions. Derive a versioned joint observability rule from these experiments and verify it on separate conditions/repetitions not used to tune the rule. Reject geometrically insufficient cases even when a nominal marker-count threshold would pass.

### Phase D — Overlap and palm-to-palm configurations

Test increasing projected overlaps, for example 0, +10, +20, +30, and +50 mm, and severe overlap within the broader test envelope. Record marker visibility and any fixture interference. Zero projected overlap is not a physical-touch ground-truth label.

### Phase E — Depth-offset test

Hold true body-Y overlap fixed while varying ΔZ and lateral alignment. Quantify Y-score sensitivity and establish alignment limits according to the protocol; evaluate correct rejection as well as accepted-result accuracy.

### Phase F — Body-reference rotation

Keep true endpoint geometry fixed and vary yaw, pitch, and roll. Initial candidate angles from the original plan are yaw ±20 degrees and pitch/roll ±15 degrees. Verify transformation stability and characterize limits rather than assume these angles are supported.

Separately perturb/remount the body-reference fixture relative to the torso surrogate while keeping endpoint geometry fixed. Quantify body-axis registration error and its effect on ΔY, then evaluate drift/remounting on participants during consented pilots. Board visibility and camera-relative pose quality do not replace mounting validity.

### Phase G — Human repeatability and equipment effects

Bench acceptance for static distance, depth offsets, and rotation is required before scored human research measurements; occlusion/overlap behavior must also support intended use. Earlier consented setup/comfort pilots remain exploratory. Assess repeat visits, operator effects, cap removal/reinstallation, back-reference mounting, and effects on reach/comfort versus the intended bare-hand test. Keep the assigned hand-up position consistent and follow the three-valid-trial protocol.

Evaluate multi-region finger registration and mounting effects independently from optical tracking. For any intended normative comparison, add paired measurements under the source test definition and the instrument protocol, including alignment edge cases, and establish measurement-definition agreement before applying published categories.

### Phase H — External ground-truth comparison

If available, compare against Qualisys/Vicon, calibrated stereo vision, or a precision mechanical fixture with documented reference error. Interpret results within each method's limitations. Human repeatability and equipment effects must be assessed before accuracy claims or bare-hand reference categories are used.

---

## 22. Performance Metrics

### Accuracy and provisional engineering acceptance

Report MAE, RMSE, signed systematic bias, SD of errors, and 95% limits of agreement against the precision reference. Accepted provisional bench targets are MAE ≤ 5 mm, absolute bias ≤ 2 mm, and repeated static measurement SD ≤ 2 mm across agreed conditions. These are requirements to test, not established capabilities.

### Repeatability

Report within-window/within-trial dispersion, repeated static trial SD, between-trial and remount SD, and operator/visit effects. Report ICC where appropriate with its model and confidence interval. Coefficient of variation is unsuitable near zero or across signed gap/overlap values; prioritize absolute-unit metrics there.

### Robustness and latency

Report detection success, valid/rejected-frame rates, depth availability, completion rate, and rejection correctness with explicit denominators, including failed attempts. Report FPS and frame-to-result latency.

Stratify performance by visible-tag count, viewing angle, occlusion severity, camera distance, depth quality, signed overlap, and reference/hand orientation. Publish validation sample counts and uncertainty intervals; acceptance on surviving frames alone is insufficient evidence of robustness.

---

## 23. Data Logging

Clearly separate four categories:

- Sensor observations: captured RGB pixel values, depth samples/maps, and timestamps; raw media are retained only when recording is enabled. Depth is a sensor-reported measurement with bias/noise, not an exact physical position.
- Estimated quantities: extracted/refined image corners, fused rigid-body/body poses, virtual endpoint coordinates, velocities, residuals, signed score, and uncertainty if validated. Detected corners are derived image observations used by the pose estimator.
- Calibration parameters: marker dimensions, face transforms, virtual-tip points, device calibration, units/conventions, and versioned quality limits.
- Validation metrics: reference-based MAE/RMSE/bias, repeatability, agreement limits, and robustness/completion rates from dedicated experiments.

Mandatory per-frame records include:

```text
timestamp / RGB-depth pair timing
physical jig IDs / assigned upper-lower roles
upper and lower rigid-body poses / body-reference pose
visible upper and lower marker IDs / visible counts
RGB reprojection error / depth residual / plane-normal residual
pose conditioning / ambiguity status / image-space corner geometry
depth valid ratio / variance / RGB-depth disagreement
upper and lower virtual-tip XYZ (camera and body frames)
delta_x_mm / delta_y_mm / delta_z_mm / euclidean_distance_mm
hand velocities / body velocity and orientation
observation-versus-prediction status
frame_validity / frame_rejection_reasons
calibration IDs and versions / threshold-set version
body-reference mounting calibration ID and version
```

Represent missing estimates explicitly. Mandatory attempt/session records include participant study ID, session and attempt IDs, selected upper hand, device/iOS/capture mode, timing, familiarization/scored status, pre/post seating outcomes, swap/slippage events, raw trial aggregate, accepted final trial score, valid/rejected counts, acquisition duration, validity/failure reasons, inter-attempt intervals, and position completeness/session score. Incomplete positions have no session score.

Retain these records locally and indefinitely. RGB/depth recording is an explicit consented research option, also retained indefinitely when enabled. Optional debug overlays, raw corners, and point-cloud samples support investigation. Summary CSV and structured detailed JSON exports include every attempt and mandatory per-frame records; attach optional RGB/depth media only when explicitly selected for export. Preserve calibration and threshold definitions needed to interpret the records.

---

## 24. UI Plan

### Capture screen

Show live RGB, all detected fiducial faces, common upper/lower rigid-body axes, estimated virtual anatomical endpoints, body axes, signed body-Y overlap, and lateral/depth alignment. Clearly identify estimated points even when fingertips are hidden; invalid/predicted previews must not look like valid observations.

Show selected upper hand, physical-jig assignments, calibration status, rigid-body visibility/confidence, depth consistency, motion quality, stable-window elapsed time, attempt count, and remaining acquisition time. The researcher confirms pre-trial seating and starts an attempt. Swaps/slippage pause with explicit corrective guidance. Captured scores remain pending until post-trial seating confirmation.

### Result screen

Show signed overlap/gap in mm and cm, ΔX/ΔZ, quality status, valid/rejected counts, trial outcomes, three-trial median when complete, and save/export controls. Display unsuccessful attempts and incomplete positions explicitly. Do not label zero projection as touching or unvalidated dispersion as accuracy.

Include explicit optional recording/consent status and media-export selection. Rest is researcher-controlled; elapsed inter-attempt intervals are logged automatically.

---

## 25. Development Milestones

The numbering organizes deliverables rather than requiring all software to precede hardware. Start physical fixture feasibility in Milestone 4 with minimal Milestone 1/2 tooling; its registration evidence gates expanded pose-fusion software and finalized geometry.

### Milestone 1 — Sensor prototype

Choose and document one front TrueDepth-equipped iPhone; verify synchronized RGB/depth access, intrinsics/registration, timestamps, visualization, and calibrated 3D sample conversion. Deliver a recorded feasibility dataset and live viewer.

### Milestone 2 — Multi-marker common-pose tracking

Detect configurable marker IDs, refine corners, use measured multi-face geometry, and estimate one common 6-DoF pose per rigid body from all visible corners. Deliver axes, reprojection residuals, and visibility/confidence logging.

### Milestone 3 — RGB/depth/geometry fusion

Fit separate visible-face surfaces, fuse observations with quality weighting and temporal constraints, and reject disagreement or unobservable references. Deliver observed fused poses and rejection evidence.

### Milestone 4 — Physical jigs and endpoint calibration

Prototype lightweight multi-face fixtures with at least three markers each and repeatable fingertip stops. Test materials, comfort, slippage, and overlap interference; document chosen dimensions, per-face transforms, and anatomical endpoint calibration.

Prioritize this physical feasibility gate before expanding the measurement software: demonstrate multi-region anatomical registration, quantify endpoint drift/remount repeatability with an independent reference, and document effects on posture/reach. Minimal capture/detection tooling may support fixture experiments; final geometry is not frozen until evidence supports it.

### Milestone 5 — Two-rigid-body and occlusion tracking

Track both physical structures and virtual tips, test rotation/partial occlusion/palm overlap, and verify swap notifications and role assignment. Reject fully unobserved bodies rather than accepting extrapolated tips.

### Milestone 6 — Body reference

Calibrate/mount the ChArUco reference, transform tips into body coordinates, and compute signed ΔY with ΔX/ΔZ diagnostics. Deliver metric body-frame geometry.

### Milestone 7 — Temporal QC and researcher protocol

Implement the continuous one-second median, observation/motion checks, pre/post seating confirmation, provisional 15-second timeout, and three-valid-trials-in-five-attempts protocol. Log researcher-controlled intervals and all failed attempts.

### Milestone 8 — Calibration and threshold characterization

Verify device calibration, multi-face geometry, anatomical tip calibration, reference registration, depth bias, and remount repeatability. Derive and version quality thresholds; block missing/invalid calibration.

### Milestone 9 — Bench validation

Validate known distances, depth offsets, hand/body rotation, face occlusion, and overlap. Report accuracy targets, rejection correctness, and completion metrics by condition, and establish the supported envelope.

### Milestone 10 — Human research validation

After bench acceptance, assess repeatability, equipment/comfort and operator effects, and reference comparisons. Deliver the research dataset with summary CSV/detailed JSON and explicit optional media export. No final accuracy or bare-hand category claim precedes supporting evidence.

---

## 26. Suggested Repository Structure

```text
FingerMeasure/
│
├── README.md
├── PLAN.md
│
├── docs/
│   ├── calibration_protocol.md
│   ├── validation_protocol.md
│   ├── marker_design.md
│   └── measurement_definition.md
│
├── ios/
│   ├── App/
│   ├── Camera/
│   ├── TrueDepth/
│   ├── Fiducials/
│   ├── Fusion/
│   ├── Geometry/
│   ├── Measurement/
│   ├── QualityControl/
│   └── Export/
│
├── hardware/
│   ├── upper_finger_jig/
│   ├── lower_finger_jig/
│   ├── body_reference/
│   └── drawings/
│
├── calibration/
│   ├── camera/
│   ├── depth/
│   ├── jigs/
│   └── marker_dimensions/
│
├── validation/
│   ├── bench/
│   ├── human/
│   └── ground_truth/
│
├── data/
│   ├── raw/
│   ├── processed/
│   └── exports/
│
└── scripts/
    ├── calibration_analysis/
    ├── validation_analysis/
    └── plotting/
```

---

## 27. V1 Scope

The first usable V1 contains:

1. Native iOS front TrueDepth acquisition with synchronized RGB/depth and calibration metadata.
2. OpenCV fiducial detection and subpixel corner refinement.
3. Two rigid 3D hand/finger structures, each with at least three uniquely identified marker faces.
4. Common-pose estimation from all visible known rigid-body corners.
5. Local TrueDepth 3D samples and per-face surface fits.
6. Quality-weighted RGB/depth/rigid-geometry/temporal fusion.
7. Calibrated ChArUco body reference.
8. Per-physical-jig face transforms and virtual anatomical fingertip points.
9. Body-coordinate transformation and signed Y overlap.
10. ΔX/ΔZ alignment QC and diagnostic 3D distance.
11. Continuous one-second observed-frame median and automatic rejection.
12. Researcher start, seating confirmations, swap recovery, timeout, and trial/session aggregation.
13. Versioned calibration/quality files and complete attempt/per-frame logging.
14. Summary CSV and detailed JSON research export with explicitly selected optional media.
15. Bench-validation tools for accuracy, rotations, occlusion, overlap, and depth offsets.

Do not add AI hand tracking, cloud processing, accounts, or advanced analytics to core V1 before the instrument is validated. Hidden anatomical tips are supported only through sufficiently observed calibrated rigid bodies.

---

## 28. Success Criteria for V1

V1 succeeds when both calibrated anatomical endpoints are estimable under validated visibility conditions; sufficient body reference remains observed; signed body-Y overlap is reported in metric units; accepted pose/depth changes do not materially corrupt the score; and unobservable, inconsistent, swapped, or poorly seated configurations are rejected rather than guessed.

Demonstrate repeatable static results and test the provisional MAE ≤ 5 mm, absolute bias ≤ 2 mm, and static repeated-measurement SD ≤ 2 mm requirements across documented supported conditions. Report failures/rejections and completion rates alongside accuracy. Export all attempts and interpretable calibration/quality evidence; enforce the agreed three-valid-trial protocol and no-score incomplete outcome.

Do not define a final accuracy claim before validation. Human repeatability and equipment effects remain required before claiming suitability for the intended research test or comparison with bare-hand reference categories.

---

## 29. Final Target Architecture

```text
                        iPhone front TrueDepth
                                 |
                   +-------------+-------------+
                   |                           |
                  RGB                         Depth
                   |                           |
           Multi-marker detection          3D samples
                   |                           |
          Common rigid-body solvePnP     Per-face fitting
                   |                           |
                   +-------------+-------------+
                                 |
                       QUALITY-WEIGHTED FUSION
                                 |
             +-------------------+-------------------+
             |                   |                   |
       Upper rigid body    Lower rigid body     Body reference
             |                   |                   |
     Calibrated virtual A  Calibrated virtual L    Body frame
             |                   |                   |
             +-------------------+-------------------+
                                 |
                     Body-coordinate transformation
                                 |
                      Signed ΔY + ΔX/ΔZ alignment
                                 |
             Continuous observed 1 s median + seating checks
                                 |
                    Valid trial / explicit failed attempt
                                 |
             Three-valid-trial session median + research export
```

---

## 30. Immediate Next Steps

1. Prototype the dorsal multi-region middle-finger fixture first: two separated registration regions, gentle fingertip stop, and a lightweight multi-face target. Quantify endpoint drift, remount repeatability, comfort, and overlap interference before expanding software or freezing geometry.
2. Select and record the target iPhone; verify synchronized front TrueDepth capture and create only the minimal RGB/depth/detection tooling needed for physical feasibility experiments.
3. Select marker dictionary, dimensions, materials, face layout, and a repeatable upper-back harness/multi-contact mount through experiments; calibrate board-to-body registration.
4. Define common rigid-body frames, measure each face transform and virtual anatomical endpoint, and document seating checks.
5. Implement joint visible-corner pose estimation and separate depth fits for visible faces.
6. Implement quality-weighted fusion, disagreement checks, and rejection of unobservable references.
7. Calibrate and securely mount the upper-back ChArUco target; implement body-frame transformations and signed-overlap/alignment outputs.
8. Run dedicated pose-observability experiments plus hand rotation, occlusion, hidden-fingertip, and palm-overlap tests; derive acceptance from conditioning, residuals, and depth support rather than marker count.
9. Implement the agreed researcher trial workflow, timeout, seating confirmation, swap recovery, and attempt logging/export.
10. Build a precision-reference bench fixture, define repetitions/test conditions, characterize errors, and derive versioned thresholds.
11. Validate the provisional envelope and accuracy requirements before scored human research; narrow supported conditions if needed.
12. Assess human repeatability and equipment effects before final accuracy claims; additionally establish equivalence with the source test's measurement definition before bare-hand normative category comparisons.

---
