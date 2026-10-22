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
 * MVP 3 - Fase 3: reactivación automática de una Conversation archivada cuando llega un INBOUND
 * real de WhatsApp (firma HMAC incluida). Mismo patrón que
 * {@code ConversationModeWebhookIntegrationTest}: {@code AiProvider} y {@code WhatsAppClient}
 * mockeados, todo lo demás (JWT, JPA/H2, deduplicación, ConversationMessageService,
 * ConversationArchiveService) es el stack real.
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
class ConversationArchiveWebhookIntegrationTest {

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

    /** Se llama siempre justo después del primer INBOUND, antes de archivar: la conversación todavía
     * está activa en ese punto, por lo que basta con el listado por defecto (no archivadas). */
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
    void inboundOnArchivedBotConversationReactivatesItAndBotReplies() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("archive-webhook-bot-" + suffix + "@example.com", "Negocio Webhook Archivo BOT");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phoneNumberId = "PHONE_" + suffix;
        assignWhatsAppPhoneNumberId(businessId, phoneNumberId);

        String customerPhone = "5959" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);

        when(whatsAppClient.sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString()))
                .thenReturn(new WhatsAppSendMessageResponse());
        when(aiProvider.generateResponse(anyString(), eq("Hola")))
                .thenReturn("INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: ¡Hola! ¿En qué puedo ayudarte?");

        // 1) Primer mensaje: crea la Conversation en BOT.
        postWebhook(textMessagePayload(phoneNumberId, "wamid.1." + suffix, customerPhone, "Hola"));
        long conversationId = findConversationId(token, customerPhone);

        // 2) El operador la archiva.
        mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // 3) El cliente vuelve a escribir: debe reactivar la MISMA Conversation y el bot responde.
        when(aiProvider.generateResponse(anyString(), eq("Ya volví")))
                .thenReturn("INTENT: UNKNOWN\nCONFIDENCE: 0.5\nREPLY: ¡Bienvenido de nuevo!");
        postWebhook(textMessagePayload(phoneNumberId, "wamid.2." + suffix, customerPhone, "Ya volví"));

        verify(whatsAppClient, times(2)).sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString());

        // Vuelve a la bandeja principal, con el mismo id (no se creó una segunda Conversation).
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(conversationId))
                .andExpect(jsonPath("$.data.content[0].archivedAt").isEmpty());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(4));
    }

    @Test
    void inboundOnArchivedHumanConversationReactivatesItButBotStaysSilent() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("archive-webhook-human-" + suffix + "@example.com",
                "Negocio Webhook Archivo HUMAN");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phoneNumberId = "PHONE_" + suffix;
        assignWhatsAppPhoneNumberId(businessId, phoneNumberId);

        String customerPhone = "5959" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);

        when(whatsAppClient.sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString()))
                .thenReturn(new WhatsAppSendMessageResponse());
        when(aiProvider.generateResponse(anyString(), eq("Hola")))
                .thenReturn("INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: ¡Hola! ¿En qué puedo ayudarte?");

        // 1) Primer mensaje en BOT.
        postWebhook(textMessagePayload(phoneNumberId, "wamid.1." + suffix, customerPhone, "Hola"));
        long conversationId = findConversationId(token, customerPhone);

        // 2) Un operador toma la conversación (HUMAN) y luego la archiva.
        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 3) El cliente vuelve a escribir: se reactiva y el INBOUND se persiste, pero el modo sigue
        // siendo HUMAN, así que el bot permanece en silencio.
        postWebhook(textMessagePayload(phoneNumberId, "wamid.2." + suffix, customerPhone, "¿Sigues ahí?"));

        // Sigue en 1 sola invocación (la del paso 1, en BOT): el paso 3 no generó una respuesta
        // automática porque la conversación reactivada sigue en HUMAN.
        verify(whatsAppClient, times(1)).sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString());
        verify(aiProvider, never()).generateResponse(anyString(), eq("¿Sigues ahí?"));

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(conversationId))
                .andExpect(jsonPath("$.data.content[0].mode").value("HUMAN"))
                .andExpect(jsonPath("$.data.content[0].archivedAt").isEmpty());

        // 3 mensajes en total: INBOUND "Hola" + OUTBOUND del bot (paso 1, todavía en BOT) + INBOUND
        // "¿Sigues ahí?" (paso 3, sin respuesta porque ya está en HUMAN).
        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[2].content").value("¿Sigues ahí?"))
                .andExpect(jsonPath("$.data.content[2].direction").value("INBOUND"));
    }

    @Test
    void inboundOnArchivedConversationNeverCreatesADuplicateConversation() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("archive-webhook-noduplicate-" + suffix + "@example.com",
                "Negocio Webhook Sin Duplicado");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phoneNumberId = "PHONE_" + suffix;
        assignWhatsAppPhoneNumberId(businessId, phoneNumberId);

        String customerPhone = "5959" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);

        when(whatsAppClient.sendTextMessage(eq(phoneNumberId), eq(customerPhone), anyString()))
                .thenReturn(new WhatsAppSendMessageResponse());
        when(aiProvider.generateResponse(anyString(), anyString()))
                .thenReturn("INTENT: UNKNOWN\nCONFIDENCE: 0.5\nREPLY: Ok");

        postWebhook(textMessagePayload(phoneNumberId, "wamid.1." + suffix, customerPhone, "Hola"));
        long conversationId = findConversationId(token, customerPhone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        postWebhook(textMessagePayload(phoneNumberId, "wamid.2." + suffix, customerPhone, "Otra vez"));

        // Sigue existiendo una única Conversation para ese businessId + senderPhone: 1 en la
        // bandeja activa, 0 en archivadas.
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(conversationId));
        mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

}
