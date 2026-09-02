package com.victor.appointmentmanager.api.modules.conversations.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationMessageService;
import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MVP 2 - Fase 2: API de lectura del historial de conversaciones. Ejercita la cadena real de
 * Spring Security + JWT + JPA/H2, igual que {@code AuthSecurityIntegrationTest}, para probar el
 * aislamiento multiempresa de punta a punta (no solo con mocks). El historial se siembra invocando
 * directamente {@code ConversationMessageService} (la misma pieza validada en la Fase 1), en vez de
 * pegarle al webhook de WhatsApp, que exige firma HMAC y no es el objeto de esta fase.
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
class ConversationHistoryControllerIntegrationTest {

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private long createCustomer(String token, String firstName, String lastName, String phone) throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setPhone(phone);

        String response = mockMvc.perform(post("/api/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private String uniquePhone() {
        return "5959" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);
    }

    // ------------------------------------------------------------------
    // GET /api/conversations
    // ------------------------------------------------------------------

    @Test
    void listReturnsOnlyConversationsOfAuthenticatedBusiness() throws Exception {
        JsonNode ownerA = register("list-a@example.com", "Negocio Lista A");
        String tokenA = ownerA.path("accessToken").asText();
        long businessA = ownerA.path("user").path("businessId").asLong();

        JsonNode ownerB = register("list-b@example.com", "Negocio Lista B");
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneA = uniquePhone();
        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessA, phoneA, "wamid." + phoneA, "Hola desde A");
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola desde B");

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].senderPhone").value(phoneA));
    }

    @Test
    void listOrdersByLastMessageAtDescending() throws Exception {
        JsonNode owner = register("list-order@example.com", "Negocio Orden");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String olderPhone = uniquePhone();
        String newerPhone = uniquePhone();
        conversationMessageService.recordInbound(businessId, olderPhone, "wamid." + olderPhone, "Primero");
        Thread.sleep(5);
        conversationMessageService.recordInbound(businessId, newerPhone, "wamid." + newerPhone, "Después");

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].senderPhone").value(newerPhone))
                .andExpect(jsonPath("$.data.content[1].senderPhone").value(olderPhone));
    }

    @Test
    void listPaginatesResults() throws Exception {
        JsonNode owner = register("list-page@example.com", "Negocio Paginado");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        for (int i = 0; i < 3; i++) {
            String phone = uniquePhone();
            conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Mensaje " + i);
        }

        mockMvc.perform(get("/api/conversations")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.size").value(2));
    }

    @Test
    void listIncludesCustomerNameWhenCustomerExists() throws Exception {
        JsonNode owner = register("list-customer@example.com", "Negocio Con Cliente");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        long customerId = createCustomer(token, "Juan", "Pérez", phone);
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola");

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerId").value(customerId))
                .andExpect(jsonPath("$.data.content[0].customerName").value("Juan Pérez"))
                .andExpect(jsonPath("$.data.content[0].senderPhone").value(phone));
    }

    @Test
    void listSupportsNullCustomer() throws Exception {
        JsonNode owner = register("list-no-customer@example.com", "Negocio Sin Cliente");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid." + phone, "Hola, quiero un turno");

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerId").isEmpty())
                .andExpect(jsonPath("$.data.content[0].customerName").isEmpty())
                .andExpect(jsonPath("$.data.content[0].senderPhone").value(phone))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(1))
                .andExpect(jsonPath("$.data.content[0].lastMessagePreview").value("Hola, quiero un turno"));
    }

    @Test
    void businessIdQueryParamIsIgnoredAndDerivedFromJwt() throws Exception {
        JsonNode ownerA = register("forge-a@example.com", "Negocio Forge A");
        String tokenA = ownerA.path("accessToken").asText();
        long businessA = ownerA.path("user").path("businessId").asLong();

        JsonNode ownerB = register("forge-b@example.com", "Negocio Forge B");
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola desde B");

        // El endpoint no expone ningún parámetro businessId; intentar forzarlo por query no debe
        // filtrar datos de otro negocio (el businessId real sigue viniendo exclusivamente del JWT).
        mockMvc.perform(get("/api/conversations")
                        .param("businessId", String.valueOf(businessB))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    // ------------------------------------------------------------------
    // GET /api/conversations/{id}/messages
    // ------------------------------------------------------------------

    @Test
    void messagesAreReturnedInChronologicalOrderWithExactContent() throws Exception {
        JsonNode owner = register("messages-order@example.com", "Negocio Mensajes");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.IN1." + phone, "Hola");
        Thread.sleep(5);
        conversationMessageService.recordOutbound(businessId, phone, "wamid.OUT1." + phone,
                "¡Hola! ¿En qué puedo ayudarte?");
        Thread.sleep(5);
        conversationMessageService.recordInbound(businessId, phone, "wamid.IN2." + phone,
                "Quiero reservar un turno");

        long conversationId = findConversationId(token, phone);

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].content").value("Hola"))
                .andExpect(jsonPath("$.data.content[0].direction").value("INBOUND"))
                .andExpect(jsonPath("$.data.content[0].senderType").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.content[0].messageType").value("TEXT"))
                .andExpect(jsonPath("$.data.content[1].content").value("¡Hola! ¿En qué puedo ayudarte?"))
                .andExpect(jsonPath("$.data.content[1].direction").value("OUTBOUND"))
                .andExpect(jsonPath("$.data.content[1].senderType").value("BOT"))
                .andExpect(jsonPath("$.data.content[2].content").value("Quiero reservar un turno"))
                .andExpect(jsonPath("$.data.content[2].direction").value("INBOUND"));
    }

    @Test
    void messagesEndpointPaginates() throws Exception {
        JsonNode owner = register("messages-page@example.com", "Negocio Mensajes Paginados");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        for (int i = 0; i < 3; i++) {
            conversationMessageService.recordInbound(businessId, phone, "wamid." + phone + "." + i, "Mensaje " + i);
        }
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(get("/api/conversations/" + conversationId + "/messages")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    @Test
    void messagesFromAnotherConversationAreNotReturned() throws Exception {
        JsonNode owner = register("messages-isolation@example.com", "Negocio Aislamiento Mensajes");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone1 = uniquePhone();
        String phone2 = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone1, "wamid." + phone1, "Conversación 1");
        conversationMessageService.recordInbound(businessId, phone2, "wamid." + phone2, "Conversación 2");

        long conversationId1 = findConversationId(token, phone1);

        mockMvc.perform(get("/api/conversations/" + conversationId1 + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("Conversación 1"));
    }

    @Test
    void businessACannotReadMessagesOfConversationOwnedByBusinessB() throws Exception {
        JsonNode ownerA = register("cross-a@example.com", "Negocio Cross A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("cross-b@example.com", "Negocio Cross B");
        String tokenB = ownerB.path("accessToken").asText();
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Secreto de B");
        long conversationIdB = findConversationId(tokenB, phoneB);

        mockMvc.perform(get("/api/conversations/" + conversationIdB + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void messagesOfNonexistentConversationReturn404() throws Exception {
        JsonNode owner = register("messages-404@example.com", "Negocio 404 Mensajes");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(get("/api/conversations/999999/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // PUT /api/conversations/{id}/read
    // ------------------------------------------------------------------

    @Test
    void markAsReadResetsUnreadCountToZero() throws Exception {
        JsonNode owner = register("read-reset@example.com", "Negocio Marcar Leido");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.1." + phone, "Hola");
        conversationMessageService.recordInbound(businessId, phone, "wamid.2." + phone, "Otro mensaje");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(0));
    }

    @Test
    void newInboundAfterMarkingAsReadIncrementsUnreadCountAgain() throws Exception {
        JsonNode owner = register("read-then-inbound@example.com", "Negocio Read Then Inbound");
        String token = owner.path("accessToken").asText();
        long businessId = owner.path("user").path("businessId").asLong();

        String phone = uniquePhone();
        conversationMessageService.recordInbound(businessId, phone, "wamid.1." + phone, "Hola");
        long conversationId = findConversationId(token, phone);

        mockMvc.perform(put("/api/conversations/" + conversationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        conversationMessageService.recordInbound(businessId, phone, "wamid.2." + phone, "¿Sigues ahí?");

        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(1));
    }

    @Test
    void businessCannotMarkAnotherBusinessConversationAsRead() throws Exception {
        JsonNode ownerA = register("read-cross-a@example.com", "Negocio Read Cross A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("read-cross-b@example.com", "Negocio Read Cross B");
        String tokenB = ownerB.path("accessToken").asText();
        long businessB = ownerB.path("user").path("businessId").asLong();

        String phoneB = uniquePhone();
        conversationMessageService.recordInbound(businessB, phoneB, "wamid." + phoneB, "Hola");
        long conversationIdB = findConversationId(tokenB, phoneB);

        mockMvc.perform(put("/api/conversations/" + conversationIdB + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // La conversación de B no debe haberse modificado por el intento fallido de A.
        mockMvc.perform(get("/api/conversations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(jsonPath("$.data.content[0].unreadCount").value(1));
    }

    @Test
    void markAsReadOfNonexistentConversationReturns404() throws Exception {
        JsonNode owner = register("read-404@example.com", "Negocio 404 Read");
        String token = owner.path("accessToken").asText();

        mockMvc.perform(put("/api/conversations/999999/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Seguridad
    // ------------------------------------------------------------------

    @Test
    void listWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void messagesWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/conversations/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void markAsReadWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(put("/api/conversations/1/read"))
                .andExpect(status().isUnauthorized());
    }

    /** Encuentra el id de la Conversation recién sembrada buscándola por su senderPhone único. */
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

}
