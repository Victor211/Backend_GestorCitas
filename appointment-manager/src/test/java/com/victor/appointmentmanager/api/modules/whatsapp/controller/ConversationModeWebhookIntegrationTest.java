package com.victor.appointmentmanager.api.modules.whatsapp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.whatsapp.client.WhatsAppClient;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVP 3 - Fase 1: ciclo completo takeover -> release a través del webhook real de WhatsApp (firma
 * HMAC incluida), no de llamadas directas a los servicios. Sustituye {@code AiProvider} y
 * {@code WhatsAppClient} por mocks (mismo patrón que {@code ConversationBookingIntegrationTest})
 * para no depender de OpenAI ni de Cloud API reales; todo lo demás (JWT, JPA/H2, deduplicación,
 * ConversationMessageService, ConversationControlService) es el stack real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-dummy-secret-never-used-in-production-0123456789",
        "app.jwt.expiration=3600000",
        "app.openai.api-key=test-key",
        "app.whatsapp.access-token=test-access-token",
        "app.whatsapp.verify-token=test-verify-token",
        "app.whatsapp.app-secret=test-app-secret",
        "app.whatsapp.graph-api-version=v21.0",
        "app.whatsapp.base-url=https://graph.example.com"
})
class ConversationModeWebhookIntegrationTest {

    private static final String APP_SECRET = "test-app-secret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BusinessRepository businessRepository;

    @MockitoBean
    private AiProvider aiProvider;

    @MockitoBean
    private WhatsAppClient whatsAppClient;

    private String computeSignature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    }

    private JsonNode register(String email, String businessName) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setOwnerFirstName("Ana");
        request.setOwnerLastName("Gómez");
        request.setEmail(email);
        request.setPassword("supersecret123");
        request.setBusinessName(businessName);
        request.setBusinessPhone("+595981000000");
        request.setBusinessTimezone("America/Asuncion");

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    /** El registro no expone whatsappPhoneNumberId (ver BusinessSettingsMapper): se fija directo. */
    private void assignWhatsAppPhoneNumberId(long businessId, String phoneNumberId) {
        Business business = businessRepository.findById(businessId).orElseThrow();
        business.setWhatsappPhoneNumberId(phoneNumberId);
        businessRepository.save(business);
    }

    private String textMessagePayload(String phoneNumberId, String externalMessageId, String senderPhone,
                                       String messageBody) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "WABA_ID",
                      "changes": [
                        {
                          "field": "messages",
                          "value": {
                            "metadata": {"phone_number_id": "%s"},
                            "messages": [
                              {"from": "%s", "id": "%s", "timestamp": "1690000000", "type": "text",
                               "text": {"body": "%s"}}
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(phoneNumberId, senderPhone, externalMessageId, messageBody);
    }

    private void postWebhook(String payload) throws Exception {
        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", computeSignature(payload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private long findConversationId(String token, String phone) throws Exception {
        String response = mockMvc.perform(get("/api/conversations")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(response).path("data").path("content");
        for (JsonNode node : content) {
            if (phone.equals(node.path("senderPhone").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new AssertionError("No se encontró una Conversation para el teléfono " + phone);
    }

    @Test
    void takeoverSilencesBotAndReleaseReenablesItForTheNextMessage() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("mode-cycle-" + suffix + "@example.com", "Negocio Ciclo Modo");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phoneNumberId = "PHONE_" + suffix;
        assignWhatsAppPhoneNumberId(businessId, phoneNumberId);

        String customerPhone = "5959" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);

        when(whatsAppClient.sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString()))
                .thenReturn(new WhatsAppSendMessageResponse());

        // 1) BOT: el primer mensaje se procesa y se responde automáticamente.
        when(aiProvider.generateResponse(anyString(), eq("Hola")))
                .thenReturn("INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: ¡Hola! ¿En qué puedo ayudarte?");
        postWebhook(textMessagePayload(phoneNumberId, "wamid.1." + suffix, customerPhone, "Hola"));

        verify(whatsAppClient, times(1)).sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString());

        long conversationId = findConversationId(token, customerPhone);

        // 2) takeover: pasa a HUMAN.
        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("HUMAN"));

        // 3) HUMAN: el siguiente mensaje se persiste pero no obtiene respuesta automática.
        postWebhook(textMessagePayload(phoneNumberId, "wamid.2." + suffix, customerPhone,
                "¿Sigues ahí?"));

        // Sigue en 1 invocación total: el segundo mensaje no generó un nuevo envío.
        verify(whatsAppClient, times(1)).sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString());
        verify(aiProvider, never()).generateResponse(anyString(), eq("¿Sigues ahí?"));

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[2].content").value("¿Sigues ahí?"))
                .andExpect(jsonPath("$.data.content[2].direction").value("INBOUND"));

        // unreadCount acumula ambos INBOUND (el de BOT y el de HUMAN): el take over no lo resetea,
        // solo /read lo hace (fuera de alcance de esta prueba).
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(2))
                .andExpect(jsonPath("$.data.content[0].mode").value("HUMAN"));

        // 4) release: vuelve a BOT.
        mockMvc.perform(put("/api/conversations/" + conversationId + "/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("BOT"));

        // 5) BOT de nuevo: el próximo mensaje vuelve a procesarse automáticamente.
        when(aiProvider.generateResponse(anyString(), eq("Ya volví")))
                .thenReturn("INTENT: UNKNOWN\nCONFIDENCE: 0.5\nREPLY: ¡Bienvenido de nuevo!");
        postWebhook(textMessagePayload(phoneNumberId, "wamid.3." + suffix, customerPhone, "Ya volví"));

        verify(whatsAppClient, times(2)).sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString());
    }

}
