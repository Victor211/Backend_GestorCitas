package com.victor.appointmentmanager.api.modules.employees.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.UpdateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.services.dto.request.CreateServiceRequest;
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

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

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
class EmployeeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private long serviceId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("employees-" + suffix + "@example.com", "Negocio " + suffix);
        token = owner.path("accessToken").asText();
        serviceId = createService("Corte");
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

    private long createService(String name) throws Exception {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName(name);
        request.setDurationMinutes(30);
        request.setPrice(new BigDecimal("10.00"));
        request.setColor("#3B82F6");

        String response = mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private CreateEmployeeRequest baseCreateRequest() {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setFirstName("Ana");
        request.setLastName("López");
        request.setColor("#4CAF50");
        request.setServiceIds(Set.of(serviceId));
        return request;
    }

    @Test
    void createsEmployeeWithNullPhoneAndEmail() throws Exception {
        CreateEmployeeRequest request = baseCreateRequest();
        request.setPhone(null);
        request.setEmail(null);

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.firstName").value("Ana"));
    }

    @Test
    void createsEmployeeWithEmptyStringPhoneAndEmailPersistedAsNull() throws Exception {
        String rawJson = """
                {
                  "firstName": "Ana",
                  "lastName": "López",
                  "phone": "",
                  "email": "",
                  "color": "#4CAF50",
                  "serviceIds": [%d]
                }
                """.formatted(serviceId);

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void createsEmployeeWithWhitespaceOnlyPhoneAndEmailPersistedAsNull() throws Exception {
        String rawJson = """
                {
                  "firstName": "Ana",
                  "lastName": "López",
                  "phone": "   ",
                  "email": "   ",
                  "color": "#4CAF50",
                  "serviceIds": [%d]
                }
                """.formatted(serviceId);

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void returnsBadRequestWhenEmailHasInvalidFormat() throws Exception {
        CreateEmployeeRequest request = baseCreateRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsBadRequestWhenPhoneExceedsMaxLength() throws Exception {
        CreateEmployeeRequest request = baseCreateRequest();
        request.setPhone("1".repeat(31));

        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsTwoEmployeesWithNullPhoneInTheSameBusiness() throws Exception {
        CreateEmployeeRequest first = baseCreateRequest();
        first.setPhone(null);
        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        CreateEmployeeRequest second = baseCreateRequest();
        second.setLastName("Benitez");
        second.setPhone(null);
        mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isCreated());
    }

    @Test
    void updateRemovesExistingPhoneAndEmail() throws Exception {
        CreateEmployeeRequest createRequest = baseCreateRequest();
        createRequest.setPhone("+595981234567");
        createRequest.setEmail("ana@example.com");

        String createResponse = mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long employeeId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        UpdateEmployeeRequest updateRequest = new UpdateEmployeeRequest();
        updateRequest.setFirstName("Ana");
        updateRequest.setLastName("López");
        updateRequest.setPhone(null);
        updateRequest.setEmail(null);
        updateRequest.setColor("#4CAF50");
        updateRequest.setServiceIds(Set.of(serviceId));

        mockMvc.perform(put("/api/employees/" + employeeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    @Test
    void updateAddsPhoneAndEmailThatWerePreviouslyNull() throws Exception {
        CreateEmployeeRequest createRequest = baseCreateRequest();

        String createResponse = mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long employeeId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        UpdateEmployeeRequest updateRequest = new UpdateEmployeeRequest();
        updateRequest.setFirstName("Ana");
        updateRequest.setLastName("López");
        updateRequest.setPhone("+595987654321");
        updateRequest.setEmail("ana@example.com");
        updateRequest.setColor("#4CAF50");
        updateRequest.setServiceIds(Set.of(serviceId));

        mockMvc.perform(put("/api/employees/" + employeeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("+595987654321"))
                .andExpect(jsonPath("$.data.email").value("ana@example.com"));
    }

}
