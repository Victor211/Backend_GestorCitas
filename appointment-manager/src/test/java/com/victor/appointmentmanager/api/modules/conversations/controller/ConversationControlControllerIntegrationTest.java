package com.victor.appointmentmanager.api.modules.conversations.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVP 3 - Fase 1: control BOT/HUMAN de conversaciones (takeover/release). Ejercita la cadena real
 * de Spring Security + JWT + JPA/H2, igual que {@code ConversationHistoryControllerIntegrationTest}.
 * El envío manual de mensajes y el frontend quedan fuera de alcance de esta fase.
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
class ConversationControlControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationMessageService conversationMessageService;

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
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
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

    // ------------------------------------------------------------------
    // GET /api/conversations expone mode
    // ------------------------------------------------------------------

    @Test
    void newConversationStartsInBotMode() throws Exception {
        JsonNode owner = register("mode-default@example.com", "Negocio Modo Default");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mode").value("BOT"));
    }

    // ------------------------------------------------------------------
    // PUT /api/conversations/{id}/takeover
    // ------------------------------------------------------------------

    @Test
    void takeoverChangesModeToHuman() throws Exception {
        JsonNode owner = register("takeover-ok@example.com", "Negocio Takeover");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId))
                .andExpect(jsonPath("$.data.mode").value("HUMAN"));

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].mode").value("HUMAN"));
    }

    @Test
    void takeoverIsIdempotentWhenAlreadyHuman() throws Exception {
        JsonNode owner = register("takeover-idem@example.com", "Negocio Takeover Idempotente");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("HUMAN"));
    }

    @Test
    void businessCannotTakeoverAnotherBusinessConversation() throws Exception {
        JsonNode ownerA = register("takeover-cross-a@example.com", "Negocio Takeover Cross A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("takeover-cross-b@example.com", "Negocio Takeover Cross B");
        String tokenB = ownerB.path("accessToken").asText();
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola");
        long conversationIdB = findConversationId(tokenB, phoneB);

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // La conversación de B no debe haberse modificado por el intento fallido de A.
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.content[0].mode").value("BOT"));
    }

    @Test
    void takeoverOfNonexistentConversationReturns404() throws Exception {
        JsonNode owner = register("takeover-404@example.com", "Negocio 404 Takeover");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(put("/api/conversations/999999/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void takeoverWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(put("/api/conversations/1/takeover"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // PUT /api/conversations/{id}/release
    // ------------------------------------------------------------------

    @Test
    void releaseChangesModeBackToBot() throws Exception {
        JsonNode owner = register("release-ok@example.com", "Negocio Release");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/conversations/" + conversationId + "/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId))
                .andExpect(jsonPath("$.data.mode").value("BOT"));

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].mode").value("BOT"));
    }

    @Test
    void releaseIsIdempotentWhenAlreadyBot() throws Exception {
        JsonNode owner = register("release-idem@example.com", "Negocio Release Idempotente");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("BOT"));
    }

    @Test
    void businessCannotReleaseAnotherBusinessConversation() throws Exception {
        JsonNode ownerA = register("release-cross-a@example.com", "Negocio Release Cross A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("release-cross-b@example.com", "Negocio Release Cross B");
        String tokenB = ownerB.path("accessToken").asText();
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola");
        long conversationIdB = findConversationId(tokenB, phoneB);

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // La conversación de B sigue HUMAN: el intento fallido de A no debe haberla devuelto a BOT.
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.content[0].mode").value("HUMAN"));
    }

    @Test
    void releaseOfNonexistentConversationReturns404() throws Exception {
        JsonNode owner = register("release-404@example.com", "Negocio 404 Release");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(put("/api/conversations/999999/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void releaseWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(put("/api/conversations/1/release"))
                .andExpect(status().isUnauthorized());
    }

}
