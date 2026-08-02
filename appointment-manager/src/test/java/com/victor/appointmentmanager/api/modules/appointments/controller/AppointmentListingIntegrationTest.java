package com.victor.appointmentmanager.api.modules.appointments.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integración del listado paginado de citas (GET /api/appointments), enfocadas en
 * el bug ET-014: PostgreSQL fallaba con SQLState 42P18 ("no se pudo determinar el tipo del
 * parámetro") cuando los filtros opcionales llegaban null a expresiones JPQL del tipo
 * ":param IS NULL OR ...". El fix reemplaza esa consulta por JpaSpecificationExecutor +
 * Specifications construidas dinámicamente, que nunca vinculan un parámetro null.
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
class AppointmentListingIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Asuncion");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private long employee1Id;
    private long employee2Id;
    private long customer1Id;
    private long customer2Id;

    private Instant appt1At;
    private Instant appt2At;
    private Instant appt3At;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("appointments-" + suffix + "@example.com", "Negocio " + suffix);
        token = owner.path("accessToken").asText();

        long serviceId = createService("Corte");
        employee1Id = createEmployee("Juan", "+59598" + suffix.substring(0, 7), Set.of(serviceId));
        employee2Id = createEmployee("Pedro", "+59599" + suffix.substring(0, 7), Set.of(serviceId));
        customer1Id = createCustomer("Ana", "+59597" + suffix.substring(0, 7));
        customer2Id = createCustomer("Lucía", "+59596" + suffix.substring(0, 7));

        createFullDaySchedule(employee1Id);
        createFullDaySchedule(employee2Id);

        LocalDate nextMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        appt1At = atMonday(nextMonday, 9, 0);
        appt2At = atMonday(nextMonday, 10, 0);
        appt3At = atMonday(nextMonday, 11, 30);

        createAppointment(customer1Id, employee1Id, serviceId, appt1At);
        createAppointment(customer2Id, employee2Id, serviceId, appt2At);
        createAppointment(customer2Id, employee1Id, serviceId, appt3At);
    }

    private Instant atMonday(LocalDate monday, int hour, int minute) {
        return ZonedDateTime.of(monday, LocalTime.of(hour, minute), ZONE).toInstant();
    }

    private String formatInstant(Instant instant) {
        return instant.toString();
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

    private long createEmployee(String firstName, String phone, Set<Long> serviceIds) throws Exception {
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

    private long createCustomer(String firstName, String phone) throws Exception {
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

    private void createFullDaySchedule(long employeeId) throws Exception {
        CreateScheduleRequest request = new CreateScheduleRequest();
        request.setEmployeeId(employeeId);
        request.setDayOfWeek(DayOfWeek.MONDAY);
        request.setStartTime(LocalTime.of(6, 0));
        request.setEndTime(LocalTime.of(22, 0));

        mockMvc.perform(post("/api/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void createAppointment(long customerId, long employeeId, long serviceId, Instant startAt)
            throws Exception {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setCustomerId(customerId);
        request.setEmployeeId(employeeId);
        request.setServiceId(serviceId);
        request.setStartAt(startAt);

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void listsAppointmentsWithoutAnyFilters() throws Exception {
        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].id").exists())
                .andExpect(jsonPath("$.data.content[0].startAt").value(formatInstant(appt1At)))
                .andExpect(jsonPath("$.data.content[1].startAt").value(formatInstant(appt2At)))
                .andExpect(jsonPath("$.data.content[2].startAt").value(formatInstant(appt3At)));
    }

    @Test
    void listsAppointmentsFilteredByEmployeeId() throws Exception {
        mockMvc.perform(get("/api/appointments?page=0&size=10&employeeId=" + employee1Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listsAppointmentsFilteredByCustomerId() throws Exception {
        mockMvc.perform(get("/api/appointments?page=0&size=10&customerId=" + customer2Id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listsAppointmentsFilteredByStatus() throws Exception {
        mockMvc.perform(get("/api/appointments?page=0&size=10&status=CONFIRMED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(get("/api/appointments?page=0&size=10&status=CANCELLED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listsAppointmentsFilteredOnlyByFrom() throws Exception {
        mockMvc.perform(get("/api/appointments?page=0&size=10&from=" + formatInstant(appt2At))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listsAppointmentsFilteredOnlyByTo() throws Exception {
        Instant to = appt1At.plusSeconds(1800);

        mockMvc.perform(get("/api/appointments?page=0&size=10&to=" + formatInstant(to))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listsAppointmentsFilteredByFromAndTo() throws Exception {
        Instant from = appt1At.plusSeconds(1800);
        Instant to = appt2At.plusSeconds(1800);

        mockMvc.perform(get("/api/appointments?page=0&size=10&from=" + formatInstant(from)
                        + "&to=" + formatInstant(to))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listsAppointmentsWithMultipleCombinedFilters() throws Exception {
        mockMvc.perform(get("/api/appointments?page=0&size=10&employeeId=" + employee1Id + "&status=CONFIRMED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void returnsBadRequestWhenFromIsAfterTo() throws Exception {
        mockMvc.perform(get("/api/appointments?page=0&size=10&from=" + formatInstant(appt3At)
                        + "&to=" + formatInstant(appt1At))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isolatesResultsByAuthenticatedBusiness() throws Exception {
        JsonNode otherOwner = register("appointments-other-" + UUID.randomUUID() + "@example.com", "Otro Negocio");
        String otherToken = otherOwner.path("accessToken").asText();

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listingWithoutFiltersDoesNotReturnHttp500() throws Exception {
        // Reproduce específico del bug ET-014: SQLState 42P18 ocurría con todos los filtros
        // opcionales null. Contra Postgres, la consulta JPQL anterior fallaba con 500 antes
        // de este fix; la consulta actual (JpaSpecificationExecutor + Specification dinámica)
        // nunca vincula un parámetro null en una expresión de comparación.
        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

}
