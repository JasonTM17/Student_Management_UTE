# Project Documentation Management

Use this rule when creating plans or changing project documentation.

## Docs impact

Update docs only when work affects user-visible behavior, setup, commands,
configuration, architecture, security, public contracts, machine-readable
contracts, or durable maintainer decisions. Internal edits and phase completion
do not require evergreen docs churn.

Discover the target through repository instructions, the root README, and the
project's existing docs navigation. Do not assume a fixed filename list or docs
tree. Update the smallest owning surface, and link to machine-owned scripts,
manifests, schemas, or generated references instead of copying their details.

## Plans

Follow the repository's configured plan location and naming convention. Keep a
plan index short: status, phases, dependencies, acceptance criteria, and links
to execution detail. Phase files contain only the context, requirements, files,
steps, validation, risk, and rollback information needed to execute safely.

Classify each implementation plan by its primary outcome:

- **Feature:** capture user value, functional and non-functional requirements,
  contract/data-flow changes, security, delivery sequence, and acceptance tests.
- **Bug fix:** capture expected versus actual behavior, reproduction evidence,
  verified root cause or a bounded diagnosis gate, regression coverage, and a
  realistic rollback path.
- **Refactor:** capture preserved behavior, current-state evidence,
  characterization tests, compatibility/migration boundaries, and repeatable
  before/after measurements.
- **Generic:** use only when none of the specialized contracts fits.

Treat these as overlays, not alternate schemas. Repository instructions, the
live planning skill, and CLI-owned frontmatter/status remain authoritative.
Remove irrelevant sections and never fill a template with invented evidence,
metrics, paths, or risks.

Plans, reports, and audit results are stateful records. They do not become
evergreen product authority merely because a phase completed.

Before updating a document, read it. After updating, verify links and claims
against source, tests, scripts, artifacts, or live state.
