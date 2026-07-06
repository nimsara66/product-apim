# Backend suite port — master index

Porting the legacy backend suite (`modules/integration/tests-integration/tests-backend/src/test/resources/testng.xml`)
into `integration-v2`, thoroughly and without missing scenarios, adapted to the concurrent block model and
capability separation.

Legacy suite size (from `testng.xml`): **~211 active + ~56 commented ≈ 267** class references across `group1..4`.

## Cross-cutting backlog
- [increment-2-backlog.md](increment-2-backlog.md) — deferred group1 sub-scenarios (duplicate-name 409, role/permission enforcement, policy↔API assignment, product variants, env vhost/deploy variants), grouped by shared enabler. These are tracked to port, not dropped.

## Group tracking docs
| Group | Doc | Status |
|---|---|---|
| group1 | [group1.md](group1.md) | 🔨 **Wave A in progress** — ✔ API Products increment 1 (14/14); ✔ throttle-policy CRUD breadth (8/8); ✔ gateway environments (4/4); ✔ **governance rulesets/policies/compliance (20/20)**; ✔ **key-manager config (26/26)**; ✔ **create-validation matrix (9 new)**; ✔ **deny/block policies (8/8)**; ✔ **tenant config (8/8)**; ✔ **doc-type × source matrix (4 new)**; ✔ **OAS validation (12 new)**; ✔ **consumer-secret / multiple client secrets (4/4)**; ✔ **small publisher endpoints (linter/tiers/url-encoded-name, 8 new)**; ✔ **prototype transition (2 new; keyless-invoke deferred)**; ✔ **org visibility B2B (methods 1–7 + disallowed-tier 403, ×2 tenant)**; ✔ **create-negatives: malformed-context 400 + duplicate-context 409 (APIM18)**; ✔ **system-scope role-aliases (APISystemScopes, ×2)**; ✔ **delete-attached-tier then API-update (×2)**; ✔ **throttle-policy export/import (4 types × new/update/conflict; 7/7)**; per-class reports in [group1/](group1/). **Wave A standalone tail is CLOSED** — everything portable-now is done. Rest of the tail → increment 2: app-search, APIM18 archive-import + sandbox-only/internal-key, ApplicationScopeValidation/CustomAttributes (data-file flows), MandatoryProperties + PluggableVersioning (config, PORT+VERIFY); ⚪ dropped-as-dup/hollow: APIMANAGER4877 (dup of scopes.feature), APIResourceWithTemplate pub-plane (dup; uri-encoding invoke → Wave B), DocAPIParameterTampering (hollow). |
| group2 | — | not started |
| group3 | — | not started |
| group4 | — | not started |

## Disposition legend (used in every group doc)
| Mark | Meaning |
|---|---|
| ✅ COVERED | Already in v2 (dedup). No port; residual gaps noted if any. |
| 🟡 EXTEND | Partially covered — port only the delta into the existing feature. |
| 🟢 PORT | Genuine gap, API-driven & deterministic — port it. |
| 🔵 PORT+VERIFY | Port, but needs a live-container behaviour check first (enforcement/infra uncertainty) — the bandwidth/custom-Siddhi lesson. |
| 🏗 NEW-HARNESS | Needs new v2 framework capability (WS client, AI/MCP backend, remote log sink, browser grant flow, mutual-SSL certs) before it can be ported. |
| 🧩 NEW-CAP | Needs a `capability-map.yml` vocabulary addition first. |
| ⚪ SKIP | Degenerate / duplicate / infra-internal / not black-box portable — with reason. |

## Standing principles (carried from the server-restart port)
1. **Dedup across the whole suite**, not per-group — the same capability is scattered across group1..4.
2. **Omit restarts that assert no runtime property** (family-wide: the restart was almost always incidental).
3. **×2 tenant** where the concern is tenant-agnostic and cheap; **super-only** where the feature is admin-global (e.g. custom Siddhi rules → tenant create is 403).
4. **Verify-in-a-live-container before writing** tests for uncertain enforcement/infra.
   - **4a. Don't trust legacy status codes / skip logic — probe the current pack.** A legacy assertion of 500 or a `SkipException` guard (e.g. skip-on-900916) describes an *old* runtime, not 4.7.0. Before recording a "500 / not-portable / config-required" disposition, run it live and use the observed code. (Caught: create-without-tier/endpoint/resources → actually 201; deny malformed-IP → 500 confirmed, invalid-user → 201.)
   - **4b. An "overlay / config required" claim must be proven by running the DEFAULT pack WITHOUT it** — and cross-checked in the distribution `conf/default.json` — not inferred from legacy overlay files or skip guards. (Caught: multiple-client-secrets is `enable=true` by default in 4.7.0 → the overlay + dedicated block were redundant and removed.)
   - **4c. Read the server-side stacktrace before recording a "product returns 500".** A generic error body (e.g. `900967 "General Error"`) is not self-explanatory — it can be a garbage-INPUT path, not the behaviour under test. Tail the container's `wso2carbon.log` (it streams into the mvn output) for the actual reason. (Caught: org-policy disallowed-tier subscribe was recorded as "500 → deferred"; the log showed *"Failed to retrieve the API {{apiId}}"* — an unresolved `{{placeholder}}` in the test payload, not a tier-denial. With the id resolved it returns a clean **403**; the negative was un-deferred and ported. Corollary: if a legacy test's CI was green on a clean 4xx, distrust a v2 "500" reading until you've reproduced legacy's exact setup.)
5. **Isolation-safety** on the shared container (unique names; global rules keyed on unique attributes).
6. **Skip degenerate/commented tests but salvage their ideas** where valuable.
7. **Consolidate repetitive matrices** into `Scenario Outline`s (e.g. 6 key-manager types → one outline), never 1:1 method→scenario when a table row captures it.
8. **Weight/placement awareness** — invocation-heavy scenarios starve at K=2 (see the 5 full-suite failures); flag them and place/scale accordingly.

## Per-class status lifecycle
⬜ Analysed → ✅ Approved → 🔨 Implemented → ✔ Verified. (group1 rows are all ⬜ pending your approval.)
