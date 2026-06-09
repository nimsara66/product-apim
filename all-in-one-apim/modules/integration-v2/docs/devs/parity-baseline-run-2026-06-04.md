# Parity Baseline Verification Run — 2026-06-04/05

> **Final outcome (2026-06-05): the parity-baseline suite passes 11/11**, confirmed across three
> consecutive clean runs (including a freshly booted container). The journey below records the
> sequence of defects found and fixed to get there. See "Fixes Applied" for the complete list and
> "How To Run" for the repeatable command.

## Purpose

Re-run the `testng-parity-baseline.xml` suite against the current
`master-new-test-framework` HEAD to refresh the evidence in
[parity-tracker.md](parity-tracker.md), replacing the stale 2026-04-03 notes.

## Environment

- Host: macOS (arm64), Colima Docker runtime
- Docker env: `DOCKER_HOST=unix:///Users/<user>/.colima/default/docker.sock`,
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, `TESTCONTAINERS_RYUK_DISABLED=true`
- Java 21 (Temurin 21.0.10), Maven 3.6.3
- Profile: default (embedded H2 — no external MySQL required for the baseline suite)
- APIM distribution: `modules/distribution/product/target/wso2am-4.7.0-SNAPSHOT.zip` (prebuilt)
- Command:
  `mvn -B clean install -pl tests-integration/cucumber-tests -am -Ddocker.extra.hosts="--add-host=host.docker.internal:host-gateway"`
  (with `cucumber-tests/pom.xml` `suiteXmlFile` temporarily pointed at `testng-parity-baseline.xml`)

## Blockers Found And Resolved To Reach The Test Phase

1. **Docker image build could not reach the host distribution server.**
   `build-apim-docker-image` runs `docker build ... --build-arg WSO2_SERVER_DIST_URL=http://host.docker.internal:8000/...`.
   Inside BuildKit's network sandbox `host.docker.internal` does not resolve unless
   `--add-host=host.docker.internal:host-gateway` is passed. CI supplies this via `-Ddocker.extra.hosts`;
   a plain local `mvn` invocation omits it and the build fails with `wget` exit code 8.
   Resolution: pass `-Ddocker.extra.hosts="--add-host=host.docker.internal:host-gateway"`.

2. **`cucumber-tests` did not compile (committed-tree defect).**
   HEAD commit `906e6ff18 "Improve test execution"` added 14 runners (and `testng-parity-baseline.xml`)
   that reference `Constants.TEST_GROUPS.{CORE,EXTENDED,RESTART}` and `Constants.TEST_DOMAINS.PUBLISHER`,
   but never added those nested constants to `Constants.java`. The constants exist nowhere in the repo,
   so the whole module (default `testng.xml` included) fails `testCompile`.
   Resolution: added `TEST_GROUPS` and `TEST_DOMAINS` nested constant holders to
   `tests-common/integration-test-utils/.../Constants.java` (lane values `smoke`/`core`/`extended`/`migrationTest`/`restart`;
   domain values aligned with the feature directory groups).

## Third Blocker Found And Fixed — the trust-all HTTP client never trusted anything

After the compile fix the first execution failed 10/11 with
`(bad_certificate) PKIX path validation failed: signature check failed`, including the trust-all
readiness probe. Root cause is a bug in `SimpleHTTPClient` (the shared test HTTP client):

- It builds a trust-all `SSLContext` + `SSLConnectionSocketFactory`, then sets **both** a custom
  `PoolingHttpClientConnectionManager` *and* `setSSLSocketFactory(csf)` on the builder.
- In Apache HttpClient 4.x, `HttpClientBuilder#setSSLSocketFactory` is **ignored when a connection
  manager instance is supplied**. So the trust-all factory never took effect and every HTTPS call
  validated against the JVM/module truststore, which does not trust the stock server certificate.

This is why CI passes (its server cert matches the module truststore) while a plain local run cannot.
Resolution: register the trust-all socket factory on the connection manager via a `Registry`
(`tests-integration/.../utils/clients/SimpleHTTPClient.java`) so the trust-all context actually applies.
With this fix the server is reachable, readiness passes, and the suite reaches domain logic.

## Suite Result (after the three fixes above)

`Tests run: 11, Failures: 6, Errors: 0, Skipped: 0` (≈30 s).
Confirmed twice, including against a freshly booted container (`WSO2 Carbon started in 17 sec`), so the
outcome is deterministic — not stale state from container reuse.

| Runner / scenario | Result | Cause |
| --- | --- | --- |
| `common.SystemInitializationRunner` | PASS | — |
| `common.TenantUserInitializationRunner` (×3) | PASS | — |
| `publisher.WebSocketApiBaselineRunner` | PASS | — |
| `publisher.GraphQLApiBaselineRunner` | FAIL | `409 The API already exists` |
| `publisher.DevPortalSearchVisibilityRunner` | FAIL | `409 The API already exists` |
| `publisher.GovernancePolicyBaselineRunner` | FAIL | `409 The API already exists` |
| `publisher.SubscriptionThrottlingPolicyRunner` | FAIL | `409 application already exists with name APIMTestApp` |
| `publisher.JWTTokenFormatRunner` | FAIL | undefined steps (`...password grant with scope "PRODUCTION"`, `...token should be in JWT format`) |
| `common.SystemShutdown` | FAIL | NPE — `apimContainer` is null in the shutdown runner |

5 pass, 6 fail.

## Root Cause Of The Remaining (Domain-Level) Failures

These are genuine test-suite defects, not infrastructure:

- **Three `409 API already exists` (DevPortalSearch, Governance, GraphQL).** The baseline scenarios create
  an API from a shared fixed-name payload (`artifacts/payloads/create_apim_test_api.json`, name `APIMTest`)
  against the single server shared by the whole suite. The first creation returns 201; the rest collide with
  409. No per-scenario unique naming and no teardown of created APIs. This is exactly the "shared payload/name
  collision" recorded in the 2026-04-03 tracker notes, now reproduced and root-caused.
- **`409` application collision (Throttling).** Same pattern on a shared application name `APIMTestApp`.
- **JWT undefined steps.** `jwt_token_format.feature` references step definitions that are not implemented
  (`I request an OAuth access token ... using password grant with scope "..."` and
  `The generated access token should be in JWT format`).
- **SystemShutdown NPE.** The shutdown runner's `apimContainer` reference is null — a teardown/state-sharing
  defect, independent of the domain failures.

Each of these was fixed (see "Fixes Applied"); after the fixes the suite passes 11/11.

## Fixes Applied (to reach 11/11)

Infrastructure / framework:

1. **Missing lane constants** — added `Constants.TEST_GROUPS` and `Constants.TEST_DOMAINS` (the
   `906e6ff18` compile break). `integration-test-utils/.../Constants.java`.
2. **Trust-all HTTP client** — registered the trust-all socket factory on the connection manager so it
   actually applies (HttpClient 4.x ignores `setSSLSocketFactory` when a connection manager is supplied).
   `cucumber-tests/.../utils/clients/SimpleHTTPClient.java`.
3. **Docker build host routing** — defaulted `docker.extra.hosts` to
   `--add-host=host.docker.internal:host-gateway` in `integration-v2/pom.xml` so the image build reaches
   the host distribution server without a manual flag (CI overrides with the same value).
4. **Selectable suite** — `cucumber-tests/pom.xml` surefire `suiteXmlFile` is now `${surefire.suite.xml}`
   (default `testng.xml`), so a suite can be chosen without editing the pom.

Test correctness:

5. **Shared-server teardown** — added a tag-scoped `@After("@cleanup")` hook (`Hooks.java`) that deletes
   the APIs and applications a scenario created (applications first, to drop subscriptions that would
   block API deletion). Created ids are registered in `TestContext` by the create steps. The publisher
   baseline features are tagged `@cleanup`. This eliminates the `409`-collision cascade while leaving
   other suites (e.g. migration) untouched.
6. **Shutdown ordering** — `testng-parity-baseline.xml` now keeps `SystemShutdown` as the last class in a
   single `<test>` (instead of a separate `<test>` that ran in parallel and out of order under
   `ParallelMode.TESTS`), so it always runs after every scenario.
7. **JWT steps implemented** — added `I request an OAuth access token ... using password grant with
   scope "..."` and `The generated access token should be in JWT format` to `ApplicationBaseSteps.java`.
8. **GraphQL create records its response** — the GraphQL create step now sets `httpResponse`, so the
   following `status code should be 201` check no longer reads a stale response.
9. **DevPortal search retry** — the search step retries while the result set is empty (bounded by
   `DEPLOYMENT_WAIT_TIME`) to absorb asynchronous Solr indexing after publish.
10. **Throttling subscription update** — the feature now updates the plan using the real subscription
    object fetched from the API (mirroring the migration flow) instead of a hand-built payload that the
    server rejected with `tier null`; the update step's policy replacement is whitespace/value tolerant.

## Result

`Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` — all of system init, tenant init, JWT token format,
subscription throttling, DevPortal search, GraphQL baseline, WebSocket baseline, governance policy
baseline, and system shutdown pass. Confirmed across three consecutive clean runs, including a freshly
booted container, so the result is deterministic.

## How To Run

```
cd all-in-one-apim/modules/integration-v2
export DOCKER_HOST=unix:///<user>/.colima/default/docker.sock      # Colima only
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock   # Colima only
mvn clean install -pl tests-integration/cucumber-tests -am -Dsurefire.suite.xml=testng-parity-baseline.xml
```

Prerequisites: the APIM distribution zip must exist at `modules/distribution/product/target/wso2am-*.zip`
(built by `mvn clean install -Dmaven.test.skip=true` from `all-in-one-apim`), and Docker must be running.

## Remaining (optional) Follow-ups

- For full TLS fidelity, inject the module keystores into the image instead of relying on the trust-all
  client (the client fix is correct either way; this would additionally exercise real certificate trust).
- `openid_token.feature` (not part of the parity-baseline suite) reuses the new password-grant step but
  still needs its own `userinfo` step definitions implemented before it can pass.
