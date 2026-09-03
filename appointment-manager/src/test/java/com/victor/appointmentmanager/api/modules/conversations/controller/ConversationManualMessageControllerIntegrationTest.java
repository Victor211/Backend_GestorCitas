package com.victor.appointmentmanager.api.modules.conversations.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.conversations.dto.request.SendConversationMessageRequest;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationMessageService;
import com.victor.appointmentmanager.api.modules.whatsapp.client.WhatsAppClient;
import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;
import com.victor.appointmentmanager.api.modules.whatsapp.exception.WhatsAppApiException;
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
 * MVP 3 - Fase 2: envío manual de mensajes. Ejercita la cadena real de Spring Security + JWT +
 * JPA/H2 (mismo patrón que {@code ConversationControlControllerIntegrationTest}), sustituyendo
 * únicamente {@code WhatsAppClient} por un mock para no depender de Cloud API real.
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
class ConversationManualMessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationMessageService conversationMessageService;

    @Autowired
    private BusinessRepository businessRepository;

    @MockitoBean
    private WhatsAppClient whatsAppClient;

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

    private String uniquePhone() {
        return "5959" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);
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

    private String requestBody(String content) throws Exception {
        return objectMapper.writeValueAsString(new SendConversationMessageRequest(content));
    }

    private WhatsAppSendMessageResponse successResponse(String wamid) {
        WhatsAppSendMessageResponse.SentMessage sentMessage = new WhatsAppSendMessageResponse.SentMessage();
        sentMessage.setId(wamid);
        WhatsAppSendMessageResponse response = new WhatsAppSendMessageResponse();
        response.setMessages(java.util.List.of(sentMessage));
        return response;
    }

    // ------------------------------------------------------------------
    // Caso feliz: HUMAN
    // ------------------------------------------------------------------

    @Test
    void humanModeConversationSendsAndPersistsManualMessage() throws Exception {
        JsonNode owner = register("manual-ok@example.com", "Negocio Manual OK");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();
        assignWhatsAppPhoneNumberId(businessId, "PHONE_MANUAL_OK");

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.in." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        when(whatsAppClient.sendTextMessage(eq("PHONE_MANUAL_OK"), eq(phone), eq("Hola, te habla Juan")))
                .thenReturn(successResponse("wamid.MANUAL1"));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Hola, te habla Juan")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.senderType").value("HUMAN"))
                .andExpect(jsonPath("$.data.messageType").value("TEXT"))
                .andExpect(jsonPath("$.data.content").value("Hola, te habla Juan"));

        verify(whatsAppClient, times(1)).sendTextMessage(any(), any(), any());

        // Historial: queda el INBOUND original + el OUTBOUND manual, en orden.
        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[1].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.content[1].senderType").value("HUMAN"))
                .andExpect(jsonPath("$.data.content[1].content").value("Hola, te habla Juan"));

        // Metadata: lastMessageAt/preview se actualizan, unreadCount NO cambia (solo cuenta INBOUND
        // del cliente), y el modo sigue HUMAN (el envío manual no cambia mode).
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("Hola, te habla Juan"))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data.content[0].mode").value("HUMAN"));
    }

    @Test
    void worksWhenConversationHasNoCustomerAssociatedYet() throws Exception {
        JsonNode owner = register("manual-no-customer@example.com", "Negocio Manual Sin Cliente");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();
        assignWhatsAppPhoneNumberId(businessId, "PHONE_NO_CUSTOMER");

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.in." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        when(whatsAppClient.sendTextMessage(eq("PHONE_NO_CUSTOMER"), eq(phone), anyString()))
                .thenReturn(successResponse("wamid.MANUAL2"));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Hola")))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Modo BOT: rechazado con 409, sin llamar a WhatsApp
    // ------------------------------------------------------------------

    @Test
    void botModeConversationRejectsManualMessageWith409AndNeverCallsWhatsApp() throws Exception {
        JsonNode owner = register("manual-bot@example.com", "Negocio Manual Bot");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();
        assignWhatsAppPhoneNumberId(businessId, "PHONE_BOT");

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.in." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        // Sin takeover: sigue en BOT.
        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Hola")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Multiempresa / 404
    // ------------------------------------------------------------------

    @Test
    void businessCannotSendManualMessageToAnotherBusinessConversation() throws Exception {
        JsonNode ownerA = register("manual-cross-a@example.com", "Negocio Manual Cross A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("manual-cross-b@example.com", "Negocio Manual Cross B");
        String tokenB = ownerB.path("accessToken").asText();
        long businessB = ownerB.path("user").path("businessId").asLong();
        assignWhatsAppPhoneNumberId(businessB, "PHONE_CROSS_B");

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola");
        long conversationIdB = findConversationId(tokenB, phoneB);

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/conversations/" + conversationIdB + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Hola")))
                .andExpect(status().isNotFound());

        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
    }

    @Test
    void nonexistentConversationReturns404() throws Exception {
        JsonNode owner = register("manual-404@example.com", "Negocio Manual 404");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(post("/api/conversations/999999/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Hola")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Validación de content
    // ------------------------------------------------------------------

    @Test
    void nullContentIsRejectedWith400() throws Exception {
        JsonNode owner = register("manual-null@example.com", "Negocio Manual Null");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(post("/api/conversations/1/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(whatsAppClient, never()).sendTextMessage(any(), any(), any());
    }

    @Test
    void emptyContentIsRejectedWith400() throws Exception {
        JsonNode owner = register("manual-empty@example.com", "Negocio Manual Empty");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(post("/api/conversations/1/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankContentIsRejectedWith400() throws Exception {
        JsonNode owner = register("manual-blank@example.com", "Negocio Manual Blank");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(post("/api/conversations/1/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("    ")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Falla de WhatsApp: no persiste, no actualiza preview
    // ------------------------------------------------------------------

    @Test
    void whatsAppFailureDoesNotPersistOutboundNorUpdatePreview() throws Exception {
        JsonNode owner = register("manual-fail@example.com", "Negocio Manual Fail");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();
        assignWhatsAppPhoneNumberId(businessId, "PHONE_FAIL");

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.in." + phone, "Mensaje original");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        when(whatsAppClient.sendTextMessage(eq("PHONE_FAIL"), eq(phone), anyString()))
                .thenThrow(new WhatsAppApiException("No se pudo enviar el mensaje mediante WhatsApp Cloud API"));

        mockMvc.perform(post("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Este mensaje no debería quedar guardado")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("Mensaje original"));
    }

    // ------------------------------------------------------------------
    // Seguridad
    // ------------------------------------------------------------------

    @Test
    void sendMessageWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/conversations/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Hola")))
                .andExpect(status().isUnauthorized());
    }

}
