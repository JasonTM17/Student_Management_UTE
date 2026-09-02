# Phase 08 — Integration and ship

Run focused checks, then the terminal Maven/frontend/mobile/Compose/security/browser matrix. Open the reviewed release PR, merge only approved commits into `main`, verify the exact resulting SHA and GitHub CI, run final exact-head review, and dispatch immutable publication. Pull and smoke both registry references, save release manifest/SBOM/provenance evidence, and report Git push, CI, publication and deployment as separate states.

Exit criterion: `main` is clean and remote-matched, all release artifacts are digest-parity verified, and the handoff explicitly says VPS/domain cutover is still pending.
