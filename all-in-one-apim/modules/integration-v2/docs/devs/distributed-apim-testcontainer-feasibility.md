# Feasibility Study: Dynamic Distributed APIM Test Container

**Status:** Feasibility study only — no implementation.
**Author:** nimsara@wso2.com · **Date:** 2026-07-01
**Scope:** integration-v2 test framework (`all-in-one-apim/modules/integration-v2`).

## Objective

Replace the single all-in-one `DynamicApimContainer` with a dynamic *distributed* provisioner
that boots the three APIM components — **API Control Plane (ACP)**, **Universal Gateway (GW)**,
and **Traffic Manager (TM)** — in **separate** containers, backed by a **shared MySQL
container**, while `testng-v2.xml` runs **unchanged**.

**Verdict:** ✅ **Feasible, moderate effort.** The build/image pipeline generalizes cleanly to
three purpose-built component images, and the framework's dynamic-host-port + shared-network
design already supplies the right primitives. The real work is (a) inter-component wiring
config, (b) a build-pipeline extension to produce three component images, (c) a shared MySQL
container with schema init + driver packaging, and (d) making three framework-internal helpers
component-aware. **No changes to `testng-v2.xml` or feature/step files.**

---

## 1. Baseline — how the all-in-one design works today

`DynamicApimContainer`
(`tests-common/testcontainers/.../DynamicApimContainer.java`) boots one all-in-one APIM
container:

- Runs with `-DportOffset=0` and `withExposedPorts(9443, 9763, 8243, 8280)` — Docker maps each
  canonical port to an **ephemeral host port**, resolved lazily via `getMappedPort()`. This is
  the "dynamic" mechanism: no offset math, no per-container DB renaming, each container has its
  own network namespace.
- Exposes two URLs: `getServletHttpsUrl()` (9443 → control-plane REST/SOAP/OAuth) and
  `getGatewayHttpsUrl()` (8243 → runtime invocation).
- Joins `ContainerNetwork.SHARED_NETWORK`; reads a merged `deployment.toml`
  (distribution base + `basic` overlay + optional feature overlay); DB coordinates injected via
  `$env{...}`.

`BlockLifecycleListener.onStart` boots **one container per TestNG `<test>` block**, gates on
`ServerReadiness.awaitReady(baseUrl)`, then publishes into the block's shared `TestContext`
scope:

| Key | Value | Consumed by |
|-----|-------|-------------|
| `blockApimContainer` | container handle | lifecycle stop, `ServerLifecycleSteps` restart |
| `baseUrl` | `getServletHttpsUrl()` | **Control Plane**: publisher/devportal/admin REST, DCR, `oauth2/token`, SOAP (Tenant/User/ServerAdmin), health-check + artifact-deployed poll |
| `baseGatewayUrl` | `getGatewayHttpsUrl()` | **Gateway**: deployed-API invocation (`APIInvocationSteps`) |

**Key property:** `testng-v2.xml` names no container class. It only sets `<parameter>`s
(`blockLabel`, `initTenantUsers`, `initBackend`, `tomlExtraOverlayPath`). The XML is
topology-agnostic by construction — which is exactly why the "unchanged XML" requirement is
achievable.

---

## 2. The distributed topology

Each component is a **separate distribution** already built by this repo (confirmed present
under each component's `modules/distribution/product/target/`):

| Component | Zip artifact (present in repo) | Docker Hub image / `docker-apim` subdir | Role |
|-----------|-------------------------------|------------------------------------------|------|
| API Control Plane | `wso2am-acp-4.7.0-SNAPSHOT.zip` | `wso2/wso2am-acp` — `dockerfiles/ubuntu/apim-acp` | Publisher, DevPortal, Admin, Key Manager, SOAP admin, token/DCR, event-hub source |
| Universal Gateway | `wso2am-universal-gw-4.7.0-SNAPSHOT.zip` | `wso2/wso2am-universal-gw` — `dockerfiles/ubuntu/apim-universal-gw` | Runtime API traffic; syncs artifacts from CP |
| Traffic Manager | `wso2am-tm-4.7.0-SNAPSHOT.zip` | `wso2/wso2am-tm` — `dockerfiles/ubuntu/apim-tm` | Throttle decision engine + event hub |

The role is **inherent to each pack** (which features/webapps it bundles), so no `server_role`
profile juggling is needed — only cross-component wiring config.

**Ports** (from the `apim-acp` Dockerfile `EXPOSE`, representative of the component images):
`9763 9443 9999 11111 8280 8243 5672 9711 9611 9099`. East-west ports (`5672` AMQP/event-hub,
`9611`/`9711` binary/thrift throttle, `9099` ws) travel **inside** the shared Docker network on
canonical ports; only host-facing ports (ACP `9443`; GW `8243` + `9443`) need ephemeral mapping.

All three nodes share **one `apim_db` + one `shared_db`** — this is how distributed APIM
coordinates (see §5 for the MySQL container that provides them).

---

## 3. Two facts that make this straightforward

### (a) The build/image pipeline generalizes directly — three images built exactly like the all-in-one

The current all-in-one image is built (**not pinned**; tracks `#master`) by
`tests-common/testcontainers/pom.xml`:

1. `python3 -m http.server 8000` over `distribution/product/target` (serves the zip);
2. `docker build https://github.com/wso2/docker-apim.git#master:dockerfiles/ubuntu/apim
   -t wso2am:4.7.0-SNAPSHOT-jdk21 --build-arg WSO2_SERVER=wso2am-4.7.0-SNAPSHOT
   --build-arg WSO2_SERVER_DIST_URL=http://host.docker.internal:8000/…zip`.

The component Dockerfiles accept the **same build args** (`WSO2_SERVER`, `WSO2_SERVER_VERSION`,
`WSO2_SERVER_ZIP_VERSION`, `WSO2_SERVER_DIST_URL`). Each component image is built by the same
recipe pointed at a different subdir + zip:

```bash
# per component (acp shown)
docker build ${docker.extra.hosts} https://github.com/wso2/docker-apim.git#master:dockerfiles/ubuntu/apim-acp \
  -t wso2am-acp:4.7.0-SNAPSHOT-jdk21 \
  --build-arg WSO2_SERVER=wso2am-acp-4.7.0-SNAPSHOT \
  --build-arg WSO2_SERVER_VERSION=4.7.0-SNAPSHOT \
  --build-arg WSO2_SERVER_ZIP_VERSION=4.7.0-SNAPSHOT \
  --build-arg WSO2_SERVER_DIST_URL=http://host.docker.internal:8000/wso2am-acp-4.7.0-SNAPSHOT.zip
```

The zips already exist after a tests-skipped reactor build, so producing three images is
additive pom work, tracking `#master` exactly as today.

### (b) Dynamic ports are only needed at the host boundary

Inter-component traffic uses canonical ports + stable network aliases (`acp`, `gateway`,
`trafficmanager`, `mysql`); only the ports tests reach from the host get ephemeral
`withExposedPorts` mapping. Parallel-block port-collision safety is preserved for free — the
same trick `DynamicApimContainer` already uses, replicated across the cluster.

---

## 4. Proposed design

A `DistributedApimCluster` facade (a small orchestrator, **not** a `GenericContainer`) holding
the containers on a per-cluster `Network`:

```
DistributedApimCluster(label, tomlOverlays)
 ├─ mysql          image mysql:8.x       alias "mysql"          (no host exposure)
 ├─ trafficManager image wso2am-tm:*      alias "trafficmanager" (no host exposure)
 ├─ acp            image wso2am-acp:*     alias "acp"            exposes 9443
 └─ gateway        image wso2am-univ..:*  alias "gateway"        exposes 8243, 8280, 9443
```

- **Boot order:** `mysql` → (schema init) → TM → ACP → GW (GW subscribes to ACP's event hub;
  ACP must precede it; MySQL must precede all APIM nodes).
- **Per-component readiness:** MySQL accepting connections + schema loaded; ACP on a
  control-plane endpoint; GW on `…:9443/api/am/gateway/v2/server-startup-healthcheck`; TM on its
  throttle port.
- **URL accessors preserve the current contract** so `TestContext` keys are unchanged:
  - `getServletHttpsUrl()` → `acp.getMappedPort(9443)` → published as `baseUrl`
  - `getGatewayHttpsUrl()` → `gateway.getMappedPort(8243)` → published as `baseGatewayUrl`
  - **new** `getGatewayMgmtHttpsUrl()` → `gateway.getMappedPort(9443)` for the gateway webapp
    (see §6.2)
- **Per-component TOML overlays** rewrite the shipped `localhost` references to network aliases +
  canonical ports via `$env{...}` injection, using the existing `Utils.mergeToml` machinery:
  - **ACP** → `[[apim.gateway.environment]]` `service_url=https://gateway:9443/services/`,
    `http_endpoint=http://gateway:8280`, `https_endpoint=https://gateway:8243`
  - **GW** → `[apim.key_manager] service_url=https://acp:9443/services/`;
    `[apim.event_hub] service_url=…acp:9443…`, `event_listening_endpoints=["tcp://acp:5672"]`;
    throttle `url_group` → `trafficmanager:9611/9711`
  - **TM** → `[apim.event_hub] service_url=https://acp:9443/services/`,
    `event_listening_endpoints=["tcp://acp:5672"]`
- `BlockLifecycleListener` swaps `new DynamicApimContainer(...)` →
  `new DistributedApimCluster(...)` and publishes the same keys. Nothing above the listener
  changes.

---

## 5. Database — shared MySQL container (replaces per-container H2)

> **Verified against `apim-distributed-dev-setup/`.** The concrete recipe below (image, `latin1`,
> `max_connections`, two-phase init, init-complete-flag readiness, connector-into-`lib`, JDBC params)
> is drawn from a working dev harness that runs the real 4.7.0 packs on MySQL. Full analysis and the
> testcontainers mapping are in **`mysql-setup-learnings.md`** (same directory).

### Why this changes from the all-in-one baseline

The integration-v2 pom defines **two DB modes** via Surefire/Failsafe env blocks:

- **Default profile → embedded H2**, in-container and file-based:
  `API_MANAGER_DATABASE_URL = jdbc:h2:./repository/database/WSO2AM_DB;DB_CLOSE_ON_EXIT=FALSE`
  (+ `WSO2SHARED_DB`). This is the all-in-one default — each container owns its own on-disk H2,
  which is fine because *one* node owns *all* the data.
- **Alt profile → external MySQL** at `jdbc:mysql://host.docker.internal:3306/WSO2AM_APIMGT_DB`
  and `:3307/WSO2AM_COMMON_DB` — a host-side, pre-provisioned DB reached via
  `host.docker.internal`.

**Embedded H2 is fundamentally incompatible with the distributed topology.** In distributed APIM
the ACP, GW, and TM coordinate through a **shared** `apim_db` + `shared_db` (subscriptions, keys,
throttle policies, key-manager state). Three containers cannot share a file-based H2 — each would
get its own empty database and the cluster would never agree on state. A network-reachable,
concurrently-accessed RDBMS is mandatory. Hence: a **MySQL container on the shared network,
shared by all three APIM nodes.**

### Design

- **One MySQL container per cluster** (recommended default): gives per-block DB isolation for
  free (no schema-name juggling), matches the "each block is a self-contained cluster" model, and
  needs **no host port** — only the three APIM nodes reach it internally over `mysql:3306`.
  *Alternative:* one shared MySQL with per-cluster schema names — fewer containers, but
  reintroduces per-cluster naming/isolation and a startup-contention point. Prefer per-cluster
  unless resource pressure forces sharing.
- **DB coordinates injected via the existing `$env{...}` path**, changed only in value — canonical
  port on the network alias instead of `host.docker.internal`:
  ```
  API_MANAGER_DATABASE_URL = jdbc:mysql://mysql:3306/WSO2AM_APIMGT_DB?allowPublicKeyRetrieval=true&useSSL=false
  SHARED_DATABASE_URL      = jdbc:mysql://mysql:3306/WSO2AM_COMMON_DB?allowPublicKeyRetrieval=true&useSSL=false
  *_DATABASE_TYPE=mysql    *_DATABASE_DRIVER=com.mysql.cj.jdbc.Driver
  ```
  Both schemas live in one MySQL server on `3306` (the current `3306`/`3307` split collapses to
  one container with two schemas).
- **Schema init is solved by artifacts already in the pack.** The distributions ship
  `dbscripts/apimgt/mysql.sql` (the APIM schema) and `dbscripts/mysql.sql` (the carbon/shared
  schema). Feed these to the MySQL container as init scripts (Testcontainers `withInitScript` /
  entrypoint `/docker-entrypoint-initdb.d`) after creating `WSO2AM_APIMGT_DB` and
  `WSO2AM_COMMON_DB`. Extract them once from any component zip at build time and stage them as
  test resources.

### New required work / considerations

1. **MySQL JDBC driver is NOT bundled in the packs** (verified — no `mysql`/`mariadb` jar in
   `repository/components/lib`; only the DDL scripts ship). The connector `.jar` must be dropped
   into `repository/components/lib` of **each of the three component images** at build time
   (extra `COPY`/`--build-arg`, or inject into the pack before it is served to `docker build`).
   With the default H2 mode this was never needed, so it is genuinely new. Confirm how (if at all)
   the current all-in-one MySQL profile supplies the driver and reuse that path.
2. **MySQL readiness gate** — wait for `mysqld` to accept connections *and* the init scripts to
   complete before booting APIM (a bare port-open check races the schema load). Testcontainers'
   `MySQLContainer` handles this; a plain `GenericContainer` needs an explicit log/health wait.
3. **First-boot schema contention** — TM/ACP/GW connecting to a freshly-initialized shared schema
   concurrently. Ordered boot (ACP first) mitigates most of it.
4. **Driver-in-image reproducibility** — the connector version becomes part of the image; pin it
   alongside the `#master` Dockerfile pin. (dev-setup uses `mysql-connector-j-8.4.0.jar`.)
5. **`latin1` charset + case-sensitive collation are mandatory** — WSO2 DBs must be
   `CREATE DATABASE … CHARACTER SET latin1 COLLATE latin1_bin` (utf8mb4 overruns InnoDB's index
   key-length limit on several APIM tables; `latin1_bin` is case-sensitive as WSO2 requires — latin1's
   default `latin1_swedish_ci` is case-insensitive and must not be used). Both must be set per-database
   in the create step. *(New — from dev-setup + WSO2 collation requirement.)*
6. **Raise `max_connections`** — three nodes each with `pool_options.maxActive=100` (the basic overlay)
   can demand ~300 connections; MySQL's default `151` gets exhausted mid-suite. dev-setup ships a
   `my.cnf` with `max_connections=1000` + `skip-name-resolve=1`; replicate it. *(New — from dev-setup.)*
7. **Prefer fresh ephemeral MySQL per cluster (no persistent volume)** — a clean container per block
   re-runs the init scripts and starts from a pristine schema, giving isolation for free and avoiding
   the dev-setup's `DROP DATABASE`/`--clean` volume dance. Fold the dev-setup's separate `--seed` step
   into first-boot by ordering the DDL inside `/docker-entrypoint-initdb.d`.
8. **Verified image + JDBC params:** `mysql:8.4.0-oraclelinux8`; URLs carry
   `autoReconnect=true&allowPublicKeyRetrieval=true&useSSL=false` (`allowPublicKeyRetrieval` is required
   for MySQL 8 `caching_sha2_password` over a plaintext link). DB names are arbitrary but must match the
   `$env`-injected URLs.

---

## 6. What stays untouched vs what changes

**Untouched (the contract holds):**

- ✅ `testng-v2.xml` — no changes; all six blocks boot a multi-container cluster instead of one
  container.
- ✅ All `.feature` files and step defs calling `getBaseUrl()` / `getBaseGatewayUrl()`.
- ✅ `tomlExtraOverlayPath` — feature overlays still merge onto the ACP node (their targets —
  custom auth header, app sharing, token persistence — are ACP/GW config).

**Framework-internal changes (allowed — not the XML):**

1. **New `DistributedApimCluster`** + MySQL container + three role-wiring TOML overlays + the pom
   build execs (§3a, §5, §7). Bulk of the work.
2. **Gateway-webapp poll fix.** `getGatewayHealthCheckURL(baseUrl)` and
   `getAPIArtifactDeployedInGatewayURL(baseUrl,…)` (`BaseSteps.java:766,788`,
   `ServerReadiness.java`) build `baseUrl + "api/am/gateway/v2/…"`. In all-in-one that webapp
   shares 9443 with the servlet; **distributed, it lives on the *gateway* node's 9443**, not the
   ACP's. Retarget these to `getGatewayMgmtHttpsUrl()` (publish a 4th context key, e.g.
   `gatewayMgmtUrl`). Small, localized.
3. **DB isolation moves from per-container to per-cluster** — the three nodes of one cluster share
   one MySQL/schema set; distinct blocks/clusters get distinct MySQL containers (one per cluster).
4. **`ServerLifecycleSteps` restart** (server-restart block) targets ACP's ServerAdmin SOAP on
   `baseUrl` — correct; verify the GW re-syncs after ACP returns.

---

## 7. Build-pipeline changes required

In `tests-common/testcontainers/pom.xml` and `integration-v2/pom.xml`:

1. **Serve all three component `target/` dirs** to the build daemon (point the `http.server` at a
   common ancestor, or run one server per component zip). The zips live under three different
   `distribution/product/target` dirs.
2. **Add three `build-*-docker-image` execs** mirroring the existing one, each targeting
   `dockerfiles/ubuntu/apim-{acp,tm,universal-gw}` with the component `WSO2_SERVER` /
   `WSO2_SERVER_DIST_URL`, plus the MySQL-driver packaging step (§5.1).
3. **Add three image-name properties** (`acp.docker.image.name`, `tm.docker.image.name`,
   `gw.docker.image.name`) exported to the test JVM as system properties, read by
   `DistributedApimCluster` (parallel to today's `apim.docker.image.name`).
4. **Dist build must produce all three zips first** — the tests-skipped reactor build already does
   this (confirmed); the CI "build dist" step must build the component modules too (not just
   `all-in-one-apim`).
5. **Optional but recommended: pin `#master`** to a tag/SHA for reproducibility (applies to all
   four images; orthogonal to this work). Today every image floats on `wso2/docker-apim`
   `master`.

---

## 8. Principal risks (ranked)

1. **Event-hub artifact sync latency & reliability (highest).** Publish→gateway is now async over
   JMS (5672) instead of in-process. The framework already polls
   `getAPIArtifactDeployedInGatewayURL(...)` before invoking — but it must poll the **gateway**
   node (§6.2), and flakiness/timeouts rise. The `gateway`, `keymanager`, and
   `custom-auth-sharing` blocks are most exposed.
2. **Resource cost.** Now **4 containers per block** (3 APIM + 1 MySQL) + the shared
   NodeAppServer → up to **~24 containers** at 6 parallel blocks. CI sizing or reduced block
   parallelism required; wall-clock rises. MySQL is comparatively light but must be budgeted.
3. **Shared-DB availability & schema-init timing.** The cluster is dead until MySQL is up and the
   `mysql.sql` DDL is loaded; the connector-driver packaging (§5.1) is a hard build prerequisite.
4. **Inter-node TLS / hostname verification.** Nodes talk HTTPS with the default self-signed cert
   (CN=localhost) while addressed as `acp`/`gateway`/`trafficmanager`. Hostname verification must
   be tolerant (confirm in overlays) or event-hub/keymanager calls fail.
5. **Composite readiness.** "Cluster ready" = ACP up **and** GW event-hub-connected **and** TM
   accepting throttle connections **and** MySQL loaded. A port-open gate admits tests too early.
6. **Floating `#master` (now ×3 images + driver version)** — reproducibility risk multiplied;
   mitigated by pinning (§7.5).

---

## 9. Effort estimate

| Workstream | Effort |
|-----------|--------|
| `DistributedApimCluster` + network/alias/port wiring | 3–5 days |
| Three role-wiring TOML overlays + `$env` injection + TLS/hostname config | 3–5 days |
| Build pipeline: 3 image execs + zip serving + properties + CI dist step | 2–3 days |
| MySQL container + schema-init wiring + driver-into-image packaging | 2–3 days |
| Gateway-webapp URL fix + 4th context key | 1 day |
| Per-cluster DB provisioning/isolation | 1–2 days |
| Composite readiness + event-hub-connected probe | 2–3 days |
| Stabilization of gateway-invocation blocks (sync flakiness) | **open-ended — largest unknown** |

**Rough total:** ~3–4 focused weeks to a passing run, plus a stabilization tail dominated by
risk #1.

---

## 10. Recommendation

1. **Spike first (make-or-break, cheap):** build the three component images by hand from the
   existing zips (with the MySQL driver packaged in), run them on one shared network alongside a
   **real shared MySQL container** with the `mysql.sql` schema loaded, wire them with hand-written
   overlays, and confirm an API published on the ACP becomes invocable through the GW (i.e.,
   event-hub sync + throttle + shared DB all work). H2 would mask exactly the coordination
   behavior the spike exists to validate. This validates risks #1/#3/#4 before any framework code.
2. If green, build `DistributedApimCluster` behind the *same* `BlockLifecycleListener` contract,
   confining the diff to `tests-common/testcontainers` + the pom + three helper files.
3. Add a **selector** (system property) so the same suite can boot all-in-one *or* distributed —
   giving a direct A/B for parity debugging and keeping all-in-one (H2) as the fast default.
4. Pin `wso2/docker-apim` off `master` for all images (and the MySQL driver version) while you are
   in the pom anyway.

The architecture points the same direction: topology-agnostic XML, ephemeral-host-port pattern,
shared network, `$env` overlay merge, a build recipe that generalizes 1→3 with no new tooling, and
shipped `mysql.sql` DDL. Engineering risk is concentrated in **cross-node event-hub sync fidelity,
shared-DB bring-up, and test timing** — not in "starting more containers" — so the spike and
stabilization budget should target exactly that.

---

## Appendix — key source references

- `tests-common/testcontainers/.../DynamicApimContainer.java` — baseline dynamic container.
- `tests-common/testcontainers/.../ContainerNetwork.java` — `SHARED_NETWORK`.
- `tests-integration/.../listeners/BlockLifecycleListener.java` — per-block boot + URL publish.
- `tests-integration/.../runners/block/BaseBlockRunner.java` — boot-failure guard.
- `tests-common/integration-test-utils/.../Constants.java` — canonical ports (9443/9763/8243/8280), endpoint paths.
- `tests-integration/.../resources/testng-v2.xml` — the six blocks (unchanged contract).
- `tests-common/testcontainers/pom.xml` — image build execs (`#master` docker-apim).
- `integration-v2/pom.xml` — image-name properties; H2 (default) vs MySQL (alt) DB env blocks.
- Component zips: `{api-control-plane,gateway,traffic-manager}/modules/distribution/product/target/*.zip`.
- Component Dockerfiles: `wso2/docker-apim` → `dockerfiles/ubuntu/{apim-acp,apim-tm,apim-universal-gw}`.
- DDL scripts (in each pack): `dbscripts/apimgt/mysql.sql`, `dbscripts/mysql.sql`.
