package org.tkit.onecx.ai.bff.rs.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.tkit.onecx.ai.bff.rs.AbstractTest;
import org.tkit.quarkus.log.cdi.LogService;

import gen.org.tkit.onecx.ai.management.bff.client.model.*;
import gen.org.tkit.onecx.ai.management.bff.rs.internal.model.*;
import io.quarkiverse.mockserver.test.InjectMockServerClient;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;

@QuarkusTest
@LogService
@TestHTTPEndpoint(DispatchRestController.class)
class DispatchRestControllerTest extends AbstractTest {

    @InjectMockServerClient
    MockServerClient mockServerClient;

    KeycloakTestClient keycloakTestClient = new KeycloakTestClient();
    static final String MOCK_ID = "MOCK";

    @AfterEach
    void resetMockserver() {
        try {
            mockServerClient.clear(MOCK_ID);
        } catch (Exception _) {
            // mockId not existing
        }
    }

    @Test
    void chatTest() {
        var message = new ChatMessageDTO();
        message.setConversationId("conversation-1");
        message.setType(ChatMessageDTO.TypeEnum.USER);
        message.setMessage("hello");

        var request = new ChatRequestDTO();
        request.setChatMessage(message);

        ChatMessageInternal fakeData = new ChatMessageInternal()
                .conversationId("conversation-1")
                .type(ChatMessageInternal.TypeEnum.ASSISTANT)
                .message("ok");

        mockServerClient.when(
                request().withPath("/internal/dispatch/chat")
                        .withMethod(HttpMethod.POST))
                .withPriority(100)
                .withId(MOCK_ID)
                .respond(httpRequest -> response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(JsonBody.json(fakeData)));

        var response = given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .body(request)
                .post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .as(ChatMessageDTO.class);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(fakeData.getMessage(), response.getMessage());
        Assertions.assertEquals(fakeData.getType().name(), response.getType().name());
        Assertions.assertEquals(fakeData.getConversationId(), response.getConversationId());
    }

    @Test
    void chatTest_clientWebApplicationException_400() {
        var message = new ChatMessageDTO();
        message.setConversationId("conversation-1");
        message.setType(ChatMessageDTO.TypeEnum.USER);
        message.setMessage("hello");

        var request = new ChatRequestDTO();
        request.setChatMessage(message);

        mockServerClient.when(
                request().withPath("/internal/dispatch/chat")
                        .withMethod(HttpMethod.POST))
                .withPriority(100)
                .withId(MOCK_ID)
                .respond(response()
                        .withStatusCode(Response.Status.BAD_REQUEST.getStatusCode()));

        given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .body(request)
                .post()
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void chatTest_constraint_violations_missing_body_400() {
        given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .post()
                .then()
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void chatTest_withFullRequest_200() {
        var message = new ChatMessageDTO();
        message.setConversationId("conversation-1");
        message.setType(ChatMessageDTO.TypeEnum.USER);
        message.setMessage("hello");

        var requestContext = new RequestContextDTO();
        requestContext.setAgentId("agent-1");

        var conversation = new ConversationDTO();
        conversation.setConversationId("conversation-1");

        var request = new ChatRequestDTO();
        request.setChatMessage(message);
        request.setRequestContext(requestContext);
        request.setConversation(conversation);

        ChatMessageInternal fakeData = new ChatMessageInternal()
                .conversationId("conversation-1")
                .type(ChatMessageInternal.TypeEnum.ASSISTANT)
                .message("ok");

        mockServerClient.when(
                request().withPath("/internal/dispatch/chat")
                        .withMethod(HttpMethod.POST))
                .withPriority(100)
                .withId(MOCK_ID)
                .respond(httpRequest -> response()
                        .withStatusCode(Response.Status.OK.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(JsonBody.json(fakeData)));

        var response = given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .body(request)
                .post()
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract()
                .as(ChatMessageDTO.class);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(fakeData.getMessage(), response.getMessage());
    }

    @Test
    void chatTest_500_forwardsStatus() {
        var message = new ChatMessageDTO();
        message.setConversationId("conversation-1");
        message.setType(ChatMessageDTO.TypeEnum.USER);
        message.setMessage("hello");

        var request = new ChatRequestDTO();
        request.setChatMessage(message);

        mockServerClient.when(
                request().withPath("/internal/dispatch/chat")
                        .withMethod(HttpMethod.POST))
                .withPriority(100)
                .withId(MOCK_ID)
                .respond(response()
                        .withStatusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));

        // SVC status is forwarded unchanged for dispatch
        given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .body(request)
                .post()
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    void chatTest_svcProblem_404_forwardsErrorCode() {
        assertSvcProblemForwarded(Response.Status.NOT_FOUND, "AGENT_NOT_FOUND", "Agent not found");
    }

    @Test
    void chatTest_svcProblem_502_forwardsErrorCode() {
        assertSvcProblemForwarded(Response.Status.BAD_GATEWAY, "RUNTIME_ERROR", "AI runtime returned an error");
    }

    @Test
    void chatTest_svcNonJson_503_forwardsStatusOnly() {
        mockServerClient.when(
                request().withPath("/internal/dispatch/chat")
                        .withMethod(HttpMethod.POST))
                .withPriority(100)
                .withId(MOCK_ID)
                .respond(response()
                        .withStatusCode(Response.Status.SERVICE_UNAVAILABLE.getStatusCode())
                        .withContentType(MediaType.TEXT_PLAIN)
                        .withBody("upstream down"));

        given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .body(chatRequest())
                .post()
                .then()
                .statusCode(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
    }

    private void assertSvcProblemForwarded(Response.Status status, String errorCode, String detail) {
        var problem = new ProblemDetailResponseInternal()
                .errorCode(errorCode)
                .detail(detail);

        mockServerClient.when(
                request().withPath("/internal/dispatch/chat")
                        .withMethod(HttpMethod.POST))
                .withPriority(100)
                .withId(MOCK_ID)
                .respond(response()
                        .withStatusCode(status.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(JsonBody.json(problem)));

        var response = given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .body(chatRequest())
                .post()
                .then()
                .statusCode(status.getStatusCode())
                .extract()
                .as(ProblemDetailResponseDTO.class);

        Assertions.assertEquals(errorCode, response.getErrorCode());
        Assertions.assertEquals(detail, response.getDetail());
    }

    private static ChatRequestDTO chatRequest() {
        var message = new ChatMessageDTO();
        message.setConversationId("conversation-1");
        message.setType(ChatMessageDTO.TypeEnum.USER);
        message.setMessage("hello");

        var request = new ChatRequestDTO();
        request.setChatMessage(message);
        return request;
    }

    @Test
    void chatTest_201_withBody() {
        // 201 is a 2xx code — REST client does NOT throw
        // This covers the readEntity + return line for non-200 success
        var message = new ChatMessageDTO();
        message.setConversationId("conversation-1");
        message.setType(ChatMessageDTO.TypeEnum.USER);
        message.setMessage("hello");

        var request = new ChatRequestDTO();
        request.setChatMessage(message);

        ChatMessageInternal fakeData = new ChatMessageInternal()
                .conversationId("conversation-1")
                .type(ChatMessageInternal.TypeEnum.ASSISTANT)
                .message("ok");

        mockServerClient.when(
                request().withPath("/internal/dispatch/chat")
                        .withMethod(HttpMethod.POST))
                .withPriority(100)
                .withId(MOCK_ID)
                .respond(httpRequest -> response()
                        .withStatusCode(Response.Status.CREATED.getStatusCode())
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(JsonBody.json(fakeData)));

        var response = given()
                .when()
                .auth().oauth2(keycloakTestClient.getAccessToken(ADMIN))
                .header(APM_HEADER_PARAM, ADMIN)
                .contentType(APPLICATION_JSON)
                .body(request)
                .post()
                .then()
                .statusCode(Response.Status.CREATED.getStatusCode())
                .extract()
                .as(ChatMessageDTO.class);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(fakeData.getMessage(), response.getMessage());
    }
}
