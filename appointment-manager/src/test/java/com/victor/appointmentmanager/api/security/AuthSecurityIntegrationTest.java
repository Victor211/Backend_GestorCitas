package com.victor.appointmentmanager.api.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.request.LoginRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.services.dto.request.CreateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.request.UpdateServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de seguridad de punta a punta: usan la cadena de filtros real de Spring Security
 * (a diferencia de los *SmokeTest de módulos anteriores, que la deshabilitaban con
 * addFilters = false porque todavía no existía autenticación).
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
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private RegisterRequest buildRegisterRequest(String email, String businessName) {
        RegisterRequest request = new RegisterRequest();
        request.setOwnerFirstName("Ana");
        request.setOwnerLastName("Gómez");
        request.setEmail(email);
        request.setPassword("supersecret123");
        request.setBusinessName(businessName);
        request.setBusinessPhone("+595981000000");
        request.setBusinessTimezone("America/Asuncion");
        return request;
    }

    private JsonNode register(String email, String businessName) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRegisterRequest(email, businessName))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    @Test
    void registerEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRegisterRequest("public-register@example.com", "Negocio Registro"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        register("public-login@example.com", "Negocio Login");

        LoginRequest login = new LoginRequest();
        login.setEmail("public-login@example.com");
        login.setPassword("supersecret123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void validTokenGrantsAccessToProtectedEndpoint() throws Exception {
        JsonNode data = register("valid-token@example.com", "Negocio Token");
        String token = data.path("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("valid-token@example.com"));
    }

    private CreateServiceRequest buildCreateServiceRequest() {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("Corte de prueba");
        request.setDurationMinutes(30);
        request.setPrice(new BigDecimal("10.00"));
        request.setColor("#3B82F6");
        return request;
    }

    private long createService(String token) throws Exception {
        String response = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateServiceRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    void serviceBusinessIdIsDerivedFromJwtNotFromClientInput() throws Exception {
        JsonNode owner = register("jwt-derivation@example.com", "Negocio JWT");
        String token = owner.path("accessToken").asText();
        long expectedBusinessId = owner.path("user").path("businessId").asLong();

        long serviceId = createService(token);

        mockMvc.perform(get("/api/services/" + serviceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessId").value(expectedBusinessId));
    }

    @Test
    void businessCannotReadUpdateOrDeleteAnotherBusinessService() throws Exception {
        JsonNode ownerA = register("owner-a@example.com", "Negocio A");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("owner-b@example.com", "Negocio B");
        String tokenB = ownerB.path("accessToken").asText();

        long serviceIdB = createService(tokenB);

        mockMvc.perform(get("/api/services/" + serviceIdB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        UpdateServiceRequest updateRequest = new UpdateServiceRequest();
        updateRequest.setName("Intento de robo");
        updateRequest.setDurationMinutes(45);
        updateRequest.setPrice(new BigDecimal("20.00"));
        updateRequest.setColor("#000000");

        mockMvc.perform(put("/api/services/" + serviceIdB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/services/" + serviceIdB)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessCannotCreateAppointmentUsingAnotherBusinessResources() throws Exception {
        JsonNode ownerA = register("owner-a2@example.com", "Negocio A2");
        String tokenA = ownerA.path("accessToken").asText();

        JsonNode ownerB = register("owner-b2@example.com", "Negocio B2");
        String tokenB = ownerB.path("accessToken").asText();

        long serviceIdB = createService(tokenB);

        CreateEmployeeRequest employeeRequest = new CreateEmployeeRequest();
        employeeRequest.setFirstName("Juan");
        employeeRequest.setLastName("Pérez");
        employeeRequest.setPhone("+595981111111");
        employeeRequest.setEmail("juan@example.com");
        employeeRequest.setColor("#3B82F6");

        String employeeResponse = mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employeeRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long employeeIdB = objectMapper.readTree(employeeResponse).path("data").path("id").asLong();

        CreateCustomerRequest customerRequest = new CreateCustomerRequest();
        customerRequest.setFirstName("Ana");
        customerRequest.setLastName("Gómez");
        customerRequest.setPhone("+595982222222");

        String customerResponse = mockMvc.perform(post("/api/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customerRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long customerIdB = objectMapper.readTree(customerResponse).path("data").path("id").asLong();

        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest();
        appointmentRequest.setCustomerId(customerIdB);
        appointmentRequest.setEmployeeId(employeeIdB);
        appointmentRequest.setServiceId(serviceIdB);
        appointmentRequest.setStartAt(Instant.now().plus(7, ChronoUnit.DAYS));

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appointmentRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerSchemaDoesNotExposeBusinessIdOnAuthenticatedCreateRequests() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode schemas = objectMapper.readTree(docs).path("components").path("schemas");

        assertThat(schemas.path("CreateServiceRequest").path("properties").has("businessId")).isFalse();
        assertThat(schemas.path("CreateEmployeeRequest").path("properties").has("businessId")).isFalse();
        assertThat(schemas.path("CreateCustomerRequest").path("properties").has("businessId")).isFalse();
        assertThat(schemas.path("CreateAppointmentRequest").path("properties").has("businessId")).isFalse();
        assertThat(schemas.path("ConversationRequest").path("properties").has("businessId")).isFalse();
    }

}
