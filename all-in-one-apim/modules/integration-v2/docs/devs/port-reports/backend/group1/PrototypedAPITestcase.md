# Port report — Prototype (group1, publisher / gateway / devportal)

Legacy: `PrototypedAPITestcase` (4), `APIM574…ChangeStatusToPrototyped` (transition), `APIM23`/`APIM24` (devportal visibility). PROTOTYPED is a lifecycle STATE, so it folds into existing capabilities (no new tag).
Delivered: `publisher/api_lifecycle.feature` (`PublisherLifecycleRunner` — **no new runner**), `@cap:publisher @feat:api-lifecycle`. **Verified** (transition scenario ×2 tenant; lifecycle runner 17/17).

## Method dispositions
| Legacy | Disposition | Where / note |
|---|---|---|
| APIM574 / PrototypedAPITestcase — transition to PROTOTYPED | ✅ ported (×2) | create → "Deploy as a Prototype" → 200, lifecycle status **Prototyped** (`api_lifecycle.feature`) |
| PrototypedAPITestcase — **keyless gateway invocation** of a prototyped API | ⏭️ **increment 2 (needs investigation)** | see finding below |
| PrototypedAPITestcase — demote PROTOTYPED → CREATED + invoke | ⏭️ increment 2 | depends on the keyless-invoke property |
| PrototypedAPITestcase — inline OAS2/OAS3 mock | ⏭️ increment 2 | mock-implementation generation (publisher-plane) |
| APIM23 / APIM24 — prototyped API visible in devportal | ⏭️ increment 2 | devportal visibility of a PROTOTYPED api — uncertain on 4.7.0 (not verified) |

## Finding (verify-first) — keyless prototype invocation does NOT reproduce on 4.7.0
Legacy prototyped APIs are invocable at the gateway **without a subscription/token**. Probed both orders on 4.7.0 — deploy-then-prototype AND prototype-then-deploy — and **both returned 401 `900902` "Missing Credentials"** for a no-auth GET against a prototyped, deployed API (real HTTP backend). So the keyless-prototype runtime property does not hold with the straightforward 4.x revision flow; prototype semantics changed (possibly it now requires the inline mock implementation, or a config). Rather than force it or enshrine a wrong expectation, the keyless invoke is deferred to increment 2 for deeper investigation. (The `I invoke … without authentication until …` step was added and is retained — it is also the natural 401 negative for a normal secured API.)

## Net
The publisher-plane PROTOTYPED **state transition** is ported and verified ×2 (the api-lifecycle fold). The runtime keyless-invoke property, demote, inline-mock, and devportal visibility are deferred to increment 2 — the keyless invoke specifically because it behaves differently on 4.7.0 (401) and needs investigation before a faithful port.
