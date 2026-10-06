# Design interview decisions

This log records the grill-with-docs decisions and their subsequent amendments. Later decisions supersede conflicting earlier entries. The user's latest attached instruction authorizes reconciliation of the methodology with the multi-marker architecture; experimental prerequisites remain unverified and software implementation is outside this documentation update.

## Round 1 — Accepted

1. Report an anatomical fingertip score using calibrated jig geometry; distinguish the jig tracking reference from the anatomical endpoint.
2. Accept two finger caps and a back reference for the first research prototype, and experimentally assess their effects on reach and comfort.
3. Make the first release a research measurement tool. Export the signed score and quality evidence; defer fitness categories until the reference table's source, units, applicable population, and boundaries are established.
4. Begin with a fixed phone stand and a trained operator.

## Round 2 — Accepted

5. Define the anatomical endpoint as the furthest point along the middle finger's length. Seat it gently against the cap's internal stop, calibrate the stop relative to the marker, and check seating before each trial.
6. Define the primary score as signed body-Y overlap: negative for a projected gap and positive for projected overlap. Report sideways/depth offsets separately and reject poses outside validated alignment limits. Zero projected overlap does not establish physical contact.
7. Measure right-hand-up and left-hand-up positions separately. Record the upper hand for every trial and do not average results across positions.
8. Assess equipment effects as a separate validation requirement. Describe measurements as a jig-assisted test and defer comparison with bare-hand reference categories until equipment effects are assessed.

## Round 3 — Accepted and pending

9. Accepted in round 4: provisional bench engineering targets are MAE <= 5 mm, absolute bias <= 2 mm, and static repeated-measurement SD <= 2 mm across agreed test conditions. These are requirements to test, not demonstrated accuracy claims. Report rejection/failure rates alongside accepted-measurement accuracy.
10. Use one familiarization attempt followed by three valid trials per position. Save every trial, including unsuccessful attempts, and use the median of the three valid trials as that position's session score.
11. Invalidate an affected trial if a cap shifts, reseat the cap, and restart. Require an operator seating check before and after each trial; marker visibility alone does not prove anatomical seating.
12. Always retain per-frame geometry, quality metrics, rejection reasons, and calibration identifiers. RGB/depth recording is an explicit research option requiring participant consent, local storage, and a defined retention period; that period remains unresolved.

## Round 4 — Accepted and pending

13. Allow at most five scored attempts per position to obtain three valid trials. Preserve all attempts. If fewer than three trials are valid, mark the position incomplete and do not issue a position session score. The familiarization attempt is separate from the scored-attempt limit.
14. The researcher operates the phone on the fixed stand. In round 6, the user selected one hand-up position per participant, kept consistent across visits. This supersedes Q7's requirement to measure both positions; record the selected upper hand. Starting-side balancing was not accepted.
15. Store retained research data, including optional RGB/depth recordings, indefinitely. This supersedes the unresolved retention period in Q12; optional recording still requires explicit participant consent and uses local storage. No automatic time-based deletion is planned.

## Round 5 — Accepted and pending

16. Rest between attempts is left to the researcher's discretion. No fixed or minimum rest interval has been accepted.

## Round 6 — Accepted and pending

17. No exact iPhone model has been selected. In round 7, front TrueDepth was confirmed as the prototype sensor requirement; verify synchronized RGB/depth capture support on the selected device before implementation proceeds beyond feasibility work.

## Round 7 — Accepted and pending

18. Keep front TrueDepth as the prototype requirement and confirm capture support on the chosen device before proceeding.
19. The researcher assigns each participant's hand-up position under the study protocol, records the upper hand at enrollment, and keeps it consistent across visits.
20. Notify and pause if jigs are swapped during the same session, invalidate the affected trial, and require the researcher to confirm jig assignments and seating before restarting. Calibration stays attached to the physical jig ID.

## Round 8 — Accepted

21. Use provisional test ranges of 300–600 mm camera distance and -100 to +100 mm signed body-Y overlap. Evaluate the accepted accuracy targets throughout the ranges and narrow the supported operating range if validation fails.
22. Block measurement when a required jig calibration is missing or its geometry has changed. Store a calibration version with every trial, and require recalibration after marker movement, damage, or endpoint changes.

## Round 9 — Accepted and pending

23. Derive quality thresholds from bench validation and version them. Prevent overrides from producing valid scores, and allow failed attempts to be saved for investigation.
24. Require one continuous second of passing quality checks for capture. Any required check failure breaks continuity and requires a new passing interval. Within-window aggregation was accepted in Q27.
25. The researcher starts each attempt, the app captures automatically when a valid stable window is available, and the researcher then confirms seating. Acceptance remains subject to the previously required before/after seating checks.
26. Accepted in round 10: export summary CSV plus detailed JSON containing all attempts, per-frame geometry, quality metrics, rejection reasons, selected upper hand, device details, and calibration/threshold versions. Include optional RGB/depth recordings only when explicitly selected for export.

## Round 10 — Accepted

27. Use the median signed body-Y overlap across the continuous one-second passing window. Restart the window after a required quality check fails. Accept the captured trial only after the post-trial seating check passes.
28. End an attempt after a provisional 15 seconds without successful capture, preserve it as unsuccessful with its failure reasons, and count it toward the five-scored-attempt limit. The researcher can stop earlier. The timeout applies to acquisition, not the subsequent seating confirmation.

## Round 11 — Accepted and pending

29. Require static-distance, depth-offset, and rotation bench acceptance before scored human research measurements. Permit consented setup and comfort pilots earlier but label their measurements exploratory. After bench acceptance, assess human repeatability and equipment effects before accuracy claims or comparison with bare-hand reference categories.
30. Select marker dimensions, cap sizes, endpoint materials, and back-reference mounting through feasibility experiments. Require marker visibility throughout the pose, repeatable fingertip seating, secure back-reference mounting, and acceptable comfort. Document chosen geometry and calibration before bench validation.
31. Accepted after clarification: automatically record attempt timestamps and the interval from the end of one attempt to the start of the next. Rest duration remains at the researcher's discretion; the recorded interval is elapsed time between attempts and may include setup or adjustments.

## Outstanding branches

None at the current design level. The subsequent methodology-update instruction authorizes document reconciliation; experimental prerequisites below remain required work.

## Final architecture amendment — Authorized methodology update

The attached methodology instruction replaces the original single-marker-per-fingertip architecture with two rigid 3D structures, each containing at least three uniquely identified planar fiducials on differently oriented faces. Estimate one common pose from all usable visible corners and per-face depth geometry; do not average independent marker poses. Calibrate every face transform and the virtual anatomical endpoint in a common frame for each physical jig.

Biological fingertip visibility is not required. Sufficient current observations of both rigid bodies and the body reference are required for valid frames; purely predicted or extrapolated points cannot enter the measurement window. Expand validation to hand rotation, marker-face occlusion, hidden fingertips, overlap/palm-to-palm poses, depth offsets, and body rotation, and report performance by visibility and viewing conditions.

Retain the finalized one-second continuous median, trial/session workflow, indefinite retention, and explicit optional media export. Retain Q6's zero-as-equal-body-Y definition rather than the attachment's illustrative zero-as-touch wording, because projected overlap alone does not establish physical contact. Dimensions, minimum usable face visibility, optimizer details, and quality thresholds remain experimental choices. The update preserves the research objective and the 30-section plan structure.

## Methodology review refinements

1. Prototype a dorsal finger fixture with at least two separated anatomical registration regions, such as the middle and distal phalanges, plus the fingertip stop. Verify resistance to endpoint-changing flexion, rotation, and sliding; multiple supports do not establish rigidity without evidence. Evaluate posture/reach and comfort effects independently.
2. Require measurement-definition equivalence with any source normative test, in addition to equipment-effect validation, before applying its categories. Preserve ΔX/ΔZ to investigate alignment-dependent disagreement.
3. Treat the upper-back mount as a calibrated fixture with repeatable harness/multi-contact registration, a versioned board-to-body transform, and drift/remount verification. A visible board or clothing attachment alone does not establish anatomical alignment.
4. Add a dedicated complete rigid-body observability experiment. Derive acceptance from conditioning/ambiguity, reprojection residuals, observed corner geometry, and depth support rather than marker count alone; validate the rule on separate conditions/repetitions.
5. Make physical finger-fixture feasibility the next development gate. Permit minimal sensor/detection tooling to support experiments, while deferring expanded software and finalized dimensions until registration evidence supports the fixture.

## Experimental prerequisites after review

The following are intentionally determined by feasibility work and validation, not assumed to be settled design values:

- Exact iPhone model and verified front TrueDepth capture support.
- Marker dimensions, endpoint materials, cap sizes, and reference mounting geometry.
- Calibration verification procedures, jig seating/remount repeatability, and marker visibility across the pose.
- Bench-derived quality limits, including lateral/depth alignment limits, with versioned threshold sets.
- Detailed validation conditions, equipment effects, and compliance with provisional accuracy targets.

Neither the design interview nor the methodology update certifies these prerequisites as achieved. Record their procedures and results before treating the instrument as validated.
