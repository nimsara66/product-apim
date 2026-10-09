@cleanup
Feature: Backend JWT claims for a federated OIDC resource owner

  Exercise the legacy FederatedUserJWTTestCase authorization-code path end to end: APIM redirects the OAuth
  client to an external OIDC Identity Server, the user authenticates there, APIM handles the federated callback,
  the client receives and exchanges an authorization code, and the resulting token invokes an API whose backend
  reflects the gateway-generated JWT. The external Identity Server is a real block-scoped WSO2 IS container.

  @cap:key-manager @feat:backend-jwt @rule:federated-authorization-code @type:regression @dep:gateway @legacy:FederatedUserJWTTestCase
  Scenario Outline: Federated OIDC authorization-code claims reach the backend JWT in <tenant>
    Given The system is ready
    And I have valid access tokens as "<adminActor>"
    And I set the OAuth callback URI for this topology
    And I register the OIDC user-profile claim mappings and scope in tenant "<tenant>"
    And I register an external OIDC IdP for backend JWT in tenant "<tenant>"
    And I have created an api from "artifacts/payloads/create_apim_reflect_api.json" as "fedJwtApiId" and deployed it
    When I publish the "apis" resource with id "fedJwtApiId"
    Then The lifecycle status of API "fedJwtApiId" should be "Published"
    When I retrieve the "apis" resource with id "fedJwtApiId"
    And I extract response field "context" and store it as "fedJwtApiContext"

    When I put JSON payload from file "artifacts/payloads/create_apim_app_with_attribute.json" in context as "fedJwtAppPayload"
    And I set the field "tokenType" to "JWT" in the payload "fedJwtAppPayload"
    And I create an application with payload "fedJwtAppPayload"
    Then The response status code should be 201
    When I retrieve the application with id "createdAppId"
    And I extract response field "name" and store it as "fedJwtAppName"
    When I put the following JSON payload in context as "fedJwtKeys"
    """
    {"keyType":"PRODUCTION","grantTypesToBeSupported":["authorization_code"],"callbackUrl":"{{fedJwtCallback}}"}
    """
    And I generate client credentials for application id "createdAppId" with payload "fedJwtKeys"
    Then The response status code should be 200
    When I configure OAuth consumer key "consumerKey" for federated OIDC authentication in tenant "<tenant>"
    And I put the following JSON payload in context as "fedJwtSub"
    """
    {"applicationId":"{{applicationId}}","apiId":"{{apiId}}","throttlingPolicy":"Gold"}
    """
    And I subscribe to API "fedJwtApiId" using application "createdAppId" with payload "fedJwtSub" as "fedJwtSubId"
    Then The response status code should be 201

    When I obtain an OAuth authorization-code token through the external OIDC IdP
    Then The response status code should be 200
    When I invoke the API at gateway context "{{fedJwtApiContext}}/1.0.0/reflect-headers" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 60 seconds
    Then The response status code should be 200
    # Pin APIM's default backend-JWT claims on the federated flow too. The separate client-credentials JWT scenario
    # exercises these on another grant, while the legacy federated test checks them on this authorization-code path.
    And The reflected backend JWT should contain claim "iss" with value "wso2.org/products/am"
    And The reflected backend JWT should contain claim "subscriber" with value "<adminActor>"
    And The reflected backend JWT should contain claim "applicationname" with value "{{fedJwtAppName}}"
    And The reflected backend JWT should contain claim "applicationtier" with value "Unlimited"
    And The reflected backend JWT should contain claim "keytype" with value "PRODUCTION"
    And The reflected backend JWT should contain claim "givenname" with value "{{fedJwtUser}}"
    And The reflected backend JWT should contain claim "lastname" with value "SSO"
    And The reflected backend JWT should contain claim "mobile" with value "94123456987"
    And The reflected backend JWT should contain claim "telephone" with value "424479772294778"
    And The reflected backend JWT should contain claim "organization" with value "FederatedOrg"
    And The reflected backend JWT should contain claim "emailaddress" with value "{{fedJwtEmail}}"
    And The reflected backend JWT header should contain "typ" with value "JWT"
    And The reflected backend JWT should be signed with algorithm "RS256"
    And The reflected backend JWT should be verifiably signed by the key the gateway JWKS endpoint publishes
    And The reflected backend JWT should not contain claim "wrongclaim"

    Examples:
      | tenant       | suffix       | adminActor        |
      | carbon.super |              | admin             |
      | tenant1.com  | @tenant1.com | admin@tenant1.com |
