# Integration V2 Migration Plan

## Goal

Expand `modules/integration-v2` until it can replace the supported coverage in the legacy
`modules/integration` suite.

The migration should avoid copying the old suite blindly. Instead, we should:

1. establish parity targets
2. strengthen the v2 framework where old coverage depends on missing primitives
3. migrate feature domains in execution waves
4. keep legacy and v2 running in parallel until parity is demonstrated

## Success Criteria

- Every supported legacy coverage domain is represented in `integration-v2`.
- Every legacy-only test is classified as one of:
  - migrated to v2
  - replaced by a broader v2 scenario
  - intentionally retired as obsolete or unsupported
- `integration-v2` supports layered execution lanes:
  - `smoke`
  - `core`
  - `extended`
  - `migration`
- CI can run fast PR validation and deeper scheduled validation without relying on the legacy suite.

## Current State

### Legacy `modules/integration`

- Broad TestNG suite built around Carbon Automation.
- Covers backend, restart, benchmark, UI, and many feature-specific regression areas.
- Large surface area including tokens, throttling, GraphQL, WebSocket, workflows, analytics,
  governance, schema validation, and restart persistence.

### Current `modules/integration-v2`

- Surefire + TestNG + Cucumber + Testcontainers architecture.
- Stronger container isolation and clearer BDD structure.
- Coverage is currently concentrated in:
  - common initialization
  - publisher flows
  - migration validation
  - custom authorization header

## Workstreams

### 1. Parity Management

- Maintain a parity tracker that maps legacy coverage domains to v2 status.
- Track each domain by:
  - framework readiness
  - scenario readiness
  - CI readiness
  - legacy decommission readiness

### 2. Framework Foundation

Build missing v2 primitives before migrating specialized domains.

Required framework improvements:

- richer APIM container variants driven by `deployment.toml`
- reusable database/profile configuration support
- more backend containers and mock services
- reusable scenario helpers for:
  - API creation
  - application/subscription setup
  - token and API key generation
  - gateway invocation assertions
  - restart/redeploy flows
- common assertions for headers, payloads, logs, and lifecycle state
- runner/group strategy for lane-based execution

### 3. Functional Migration Waves

#### Wave 1: Core API Management

- publisher CRUD
- deploy/publish/revision
- OAS import
- documents
- application creation
- subscriptions
- base gateway invocation
- tenant-aware coverage for core flows

#### Wave 2: Runtime and Security

- OAuth/token scenarios
- API keys
- invalid/revoked token cases
- custom headers
- CORS and runtime header behavior
- endpoint security and invocation behavior

#### Wave 3: Product Features

- throttling
- API products
- governance/policies
- shared scopes
- search and visibility rules
- admin-side API management

#### Wave 4: Specialized APIs

- GraphQL
- WebSocket
- SSE/WebSub/async APIs
- SOAP/WSDL
- service catalog and related integrations

#### Wave 5: Operational and Persistence

- workflows
- analytics/logging
- restart persistence
- environment/profile-specific coverage

### 4. CI Rollout

- `smoke`: minimal PR gate
- `core`: main PR/nightly validation
- `extended`: scheduled broader non-migration coverage
- `migration`: scheduled migration validation

## Recommended Migration Order

1. common runner and lane model
2. core publisher/application/invocation vertical slice
3. token and security primitives
4. throttling and policy support
5. specialized API types
6. restart and operational scenarios
7. UI or separate E2E strategy

## Immediate Implementation Backlog

### Sprint 1

- add migration planning and parity tracking docs
- add v2 runner grouping for `smoke`, `core`, `extended`, and `migration`
- add Maven profiles that can run lane-specific subsets
- migrate and stabilize one complete core slice:
  - create API
  - deploy/publish
  - create application
  - subscribe
  - generate credentials
  - invoke and assert

### Sprint 2

- add token/API key primitives
- add subscription and devportal coverage
- migrate visibility/search/shared-scope basics

### Sprint 3

- add throttling and policy container/config support
- migrate throttling and admin policy coverage

## Definition Of Done Per Domain

A domain is complete only when:

- framework support exists
- scenarios exist in v2
- scenarios are tagged into the right lanes
- CI lane coverage is enabled
- parity tracker is updated
- legacy tests for that domain are either retired or explicitly retained
