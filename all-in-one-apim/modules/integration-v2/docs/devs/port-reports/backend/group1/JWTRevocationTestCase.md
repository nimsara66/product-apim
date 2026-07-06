# Port report — `JWTRevocationTestCase` (group1)

Legacy source: `modules/integration/tests-integration/tests-backend/src/test/java/org/wso2/am/integration/tests/jwt/JWTRevocationTestCase.java`
Factory: no (single, super tenant). Restart: no.

## 1. What the legacy test does
| # | Method | Flow / assertion |
|---|--------|------------------|
| 1 | `testJWTTokenRevocation` | create JWT-type app → subscribe → get token → invoke → **200**; POST `oauth2/revoke` (Basic client auth) → **200**; invoke again → token is no longer valid (invocation rejected) |

Concern: **access-token revocation** — a revoked token can no longer invoke at the gateway.

## 2. v2 coverage
`key-manager/token_revocation.feature` — `Scenario Outline: Revoke an access token and verify invocation is
blocked as <actor>` (`@legacy:RevokeTokenTestCase`, ×2 tenant): obtain a token, invoke → 200, revoke, then
invoke **until 401** and assert rejected. v2 applications default to **JWT-format tokens** (see
`token_issuance`), so this exercises the JWT-token revocation path — the same runtime behaviour, and ×2 tenant
(broader than the legacy's super-only).

## 3. Disposition — ✅ COVERED (no port)
Redundant with `token_revocation.feature`. The distinct "JWT is self-contained, so revocation relies on the
gateway's revoked-token list" nuance produces the **same black-box outcome** (revoke → invocation blocked),
which v2 already asserts.

**Not to be confused with** `MicroGWJWTRevocationTestCase` (also group1) — that separate class asserts the
revoked JTI lands in **ETCD / a JMS topic** (internal transport), which is ⚪ SKIP (not a black-box property;
micro-gateway specific). See `group1.md` §13.

## 4. Coverage summary
| Legacy behaviour | Covered in v2? | Where / note |
|---|---|---|
| Invoke with a valid token → 200 | ✅ | `token_revocation.feature` |
| Revoke token → 200 | ✅ | `token_revocation.feature` |
| Revoked token → invocation blocked | ✅ | `token_revocation.feature` (invoke until 401) |
| JWT-format token specifically | ✅ | v2 apps default to JWT tokens |
| ×2 tenant | ✅ (extension) | v2 runs super + tenant1; legacy was super-only |
