package com.victor.appointmentmanager.api.modules.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reproduce el bug ET-016: la IA confirmaba una reserva ("reply" de confirmación) sin que
 * AppointmentService hubiera persistido ningún Appointment. Estas pruebas ejercitan el flujo
 * conversacional completo (ConversationServiceImpl real + AppointmentServiceImpl real + H2)
 * sustituyendo únicamente el AiProvider por un mock controlado, para verificar que
 * ConversationResponse.appointmentId siempre corresponde a una cita realmente persistida.
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
class ConversationBookingIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Asuncion");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiProvider aiProvider;

    private String token;
    private String customerPhone;
    private long serviceId;
    private Instant slotStart;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString();
        JsonNode owner = register("ai-booking-" + suffix + "@example.com", "Negocio IA " + suffix);
        token = owner.path("accessToken").asText();

        serviceId = createService("Corte");
        long employeeId = createEmployee("+59598" + suffix.substring(0, 7), Set.of(serviceId));
        customerPhone = "+59597" + suffix.substring(0, 7);
        createCustomer(customerPhone);
        createFullDaySchedule(employeeId);

        LocalDate nextMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        slotStart = ZonedDateTime.of(nextMonday, LocalTime.of(9, 0), ZONE).toInstant();
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

    private long createEmployee(String phone, Set<Long> serviceIds) throws Exception {
        CreateEmployeeRequest request = new CreateEmployeeRequest();
        request.setFirstName("Juan");
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

    private void createCustomer(String phone) throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFirstName("Ana");
        request.setLastName("Gómez");
        request.setPhone(phone);

        mockMvc.perform(post("/api/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
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

    private void stubAiBookingReply(Instant startAt) {
        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: " + startAt
                        + "\nCONFIDENCE: 0.9\nREPLY: Tu cita ha sido confirmada.");
    }

    private String converse(String message) throws Exception {
        ConversationRequest request = new ConversationRequest();
        request.setCustomerPhone(customerPhone);
        request.setMessage(message);

        return mockMvc.perform(post("/api/ai/conversation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void bookAppointmentIntentPersistsConfirmedAppointmentWithRealId() throws Exception {
        stubAiBookingReply(slotStart);

        String response = converse("Quiero reservar un corte el lunes a las 9");
        JsonNode data = objectMapper.readTree(response).path("data");

        assertThat(data.path("intent").asText()).isEqualTo("BOOK_APPOINTMENT");
        assertThat(data.path("reply").asText()).contains("confirmada");
        long appointmentId = data.path("appointmentId").asLong();
        assertThat(appointmentId).isPositive();

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(appointmentId))
                .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"));
    }

    @Test
    void cancelledAppointmentDoesNotBlockNewBookingAndCreatesNewAppointment() throws Exception {
        stubAiBookingReply(slotStart);

        String firstResponse = converse("Quiero reservar un corte el lunes a las 9");
        long firstAppointmentId = objectMapper.readTree(firstResponse).path("data").path("appointmentId").asLong();
        assertThat(firstAppointmentId).isPositive();

        mockMvc.perform(patch("/api/appointments/" + firstAppointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        String secondResponse = converse("Quiero reservar otro corte el lunes a las 9");
        JsonNode secondData = objectMapper.readTree(secondResponse).path("data");
        long secondAppointmentId = secondData.path("appointmentId").asLong();

        assertThat(secondAppointmentId).isPositive();
        assertThat(secondAppointmentId).isNotEqualTo(firstAppointmentId);
        assertThat(secondData.path("reply").asText()).contains("confirmada");

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/appointments/" + firstAppointmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/appointments/" + secondAppointmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void unavailableSlotDoesNotConfirmBookingDespiteAiConfirmationText() throws Exception {
        stubAiBookingReply(slotStart);
        converse("Quiero reservar un corte el lunes a las 9");

        // El modelo redacta el mismo texto de "confirmada" aunque el horario ya esté ocupado;
        // la regla de negocio (superposición) debe rechazar la segunda reserva igualmente.
        String secondResponse = converse("Quiero reservar otro corte el lunes a las 9");
        JsonNode secondData = objectMapper.readTree(secondResponse).path("data");

        assertThat(secondData.path("appointmentId").isNull()).isTrue();
        assertThat(secondData.path("reply").asText()).doesNotContain("Tu cita ha sido confirmada.");

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void localTimeStatedByCustomerIsNotPersistedAsThatSameUtcTime() throws Exception {
        // Reproduce el bug ET-018: la IA (mock) devuelve la hora exactamente como la dijo el
        // cliente, sin convertirla ("10 de agosto de 2026 a las 15:00" es un lunes, cubierto
        // por el horario del empleado). El backend debe interpretarla en America/Asuncion,
        // NO como si fuera UTC.
        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 10 de agosto de 2026 a las 15:00\n"
                        + "CONFIDENCE: 0.9\nREPLY: no importa, el backend decide el mensaje final.");

        String response = converse(
                "Quiero reservar un Corte Premium con Juan Gómez el 10 de agosto de 2026 a las 15:00.");
        JsonNode data = objectMapper.readTree(response).path("data");

        long appointmentId = data.path("appointmentId").asLong();
        assertThat(appointmentId).isPositive();
        assertThat(data.path("reply").asText()).contains("15:00");

        Instant expectedUtc = ZonedDateTime.of(
                LocalDate.of(2026, 8, 10), LocalTime.of(15, 0), ZONE).toInstant();
        assertThat(expectedUtc).isNotEqualTo(Instant.parse("2026-08-10T15:00:00Z"));

        String appointmentResponse = mockMvc.perform(get("/api/appointments/" + appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant persistedStartAt = Instant.parse(
                objectMapper.readTree(appointmentResponse).path("data").path("startAt").asText());

        assertThat(persistedStartAt).isEqualTo(expectedUtc);
        assertThat(persistedStartAt).isNotEqualTo(Instant.parse("2026-08-10T15:00:00Z"));
        assertThat(persistedStartAt.atZone(ZONE).toLocalTime()).isEqualTo(LocalTime.of(15, 0));
    }

}
