# Integration V2 Coverage Execution Plan

## Objective

Improve `modules/integration-v2` coverage to a level where it can replace supported areas of the
legacy `modules/integration` suite, while preserving the current v2 architecture and module layout.

This plan keeps the underlying structure unchanged:

- keep `tests-integration/cucumber-tests` as the single functional test module
- keep `tests-common/integration-test-utils` and `tests-common/testcontainers` as shared foundations
- keep lane-based execution (`smoke`, `core`, `extended`, `migration`, `restart`)
- expand coverage by adding scenarios, step primitives, and lane wiring, not by restructuring modules

## Non-Goals

- no split into multiple new test modules for domain features
- no replacement of Cucumber + TestNG execution model
- no immediate decommissioning of legacy `modules/integration`

## Delivery Principles

1. Vertical slices over isolated utilities: ship runnable end-to-end scenarios each sprint.
2. Lane-first quality gates: every new domain must be mapped to at least one lane.
3. Evidence-driven parity: every status update must cite feature files and CI results.
4. Backward-safe migration: keep legacy as the regression safety net until domain exit criteria are met.

## Current Baseline

- `integration-v2` currently has supported-scope coverage in common bootstrap/tenant setup and core
  token/OAuth flows, with verified parity-baseline and default-suite execution evidence.
- `integration-v2` also covers `publisher`, `migration`, `header`, and initial `restart`, but several
  domains are still only partial in breadth.
- Major remaining gaps are in deeper runtime invocation breadth, throttling, GraphQL, streaming APIs,
  governance/workflows, admin, schema, analytics/logging, and benchmark decisions.
- Lane model exists and is usable, but domain depth is uneven.

## Workstreams

### A. Framework Primitives (No Structural Changes)

- Expand reusable step primitives for:
  - token lifecycle and key-manager flows
  - policy/throttling setup and assertion
  - specialized API creation/invocation (GraphQL, WebSocket, SSE/WebSub)
  - restart/redeploy and state validation
- Extend container configuration variants through `deployment.toml` overlays.
- Standardize assertion helpers for headers, payload, lifecycle, and eventual consistency behavior.

### B. Coverage Migration Waves

- Wave 1: close core runtime, publisher, and application edge-case breadth
- Wave 2: throttling and policy governance
- Wave 3: specialized API types (GraphQL, streaming)
- Wave 4: operational and platform features (workflow, analytics/logging, schema, admin)

### C. CI and Lane Governance

- Make `smoke` mandatory for PRs.
- Make `core` required for merge to protected branches.
- Run `extended`, `migration`, and `restart` scheduled nightly.
- Publish domain pass/fail trend per lane.

## 12-Week Execution Plan

## Phase 0 (Week 1): Stabilize Baseline and Tracking

Deliverables:

- Add a domain owner and target release field to parity entries.
- Add evidence links per domain (feature path, report path, CI job).
- Freeze a baseline coverage snapshot for v2 and legacy.

Exit Criteria:

- Parity tracker contains owner, target, and evidence for all in-progress/review domains.
- CI dashboards show lane-level pass rates.

## Phase 1 (Weeks 2-4): Core Runtime and Publisher Breadth

Target Domains:

- API invocation/runtime basics
- application management edge cases
- publisher core flows
- restart persistence hardening

Execution:

- Add negative-path and eventual-consistency scenarios for invocation and subscription state.
- Add multi-tenant and role-variant scenarios for publisher and application lifecycle flows.
- Extend restart lane with at least two additional restart persistence scenarios beyond token persistence.
- Fold the newer parity-baseline publisher runners into the main default suite once stable.

Exit Criteria:

- `smoke` and `core` stable with at least 95 percent pass rate over 7 consecutive days.
- Invocation, application, and publisher-core domains gain broader negative-path coverage with evidence.

## Phase 2 (Weeks 5-7): Throttling and Governance Foundation

Target Domains:

- throttling
- shared scopes and policies
- governance and operation policies

Execution:

- Introduce throttling fixture configuration and reusable assertions.
- Add scenario sets for API-level, app-level, and burst-like behavior.
- Add governance/policy CRUD and enforcement verification scenarios.

Exit Criteria:

- At least one `core` and one `extended` scenario exists for each of the three domains.
- Throttling and governance domains have passing breadth beyond the current parity-baseline happy paths.

## Phase 3 (Weeks 8-9): Specialized API Types

Target Domains:

- GraphQL
- WebSocket and streaming APIs

Execution:

- Implement shared API-type setup steps and transport-specific invocation assertions.
- Add baseline scenarios for creation, publish, subscribe, invoke, and policy checks.

Exit Criteria:

- GraphQL and streaming domains each have minimum viable coverage in `extended`.
- Both domains extend beyond create/publish baselines into invocation and policy validation.

## Phase 4 (Weeks 10-11): Platform Features

Target Domains:

- workflows
- analytics/logging
- admin and organization features
- schema validation

Execution:

- Add minimally representative scenarios per domain (happy path + one negative path).
- Wire scenarios to `extended` and selective `core` where stable and fast.

Exit Criteria:

- Four target domains have executable v2 scenarios and CI evidence.
- Domain statuses updated with blockers explicitly recorded.

## Phase 5 (Week 12): Parity Gate Review

Execution:

- Run side-by-side legacy vs v2 domain review.
- Classify each legacy domain as:
  - migrated
  - replaced by broader v2 scenario
  - intentionally retained in legacy

Exit Criteria:

- Release-ready parity report published.
- Decommission candidates identified with rollback plan.

## Domain-Level Definition of Done

A domain can move to `done` only when all are true:

1. Framework readiness: required primitives and container overlays exist.
2. Scenario readiness: happy path + negative path + tenant or role variant exists.
3. Lane readiness: mapped to at least one required lane.
4. CI readiness: stable pass trend for 7 days in mapped lanes.
5. Evidence readiness: parity tracker links to feature files and CI reports.
6. Legacy decision: legacy tests mapped to migrated/replaced/retained decision.

## Quantitative Targets

- `smoke` median runtime less than or equal to 15 minutes.
- `core` median runtime less than or equal to 45 minutes.
- Lane flakiness less than or equal to 2 percent (rerun-adjusted).
- Each in-progress domain adds at least 2 net-new scenarios per sprint until `done`.
- Effective verified parity increases sprint over sprint from the current `49.1%` baseline.

## RACI (Suggested)

- Framework Maintainers: primitives, container overlays, lane wiring.
- Domain Test Owners: scenario authoring and parity evidence.
- CI Owners: lane stability, schedule, and reporting.
- Release Owner: parity gate and legacy retirement decisions.

## Immediate Next Sprint Backlog

1. Complete API invocation edge-case scenario pack (invalid, revoked, blocked, delayed consistency).
2. Fold the passing parity-baseline publisher runners into the default `testng.xml` lane safely.
3. Add two restart persistence scenarios beyond token persistence.
4. Expand throttling beyond the current subscription-plan baseline with API-level and policy assertions.
5. Enable nightly report aggregation by lane and publish effective verified parity alongside structural parity.

## How to Use This Plan

- Use this file as the execution source of truth.
- Use `parity-tracker.md` for status and evidence snapshots.
- Use `coverage-comparison.md` for narrative scope comparison and stakeholder communication.
