# RGB web execution record · 2026-10-07

User approved inline implementation, milestone pushes, GitHub Pages hosting and subsequently making the repository public after GitHub rejected private-repository Pages under the account plan.

Completed software increments:

- `99306da`: shared calibration/protocol geometry, common multi-face pose solver, local camera worker, operator UI, records/exports and Pages workflow. Local core and browser checks passed. Initial GitHub build tests passed; Pages configuration failed before eligibility was resolved.
- `320b130`: active/candidate persistence, pre-attempt reply rejection, fresh binding confirmations, pending capture cancellation, degenerate face rejection and production artifact staging. Core and real browser tests passed; GitHub CI and Pages deployment passed.
- `4091a5e`: independently projected rotated/distorted pose recovery, inconsistent observations rejected, actual generated-marker detection, optional RGB consent/export checks and quota-failure recovery preserving historical records. Local suite passed.

Fresh read-only final review found three actionable issues: earlier in-flight frames consuming scored attempts, stale camera binding confirmation, and degenerate calibration faces. All were fixed. The fake-camera test initially reproduced the timing failure; the malformed-corner regression failed before the geometry guard was corrected. Browser regressions verify persisted active attempts, stopped outcomes after reload, and storage-failure emergency exports. A subsequent quota test reproduced overwritten storage-error status, which was corrected before the suite passed.

Ruling: use the already-authorized inline implementation and publication workflow — user explicitly requested build and milestone pushes — avoiding a duplicate confirmation costs no additional authorization.

Ruling: use the pinned official OpenCV.js artifact with three LM initializations plus noncoplanar geometry, reprojection, conditioning and positive-depth checks — available runtime supports this without a custom toolchain — this heuristic is not exhaustive uniqueness evidence; supported geometries and ambiguity thresholds require bench validation.

Ruling: supplied nominal calibration enables exploratory estimates only — no physical calibration exists in the repository — no valid research trial can be obtained from the example bundle.

Ruling: store optional RGB snapshots, not continuous video — snapshots support per-frame investigations within the implemented local workflow — video recording would need a separately implemented consent/retention path.

No fresh review findings were deferred. Mechanical registration, human repeatability, equipment effects, bare-hand clinical equivalence and phone-specific performance remain unverified; they cannot be established by synthetic software tests. Browser storage has indefinite application retention but can be evicted, so researcher exports remain necessary.

Verification commands: `node --test web/tests/core.test.mjs`, `node web/tests/browser.mjs`, and `node web/tests/deployed.mjs` (Playwright Chromium or installed Edge via `REACHSENSE_BROWSER_CHANNEL=msedge`). The deployed check exercises the public HTTPS project path and actual WASM worker without accessing a camera or uploading images.

GitHub Actions run `37513837576` for `4091a5e` passed both build and deployment. Public URL: https://imconfusedafbruh.github.io/reachsenseai/. The actual deployed UI, example bundle, project-relative assets, OpenCV WASM worker and missing-reference rejection passed the HTTPS smoke check. Final local browser checks also pass late permission cancellation and injected worker failure. The branch is retained and published as requested; no merge into the separate native main branch was requested.
