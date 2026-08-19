# Disk and process safety audit — 2026-08-19

## Result

`C:` is critically low and remains a capability constraint for memory-heavy
Java/Expo/browser work. The latest local measurement was approximately
`297,807,872` bytes free (`284 MiB`). `D:` had approximately `44.89 GiB` free
when the independent audit ran. No cleanup was performed by this audit.

## Read-only evidence

- No junctions, symlinks, or other reparse points were found under the project
  root by the independent audit.
- No live process command line referenced this project root at audit time.
- An IntelliJ/JPS Java process remains active for a different project and was
  preserved. Its JetBrains caches are outside this workspace and are not safe
  cleanup targets without separate approval.
- The workspace remains intentionally dirty in the following ways: the review
  ledger is being updated, and untracked `.agents/` is preserved and must not
  be staged.

## Bounded candidates identified (not removed)

Small JVM diagnostics in the project tree:

- root `hs_err_pid18200.log` (47,046 bytes)
- root `replay_pid18200.log` (432,461 bytes)
- root `hs_err_pid20744.log` (16,083 bytes)
- additional `hs_err_pid*.log`/`replay_pid*.log` files under
  `java-services/restful-api/`

Generated Maven `target/` trees were estimated at about 297.43 MB across the
five Java services. They are reproducible, but recursive removal must be
approved one exact directory at a time and must not be used as a workaround for
the blocked environment deletion policy.

## Safety decision

- Do not touch C: user caches, JetBrains caches, IDE extensions, Docker data,
  or unrelated projects from this workspace audit.
- Do not stop the active Java/IDE process.
- Do not use `cmd`, `rmdir`, alternate shells, or broad globs to bypass a
  rejected deletion operation.
- Keep Maven full tests, image builds, dependency installation, and browser
  kernel startup marked `NOT_RUN`/`BLOCKED_CAPABILITY` while free space remains
  critically low.
- If cleanup is later authorized, resolve and print each exact target, confirm
  it is not open or referenced by a live process, then use a recoverable or
  explicitly approved operation and record the bytes reclaimed.
