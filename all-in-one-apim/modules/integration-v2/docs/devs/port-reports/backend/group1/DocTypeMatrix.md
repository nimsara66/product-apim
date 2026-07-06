# Port report — Documentation type × source matrix (group1, publisher)

Legacy: `APIM611…HowTo` (2), `APIM620…SampleAndSDK` (2), `APIM623…PublicForum` (1), `APIM625…SupportForum` (1), `APIM627…Other` (3, incl. remove), `APIM614…file-source` (4). All Factory ×2.
Delivered: extended `publisher/docs.feature` (`PublisherDocsRunner`, Publisher block — **no new runner**), `@cap:publisher @feat:docs`. **Verified** (2 new scenario definitions ×2 tenant; full docs runner 14/14).

## Gap closed
v2 `docs.feature` already covered HOWTO/INLINE add→retrieve→update→delete across API types (REST/SOAP/WS/GraphQL). The breadth gap was the **doc type** and **source** dimensions. Added two consolidated matrix scenarios (each ×2 tenant, one API per scenario, unique doc names so several docs coexist):
- **All documentation types** — HOWTO, SAMPLES, PUBLIC_FORUM, SUPPORT_FORUM, OTHER added inline to one API → list reflects every type.
- **All document sources** — INLINE, URL (`sourceUrl`), FILE (metadata + multipart content upload) → list reflects every source.

## Method dispositions
| Legacy class(es) | Disposition | Where / note |
|---|---|---|
| APIM611 HowTo (inline/url) | ✅ ported | HOWTO in the type scenario; INLINE + URL in the source scenario |
| APIM620 Sample/SDK | ✅ ported | SAMPLES type |
| APIM623 PublicForum | ✅ ported | PUBLIC_FORUM type |
| APIM625 SupportForum | ✅ ported | SUPPORT_FORUM type |
| APIM627 Other (+remove) | ✅ ported | OTHER type (with `otherTypeName`); remove/delete already covered by the existing docs CRUD scenario |
| APIM614 file-source | ✅ ported | FILE source: create metadata (sourceType FILE) → upload `artifacts/docs/sample-doc.txt` via multipart (field `file`) → 201 |

## New/extended glue
- `I prepare a document named {string} of type {string} with sourceType {string} and content {string}` — builds the doc JSON programmatically, routing content to `inlineContent` (INLINE/MARKDOWN) or `sourceUrl` (URL), setting `otherTypeName` for OTHER, and resolving `${UNIQUE:...}` names so multiple docs coexist on one API.
- `I upload the document file {string} for document {string} of API {string}` — multipart content upload (`Utils.getAPIDocumentContent`, form field `file`).
- Fixture `artifacts/docs/sample-doc.txt`.

## Net
Documentation breadth completed — all 5 doc types + all 3 sources (inline/url/file), ×2 tenant, consolidated into two matrix scenarios reusing the existing docs runner. The MARKDOWN source enum exists but was not in legacy scope; can be added trivially if wanted.
