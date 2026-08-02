package com.victor.appointmentmanager.api.modules.whatsapp.client;

import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.modules.whatsapp.exception.WhatsAppApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * No realiza llamadas reales a Meta: intercepta el RestClient mediante MockRestServiceServer.
 */
class MetaWhatsAppClientTest {

    private static final String BASE_URL = "https://graph.example.com";
    private static final String GRAPH_VERSION = "v21.0";
    private static final String ACCESS_TOKEN = "test-access-token";

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private MetaWhatsAppClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new MetaWhatsAppClient(builder, ACCESS_TOKEN, BASE_URL, GRAPH_VERSION);
    }

    @Test
    void buildsTextRequestUrlAndAuthorizationHeaderCorrectly() {
        mockServer.expect(requestTo(BASE_URL + "/" + GRAPH_VERSION + "/PHONE_ID_1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andRespond(withSuccess(
                        "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.OUT1\"}]}",
                        MediaType.APPLICATION_JSON));

        client.sendTextMessage("PHONE_ID_1", "595981000000", "Hola");

        mockServer.verify();
    }

    @Test
    void buildsTextMessageRequestBodyCorrectly() {
        mockServer.expect(requestTo(BASE_URL + "/" + GRAPH_VERSION + "/PHONE_ID_1/messages"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.to").value("595981000000"))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Hola, ¿cómo estás?"))
                .andRespond(withSuccess(
                        "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.OUT2\"}]}",
                        MediaType.APPLICATION_JSON));

        client.sendTextMessage("PHONE_ID_1", "595981000000", "Hola, ¿cómo estás?");

        mockServer.verify();
    }

    @Test
    void returnsStructuredResponseOnSuccess() {
        mockServer.expect(requestTo(BASE_URL + "/" + GRAPH_VERSION + "/PHONE_ID_1/messages"))
                .andRespond(withSuccess(
                        "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.OUT3\"}]}",
                        MediaType.APPLICATION_JSON));

        WhatsAppSendMessageResponse response = client.sendTextMessage("PHONE_ID_1", "595981000000", "Hola");

        assertThat(response.getMessagingProduct()).isEqualTo("whatsapp");
        assertThat(response.getMessages()).hasSize(1);
        assertThat(response.getMessages().get(0).getId()).isEqualTo("wamid.OUT3");
    }

    @Test
    void throwsWhatsAppApiExceptionOnHttpErrorResponse() {
        mockServer.expect(requestTo(BASE_URL + "/" + GRAPH_VERSION + "/PHONE_ID_1/messages"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid parameter\"}}"));

        assertThatThrownBy(() -> client.sendTextMessage("PHONE_ID_1", "595981000000", "Hola"))
                .isInstanceOf(WhatsAppApiException.class);
    }

    @Test
    void throwsWhatsAppApiExceptionOnConnectionError() {
        mockServer.expect(requestTo(BASE_URL + "/" + GRAPH_VERSION + "/PHONE_ID_1/messages"))
                .andRespond(request -> {
                    throw new IOException("Simulated connection error");
                });

        assertThatThrownBy(() -> client.sendTextMessage("PHONE_ID_1", "595981000000", "Hola"))
                .isInstanceOf(WhatsAppApiException.class);
    }

    @Test
    void doesNotLeakRawErrorDetailsInExceptionMessage() {
        mockServer.expect(requestTo(BASE_URL + "/" + GRAPH_VERSION + "/PHONE_ID_1/messages"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid OAuth access token - " + ACCESS_TOKEN + "\"}}"));

        assertThatThrownBy(() -> client.sendTextMessage("PHONE_ID_1", "595981000000", "Hola"))
                .isInstanceOf(WhatsAppApiException.class)
                .hasMessageNotContaining(ACCESS_TOKEN);
    }

}
