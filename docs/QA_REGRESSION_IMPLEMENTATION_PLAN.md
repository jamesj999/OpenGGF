# QA Regression Implementation Plan (90 Days)

## Scope and intent
This document defines a practical backlog to reduce regression escape risk in OpenGGF, with emphasis on:
- ROM-dependent correctness (S1, S2 REV01, S3K)
- headless physics/object behavior stability
- CI signal quality (skip visibility, flake control, fast failure)

Time horizon:
- Phase 1: Day 0-30
- Phase 2: Day 30-60
- Phase 3: Day 60-90

## Goals
1. Prevent silent regressions in core gameplay parity paths (physics, collision, object interactions, level loading).
2. Make ROM test coverage observable instead of implicitly skipped.
3. Reduce mean detection latency for parity regressions to under 1 pull request cycle.
4. Keep PR feedback fast with layered CI tiers.

Success targets by Day 90:
- Skip rate in required CI tiers: <= 5%
- Detection latency (PR open to first failing relevant signal): <= 30 minutes median
- Flake rate (non-deterministic failures): <= 2% rolling 14-day

## Current observed gaps
1. CI currently runs a single `mvn test -B` lane on PRs to `develop`; no explicit tiering by risk/cost.
2. ROM-dependent tests frequently use `Assume.assumeTrue(...)` skips; skips are not surfaced as a tracked metric.
3. Tests requiring different ROMs are inconsistently configured (mixed system properties, env vars, defaults).
4. Headless integration tests exist and are valuable, but there is no enforced split between smoke and long-running parity suites.
5. Visual regression tests are excluded from Surefire and not represented in CI tiers.
6. Known open gameplay issue remains (`ARZ pillars reset immediately when off-screen` in `docs/BUGLIST.md`), but is not pinned to a regression gate.
7. S3K-specific known limitations (e.g., resource reference validation warnings) are documented but not tied to explicit pass/fail criteria.

## Guiding principles
- Prefer deterministic, headless parity tests over ad-hoc manual checks.
- Keep ROM-optional developer workflows, but make ROM-missing skips visible and actionable in CI.
- Gate merges with fast high-value tests first; run expensive suites after early signal.
- Validate behavior against disassembly references where feasible (S1/S2/S3K docs trees).

## Prioritized epics
1. Epic A (P0): Regression observability and metric plumbing.
2. Epic B (P0): CI tier strategy and gating policy.
3. Epic C (P0): Physics/object parity guardrails (headless).
4. Epic D (P1): ROM matrix hardening (S1/S2/S3K).
5. Epic E (P1): Flake control and quarantine workflow.
6. Epic F (P2): Visual/audio parity lanes (non-blocking initially).

## CI tier strategy
### Tier T0 - Static and unit-fast (blocking)
- Trigger: every PR.
- Command baseline: `mvn -B -Dtest=!VisualRegressionTest,!VisualReferenceGenerator test`.
- Scope: ROM-independent unit tests, decompression, utility logic, fast collision unit tests.
- SLA: <= 10 minutes.

### Tier T1 - Headless gameplay smoke (blocking)
- Trigger: every PR.
- Scope: curated headless smoke tests for physics/object interaction in S1/S2/S3K.
- Must include at minimum:
  - one S1 headless collision/object smoke
  - one S2 object interaction smoke
  - one S3K bootstrap/spawn stability smoke
- SLA: <= 20 minutes cumulative with T0.

### Tier T2 - ROM parity suite (required before merge)
- Trigger: PR label `needs-rom-parity` or protected branch pre-merge check.
- Scope: ROM-dependent placement/parity tests, zone-specific object tests, PLC/resource loading parity.
- Inputs: secure ROM path injection (repo secrets + env vars), no file download during CI.
- Expected skip rate target: <= 5%.

### Tier T3 - Extended/diagnostic (non-blocking initially)
- Trigger: nightly and on-demand.
- Scope: visual regression, audio regression, long-running scenario tests, multi-act traversals.
- Policy: failures auto-open backlog tickets; promote stable checks to blocking by Day 90.

## Metrics model
### 1) Skip rate
- Definition: `(skipped tests) / (discovered tests)` for each tier and game.
- Reporting dimensions: `tier`, `game` (S1/S2/S3K), `reason` (ROM missing, unsupported, conditional).
- Alert thresholds:
  - Warning: > 10% daily
  - Critical: > 20% daily

### 2) Detection latency
- Definition: elapsed time from PR creation/update to first failing relevant QA signal.
- Measure points:
  - `t_pr_event`
  - `t_first_failed_check`
- Target: median <= 30 minutes, P90 <= 60 minutes.

### 3) Flake rate
- Definition: `% of tests that fail then pass on immediate rerun without code change`.
- Windows: rolling 7-day and 14-day.
- Target: <= 2% by Day 90.
- Enforcement: tests above 5% flake enter quarantine until fixed.

## Phased rollout and backlog

## Phase 1 (Day 0-30): establish visibility and fast gates
Primary outcome: QA signal becomes measurable and fast.

| Ticket | Epic | Task | Acceptance criteria |
|---|---|---|---|
| QA-001 | A | Add CI step to parse Surefire reports and publish total/pass/fail/skip counts as job summary artifact. | CI summary shows skip counts for every run; artifact retained 30 days. |
| QA-002 | A | Standardize ROM skip reasons (`ROM missing`, `module unsupported`, `test data missing`) in test utilities/rules. | >= 90% of skipped ROM tests use normalized reason strings. |
| QA-003 | B | Split PR CI into T0 and T1 jobs in `.github/workflows/ci.yml`. | PR shows separate T0 and T1 statuses; both required for merge. |
| QA-004 | B | Create smoke test list file (or naming convention) for T1 with explicit S1/S2/S3K minimum set. | At least 9 tests (3 per game) run in T1 and finish <= 10 min. |
| QA-005 | C | Promote open ARZ pillar regression to a dedicated failing/guard test (pending current behavior decision). | Test exists with disassembly reference and reproducible pass/fail criteria. |
| QA-006 | D | Define CI ROM injection contract (env vars/system props mapping for S1/S2/S3K). | Documented mapping checked into docs; CI can resolve all 3 ROM paths without local defaults. |
| QA-007 | A | Add nightly metric snapshot job (JSON + markdown) for skip rate and top skip reasons. | Nightly artifact includes tier/game breakdown and trend vs prior day. |

Phase 1 exit criteria:
- T0/T1 implemented and required.
- Skip counts visible on every PR.
- ROM path contract documented and tested in CI.

## Phase 2 (Day 30-60): harden parity coverage and remove silent skips
Primary outcome: ROM parity gates become reliable and actionable.

| Ticket | Epic | Task | Acceptance criteria |
|---|---|---|---|
| QA-008 | B | Add T2 ROM parity workflow (manual + protected-branch trigger). | T2 executes S1/S2/S3K parity suite with <= 5% skips in CI environment. |
| QA-009 | C | Expand headless parity pack for collision/object regressions in high-risk zones (CPZ, ARZ, CNZ, AIZ). | +20 deterministic headless tests; runtime increase <= 12 min in T2. |
| QA-010 | D | Migrate ad-hoc `Assume.assumeTrue` usage to shared `RequiresRomRule` or equivalent normalized helper. | 80%+ ROM-dependent tests use shared rule; skip reason taxonomy preserved. |
| QA-011 | E | Implement automatic rerun-once policy for failed tests in nightly to detect flakes. | Flake report generated nightly with per-test fail->pass evidence. |
| QA-012 | E | Add quarantine mechanism (`@Category(Flaky)` or naming convention + excluded lane). | Quarantined tests tracked separately; blocking tiers unaffected by known flakes. |
| QA-013 | C | Introduce collision trace baseline assertions for selected headless scenarios using `RecordingCollisionTrace`. | At least 5 trace-based regression tests compare event ordering and counts. |
| QA-014 | D | Add S3K-specific parity checks for known bootstrap/resource loading risks (AIZ spawn stability + resource reference guard). | T2 fails on regression in AIZ spawn stability and explicit S3K resource guard tests. |

Phase 2 exit criteria:
- T2 available and stable.
- Majority of ROM skips routed through shared utilities with normalized reasons.
- Flake tracking operational with quarantine process.

## Phase 3 (Day 60-90): optimize signal quality and broaden high-cost parity lanes
Primary outcome: robust, low-noise regression system with clear ownership.

| Ticket | Epic | Task | Acceptance criteria |
|---|---|---|---|
| QA-015 | F | Re-enable visual regression lane in T3 with deterministic reference generation policy. | Nightly visual lane runs; drift report produced for changed frames. |
| QA-016 | F | Add audio regression smoke lane (selected SFX/music decode/playback assertions). | Nightly audio lane produces pass/fail + checksum-style output deltas. |
| QA-017 | A | Add detection-latency dashboard from CI timestamps (PR event to first failing tier). | Median and P90 latency reported weekly in artifact/report issue. |
| QA-018 | E | Triage and fix top 5 flaky tests by failure frequency. | Top-5 flake list reduced by >= 60% compared to Phase 2 baseline. |
| QA-019 | B | Promote stable T3 checks (visual/audio subset) to required status where signal is clean. | At least one formerly non-blocking lane is promoted to required with <= 2% flake. |
| QA-020 | C | Add object parity backlog conversion from `docs/BUGLIST.md` and discrepancy docs into executable tests where possible. | >= 5 documented discrepancies/bugs mapped to explicit regression tests. |

Phase 3 exit criteria:
- Skip, latency, and flake targets met.
- At least one extended parity lane promoted to required.
- Documented bug/discrepancy backlog materially converted to tests.

## Ownership suggestions
Use ownership by subsystem, not by individual name, to stay maintainable.

- QA Infrastructure Owner
  - Owns CI workflows, metric extraction, dashboards, quarantine process.
  - Primary tickets: QA-001/003/007/008/011/012/017/019.

- Gameplay Parity Owner (Physics/Object)
  - Owns headless parity suites, collision trace baselines, bug-to-test conversion.
  - Primary tickets: QA-005/009/013/020.

- Multi-Game ROM Owner (S1/S2/S3K)
  - Owns ROM path contract, per-game parity stability, S3K bootstrap/resource gates.
  - Primary tickets: QA-006/010/014.

- Media Parity Owner (Visual/Audio)
  - Owns T3 visual/audio lanes and promotion readiness.
  - Primary tickets: QA-015/016.

RACI recommendation:
- Responsible: subsystem owner per ticket.
- Accountable: QA Infrastructure Owner for cross-tier release quality.
- Consulted: game-module maintainers (S1/S2/S3K), audio/render maintainers.
- Informed: all PR authors via CI summaries.

## First 2 weeks execution checklist
- [ ] Confirm baseline metrics from current CI: discovered/pass/fail/skip for 5 consecutive PR runs.
- [ ] Implement QA-001 metric summary extraction from Surefire XML.
- [ ] Draft and merge ROM path contract doc section (S1/S2/S3K env vars + system props).
- [ ] Implement QA-003 CI split into T0 and T1 (no behavior change yet, only separation).
- [ ] Define and commit T1 smoke list covering minimum S1/S2/S3K headless checks.
- [ ] Open tracking issue for ARZ pillar reset regression and link disassembly verification steps.
- [ ] Create weekly QA report template including skip rate, latency placeholder, top flaky tests.
- [ ] Run one dry-run retrospective and adjust Phase 1 ticket scope if runtime budgets are exceeded.

## Risks and mitigations
- Risk: ROM licensing constraints prevent storing ROMs in CI.
  - Mitigation: use secure path injection + pre-provisioned private storage; never auto-download in CI.
- Risk: Increased runtime from new headless parity tests slows PR flow.
  - Mitigation: enforce smoke-vs-full split (T1 vs T2), with strict runtime budgets.
- Risk: Flake quarantine hides real regressions.
  - Mitigation: quarantine requires linked remediation ticket and expiry date.

## Operational notes for this repository
- Favor `HeadlessTestRunner` for object/physics regression coverage; keep setup requirements explicit in new tests.
- Use game-specific ROM properties where appropriate: `sonic1.rom.path`, `sonic2.rom.path`, `s3k.rom.path`.
- Keep disassembly-backed references in test names/comments for parity-critical logic.
- Treat `docs/BUGLIST.md`, `docs/KNOWN_DISCREPANCIES.md`, and `docs/S3K_KNOWN_DISCREPANCIES.md` as feeder inputs for executable regression tests.
