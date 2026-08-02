# Platform Gateway — integration-v2 test track (LOCAL tracking, not shipped)

Working tracker for adding **WSO2 API Platform Gateway** coverage to integration-v2. This file is a
LOCAL working document (untracked, like `static-analysis-plan.md`). The **shipped** architecture doc is
`platform-gateway-integration-plan.md` (mirrors `is7-key-manager-integration-plan.md`) and goes WITH the PR.

Docs mined: getting-started, setting-up, adding-and-managing-policies
(`apim.docs.wso2.com/en/latest/api-gateway/platform-gateway/…`).

## Decisions (locked)
- **Scope = Docker-testable core** (Groups A–F below). VM / Kubernetes-Helm / cert-manager / Moesif
  analytics / chart upgrade-uninstall are deployment-topology/ops, NOT testcontainer-able → **excluded**.
- **Mirror the IS7 external-system pattern** (per-block container, integration actor, resource cleanup).
- IS7 design doc updated in THIS PR (network-isolation refactor staled it) — DONE.

## Architecture summary (from the docs)
Platform Gateway = a standalone gateway runtime (data plane) run in your own infra, driven by the APIM
**control plane** (design/deploy/visibility). Registered from the Admin Portal → a **one-time registration
token** → the gateway is started pointed at `GATEWAY_CONTROLPLANE_HOST` (control plane on `9443`) +
`GATEWAY_REGISTRATION_TOKEN`; once connected it shows **active** in the control plane. APIs are created in
the Publisher with gateway type **API Platform Gateway** and deployed. **REST-only** today (from scratch or
OpenAPI import) — NOT WebSocket/GraphQL/MCP/AI.

## Scenario catalog (testable subset) — coverage checklist

### A. Control-plane config & gateway registration
- [ ] A1 `[apim.platform_gateway] versions=[…]` gates the selectable gateway versions at registration
- [ ] A2 Register a Platform Gateway env (admin) → returns a one-time **registration token** (shown once)
- [ ] A3 Gateway transitions to **active** in the control plane once it connects
- [ ] A4 (negative) registering with an out-of-list version is rejected

### B. Gateway runtime bring-up
- [ ] B1 Gateway configured with `GATEWAY_CONTROLPLANE_HOST` + `GATEWAY_REGISTRATION_TOKEN` connects
- [ ] B2 Health endpoint returns healthy
- [ ] B3 (negative) bad/expired token → gateway does not become active

### C. REST API deploy to the platform gateway
- [ ] C1 Create a REST API (from scratch) with gateway type = API Platform Gateway → deploy
- [ ] C2 Create a REST API by importing OpenAPI → deploy to the platform gateway
- [ ] C3 (negative) WebSocket/GraphQL/MCP/AI API cannot target the platform gateway

### D. Invocation & auth
- [ ] D1 Invoke a deployed API through the platform gateway → 200
- [ ] D2 API Key auth (`X-API-Key`) → 200 with a valid key; 401 without
- [ ] D3 Bearer JWT/OAuth token → 200 with a valid token; 401 with an invalid one
- [ ] D4 HTTP Basic auth → 200 with valid creds
- [ ] D5 Unauthenticated invoke behaviour (per the API's security)

### E. Policies (request/response flow)
- [ ] E1 Attach a policy to the **Request** flow → it executes on inbound requests
- [ ] E2 Attach a policy to the **Response** flow → it executes on outbound responses
- [ ] E3 Rate-limiting / throttling (request-count) enforced at the gateway
- [ ] E4 CORS policy honoured (allow-origins/methods/headers)
- [ ] E5 Header manipulation (add/modify) applied
- [ ] E6 Save + redeploy revision → new policy behaviour takes effect
- [ ] E7 (stretch) policy versioning / chaining
- ⚠️ Full policy catalog lives in the external **Policy Hub** (JS site, not doc-fetchable) — exact policy
  names/params to be confirmed against the running product in Phase 0/3.

### F. Try-out
- [ ] F1 Deployed API is invocable from the Dev Portal API Console ("Try out")

## Phase plan
- **Phase 0 — Discovery / go-no-go spike** — ✅ DONE. **Verdict: GO-WITH-CAVEATS.** Findings below; all
  verified against this repo's build (product 4.7.0-SNAPSHOT / carbon-apimgt 9.33.162).
- **Phase 1 — Infra + version-pairing smoke** — 🔄 in progress.
  - ✅ Version-pairing smoke (see "Phase-1 smoke" above).
  - ✅ **`DynamicPlatformGatewayContainer` built + validated.** Single-container abstraction over testcontainers
    `ComposeContainer` (2.0.2; the no-image `ComposeContainer(File)` ctor = local `docker compose`; per-instance
    project id = per-block isolation). Materializes `platform-gateway/{docker-compose.yaml,config.toml,listener-certs}`
    under `$HOME`, generates the aesgcm key + `api-platform.env`, waits on `/_gateway-health/ready` (HTTPS 8443),
    exposes the data plane on an ephemeral host port, `withControlPlane`/`connect` for the token. **Validated
    end-to-end** via a throwaway `exec:java` run → `PGW_VALIDATE_OK data-plane=https://localhost:34939/`.
  - ✅ pom: `pg.gateway.version=1.2.0` + `pg.gateway.{controller,runtime}.image.name` props;
    `pull-pg-{controller,runtime}-docker-image` execs (mirror `pull-is-docker-image`).
  - ✅ `bootPlatformGateway` testng param + `BlockLifecycleListener` wiring (boots the container standalone,
    publishes `blockPlatformGateway` + `platformGatewayControlPlaneHost` + data-plane URL to shared scope,
    stops it in onFinish + the boot-failure catch); `CREATED_PLATFORM_GATEWAY_IDS` cleanup swept via
    `DELETE /gateways/{id}` (admin actor) + `Utils.getPlatformGateway{s,ById}URL`.
- **Phase 2 — Registration & lifecycle glue** — ✅. `PlatformGatewaySteps` (register `POST /gateways` →
  capture `registrationToken` + register id for cleanup; `connect(...)` the running gateway; poll
  `GET /gateways` until `isActive`), the `platform_gateway_lifecycle.feature` journey (single scenario),
  `PlatformGatewayRunner`, the `IntegrationV2-PlatformGateway` testng block, and the `@feat:platform-gateway`
  capability-map entry. **Validated end-to-end: BUILD SUCCESS, 1/1 scenario** (register → connect → active).
- **Phase 3 — Scenario coverage** — ✅ complete (every testable scenario built + validated 2/2 green; the
  rest are product gaps / runtime-bound follow-ups, documented below). Groups A–F, single journey + one negative.
  - ✅ A2 register→one-time-token, A3 →active, B1 connect-with-token (the validated journey).
  - ✅ **C1 create+deploy** a REST API with `gatewayType:APIPlatform` (`gatewayVendor:wso2`) → deploy to the
    auto-created env (deploy-revision `[{name:<gateway-name>, vhost:"localhost", displayOnDevportal:true}]`) →
    publish. **Validated: BUILD SUCCESS** (`create_platform_gateway_api.json` + `createAndDeployToPlatformGateway`
    reusing the publisher glue). Pins found: `gatewayType:APIPlatform` IS accepted on create; deploy `vhost` is
    the vhost HOST (`localhost`), not the vhost name (`900512` otherwise).
  - ✅ **D invoke + ENFORCED api-key auth + negatives.** Backend = an **in-network `echo-backend`**
    (`mendhak/http-https-echo`) on the gateway compose (the runtime can't reach the block's nodebackend; a
    same-compose-network echo — endpoint `http://echo-backend:8080/` — is the clean static path). The API attaches
    an **`api-key-auth`** policy (see the cracked attach mechanism below), so the gateway ENFORCES the key: generate
    an internal API key (`apis/{id}/generate-key`) → invoke `ApiKey: <key>` → **200 via_upstream**; **no auth → 401**;
    **wrong key → 401**. **Validated: BUILD SUCCESS** (register→connect→active→create→deploy→publish→generate-key→
    invoke×3). Pins: `connect()` restarts the compose so the data-plane URL is re-published post-connect;
    `https://localhost:<mapped-8443>` matches the `localhost` vhost (no explicit Host header); echo image in pom pulls.
  - ✅ **B3 unconnected → inactive**: register a gateway, never connect it → `GET /gateways` `isActive:false`
    (`GatewayConnectEndpoint` only flips active on a verified `api-key`; bad token → WS close 4401). Validated.
  - **Remaining disposition** (from the 9.33.162 source discovery — several are product gaps / runtime-bound):

## Remaining-scenario disposition & product findings (verified in carbon-apimgt 9.33.162)
- ⚠️ **Open-by-default (important pin):** the platform gateway does NOT enforce OAuth2 for an API with NO
  attached auth policy — an unauthenticated invoke returns 200. Auth (`api_key`/`basic_auth`/`oauth2`) is *derived*
  from an attached Policy-Hub policy (`api-key-auth`/`basic-auth`/`jwt-auth` → `deriveApiSecurityFromHubPolicies`),
  unlike the classic gateway (secured by default). The journey therefore **attaches `api-key-auth`** to get a
  genuine enforced-auth test (200 valid / 401 no-auth / 401 wrong-key).
- 🚫 **A4 version-gating — PRODUCT GAP / non-scenario:** `CreatePlatformGatewayRequest` has NO version field
  (`PlatformGatewayServiceImpl.createGateway` takes no version). `[apim.platform_gateway] versions` is
  read-only UI metadata (`/settings`, `Environment.platformGatewayVersions`), never a registration gate. Nothing
  to reject. (Could only assert the metadata surfacing.)
- 🚫 **C3 unsupported-type — PRODUCT GAP:** the non-REST rejection is NOT implemented for `APIPlatform` (only a
  `WSO2_APK_GATEWAY` gate exists at `PublisherCommonUtils.java:2350`; the `apiTypes:["rest"]` feature catalog is
  a UI hint only). A WS/GraphQL API with `gatewayType:APIPlatform` returns **201**, not a rejection — an
  expected-rejection test would fail. **Flag to the feature owner** (fix: add an `else if APIPlatform` restricting
  to HTTP). WS/GraphQL probe payloads captured for when the gate lands.
- ✅ **Policy-attach mechanism CRACKED — D2 (api-key) DONE.** A platform-gateway Policy-Hub policy attaches via
  the API's **`apiHubPolicies`** list, and each entry needs a **non-blank `policyId` in `name::version` form**
  (e.g. `"api-key-auth::v1"`). A by-name attach (no `policyId`) fails with *"External policy identifier cannot be
  empty"*; the operation-policy registry can't mint one because its `supportedGateways` schema enum
  (`Synapse/ChoreoConnect/AWS/Azure/Kong`) has **no `APIPlatform`** — but for a platform-gateway API an unresolved
  `policyId` takes the **placeholder path** (`ApiMgtDAO.createPlaceholderPolicyDataForExternalPolicy`), so a bare
  `name::version` id suffices — **no policy registration needed**. `api-key-auth` (params `{in:header, key:ApiKey}`)
  + `apis/{id}/generate-key` → enforced 200/401/401. **Validated.** The gateway ships the real specs in
  `/app/default-policies`.
- ✅ **E policies — `set-headers` VALIDATED (mechanism proven for all).** Attached `set-headers` alongside
  `api-key-auth` via `apiHubPolicies` (`policyId="set-headers::v1"`, params
  `{request:{headers:[{name,value}]}}`); the gateway injects the request header → reflected in the echo backend's
  response body → asserted with `The response should contain`. **Validated: BUILD SUCCESS.** The same mechanism
  covers CORS / rate-limit / remove-headers (breadth follow-up) and **D4 Basic** (`basic-auth::v1`; needs a
  credential source). `jwt-auth` remains unusable — no JWKS/key-manager is pushed to the gateway, so APIM Bearer
  tokens can't validate (that's why `api-key-auth` is the enforced-auth choice).
- ➖ **F try-out — COVERED (redundant otherwise):** no server-side try-out endpoint in `devportal-api.yaml`; the
  API Console is browser Swagger-UI hitting the gateway URL directly = an authenticated invoke, which the journey
  now does with an enforced api-key. No separate test.

## Product issues to FILE (manual test + report — behavior absent in 9.33.162)
- **A4:** no version field on `CreatePlatformGatewayRequest`; `[apim.platform_gateway] versions` gates nothing at
  registration (read-only UI metadata only). Intended "reject out-of-list version" does not exist.
- **C3:** non-REST APIs are NOT rejected for `gatewayType:APIPlatform` (create returns 201). Fix location:
  add an `else if (WSO2_API_PLATFORM_GATEWAY...)` restricting to HTTP at `PublisherCommonUtils.java:2350`.
  Probe payloads (WS/GraphQL with `gatewayType:APIPlatform`) captured in the design doc / discovery.
- **Phase 4 — Docs** — ⬜ partial. Shipped design doc `platform-gateway-integration-plan.md` (after the
  approach decisions land) + IS7 doc update (✅ DONE).

## Phase 0 findings (verified)
- **Images (multi-service, anonymously pullable from ghcr.io):** `ghcr.io/wso2/api-platform/gateway-controller:1.2.0`
  (control plane of the gateway) + `ghcr.io/wso2/api-platform/gateway-runtime:1.2.0` (Envoy data plane).
  NOT a single image → NOT a direct `DynamicISContainer` clone.
- **Ports:** runtime data-plane **8443 HTTPS**, health `/_gateway-health/healthy` + `/_gateway-health/ready`
  (HTTPS on 8443, NOT plain-HTTP `/health` — that was the legacy gateway); controller 9090 REST / 9092 admin /
  18000 xDS gRPC.
- **Registration API (present in the local 9.33.162 admin WAR, tag "Platform Gateways", `/api/am/admin/v4`):**
  `POST /gateways` (`CreatePlatformGatewayRequest`: `name ^[a-z0-9-]+$` 3–64, `displayName`, `vhost` uri;
  optional description/properties/permissions) → `GatewayResponseWithToken` 201 with **`registrationToken`**
  (shown once, stored hashed). `GET /gateways`, `PUT/DELETE /gateways/{id}`,
  `POST /gateways/{id}/regenerate-token`. Scope `apim:admin`. **gatewayType = `APIPlatform`**
  (`APIConstants.WSO2_API_PLATFORM_GATEWAY`). `Environment.status` = Active/Inactive for APIPlatform.
- **deployment.toml:** `[apim.platform_gateway] versions = ["1.0.0"]` (default in `default.json:298`); optional
  pre-seed `[[apim.platform_gateway.connect]]` (`registration_token`, `name`, `display_name`, `url`,
  `organization`) lets a FIXED token authenticate before the DB row exists.
- **Connection:** persistent WebSocket `wss://<cp>:9443/internal/data/v1/ws/gateways/connect` (class
  `GatewayConnectEndpoint`), `api-key` header = `registrationToken`, invalid → WS close **4401**; artifacts
  PUSHED server→gateway (~2s scheduler). Gateway (WSS client) must trust the control plane's TLS cert.
- **Stale client stub to regenerate:** `modules/integration/tests-common/clients/admin/src/main/resources/admin-api.yaml`
  lacks the Platform Gateways API.
- **Unverified (carry as risk):** exact protocol compatibility of the 9.33.162 ⇄ gateway-1.2.0 pairing; the
  first-GA APIM version; whether the CP must trust the gateway cert (mutual TLS) — auth observed is
  token-in-header, not client-cert.

## IS7 → Platform Gateway infra mapping (updated with Phase-0 facts)
| IS7 | Platform Gateway |
|-----|------------------|
| `DynamicISContainer` (1 container) | `gateway-controller` + `gateway-runtime` (**2 containers**) on the block network |
| `is.docker.image.name` = `wso2/wso2is:7.3.0` | `ghcr.io/wso2/api-platform/gateway-{controller,runtime}:1.2.0` |
| pom exec `pull-is-docker-image` | pull BOTH gateway images |
| alias `wso2is`, port 9443 | runtime data-plane **8443 HTTPS**, health `/_gateway-health/ready`; controller 9090/9092/18000 |
| listener param `bootExternalIdentityServer` | `bootPlatformGateway` |
| `IntegrationActors.IS` (own SCIM/OAuth principal) | **none** — the gateway env is APIM-owned; its identity is the `registrationToken` (api-key), no separate management API |
| `ISResourceCleanup` (external system) | just a new `CREATED_PLATFORM_GATEWAY_IDS` list in `ResourceCleanup`, swept via `DELETE /api/am/admin/v4/gateways/{id}` as the **admin** actor |
| `ServerReadiness.awaitIdentityServerReady` | `awaitPlatformGatewayReady` (HTTPS `/_gateway-health/ready`) + poll `GET /gateways` `status==Active` |
| trust: IS cert → APIM truststore | gateway (WSS client) trusts the control-plane cert; CP→gateway trust unconfirmed |
| testng block `IntegrationV2-Is7KeyManager` | `IntegrationV2-PlatformGateway` |

## Decisions (locked — round 2)
- **A. Container model = ComposeContainer, wrapped as a SINGLE-container abstraction** (a
  `DynamicPlatformGatewayContainer` that internally runs the trimmed gateway compose = controller + runtime, no
  observability stack). **Strict per-block isolation is mandatory:** each block gets a UNIQUE compose project
  identifier + its OWN network so concurrent blocks' gateways never collide (⚠️ the #1 Phase-1 risk — see below).
  The **infra block/listener simply spins up the composed container**; it does NOT register anything.
- **B. Registration = HYBRID:** pre-seed a fixed token (`[[apim.platform_gateway.connect]]` in APIM toml +
  `GATEWAY_REGISTRATION_TOKEN` in the compose env) so the gateway connects at boot for the bulk of the
  journey; ONE part of the journey exercises the real `POST /gateways` → one-time token → `status==Active`
  for A2/A3 product coverage.
- **Test topology = a SINGLE feature journey in a SINGLE runner.** Registration AND every scenario (A–F) are
  **cucumber steps** (product ops, §14) — no product behaviour in the listener. `_setup_` only if a
  prerequisite genuinely must precede the journey.

## Phase-1 smoke — progress + hard-won findings
- ✅ **Gateway runs, native arm64, healthy standalone.** Images `ghcr.io/wso2/api-platform/gateway-{controller,runtime}:1.2.0`
  are `linux/arm64` (no emulation). Booted the 2-service compose (observability all behind `profiles:` → a plain
  `up` starts only controller+runtime). Readiness gate confirmed: `GET https://<host>:8443/_gateway-health/ready` → 200.
- ✅ **Per-block isolation is native.** `scripts/setup.sh` pins a unique `COMPOSE_PROJECT_NAME`
  (`wso2apip-gateway-<ver>-<hex>`) in `.env`, prefixing every container/network/volume → each block just sets its
  own project name. No custom network juggling needed for isolation.
- ✅ **Connection config (verified in `configs/config.toml`):** `[controller.controlplane]` `host` / `token` /
  `gateway_name` via env `APIP_GW_CONTROLLER_CONTROLPLANE_{HOST,TOKEN,GATEWAY_NAME}`; **`insecure_skip_verify`
  defaults `true`** (no control-plane cert trust needed in tests). Storage = embedded **sqlite** (no external DB).
  Controller has its own **basic-auth admin** (`ADMIN_USERNAME`/`ADMIN_PASSWORD` → bcrypt in `api-platform.env`).
- ⚠️ **Two host-environment gotchas the harness MUST handle:**
  1. **Docker Compose ≥2.30 required** for the shipped compose's `env_file: {format: raw}`; Colima ships 2.29.7 →
     patch to short-form `env_file: [api-platform.env]` (literal on 2.29, so the bcrypt `$` survives). Ship our
     own adjusted compose.
  2. **Colima bind-mount sources must be under `$HOME`** — `/tmp` is NOT shared into the Colima VM, so file
     bind-mounts (config.toml, certs, aesgcm key) silently become empty *directories* → services exit. testcontainers
     `ComposeContainer` must materialize the dist under a VM-shared path (or use copy-to-container, not bind mounts).
- ✅ **APIM control-plane wiring + WS handshake PROVEN (the version-pairing gate).** On the stock
  `wso2am:4.7.0-SNAPSHOT-jdk21` (no toml override needed — the `default.json:298` `versions=["1.0.0"]` default
  applies): DCR + `apim:admin` token → `POST /api/am/admin/v4/gateways` (name/displayName/vhost) → **HTTP 201**
  with `registrationToken`, `isActive:false`. Wired the running gateway (`APIP_GW_CONTROLLER_CONTROLPLANE_HOST=
  host.docker.internal:9443`, `..._TOKEN`, `..._GATEWAY_NAME=smoke-gw`) → controller log:
  `Connecting to control plane url=wss://host.docker.internal:9443/internal/data/v1/ws/gateways/connect` →
  `TLS verification disabled (insecure_skip_verify=true)` → `Received connection acknowledgment` →
  `Connection state changed connecting→connected` → `Control plane connection established`. APIM then reports
  **`GET /gateways` → `isActive: true`**. **gateway-1.2.0 ↔ APIM 9.33.162 is compatible.**
- ⏭️ **Deploy a REST API + invoke through the gateway** — deferred to Phase-3 Group C/D, built on the framework's
  API-creation glue (cleaner than raw REST in a smoke). Not a risk to the infra.

## Phase-1 smoke — risk verdicts
1. **Per-block compose isolation** — ✅ native via `COMPOSE_PROJECT_NAME` (prefixes containers/network/volumes).
   **Reachability** — ✅ proven via `host.docker.internal:9443` (skip-verify); for the real harness we can keep
   host.docker.internal OR place the compose on the block's `Network` — either works, isolation is by project.
2. **Version pairing** 9.33.162 ⇄ gateway-1.2.0 — ✅ WS handshake + `isActive:true`.
3. **TLS trust** — ✅ `insecure_skip_verify=true` (default) → no CP cert needed; no mutual TLS observed.
