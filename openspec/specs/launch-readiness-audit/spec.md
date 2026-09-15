# launch-readiness-audit Specification

## Purpose
Provides a structured, multi-role launch-readiness findings registry (optimizations + risks) with severity, ownership, and disposition workflow that gates the mini-program release.

## Requirements

### Requirement: Findings registry is structured and complete
The launch-readiness evaluation SHALL produce a findings registry in which every entry records: `id`, `category` (optimization | risk), `dimension` (PM | UI-UX | mini-program | backend | quality), `severity` (P0 | P1 | P2 | P3), `description`, `impact`, `ownerRole`, `disposition` (fix-before-launch | track-post-launch | accept), and `evidence`.

#### Scenario: Designer records a finding
- **WHEN** a designer identifies an optimization point or a hidden risk during the evaluation
- **THEN** a registry entry is created containing all required fields, and no finding may be logged with missing severity, disposition, or evidence

#### Scenario: Evidence is cited
- **WHEN** a finding is added
- **THEN** its `evidence` field references the concrete source (authoritative doc clause, code path, or runtime log line) supporting the claim

### Requirement: Evaluation covers all five dimensions
The evaluation SHALL cover at least the PM, UI-UX, mini-program, backend, and quality dimensions, with each finding tagged to the dimension that owns it and judged against the authoritative docs (`docs/project_spec.md`, `CODEBUDDY.md`, `api-design.md`, `database.md`, `ui-design.md`) and the established red lines.

#### Scenario: Each role completes a pass
- **WHEN** the evaluation runs
- **THEN** findings from PM, UI-UX, mini-program, backend, and quality are each present (or explicitly marked "no findings" per dimension), and every finding cites the doc/red-line it was checked against

#### Scenario: Runtime walkthrough is cross-checked
- **WHEN** a runtime/devtools observation (e.g. silent-login 5xx, unregistered icon key, renderer crash) is reported
- **THEN** it is recorded as a finding with `evidence` pointing to the log line and classified by `dimension` and `severity`

### Requirement: Severity and disposition are explicit
Every finding SHALL be assigned a severity (P0–P3) and a disposition with rationale: `fix-before-launch` (must be resolved prior to release), `track-post-launch` (scheduled as a follow-up), or `accept` (documented as accepted risk).

#### Scenario: High-severity risk disposition
- **WHEN** a finding is classified P0 or P1
- **THEN** its disposition is `fix-before-launch` unless a written rationale upgrades it to `accept` with sign-off from the 技术负责人

#### Scenario: Low-severity optimization disposition
- **WHEN** a finding is classified P2 or P3 and is an optimization (not a defect)
- **THEN** its disposition is `track-post-launch` or `accept`, recorded in the post-launch tracking list

### Requirement: Launch gate blocks on open P0/P1
Graduation/archive of the mini-program SHALL be blocked while any P0 or P1 finding has an undecided disposition or a `fix-before-launch` disposition whose linked fix is not verified complete.

#### Scenario: Open P0 present
- **WHEN** the registry contains a P0 finding with disposition `fix-before-launch` and no verified completed fix
- **THEN** the launch gate reports BLOCKED and lists the open P0/P1 items

#### Scenario: All P0/P1 cleared
- **WHEN** every P0/P1 finding is either verified-fixed or `accept`-signed-off by 技术负责人
- **THEN** the launch gate reports PASS and the change may be archived

### Requirement: Fixes are tracked, not silently merged
Any code or doc change that resolves a finding SHALL be delivered through a separate follow-up change referencing the finding `id`, and MUST NOT be folded silently into this audit change.

#### Scenario: Finding resolved by a fix
- **WHEN** a `fix-before-launch` finding is remediated
- **THEN** the remediation is a distinct follow-up change whose description references the finding `id`, and this change's registry is updated to link the fix
