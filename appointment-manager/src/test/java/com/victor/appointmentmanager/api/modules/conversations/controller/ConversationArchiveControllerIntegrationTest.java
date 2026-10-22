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
 * MVP 3 - Fase 3: archivado/restauración de conversaciones (archive/restore) y el filtro
 * {@code ?archived=} de {@code GET /api/conversations}. Ejercita la cadena real de Spring Security
 * + JWT + JPA/H2, igual que {@code ConversationControlControllerIntegrationTest}. La reactivación
 * automática por INBOUND se cubre en {@code ConversationArchiveWebhookIntegrationTest}.
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
class ConversationArchiveControllerIntegrationTest {

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
    // PUT /api/conversations/{id}/archive
    // ------------------------------------------------------------------

    @Test
    void archiveSetsArchivedAtAndHidesFromDefaultListing() throws Exception {
        JsonNode owner = register("archive-ok@example.com", "Negocio Archivar");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId))
                .andExpect(jsonPath("$.data.archivedAt").isNotEmpty());

        // Desaparece de la bandeja principal (sin parámetro y con archived=false).
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mockMvc.perform(get("/api/conversations").param("archived", "false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        // Aparece en el listado de archivadas.
        mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(conversationId))
                .andExpect(jsonPath("$.data.content[0].archivedAt").isNotEmpty());
    }

    @Test
    void archiveDoesNotDeleteMessagesModeOrUnreadCount() throws Exception {
        JsonNode owner = register("archive-preserves@example.com", "Negocio Archivar Preserva");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.1." + phone, "Hola");
        conversationMessageService.recordInbound(businessId, phone, "wamid.2." + phone, "¿Hay turnos?");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/takeover")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));

        mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].mode").value("HUMAN"))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(2));
    }

    @Test
    void archiveIsIdempotentAndKeepsOriginalTimestamp() throws Exception {
        JsonNode owner = register("archive-idem@example.com", "Negocio Archivar Idempotente");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // Se lee el archivedAt ya persistido (GET, no la respuesta del PUT que acaba de escribirlo):
        // así ambos valores comparados pasaron por el mismo redondeo de precisión al guardarse/leerse
        // de la base, y la comparación no depende de la precisión del Instant en memoria recién creado.
        String afterFirstArchive = mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String firstArchivedAt =
                objectMapper.readTree(afterFirstArchive).path("data").path("content").get(0).path("archivedAt").asText();

        Thread.sleep(5);

        String secondResponse = mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondArchivedAt = objectMapper.readTree(secondResponse).path("data").path("archivedAt").asText();

        org.assertj.core.api.Assertions.assertThat(java.time.Instant.parse(secondArchivedAt))
                .isEqualTo(java.time.Instant.parse(firstArchivedAt));
    }

    @Test
    void businessCannotArchiveAnotherBusinessConversation() throws Exception {
        JsonNode ownerA = register("archive-cross-a@example.com", "Negocio Archivar Cross A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("archive-cross-b@example.com", "Negocio Archivar Cross B");
        String tokenB = ownerB.path("accessToken").asText();
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola");
        long conversationIdB = findConversationId(tokenB, phoneB);

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void archiveOfNonexistentConversationReturns404() throws Exception {
        JsonNode owner = register("archive-404@example.com", "Negocio 404 Archivar");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(put("/api/conversations/999999/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(put("/api/conversations/1/archive"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // PUT /api/conversations/{id}/restore
    // ------------------------------------------------------------------

    @Test
    void restoreClearsArchivedAtAndReturnsToDefaultListing() throws Exception {
        JsonNode owner = register("restore-ok@example.com", "Negocio Restaurar");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/conversations/" + conversationId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId))
                .andExpect(jsonPath("$.data.archivedAt").isEmpty());

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(conversationId));

        mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void restoreIsIdempotentWhenAlreadyActive() throws Exception {
        JsonNode owner = register("restore-idem@example.com", "Negocio Restaurar Idempotente");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archivedAt").isEmpty());
    }

    @Test
    void businessCannotRestoreAnotherBusinessConversation() throws Exception {
        JsonNode ownerA = register("restore-cross-a@example.com", "Negocio Restaurar Cross A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("restore-cross-b@example.com", "Negocio Restaurar Cross B");
        String tokenB = ownerB.path("accessToken").asText();
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola");
        long conversationIdB = findConversationId(tokenB, phoneB);

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // La conversación de B sigue archivada: el intento fallido de A no debe haberla restaurado.
        mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void restoreOfNonexistentConversationReturns404() throws Exception {
        JsonNode owner = register("restore-404@example.com", "Negocio 404 Restaurar");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(put("/api/conversations/999999/restore")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(put("/api/conversations/1/restore"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // GET /api/conversations?archived= : paginación y orden se preservan
    // ------------------------------------------------------------------

    @Test
    void archivedListingPaginatesAndOrdersByLastMessageAtDescending() throws Exception {
        JsonNode owner = register("archived-list-page@example.com", "Negocio Lista Archivadas");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String olderPhone = uniquePhone();
        String newerPhone = uniquePhone();
        conversationMessageService.recordInbound(businessId, olderPhone, "wamid." + olderPhone, "Primero");
        Thread.sleep(5);
        conversationMessageService.recordInbound(businessId, newerPhone, "wamid." + newerPhone, "Después");

        long olderId = findConversationId(token, olderPhone);
        long newerId = findConversationId(token, newerPhone);

        mockMvc.perform(put("/api/conversations/" + olderId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/conversations/" + newerId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].senderPhone").value(newerPhone))
                .andExpect(jsonPath("$.data.content[1].senderPhone").value(olderPhone));

        mockMvc.perform(get("/api/conversations").param("archived", "true")
                        .param("page", "0").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

}
