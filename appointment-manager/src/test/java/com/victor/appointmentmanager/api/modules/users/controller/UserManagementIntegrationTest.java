package com.victor.appointmentmanager.api.modules.users.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.auth.dto.request.LoginRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.users.dto.request.CreateUserRequest;
import com.victor.appointmentmanager.api.modules.users.dto.request.UpdateUserStatusRequest;
import com.victor.appointmentmanager.api.modules.users.entity.User;
import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import com.victor.appointmentmanager.api.modules.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class UserManagementIntegrationTest {

    private static final String PASSWORD = "ContraseñaSegura123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String suffix;
    private JsonNode owner;
    private String ownerToken;
    private long ownerBusinessId;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString();
        owner = register("owner-" + suffix + "@example.com", "Negocio " + suffix);
        ownerToken = owner.path("accessToken").asText();
        ownerBusinessId = owner.path("user").path("businessId").asLong();
    }

    // ---------- Creación ----------

    @Test
    void ownerCreatesAdmin() throws Exception {
        createUser(ownerToken, "admin-" + suffix + "@example.com", "ADMIN")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.email").value("admin-" + suffix + "@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.businessId").doesNotExist());
    }

    @Test
    void ownerCreatesStaff() throws Exception {
        createUser(ownerToken, "staff-" + suffix + "@example.com", "STAFF")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("STAFF"));
    }

    @Test
    void ownerCannotCreateAnotherOwner() throws Exception {
        createUser(ownerToken, "owner2-" + suffix + "@example.com", "OWNER")
                .andExpect(status().isBadRequest());
        assertThat(userRepository.existsByEmailIgnoreCase("owner2-" + suffix + "@example.com")).isFalse();
    }

    @Test
    void unknownRoleIsRejectedWith400() throws Exception {
        createUser(ownerToken, "x-" + suffix + "@example.com", "SUPERUSER")
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCannotCreateUsers() throws Exception {
        String adminToken = createAndLogin("ADMIN");
        createUser(adminToken, "nuevo-" + suffix + "@example.com", "STAFF")
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCreateUsers() throws Exception {
        String staffToken = createAndLogin("STAFF");
        createUser(staffToken, "nuevo-" + suffix + "@example.com", "STAFF")
                .andExpect(status().isForbidden());
    }

    @Test
    void createWithoutJwtReturns401() throws Exception {
        createUser(null, "nuevo-" + suffix + "@example.com", "ADMIN")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailReturns409EvenWithDifferentCase() throws Exception {
        String email = "dup-" + suffix + "@example.com";
        createUser(ownerToken, email, "ADMIN").andExpect(status().isCreated());
        createUser(ownerToken, email.toUpperCase(), "STAFF").andExpect(status().isConflict());
    }

    @Test
    void passwordIsStoredWithBcryptAndEmailNormalized() throws Exception {
        createUser(ownerToken, "MiXed-" + suffix + "@Example.com", "ADMIN").andExpect(status().isCreated());

        User stored = userRepository.findByEmailIgnoreCase("mixed-" + suffix + "@example.com").orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("mixed-" + suffix + "@example.com");
        assertThat(stored.getPassword()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPassword())).isTrue();
    }

    @Test
    void newUserBelongsToOwnersBusinessAndClientBusinessIdIsIgnored() throws Exception {
        JsonNode other = register("other-" + suffix + "@example.com", "Otro " + suffix);
        long otherBusinessId = other.path("user").path("businessId").asLong();

        ObjectNodeBuilder body = new ObjectNodeBuilder()
                .put("firstName", "Luis").put("lastName", "Gómez")
                .put("email", "biz-" + suffix + "@example.com")
                .put("password", PASSWORD).put("role", "ADMIN")
                .put("businessId", otherBusinessId);
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.json()))
                .andExpect(status().isCreated());

        User stored = userRepository.findByEmailIgnoreCase("biz-" + suffix + "@example.com").orElseThrow();
        assertThat(stored.getBusiness().getId()).isEqualTo(ownerBusinessId).isNotEqualTo(otherBusinessId);
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        CreateUserRequest request = new CreateUserRequest("L", "", "no-es-email", "corta", null);
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ---------- Listado y aislamiento ----------

    @Test
    void ownerListsOnlyOwnBusinessUsersWithoutSensitiveData() throws Exception {
        createUser(ownerToken, "admin-" + suffix + "@example.com", "ADMIN").andExpect(status().isCreated());
        JsonNode other = register("other-" + suffix + "@example.com", "Otro " + suffix);
        String otherToken = other.path("accessToken").asText();
        createUser(otherToken, "otheradmin-" + suffix + "@example.com", "ADMIN").andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("admin-" + suffix + "@example.com", "owner-" + suffix + "@example.com");
        assertThat(body).doesNotContain("other-" + suffix, "otheradmin-" + suffix);
        assertThat(body).doesNotContain("password", "$2");
    }

    @Test
    void sortByPasswordParameterIsIgnored() throws Exception {
        mockMvc.perform(get("/api/users?sort=password,desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminAndStaffCannotListUsers() throws Exception {
        for (String role : new String[]{"ADMIN", "STAFF"}) {
            String token = createAndLogin(role);
            mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void ownerCannotDeactivateAnotherBusinessUserEvenWithManipulatedId() throws Exception {
        JsonNode other = register("other-" + suffix + "@example.com", "Otro " + suffix);
        long foreignUserId = other.path("user").path("id").asLong();

        patchStatus(ownerToken, foreignUserId, false).andExpect(status().isNotFound());

        assertThat(userRepository.findById(foreignUserId).orElseThrow().getActive()).isTrue();
    }

    // ---------- Activación / desactivación ----------

    @Test
    void ownerDeactivatesAndReactivatesUser() throws Exception {
        long staffId = createUserAndGetId("staff-" + suffix + "@example.com", "STAFF");

        patchStatus(ownerToken, staffId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        patchStatus(ownerToken, staffId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    void ownerCannotDeactivateSelfNorLastOwner() throws Exception {
        long ownerId = owner.path("user").path("id").asLong();
        patchStatus(ownerToken, ownerId, false).andExpect(status().isConflict());
        assertThat(userRepository.findById(ownerId).orElseThrow().getActive()).isTrue();
    }

    @Test
    void secondOwnerCanDeactivateFirstButNotItself() throws Exception {
        // Segundo OWNER sembrado directamente: el endpoint no permite crearlos.
        User second = new User();
        second.setFirstName("Segundo");
        second.setLastName("Owner");
        second.setEmail("second-" + suffix + "@example.com");
        second.setPassword(passwordEncoder.encode(PASSWORD));
        second.setRole(UserRole.OWNER);
        second.setBusiness(userRepository.findById(owner.path("user").path("id").asLong())
                .orElseThrow().getBusiness());
        userRepository.save(second);
        String secondToken = login("second-" + suffix + "@example.com", PASSWORD)
                .path("data").path("accessToken").asText();

        long firstOwnerId = owner.path("user").path("id").asLong();
        patchStatus(secondToken, firstOwnerId, false).andExpect(status().isOk());
        // El segundo OWNER es ahora el único activo y tampoco puede desactivarse a sí mismo.
        patchStatus(secondToken, second.getId(), false).andExpect(status().isConflict());
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        String email = "staff-" + suffix + "@example.com";
        long staffId = createUserAndGetId(email, "STAFF");
        patchStatus(ownerToken, staffId, false).andExpect(status().isOk());

        postLogin(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void jwtOfDeactivatedUserStopsWorkingImmediately() throws Exception {
        String email = "admin-" + suffix + "@example.com";
        long adminId = createUserAndGetId(email, "ADMIN");
        String adminToken = login(email, PASSWORD).path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());

        patchStatus(ownerToken, adminId, false).andExpect(status().isOk());

        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAndStaffCannotChangeUserStatus() throws Exception {
        long staffId = createUserAndGetId("staff-" + suffix + "@example.com", "STAFF");
        for (String role : new String[]{"ADMIN", "STAFF"}) {
            String token = createAndLogin(role);
            patchStatus(token, staffId, false).andExpect(status().isForbidden());
        }
    }

    // ---------- Autenticación ----------

    @Test
    void adminAndStaffCanLogInAndReceiveTheirRole() throws Exception {
        for (String role : new String[]{"ADMIN", "STAFF"}) {
            String email = role.toLowerCase() + "-login-" + suffix + "@example.com";
            createUser(ownerToken, email, role).andExpect(status().isCreated());
            String token = login(email, PASSWORD).path("data").path("accessToken").asText();

            mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value(role))
                    .andExpect(jsonPath("$.data.businessId").value(ownerBusinessId));
        }
    }

    @Test
    void existingOwnerStillLogsInWithOwnerRole() throws Exception {
        postLogin("owner-" + suffix + "@example.com", "supersecret123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("OWNER"));
    }

    // ---------- Matriz de permisos sobre los endpoints existentes ----------

    @Test
    void ownerKeepsOperationalAccess() throws Exception {
        for (String path : new String[]{"/api/employees", "/api/services", "/api/customers",
                "/api/appointments", "/api/dashboard", "/api/conversations", "/api/settings/business"}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void adminAccessesOperationalModulesButNotBusinessSettingsWrite() throws Exception {
        String adminToken = createAndLogin("ADMIN");
        for (String path : new String[]{"/api/employees", "/api/services", "/api/customers",
                "/api/appointments", "/api/dashboard", "/api/conversations", "/api/settings/business"}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put("/api/settings/business")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffIsDeniedOnEveryOperationalModuleUntilPermissionsAreDefined() throws Exception {
        String staffToken = createAndLogin("STAFF");
        for (String path : new String[]{"/api/employees", "/api/services", "/api/customers",
                "/api/appointments", "/api/dashboard", "/api/conversations", "/api/settings/business",
                "/api/schedules", "/api/users"}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(delete("/api/employees/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken))
                .andExpect(status().isOk());
    }

    @Test
    void unclassifiedEndpointsAreOwnerOnly() throws Exception {
        String adminToken = createAndLogin("ADMIN");
        mockMvc.perform(get("/api/unclassified-future-endpoint")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicEndpointsRemainPublic() throws Exception {
        postLogin("nadie-" + suffix + "@example.com", "x-no-importa-1").andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/webhooks/whatsapp")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    // ---------- helpers ----------

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

    private ResultActions createUser(String token, String email, String role) throws Exception {
        String json = new ObjectNodeBuilder()
                .put("firstName", "Luis").put("lastName", "Gómez")
                .put("email", email).put("password", PASSWORD).put("role", role).json();
        MockHttpServletRequestBuilder builder = post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private long createUserAndGetId(String email, String role) throws Exception {
        String response = createUser(ownerToken, email, role)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private String createAndLogin(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        createUser(ownerToken, email, role).andExpect(status().isCreated());
        return login(email, PASSWORD).path("data").path("accessToken").asText();
    }

    private ResultActions patchStatus(String token, long userId, boolean active) throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest(active);
        return mockMvc.perform(patch("/api/users/" + userId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions postLogin(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private JsonNode login(String email, String password) throws Exception {
        String response = postLogin(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    /** Construye JSON a mano para poder enviar campos que el DTO no admite (p. ej. businessId). */
    private final class ObjectNodeBuilder {
        private final com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();

        ObjectNodeBuilder put(String key, String value) {
            node.put(key, value);
            return this;
        }

        ObjectNodeBuilder put(String key, long value) {
            node.put(key, value);
            return this;
        }

        String json() {
            return node.toString();
        }
    }

}
