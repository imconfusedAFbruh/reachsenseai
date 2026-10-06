# ReachSenseAI concept image provenance

Created with the built-in image generation tool, then corrected with its image-editing mode. The README uses `reachsense-concept.png`. This is an illustrative hardware concept, not a validated fixture design; marker patterns are not printable calibration targets.

## Initial generation prompt

```text
Use case: scientific-educational
Asset type: landscape hero illustration for the ReachSenseAI GitHub README.
Create a polished, anatomically coherent scientific product-concept illustration of a behind-the-back shoulder flexibility measurement setup. Wide landscape layout, clean light background, restrained teal and navy accents, soft studio lighting, precise semi-realistic 3D illustration.
Main scene: rear three-quarter view of one adult participant in a plain close-fitting shirt. The right arm reaches over the right shoulder with the hand pointing downward along the upper back; the left arm reaches upward from the waist with its hand pointing upward. The two middle fingers approach each other at mid-back. Both hands must have natural anatomy and five fingers. Each MIDDLE FINGER wears a small lightweight dorsal rigid fixture with two separated support regions and a fingertip stop. Each fixture carries a compact three-dimensional target with THREE distinct black-and-white square fiducial faces on differently oriented planes; do not use just one flat tag. A separate small ChArUco checkerboard reference is on the upper back, mounted by a lightweight repeatable harness, above and clear of the hands.
At the left of the composition show an iPhone on a fixed slim stand behind the participant, front screen/front sensor side aimed at their back. A faint translucent field-of-view cone links the front sensor to the targets; it represents the camera viewing volume, not vision through skin.
At the right show a clean close-up inset of the dorsal middle-finger fixture: clearly show two separated supports, three marker faces, and a tiny teal dashed virtual point at the distal anatomical fingertip. A thin dotted connector links inset to the fixture in the main scene.
Minimal exact text labels only: "Front TrueDepth", "Multi-face reference", "Body reference", "Virtual fingertip". Small unobtrusive footer: "Concept illustration".
Important: no numeric measurements, no accuracy claim, no app UI, no fake clinical ratings, no X-ray or seeing through a hand, no magical beam, no surgery, no anatomy errors, no extra arms, no floating marker cubes unconnected to fingers, no oversized targets blocking contact. This is an experimental hardware concept, not a finished device photograph. Keep all labels readable and lines uncrowded.
```

## Final correction prompt

```text
Edit this scientific ReachSenseAI concept illustration. Keep the participant pose, clean landscape composition, two finger fixtures, fixture close-up, colors, and overall typography. Make two precise corrections: (1) The phone on the stand must show its FRONT glass screen, notch/front sensor side facing the participant, with the viewing cone originating at the front TrueDepth sensor. Remove the visible Apple back logo and rear camera module; do not show or use rear cameras. A slight three-quarter angle may show the front dark glass screen clearly while pointing toward the participant. (2) Correct leader lines: the exact label 'Multi-face reference' must point clearly to the three-faced target on the UPPER middle-finger fixture, not the checkerboard on the back. The exact label 'Body reference' must point clearly to the ChArUco checkerboard on the back, not a strap. 'Front TrueDepth' must point to the front sensor at the top of the phone; keep 'Virtual fingertip' pointing to the inset's dashed tip point. Preserve 'Concept illustration' footer and all other details. No numeric accuracy claims. Make these corrections anatomically and physically coherent.
```

