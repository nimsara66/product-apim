@cleanup
Feature: Workshop 4 - External Identity Server as Key Manager

  The same invocation goal as Workshop 1, with one difference that changes everything: the access token is
  minted by an EXTERNAL WSO2 Identity Server 7.x, not by APIM.

  Read the block config in the suite XML first. The listener boots the IS container and establishes cert
  trust - that is INFRASTRUCTURE. But registering IS as a key manager is ADMIN PRODUCT BEHAVIOUR, so it
  happens below, as a step, acting as an actor. That boundary is the point of this segment: two utility
  classes were deleted from this codebase for putting product calls in a listener, because behaviour hidden
  in infrastructure is invisible to the coverage tree and its resources cannot be swept by the cleanup hook.

  Follow the token: "generate client credentials" makes APIM perform DCR into IS and hand back IS client
  credentials; the token is then requested FROM IS; and the gateway accepts it because the key manager was
  registered with self-validation and IS's JWKS endpoint.

  @cap:admin @feat:external-key-manager @type:smoke @dep:gateway
  Scenario: Obtain a token from the external Identity Server and invoke a deployed API
    Given The system is ready
    And I have valid access tokens as "admin"

    # Product behaviour, not block setup: IS is registered as a third-party key manager via the admin REST API.
    When I create a key manager from payload "artifacts/payloads/keymanagers/wso2is7.json" as "workshopKm"

    # Identical to Workshop 1 from here to the subscription - the publisher plane is unaffected by the KM.
    And I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I retrieve the "apis" resource with id "createdApiId"
    And I extract response field "context" and store it as "apiContext"

    When I put JSON payload from file "artifacts/payloads/create_apim_test_app_oauth.json" in context as "createAppPayload"
    And I create an application with payload "createAppPayload"
    Then The response status code should be 201

    # The key step: naming the registered key manager makes APIM do DCR into IS, so the credentials returned
    # are an IS OAuth client, not an APIM one.
    When I put the following JSON payload in context as "generateApplicationKeysPayload"
    """
    {"keyType": "PRODUCTION", "keyManager": "{{workshopKmName}}", "grantTypesToBeSupported": ["client_credentials"]}
    """
    And I generate client credentials for application id "createdAppId" with payload "generateApplicationKeysPayload"
    Then The response status code should be 200

    When I put the following JSON payload in context as "apiSubscriptionPayload"
    """
    {"applicationId": "{{applicationId}}", "apiId": "{{apiId}}", "throttlingPolicy": "Unlimited"}
    """
    And I subscribe to API "createdApiId" using application "createdAppId" with payload "apiSubscriptionPayload" as "subscriptionId"
    Then The response status code should be 201

    # The token comes from IS, over the shared docker network at https://wso2is:9443.
    When I request an OAuth access token from the external key manager using client credentials grant
    Then The response status code should be 200

    # ...and the gateway accepts it, exactly as it accepted the APIM-issued token in Workshop 1.
    And I invoke the API at gateway context "{{apiContext}}/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 60 seconds
    Then The response status code should be 200
