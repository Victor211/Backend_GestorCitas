package com.victor.appointmentmanager.api.modules.settings.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.settings.dto.request.UpdateBusinessSettingsRequest;
import org.junit.jupiter.api.BeforeEach;
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
class BusinessSettingsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("settings-" + suffix + "@example.com", "Peluquería Elegance " + suffix);
        token = owner.path("accessToken").asText();
    }

    private JsonNode register(String email, String businessName) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setOwnerFirstName("Ana");
        request.setOwnerLastName("Gómez");
        request.setEmail(email);
        request.setPassword("supersecret123");
        request.setBusinessName(businessName);
        request.setBusinessPhone("+595981123456");
        request.setBusinessEmail("contacto@peluqueriaelegance.com");
        request.setBusinessAddress("Asunción, Paraguay");
        request.setBusinessTimezone("America/Asuncion");

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private UpdateBusinessSettingsRequest buildValidRequest() {
        UpdateBusinessSettingsRequest request = new UpdateBusinessSettingsRequest();
        request.setName("Peluquería Elegance");
        request.setPhone("+595981123456");
        request.setEmail("contacto@peluqueriaelegance.com");
        request.setAddress("Asunción, Paraguay");
        request.setTimezone("America/Asuncion");
        return request;
    }

    // 1. GET autenticado -> 200.
    @Test
    void getSettingsAuthenticatedReturnsOk() throws Exception {
        mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").isNotEmpty())
                .andExpect(jsonPath("$.data.timezone").value("America/Asuncion"))
                .andExpect(jsonPath("$.data.whatsappConfigured").value(false))
                .andExpect(jsonPath("$.data.id").exists());
    }

    // 2. GET sin JWT -> 401.
    @Test
    void getSettingsWithoutJwtReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/settings/business"))
                .andExpect(status().isUnauthorized());
    }

    // 3. PUT autenticado -> 200.
    @Test
    void putSettingsAuthenticatedReturnsOk() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setName("Nuevo Nombre del Negocio");
        request.setAddress("Nueva Dirección 123");

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Nuevo Nombre del Negocio"))
                .andExpect(jsonPath("$.data.address").value("Nueva Dirección 123"));

        mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Nuevo Nombre del Negocio"))
                .andExpect(jsonPath("$.data.address").value("Nueva Dirección 123"));
    }

    // 4. PUT sin JWT -> 401.
    @Test
    void putSettingsWithoutJwtReturnsUnauthorized() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();

        mockMvc.perform(put("/api/settings/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // 5. PUT timezone inválida -> 400.
    @Test
    void putSettingsWithInvalidTimezoneReturnsBadRequest() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setTimezone("Not/AValidZone");

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("La zona horaria indicada no es válida"));
    }

    // 6. El request no admite businessId: cualquier campo extra enviado por el cliente es ignorado y el
    // Business modificado sigue siendo el del usuario autenticado (nunca el id "sugerido" en el body).
    @Test
    void requestBodyIgnoresBusinessIdField() throws Exception {
        String currentBusinessResponse = mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long actualBusinessId = objectMapper.readTree(currentBusinessResponse).path("data").path("id").asLong();

        String rawBody = """
                {
                  "businessId": 999999,
                  "name": "Peluquería Elegance",
                  "phone": "+595981123456",
                  "email": "contacto@peluqueriaelegance.com",
                  "address": "Asunción, Paraguay",
                  "timezone": "America/Asuncion"
                }
                """;

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(actualBusinessId));
    }

    // 7. Otro Business no puede modificarse: cada usuario solo ve/edita el suyo propio.
    @Test
    void anotherBusinessCannotBeModifiedByThisUser() throws Exception {
        UpdateBusinessSettingsRequest firstUpdate = buildValidRequest();
        firstUpdate.setName("Negocio A Actualizado");
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstUpdate)))
                .andExpect(status().isOk());

        JsonNode otherOwner = register("settings-other-" + UUID.randomUUID() + "@example.com", "Otro Negocio");
        String otherToken = otherOwner.path("accessToken").asText();

        UpdateBusinessSettingsRequest secondUpdate = buildValidRequest();
        secondUpdate.setName("Negocio B Actualizado");
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Negocio B Actualizado"));

        mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Negocio A Actualizado"));
    }

    // 2. phone null -> 200.
    @Test
    void putSettingsWithNullPhoneReturnsOk() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setPhone(null);

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    // 3. email null -> 200.
    @Test
    void putSettingsWithNullEmailReturnsOk() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setEmail(null);

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    // 4. address null -> 200.
    @Test
    void putSettingsWithNullAddressReturnsOk() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setAddress(null);

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }

    // 5. los tres null -> 200 (escenario exacto reportado en el bug).
    @Test
    void putSettingsWithPhoneEmailAndAddressAllNullReturnsOk() throws Exception {
        String rawBody = """
                {
                  "name": "Peluqueria Elegance",
                  "phone": null,
                  "email": null,
                  "address": null,
                  "timezone": "America/Asuncion"
                }
                """;

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.address").doesNotExist());

        // 12. GET posterior devuelve null para los tres.
        mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }

    // 6. phone "" -> persiste null.
    @Test
    void putSettingsWithEmptyPhonePersistsNull() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setPhone("");

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    // 7. email "" -> persiste null.
    @Test
    void putSettingsWithEmptyEmailPersistsNull() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setEmail("");

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    // 8. address "   " -> persiste null.
    @Test
    void putSettingsWithBlankAddressPersistsNull() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setAddress("   ");

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }

    // 9. Quitar phone que anteriormente tenía valor.
    @Test
    void removesPhoneThatPreviouslyHadValue() throws Exception {
        UpdateBusinessSettingsRequest withPhone = buildValidRequest();
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withPhone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(withPhone.getPhone()));

        UpdateBusinessSettingsRequest withoutPhone = buildValidRequest();
        withoutPhone.setPhone(null);
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutPhone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist());

        mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    // 10. Quitar email que anteriormente tenía valor.
    @Test
    void removesEmailThatPreviouslyHadValue() throws Exception {
        UpdateBusinessSettingsRequest withEmail = buildValidRequest();
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(withEmail.getEmail()));

        UpdateBusinessSettingsRequest withoutEmail = buildValidRequest();
        withoutEmail.setEmail(null);
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist());

        mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    // 11. Quitar address que anteriormente tenía valor.
    @Test
    void removesAddressThatPreviouslyHadValue() throws Exception {
        UpdateBusinessSettingsRequest withAddress = buildValidRequest();
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withAddress)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value(withAddress.getAddress()));

        UpdateBusinessSettingsRequest withoutAddress = buildValidRequest();
        withoutAddress.setAddress(null);
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutAddress)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").doesNotExist());

        mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }

    // Tras quedar en null, los valores pueden volver a cargarse normalmente.
    @Test
    void fieldsCanBeSetAgainAfterBeingRemoved() throws Exception {
        UpdateBusinessSettingsRequest cleared = buildValidRequest();
        cleared.setPhone(null);
        cleared.setEmail(null);
        cleared.setAddress(null);
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cleared)))
                .andExpect(status().isOk());

        UpdateBusinessSettingsRequest restored = buildValidRequest();
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restored)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(restored.getPhone()))
                .andExpect(jsonPath("$.data.email").value(restored.getEmail()))
                .andExpect(jsonPath("$.data.address").value(restored.getAddress()));
    }

    // 13. Email inválido con contenido -> 400.
    @Test
    void putSettingsWithInvalidEmailContentReturnsBadRequest() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // 15. /api/auth/me continúa funcionando tras el update con campos null.
    @Test
    void authMeContinuesWorkingAfterNullUpdate() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setPhone(null);
        request.setEmail(null);
        request.setAddress(null);
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value(request.getName()));
    }

    // 16. Dashboard continúa funcionando tras el update con campos null.
    @Test
    void dashboardContinuesWorkingAfterNullUpdate() throws Exception {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setPhone(null);
        request.setEmail(null);
        request.setAddress(null);
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCustomers").value(0));
    }

    // Ningún secreto (tokens/keys) aparece nunca en la respuesta.
    @Test
    void responseNeverExposesWhatsappOrOpenAiSecrets() throws Exception {
        String response = mockMvc.perform(get("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain("WHATSAPP_ACCESS_TOKEN")
                .doesNotContain("WHATSAPP_APP_SECRET")
                .doesNotContain("WHATSAPP_VERIFY_TOKEN")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("test-access-token")
                .doesNotContain("test-app-secret")
                .doesNotContain("test-verify-token")
                .doesNotContain("test-key")
                .doesNotContain("whatsappBusinessAccountId")
                .doesNotContain("whatsappPhoneNumberId");
    }

}
