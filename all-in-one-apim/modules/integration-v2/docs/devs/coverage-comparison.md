# Legacy vs V2 Coverage Comparison

## Purpose

This document compares the legacy `modules/integration` test framework with the newer
`modules/integration-v2` framework from a test coverage perspective.

The goal is not to prove strict one-to-one parity. The goal is to show:

- what the legacy framework covers today
- what `integration-v2` already covers
- where `integration-v2` intentionally changes the testing model
- which legacy domains still have no meaningful v2 replacement

## Framework Snapshot

| Area | Legacy `integration` | `integration-v2` |
| --- | --- | --- |
| Primary style | TestNG class-based integration tests | Cucumber feature files with TestNG runners |
| Runtime model | Carbon Automation based | Testcontainers based |
| Java/runtime posture | Older mixed stack | Java 21 oriented framework |
| Main test modules | `tests-backend`, `tests-restart`, `tests-benchmark`, `tests-config` | `cucumber-tests` |
| Shared support surface | Large set of clients, extensions, backend services, UI helpers | Smaller support layer centered on `integration-test-utils` and `testcontainers` |
| Current scope shape | Broad regression suite | Focused modernization suite |

## Scope Summary

### Legacy `modules/integration`

- `tests-backend`: about 327 Java test classes
- `tests-restart`: about 20 Java test classes
- `tests-benchmark`: 5 Java test classes
- `tests-config`: 1 Java test class

Representative legacy coverage buckets include:

- API lifecycle and revision management
- API products
- application flows
- comments, search, and visibility
- OAuth, API keys, JWT, and token behavior
- throttling
- GraphQL
- WebSocket and streaming APIs
- governance, gateway policy, and operation policy
- analytics, logging, workflow, schema validation, and admin features
- restart and persistence behavior
- benchmark and performance-oriented coverage
- configuration validation through `TomlBasedConfigurationTestCase`

### `modules/integration-v2`

- `cucumber-tests`: about 52 Java support or runner classes
- feature coverage is defined through 38 `.feature` files

Current v2 feature groups are:

- `common`
- `publisher`
- `migration`
- `header`
- `restart`

This means the v2 suite is already opinionated around scenario-driven coverage rather than a large set of isolated Java test classes.

## Coverage Comparison By Domain

| Domain | Legacy Coverage | V2 Coverage | Comparison |
| --- | --- | --- | --- |
| Framework bootstrap | Broad custom bootstrap and tenant setup | `common/system_initialization`, tenant initialization, shutdown flows | Covered in both, v2 is cleaner and more reusable |
| Publisher core flows | Strong coverage across lifecycle, CRUD, deploy/publish, revisions | `create_an_api_through_the_publisher_rest_api_test`, `create_deploy_publish_an_api`, `api_versioning`, `api_runtime_configurations` | V2 covers the main happy paths, but legacy is broader |
| Applications and subscriptions | Strong legacy coverage across application and subscription behavior | `create_new_application`, `subscription_blocking` | V2 has core scenarios, but edge-case parity is incomplete |
| API invocation | Broad invocation and gateway behavior validation | `api_key_invocation`, `invalid_token_invocation`, `custom_authorization_header` | V2 covers core invocation slices, legacy still has wider runtime permutations |
| OAS and API definition | OAS, revisions, updates, import and validation covered | `import_OAS_definition`, `migrated_api_definition`, `migrated_OAS_apis` | Good early v2 coverage |
| Documents | Covered in legacy with document-related API flows | `api_documents`, `migrated_api_documentation` | Present in both, legacy needs detailed parity review |
| Versioning and revisions | Covered strongly in legacy | `api_versioning`, `migrated_api_revisioning`, `migrated_api_versioning` | Covered in both, but not yet equivalent in depth |
| Migration validation | Not a separate modernized lane, but legacy contains overlapping API assertions | Strong dedicated `migration` feature group | V2 is stronger and more explicit here |
| Token flows | Broad OAuth, OpenID, revoked token, invalid token, multiple token variants | `refresh_token`, `revoke_token`, `sandbox_token`, `openid_token`, `invalid_token_invocation` | V2 has a solid core set, legacy still covers more variants |
| API keys | Covered in legacy | `api_key`, `api_key_invocation` | Covered in both |
| Custom header behavior | Covered in legacy header tests | `custom_authorization_header`, `custom_header_test_system_initialization` | Covered in both for the current supported slice |
| Restart persistence | Legacy restart module covers multiple restart domains | `token_persistence_restart` | V2 has started this lane, but scope is much smaller |
| Configuration coverage | `TomlBasedConfigurationTestCase` exists in `tests-config` | No dedicated config test module found | Legacy only today |
| Throttling | API, application, burst, JWT and related throttling cases | No dedicated v2 feature group found | Legacy only today |
| JWT and external IDP | Multiple JWT-focused legacy packages | No dedicated v2 JWT feature group beyond selected token flows | Mostly legacy only today |
| Search and visibility | Legacy search, visibility, role and domain coverage | `migrated_api_search` only | Partial in v2 |
| API products | Full legacy package coverage | `migrated_api_product`, `new_api_product_from_migrated_apis` | Partial in v2 |
| Governance and policies | Legacy governance, gateway policy, operation policy coverage | `api_policies`, selected shared-scope migration scenarios | Partial in v2 |
| Shared scopes | Present in legacy | `migrated_shared_scopes` | Partial in v2 |
| GraphQL | Dedicated legacy GraphQL packages | No dedicated v2 feature group found | Legacy only today |
| WebSocket and streaming APIs | Dedicated legacy WebSocket, SSE, WebSub, async packages | No dedicated v2 feature group found | Legacy only today |
| Analytics and logging | Legacy analytics and logging tests | No dedicated v2 feature group found | Legacy only today |
| Workflows | Dedicated legacy workflow coverage | No dedicated v2 feature group found | Legacy only today |
| Benchmark/load | Dedicated legacy benchmark module | No v2 equivalent found | Legacy only today |
| UI and E2E helpers | Legacy includes UI helpers and UI-related support | No v2 equivalent in the current module | Not migrated |

## Coverage Mapping By Module

| Legacy Module | What It Contributes | Closest V2 Equivalent | Status |
| --- | --- | --- | --- |
| `tests-backend` | Main functional regression coverage across API Manager features | `cucumber-tests` `common`, `publisher`, `migration`, `header` groups | Partial migration |
| `tests-restart` | Restart and persistence scenarios | `restart/token_persistence_restart.feature` | Started, but small |
| `tests-benchmark` | Performance and correlation logging oriented tests | No equivalent | Not migrated |
| `tests-config` | Configuration-level verification via `TomlBasedConfigurationTestCase` | No equivalent | Not migrated |

## Domains Already Reasonably Represented In V2

- common bootstrap and tenant setup
- publisher create or deploy or publish flows
- application creation and subscription blocking basics
- token refresh, revoke, sandbox, invalid-token, and OpenID basics
- API-key coverage
- OAS import and migrated API verification
- restart token persistence as an initial operational scenario
- migration-centric validation as a first-class lane

## Major Gaps Still Carried By Legacy

- throttling
- GraphQL
- WebSocket and streaming APIs
- benchmark coverage
- dedicated configuration coverage
- broader JWT and external IDP coverage
- analytics and logging
- workflow coverage
- schema validation
- admin and organization-specific domains
- broader search, visibility, and role-based behavior

## Interpretation

The legacy framework is still the broader coverage holder.

`integration-v2` is not yet a full replacement for `integration`, but it is already stronger in a few important ways:

- migration validation is explicit and organized
- test setup is cleaner and more portable through containers
- scenario intent is easier to read because coverage is expressed in feature files

At the same time, the legacy suite still carries most of the long-tail regression coverage.
That is especially true for specialized runtime behavior, policy-heavy scenarios, restart breadth,
and older feature families such as GraphQL, streaming APIs, workflows, and benchmark tests.

## Recommended Use Today

- Use legacy `integration` as the broader regression safety net.
- Use `integration-v2` for modernized scenario coverage, migration validation, and newly migrated domains.
- Track retirement decisions only after each legacy-only domain is either migrated, replaced, or intentionally dropped.