# Writing integration-v2 tests

Rules for authoring Cucumber product tests in this module. Read before writing any test.

## 1. Search before you write
Duplicate tests are the #1 problem here. First check the coverage tree
(`../../docs/devs/v2-public-feature-coverage-map.md`) and the existing `.feature` files for the
capability you're about to test. Extend an existing feature/scenario if one fits; only add a new
file when nothing does.

## 2. Where it goes
Folders are shallow and by capability: `features/{publisher,devportal,gateway,admin,key-manager,analytics}/`
plus non-product `common/`, `framework-verification/`, `migration/`. The folder is just the physical
home and shared-fixture context — **`@cap` is the source of truth**, so a file may live in `publisher/`
yet be `@cap:gateway`.

## 3. Tags
Every product scenario is tagged. Valid `@cap`/`@feat` values are the closed vocabulary defined in
`../../docs/devs/capability-map.yml` — a tag not in that file fails lint.

| Tag | Cardinality | Meaning |
|-----|-------------|---------|
| `@cap:<id>` | exactly one | the capability under test (the subject of the assertions) |
| `@feat:<id>` | exactly one | feature under that capability |
| `@rule:<slug>` | 0–1 | free-text sub-grouping within a feature |
| `@type:smoke\|negative\|regression` | 0+ | test nature (selection axis) |
| `@dep:<cap>` | 0+ | a cross-capability **prerequisite** (NOT coverage of it) |
| `@legacy:<Class>` | 0+ | the legacy class this replaces (parity tracking) |

- **One `@cap` = one thing under test.** If you can't pick a single `@cap`, the scenario is doing too
  much — split it.
- `@dep` is for non-obvious cross-capability needs (e.g. gateway throttling needs an admin policy →
  `@dep:admin`). Don't tag the universal baseline (everything needs an API + token).
- Non-product features use exactly one exclusion marker — `@infra`, `@framework`, or `@migration` —
  and are skipped from the product tree.

## 4. Isolation (the core concurrency rule)
Tests run in parallel on shared containers. **Every test owns its resources and shares nothing
mutable.**
- **Never name a resource by hand.** Use the shared naming utilities (`utils/Utils`,
  `utils/TestContext`) so names are unique by construction. Hardcoded names = cross-test collisions.
- No reliance on global state or on artifacts created by another scenario/class.
- **Cross-tenancy:** if a test needs other tenants, create them within that test class (via
  `utils/TenantUserProvisioner`), isolated from other classes — never reuse another class's tenant.
- **Wait, never sleep.** Poll/await for readiness (`utils/ServerReadiness` and equivalents). No
  `Thread.sleep` — it is the main cause of flaky parallel tests.

## 5. Cleanup
- Every resource a test creates is removed.
- Cleanup is **idempotent** and **runs even on failure** — do it in hooks (`@After`), not as inline
  teardown scenarios that get skipped when an earlier step fails.
- Leave zero residue (APIs, apps, subscriptions, tenants, keys).

## 6. Gherkin style
- Unique, capability-named `Feature:` titles (no two files sharing a title).
- Shared setup goes in `Background`, not repeated per scenario.
- One behavior per scenario; descriptive scenario names.

## 7. Step definitions (glue)
- **Reuse** existing steps. If one doesn't quite fit, **extend it** to cover the new need — never add
  a near-duplicate step. Search the glue before writing.

## 8. Run & verify locally
Run the suite reusing prebuilt images (use `mvn test`, not `install`, so the testcontainers image-build
execs don't re-fire):
```
mvn test -pl tests-integration/cucumber-tests -am -Dsurefire.suite.xml=<suite>.xml
```
(from `all-in-one-apim/modules/integration-v2`). Confirm your new scenario passes before committing.

## 9. Copyright header
New `.java` files require the standard WSO2 license header. Use the **current year** — do not copy a
year from another file.

## Anti-patterns (don't)
Fixed ports · hardcoded resource names · `Thread.sleep` · depending on another scenario's order or
artifacts · shared mutable static state · cleanup in inline scenarios instead of hooks · duplicate
steps or duplicate tests.
