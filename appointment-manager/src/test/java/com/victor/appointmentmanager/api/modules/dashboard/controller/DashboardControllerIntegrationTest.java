package com.victor.appointmentmanager.api.modules.dashboard.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import com.victor.appointmentmanager.api.modules.employees.dto.request.CreateEmployeeRequest;
import com.victor.appointmentmanager.api.modules.schedule.dto.request.CreateScheduleRequest;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class DashboardControllerIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Asuncion");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("dashboard-" + suffix + "@example.com", "Negocio " + suffix);
        token = owner.path("accessToken").asText();
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

    private long createService(String token, String name) throws Exception {
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

    private long createEmployee(String token, String firstName, String phone, Set<Long> serviceIds)
            throws Exception {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setFirstName(firstName);
        request.setLastName("Pérez");
        request.setPhone(phone);
        request.setColor("#3B82F6");
        request.setServiceIds(serviceIds);

        String response = mockMvc.perform(post("/api/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private long createCustomer(String token, String firstName, String phone) throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFirstName(firstName);
        request.setLastName("Gómez");
        request.setPhone(phone);

        String response = mockMvc.perform(post("/api/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void createFullDaySchedule(String token, long employeeId, DayOfWeek dayOfWeek) throws Exception {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(employeeId);
        request.setDayOfWeek(dayOfWeek);
        request.setStartTime(LocalTime.of(6, 0));
        request.setEndTime(LocalTime.of(22, 0));

        mockMvc.perform(post("/api/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private long createAppointment(String token, long customerId, long employeeId, long serviceId, Instant startAt)
            throws Exception {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setCustomerId(customerId);
        request.setEmployeeId(employeeId);
        request.setServiceId(serviceId);
        request.setStartAt(startAt);

        String response = mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOkWithExpectedShapeForBusinessWithoutData() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.todayAppointments").value(0))
                .andExpect(jsonPath("$.data.activeCustomers").value(0))
                .andExpect(jsonPath("$.data.activeEmployees").value(0))
                .andExpect(jsonPath("$.data.activeServices").value(0))
                .andExpect(jsonPath("$.data.upcomingAppointments").isArray())
                .andExpect(jsonPath("$.data.upcomingAppointments").isEmpty());
    }

    @Test
    void countsReflectActiveCustomersEmployeesAndServicesAndIgnoresBusinessIdParam() throws Exception {
        long serviceId = createService(token, "Corte");
        createEmployee(token, "Juan", "+595981111111", Set.of(serviceId));
        createCustomer(token, "Ana", "+595982222222");

        mockMvc.perform(get("/api/dashboard?businessId=999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCustomers").value(1))
                .andExpect(jsonPath("$.data.activeEmployees").value(1))
                .andExpect(jsonPath("$.data.activeServices").value(1));
    }

    @Test
    void upcomingAppointmentsContainsOnlyActiveFutureAppointmentsWithExpectedFields() throws Exception {
        long serviceId = createService(token, "Corte Premium");
        long employeeId = createEmployee(token, "Juan", "+595983333333", Set.of(serviceId));
        long customerId = createCustomer(token, "Cristian", "+595984444444");

        LocalDate nextMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        createFullDaySchedule(token, employeeId, DayOfWeek.MONDAY);

        Instant keptAt = ZonedDateTime.of(nextMonday, LocalTime.of(13, 0), ZONE).toInstant();
        Instant cancelledAt = ZonedDateTime.of(nextMonday, LocalTime.of(15, 0), ZONE).toInstant();

        long keptId = createAppointment(token, customerId, employeeId, serviceId, keptAt);
        long cancelledId = createAppointment(token, customerId, employeeId, serviceId, cancelledAt);

        mockMvc.perform(patch("/api/appointments/" + cancelledId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcomingAppointments.length()").value(1))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].id").value(keptId))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].customerId").value(customerId))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].customerName").value("Cristian Gómez"))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].employeeId").value(employeeId))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].employeeName").value("Juan Pérez"))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].serviceId").value(serviceId))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].serviceName").value("Corte Premium"))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].startAt").value(keptAt.toString()))
                .andExpect(jsonPath("$.data.upcomingAppointments[0].status").value("CONFIRMED"));
    }

    @Test
    void isolatesDashboardDataByAuthenticatedBusiness() throws Exception {
        createService(token, "Corte");
        createCustomer(token, "Ana", "+595985555555");

        JsonNode otherOwner = register("dashboard-other-" + UUID.randomUUID() + "@example.com", "Otro Negocio");
        String otherToken = otherOwner.path("accessToken").asText();

        mockMvc.perform(get("/api/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeCustomers").value(0))
                .andExpect(jsonPath("$.data.activeServices").value(0))
                .andExpect(jsonPath("$.data.upcomingAppointments").isEmpty());
    }

}
