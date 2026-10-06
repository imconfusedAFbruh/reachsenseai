# Behind-the-Back Shoulder Flexibility Measurement

This context describes anatomical fingertip measurements during a behind-the-back shoulder flexibility test.

## Language

**Anatomical fingertip endpoint**:
The furthest point of a participant's middle finger along its length, located by gentle contact with the finger jig's internal stop.
_Avoid_: Jig endpoint, tracking reference

**Jig tracking reference**:
A defined point on a rigid finger jig with a calibrated geometric relationship to the anatomical fingertip endpoint.
_Avoid_: Anatomical fingertip, biological fingertip

**Finger jig**:
A wearable rigid fixture with at least two separated finger registration regions and a fingertip stop, intended to preserve the anatomical endpoint's relationship to a multi-face fiducial reference.

**Hand rigid body**:
The complete rigid tracking structure attached to one hand or middle finger, with at least three uniquely identified fiducial faces whose spatial relationships are fixed.
_Avoid_: Single fingertip marker

**Virtual anatomical fingertip**:
The anatomical endpoint represented relative to the hand rigid body, whose position remains estimable when the biological fingertip is hidden but the rigid reference remains sufficiently observable.
_Avoid_: Directly detected fingertip, point measured through an occluding hand

**Body reference**:
A target on a calibrated, repeatable upper-back mount that establishes the registered body frame for the fingertip score.

**Participant**:
The person performing the behind-the-back shoulder flexibility test.

**Operator**:
The trained person who sets up the measurement and guides the participant.

**Research measurement**:
A recorded test result accompanied by quality evidence for evaluation and analysis.
_Avoid_: Fitness category, validated clinical assessment

**Signed body-Y overlap**:
The lower-hand anatomical endpoint's body-Y coordinate minus the upper-hand anatomical endpoint's body-Y coordinate, with positive body Y directed upward along the torso. Positive values represent projected overlap, negative values represent a projected gap, and zero represents equal body-Y coordinates rather than physical contact.
_Avoid_: Finger separation for a positive score, physical touching for a zero score

**Physical contact**:
Actual contact between the fingers, which cannot be established from signed body-Y overlap alone.

**Alignment offsets**:
The sideways and outward-from-back differences between the two anatomical fingertip endpoints.

**Right-hand-up position**:
A test position in which the right hand reaches down from above and the left hand reaches up from below.

**Left-hand-up position**:
A test position in which the left hand reaches down from above and the right hand reaches up from below.

**Jig-assisted test**:
A behind-the-back shoulder flexibility test performed while wearing the finger jigs and body reference.
_Avoid_: Bare-hand test

**Trial**:
A single measurement attempt for one hand position, including its validity outcome and seating checks.
_Avoid_: Session

**Familiarization attempt**:
A practice attempt before the three scored valid trials for a hand position.

**Position session score**:
The median signed body-Y overlap of three valid trials for the participant's selected hand-up position.
_Avoid_: Best reach, combined left/right score

**Seating check**:
An operator's verification that both finger registration regions remain engaged and the anatomical fingertip remains gently seated against the internal stop.

**Invalidated trial**:
A measurement attempt excluded from scoring because a required condition failed, including detected jig movement.

**Selected hand-up position**:
The right-hand-up or left-hand-up position selected for a participant and kept consistent across repeat visits.

**Jig calibration**:
A versioned definition of a physical jig's fixed face geometry and the anatomical endpoint relative to its common rigid-body reference.

**Jig assignment**:
The association of a physical finger jig with the upper or lower hand for a trial.

**Supported operating range**:
The measurement conditions under which validation demonstrates that the system meets its acceptance requirements.
_Avoid_: Provisional test range

**Stable window**:
A continuous one-second interval in which all required measurement quality checks pass.

**Quality threshold set**:
A versioned collection of bench-derived acceptance limits for measurement quality checks.

**Inter-attempt interval**:
The elapsed time from the end of one attempt to the start of the next, including any rest, setup, or adjustments.
_Avoid_: Verified rest duration

**Anatomical registration**:
The repeatable relationship between a physical fixture and the participant's defined anatomical reference.

**Rigid-body observability**:
The sufficiency of current observed geometry to estimate a reliable rigid-body pose, including pose conditioning, residuals, ambiguity, and depth support.
_Avoid_: Visible marker count alone

**Measurement-definition equivalence**:
Evidence that the instrument's score represents the same quantity under compatible conditions as a reference test's score.
_Avoid_: Equipment-effect validation alone
