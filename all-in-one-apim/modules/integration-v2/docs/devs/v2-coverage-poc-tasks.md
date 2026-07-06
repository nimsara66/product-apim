# v2 coverage PoC — task tracker

Proof-of-concept for JaCoCo integration coverage on the **all-in-one** v2 lane.
Design: `v2-coverage-architecture.md`. Decisions locked: A1 (JAVA_TOOL_OPTIONS agent, opt-in), B1 (tcpserver dump), C1 (JaCoCo report from distribution classfiles + sources, scoped), all-in-one only, **per-PR CI to the product-apim Codecov project under flag `integration-v2_tests`** (standalone, alongside legacy `integration_tests`).

Legend: [ ] todo · [~] in progress · [x] done · [?] needs a decision from user

## Environment (verified 2026-07-03)
- Docker: UP · image `wso2am:4.7.0-SNAPSHOT-jdk21`: present
- JaCoCo agent `0.8.12`: in `~/.m2` · CLI/core/report: resolvable via mvn
- Distribution zip (classfiles source): `../../modules/distribution/product/target/wso2am-4.7.0-SNAPSHOT.zip` (538 MB)
- Sources (best-effort): `/Users/nimsara/patch-packs/rrt/tracing/carbon-apimgt` (carbon-apimgt 9.33.122.22-SNAPSHOT)

## Phase 0 — setup & decisions
- [x] Ground in framework internals (`DynamicApimContainer`, `BlockLifecycleListener`, image/JVM launch)
- [x] Create this task file
- [x] Confirm PoC shape with user → **isolated spike first**, **dist-zip classfiles + best-effort sources**
- [x] Resolve JaCoCo agent + core/report deps into `tests-common/testcontainers` (pom, jacoco 0.8.12)

## Phase 1 — Spike (Level 1): prove instrument → dump → report
- [x] `DynamicApimContainer.withCoverage()` — copy agent jar in, `JAVA_TOOL_OPTIONS` (tcpserver:6300, scoped), `addExposedPort`, `getCoverageDump{Host,Port}()`
- [x] `JacocoCoverage` helper — agent-arg builder, `extractAgentJar`, `dump(host,port)->.exec`, `extractApimgtClassfiles(zip)`, `discoverSourceRoots`, `report(...)->xml+html+%`
- [x] Verification test `JacocoCoverageSpikeVerificationTest` + suite `testng-fv-coverage.xml`
- [x] Compile (install testcontainers + test-compile cucumber-tests) — BUILD SUCCESS
- [x] Run live: boot 1 container w/ coverage, hit health, dump `.exec`, extract classfiles, render report
- [x] Record observed total % + artifact paths here

### Phase 1 RESULT (2026-07-03) — ✅ PASS end-to-end (live)
- Dump: mapped port 32973 → `target/coverage/verify-cov.exec` (89 KB)
- Classfiles: **31** `org.wso2.carbon.apimgt.*` plugin jars extracted from `wso2am-4.7.0-SNAPSHOT.zip`
- Report: **2146 classes, 68,962 lines, 7.26% line coverage** (5005/68962); instr 32689/635261
- Artifacts: `target/coverage/output/jacoco-it.xml` (12.5 MB, JaCoCo `DTD Report 1.1` — Codecov-ingestible) + `output/html/index.html` (261 pkgs)
- `Tests run: 1, Failures: 0`; no leaked containers
- 7.26% is startup + health-check only (no publish/invoke) — mechanism proven, not a coverage target.

Notes for Phase 1:
- Spike enables coverage by calling `withCoverage()` directly (no `-Dapim.coverage` gate — that goes in the listener in Phase 2).
- Sources are optional via `-Dapim.coverage.sources=<path>`; needs surefire forwarding (Phase 2) to actually reach the fork — spike runs classfiles-only otherwise (valid XML + %, no source lines).

## Phase 2 — Collector + listener  ✅
- [x] `CoverageSupport` util (gate `-Dapim.coverage`, shared paths, dist-zip locator)
- [x] `BlockLifecycleListener`: `withCoverage()` on boot + per-block dump before stop (all-in-one, best-effort)
- [x] `CoverageAggregationListener` (ISuiteListener.onFinish): merge all block `.exec` → report → `coverage/output/`
- [x] Surefire passthroughs (`apim.coverage`, `apim.coverage.sources`) in both integration-v2 profiles
- [x] Smoke suite `testng-v2-cov-smoke.xml` (1 probe block) + registered aggregation listener in `testng-v2.xml`
- [x] Run smoke with coverage ON → wired path works (dump `cov-smoke.exec` → merge → `output/txt/jacoco-it.xml`, 7.26%)
- [x] Run smoke with coverage OFF → **no agent/dump/report, no coverage dir, test passes** (default lane unaffected)

## Phase 3 — CI + Codecov (config templates; CI not runnable here)  ✅
- [x] `codecov.yml` template — flags `unit`/`integration` (carryforward), components, informational status → `docs/devs/coverage-ci/codecov.yml`
- [x] Per-PR workflow template → `docs/devs/coverage-ci/coverage-v2.yml` (same `on: pull_request` trigger as legacy → build → run IT `-Dapim.coverage=true` → upload product-apim project, flag `integration-v2_tests`)
- [x] Validated `codecov.yml` offline via `https://codecov.io/validate` → **"Valid!"** (flags + component regexes expanded correctly)

## ✅ PoC COMPLETE (2026-07-03)

All three phases done and verified live where possible. Opt-in (`-Dapim.coverage=true`); default runs unaffected.

### Files added/changed
- `tests-common/testcontainers/pom.xml` — JaCoCo agent/core/report deps (0.8.12)
- `tests-common/testcontainers/.../JacocoCoverage.java` — agent extract, tcp dump, zip classfile extract, report (xml+html+%)
- `tests-common/testcontainers/.../DynamicApimContainer.java` — `withCoverage()` + `getCoverageDump{Host,Port}()`
- `tests-integration/cucumber-tests/.../utils/CoverageSupport.java` — gate + path layout
- `tests-integration/cucumber-tests/.../utils/listeners/BlockLifecycleListener.java` — enable on boot + per-block dump
- `tests-integration/cucumber-tests/.../utils/listeners/CoverageAggregationListener.java` — suite-end merge+report
- `tests-integration/cucumber-tests/.../verification/JacocoCoverageSpikeVerificationTest.java` — isolated Phase-1 probe
- `src/test/resources/testng-fv-coverage.xml`, `testng-v2-cov-smoke.xml`; aggregation listener added to `testng-v2.xml`
- `integration-v2/pom.xml` — `apim.coverage`/`apim.coverage.sources` defaults + surefire passthroughs (both profiles)
- `docs/devs/coverage-ci/{codecov.yml,coverage-v2.yml}` — CI templates (per-PR, product-apim project)

### How to run locally
- Isolated spike: `mvn -pl tests-integration/cucumber-tests -am -Dexec.skip=true -Dsurefire.suite.xml=testng-fv-coverage.xml test`
- Wired smoke (coverage on): add `-Dapim.coverage=true -Dsurefire.suite.xml=testng-v2-cov-smoke.xml`
- Full lane (what per-PR CI runs): `-Dapim.coverage=true -Dsurefire.suite.xml=testng-v2.xml` (heavy)
- Report lands at `tests-integration/cucumber-tests/target/coverage/output/{txt/jacoco-it.xml, html/index.html}`
- Optional source lines: `-Dapim.coverage.sources=/path/to/carbon-apimgt`

### Verified
- Phase 1 (isolated): dump→report, 7.26% line coverage, valid JaCoCo XML, no leaks.
- Phase 2 (wired): boot→per-block dump→suite merge→report through the real listener; OFF path produces nothing & passes.
- Phase 3: codecov.yml validates "Valid!"; workflow template ready (needs repo CODECOV_TOKEN to run in CI).

## Phase 4 — Post-review refinements (2026-07-03)  ✅
Driven by review feedback (webapps, sources, carbon-apimgt focus, all-in-one only).
- [x] **Webapps added to scope.** `extractApimgtClassfiles` now also does nested WAR extraction:
  `WEB-INF/classes/org/wso2/carbon/apimgt/**.class` (+ `WEB-INF/lib/*apimgt*.jar`). Report robust per-root
  (analyze try/catch), and cleans the classfiles dir first (deterministic).
  - Effect: **2146 → 3005 classes, 68,962 → 111,985 lines** (983 webapp `.class`). Verified over 3 green spike runs
    (5.28–5.44% line — variance is normal boot-to-boot; the denominator grew so % is lower, as expected).
  - Publisher/devportal/admin REST impl confirmed to live in `WEB-INF/classes`, not `WEB-INF/lib`.
- [x] **Sources question answered.** `carbon.apimgt.version` IS pinned in the pom (`9.33.141`), but that's a
  version, not sources-on-disk — and it drifts (built zip carried `9.33.134`; a stray local checkout was
  `9.33.122.22-SNAPSHOT`). So don't rely on a local checkout. For the **carbon-apimgt Codecov target, local
  sources aren't needed** — Codecov resolves sources from the carbon-apimgt repo when paths match. Local HTML
  line detail stays best-effort via `-Dapim.coverage.sources`.
- [x] ~~Cross-repo target carbon-apimgt~~ → **REVERSED: upload to product-apim project, standalone (match legacy).**
  Legacy already parks integration coverage in the product-apim project (flag `integration_tests`); v2 does the
  same under flag **`integration-v2_tests`** (coexists/comparable). CI templates simplified: no carbon-apimgt
  checkout, no slug/commit override — plain upload to product-apim's own Codecov project. **Consequence
  (accepted): no unit+integration merge** (product-apim has no unit; carbon-apimgt unit stays in its own project).
  carbon-apimgt attribution kept documented as the alternative if a combined number is ever wanted.
- [x] **All-in-one only** reaffirmed in the design doc (distributed is not a coverage target by requirement).
- [x] Re-validated `codecov.yml` shape (flags/components) — unchanged structure, still valid.

### Remaining follow-ups (not blockers)
- Run the **full `testng-v2.xml` with coverage** for a realistic combined number (heavy; not run in PoC).
- One transient spike failure was observed (env/boot flake); 3 consecutive greens since. Consider a small retry
  around the dump if it recurs in CI.
- Codecov path `fixes:` may be needed for a few carbon-apimgt files that don't auto-resolve (multi-module tree).
