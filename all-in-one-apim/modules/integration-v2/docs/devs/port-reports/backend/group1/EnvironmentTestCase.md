# Port report — `EnvironmentTestCase` (group1, admin)

Legacy: `.../restapi/admin/EnvironmentTestCase.java` — Factory ×2 (super+tenant). 18 `@Test`.
Delivered: `admin/gateway_environments.feature` (`AdminGatewayEnvironmentsRunner`, Admin block), `@cap:admin @feat:environments`. **Verified 4/4.**

## Method dispositions
| Method(s) | Disposition | Where / note |
|---|---|---|
| testAddGatewayEnvironmentSingleVHost, testGetGatewayEnvironments, testUpdateEnvironment | ✅ ported | CRUD scenario (×2): create (single vhost) → list → retrieve → update-description |
| (delete) + delete-missing | ✅ ported | delete → 200, delete-again → 404 |
| testAddingGatewayEnvironmentWithGatewayType | ✅ ported | gatewayType APK (APK-style vhost: httpContext + no ws/wss ports) → 201, contains "APK" |
| testAddGatewayEnvironmentWithoutVHost | ✅ ported | negative: no vhost → 400 |
| testAddingGatewayEnvironmentNameWithSpecialCharacters | ✅ ported | negative: special-char name → 400 |
| testAddingGatewayEnvironmentWithoutDisplayName | ✅ ported | negative: no display name → 400 |
| testAddAlreadyExistingEnvironment | ✅ ported | negative: create "Default" (built-in) → 400 |
| testAddGatewayEnvironmentMultipleVHosts | ⏭️ **increment 2** | multiple vhosts (needs vhost-list control) |
| testAddingGatewayEnvironmentWithMultipleVhostsWithSameHostName | ⏭️ increment 2 | negative: duplicate hostname |
| testAddingGatewayEnvironmentWithVhostsHavingSpecialCharacters | ⏭️ increment 2 | negative: special-char vhost |
| testUpdateEnvironmentByRemovingVHost | ⏭️ increment 2 | update vhost list |
| testDeployApiRevisionWithVhost | ⏭️ increment 2 | deploy a revision to a vhost (needs an API) |
| testDeleteEnvironmentWithAPIRevisions / …AfterUndeployingRevisions | ⏭️ increment 2 | delete env with/without deployed revisions (needs an API deployed to it) |
| testValidateDevportalAPIAndSwaggerResponse | ⏭️ increment 2 | devportal swagger validation (needs an API) |
| testGatewayPermissions | ⏭️ increment 2 | env permissions (needs a 2nd user/role) |
| testGetGatewayInstancesInDefaultEnvironment | ⏭️ increment 2 | get-instances of the Default env (needs default-env-id lookup) |

## Finding
An **APK gateway type** rejects the standard vhost (ws/wss ports) with `900967` "Unsupported Vhost
Configuration" (500); it requires a vhost with an `httpContext` and **no ws/wss ports** — encoded in the create
step (typed gateways get an APK-style vhost).

## Net
Core gateway-environment **CRUD (×2)** + gatewayType + the create-validation negatives (400) + delete-missing
(404) ported. The vhost-list variants + API-dependent (deploy/delete-with-revisions/devportal-swagger) +
permissions + default-env get-instances → increment 2.
