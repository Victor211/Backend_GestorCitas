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
 * Ejercita el flujo conversacional completo (ConversationServiceImpl real + AppointmentServiceImpl
 * real + H2) sustituyendo únicamente el AiProvider por un mock controlado. Desde el tuning
 * conversacional, ninguna reserva se crea en el mismo turno en el que se completan los datos:
 * primero se propone una confirmación y solo un "Sí" posterior (segundo turno) persiste el
 * Appointment. Los tests existentes se adaptaron a ese segundo turno; los nuevos (CASO A-D) cubren
 * cliente nuevo multi-turno, cliente existente en un solo mensaje, rechazo y cambio de dato
 * durante la confirmación.
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

    private void stubAiBookingReply(String userMessage, Instant startAt) {
        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq(userMessage))).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: " + startAt
                        + "\nCONFIDENCE: 0.9\nREPLY: no importa, el backend decide el mensaje final.");
    }

    private String converse(String phone, String message) throws Exception {
        ConversationRequest request = new ConversationRequest();
        request.setCustomerPhone(phone);
        request.setMessage(message);

        return mockMvc.perform(post("/api/ai/conversation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String converse(String message) throws Exception {
        return converse(customerPhone, message);
    }

    private JsonNode confirm(String phone) throws Exception {
        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("Sí")))
                .thenReturn("INTENT: UNKNOWN\nCONFIDENCE: 0.0\nREPLY: no debería usarse");
        String response = converse(phone, "Sí");
        return objectMapper.readTree(response).path("data");
    }

    private long proposeAndConfirm(String phone, String bookingMessage, Instant startAt) throws Exception {
        stubAiBookingReply(bookingMessage, startAt);
        String proposalResponse = converse(phone, bookingMessage);
        JsonNode proposal = objectMapper.readTree(proposalResponse).path("data");
        assertThat(proposal.path("reply").asText()).contains("¿Confirmás?");
        assertThat(proposal.path("appointmentId").isNull()).isTrue();

        JsonNode confirmed = confirm(phone);
        return confirmed.path("appointmentId").asLong();
    }

    @Test
    void bookAppointmentIsNotCreatedUntilExplicitConfirmationThenPersistsWithRealId() throws Exception {
        long appointmentId = proposeAndConfirm(customerPhone, "Quiero reservar un corte el lunes a las 9", slotStart);

        assertThat(appointmentId).isPositive();

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(appointmentId))
                .andExpect(jsonPath("$.data.content[0].status").value("CONFIRMED"));
    }

    @Test
    void resetCommandNeverInvokesOpenAiAndDoesNotAffectPersistedCustomerOrAppointments() throws Exception {
        long appointmentId = proposeAndConfirm(customerPhone, "Quiero reservar un corte el lunes a las 9", slotStart);
        assertThat(appointmentId).isPositive();

        String resetResponse = converse("/reset");
        JsonNode resetData = objectMapper.readTree(resetResponse).path("data");
        assertThat(resetData.path("reply").asText()).isEqualTo("Conversación reiniciada.");
        org.mockito.Mockito.verify(aiProvider, org.mockito.Mockito.never())
                .generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("/reset"));

        // La cita creada antes del reset sigue existiendo (/reset solo limpia el borrador
        // conversacional, nunca Appointments).
        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(appointmentId));

        // El Customer identificado por sender_phone sigue existiendo (/reset no lo toca).
        mockMvc.perform(get("/api/customers/by-phone").param("phone", customerPhone)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(customerPhone));

        // Un "Hola" después del reset arranca una conversación limpia, no continúa la reserva
        // ya confirmada.
        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("Hola")))
                .thenReturn("INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: ¿En qué puedo ayudarte?");
        String greetingResponse = converse("Hola");
        JsonNode greetingData = objectMapper.readTree(greetingResponse).path("data");
        assertThat(greetingData.path("reply").asText()).doesNotContain("¿Confirmás?");
    }

    @Test
    void cancelledAppointmentDoesNotBlockNewBookingAndCreatesNewAppointment() throws Exception {
        long firstAppointmentId = proposeAndConfirm(
                customerPhone, "Quiero reservar un corte el lunes a las 9", slotStart);
        assertThat(firstAppointmentId).isPositive();

        mockMvc.perform(patch("/api/appointments/" + firstAppointmentId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        long secondAppointmentId = proposeAndConfirm(
                customerPhone, "Quiero reservar otro corte el lunes a las 9", slotStart);

        assertThat(secondAppointmentId).isPositive();
        assertThat(secondAppointmentId).isNotEqualTo(firstAppointmentId);

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
    void slotTakenBeforeProposalIsDetectedImmediatelyInsteadOfOnlyAtConfirmation() throws Exception {
        long firstAppointmentId = proposeAndConfirm(
                customerPhone, "Quiero reservar un corte el lunes a las 9", slotStart);
        assertThat(firstAppointmentId).isPositive();

        // AJUSTE 6/9: un segundo cliente que propone el mismo horario ya ocupado ahora lo detecta
        // en el mismo turno de la propuesta (disponibilidad real, no solo capacidad del servicio),
        // en vez de recién enterarse al confirmar.
        String otherPhone = customerPhone + "9";
        createCustomer(otherPhone);
        stubAiBookingReply("Quiero reservar otro corte el lunes a las 9", slotStart);
        String secondProposalResponse = converse(otherPhone, "Quiero reservar otro corte el lunes a las 9");
        JsonNode secondProposal = objectMapper.readTree(secondProposalResponse).path("data");
        assertThat(secondProposal.path("reply").asText()).contains("disponibilidad");
        assertThat(secondProposal.path("appointmentId").isNull()).isTrue();

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void localTimeStatedByCustomerIsNotPersistedAsThatSameUtcTime() throws Exception {
        // Reproduce el bug ET-018: la IA (mock) devuelve la hora exactamente como la dijo el
        // cliente, sin convertirla. Se usa el próximo lunes (cubierto por el horario del
        // empleado creado en setUp) para que la fecha nunca quede en el pasado. El backend debe
        // interpretarla en America/Asuncion, NO como si fuera UTC.
        LocalDate futureMonday = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        String spanishDate = java.time.format.DateTimeFormatter
                .ofPattern("d 'de' MMMM 'de' uuuu", java.util.Locale.of("es", "ES"))
                .format(futureMonday);
        String message = "Quiero reservar un Corte Premium con Juan Gómez el " + spanishDate + " a las 15:00.";
        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq(message))).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: " + spanishDate + " a las 15:00\n"
                        + "CONFIDENCE: 0.9\nREPLY: no importa, el backend decide el mensaje final.");

        String proposalResponse = converse(message);
        JsonNode proposal = objectMapper.readTree(proposalResponse).path("data");
        assertThat(proposal.path("reply").asText()).contains("15:00");

        JsonNode confirmed = confirm(customerPhone);
        long appointmentId = confirmed.path("appointmentId").asLong();
        assertThat(appointmentId).isPositive();

        Instant expectedUtc = ZonedDateTime.of(futureMonday, LocalTime.of(15, 0), ZONE).toInstant();
        assertThat(expectedUtc).isNotEqualTo(Instant.parse(futureMonday + "T15:00:00Z"));

        String appointmentResponse = mockMvc.perform(get("/api/appointments/" + appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant persistedStartAt = Instant.parse(
                objectMapper.readTree(appointmentResponse).path("data").path("startAt").asText());

        assertThat(persistedStartAt).isEqualTo(expectedUtc);
        assertThat(persistedStartAt).isNotEqualTo(Instant.parse(futureMonday + "T15:00:00Z"));
        assertThat(persistedStartAt.atZone(ZONE).toLocalTime()).isEqualTo(LocalTime.of(15, 0));
    }

    // ---------------------------------------------------------------------------------------
    // CASO A / TEST B: cliente nuevo, multi-turno completo. Customer no es prerequisito para
    // recolectar servicio/fecha/hora: se identifica recién después de la confirmación positiva,
    // en la etapa dedicada AWAITING_CUSTOMER_NAME, sin volver a pedir "¿Confirmás?".
    // ---------------------------------------------------------------------------------------

    @Test
    void newCustomerFullConversationCreatesExactlyOneAppointment() throws Exception {
        String newPhone = customerPhone + "-nuevo";

        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("Hola, quiero reservar un turno")))
                .thenReturn("INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");
        String firstResponse = converse(newPhone, "Hola, quiero reservar un turno");
        JsonNode first = objectMapper.readTree(firstResponse).path("data");
        // Sin servicio identificado se muestra el catálogo; nunca se pide el nombre en este punto.
        assertThat(first.path("reply").asText()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(first.path("reply").asText()).contains("Corte");

        stubAiBookingReply("Quiero un corte el lunes a las 9", slotStart);
        String secondResponse = converse(newPhone, "Quiero un corte el lunes a las 9");
        JsonNode second = objectMapper.readTree(secondResponse).path("data");
        assertThat(second.path("reply").asText()).contains("¿Confirmás?");
        assertThat(second.path("appointmentId").isNull()).isTrue();

        JsonNode afterYes = confirm(newPhone);
        assertThat(afterYes.path("reply").asText()).contains("¿Cuál es tu nombre?");
        assertThat(afterYes.path("reply").asText().toLowerCase()).doesNotContain("teléfono");
        assertThat(afterYes.path("appointmentId").isNull()).isTrue();

        String nameResponse = converse(newPhone, "María López");
        JsonNode named = objectMapper.readTree(nameResponse).path("data");
        // El "sí" anterior sigue siendo válido: no se vuelve a preguntar "¿Confirmás?".
        assertThat(named.path("reply").asText()).doesNotContain("¿Confirmás?");
        long appointmentId = named.path("appointmentId").asLong();
        assertThat(appointmentId).isPositive();

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/customers/by-phone").param("phone", newPhone)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("María"))
                .andExpect(jsonPath("$.data.phone").value(newPhone));
    }

    // ---------------------------------------------------------------------------------------
    // Corrección: "sender_phone desconocido" no implica identificar al Customer ya mismo. Un
    // saludo o una consulta informativa NO deben crear Customer ni pedir nombre; solo la
    // intención de reservar lo requiere.
    // ---------------------------------------------------------------------------------------

    @Test
    void unknownPhoneGreetingAndInfoQueriesNeverCreateACustomerOnlyConfirmedBookingDoes() throws Exception {
        String unknownPhone = customerPhone + "-desconocido";

        long customersBefore = customersCount();

        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("Hola"))).thenReturn(
                "INTENT: GREETING\nCONFIDENCE: 0.9\n"
                        + "REPLY: ¿Te ayudo a reservar un turno o necesitas información sobre nuestros servicios?");
        String greetingResponse = converse(unknownPhone, "Hola");
        JsonNode greeting = objectMapper.readTree(greetingResponse).path("data");
        assertThat(greeting.path("reply").asText()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersCount()).isEqualTo(customersBefore);

        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("¿Qué servicios ofrecen?")))
                .thenReturn("INTENT: LIST_SERVICES\nCONFIDENCE: 0.9\nREPLY: no importa");
        String servicesResponse = converse(unknownPhone, "¿Qué servicios ofrecen?");
        JsonNode services = objectMapper.readTree(servicesResponse).path("data");
        assertThat(services.path("reply").asText()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersCount()).isEqualTo(customersBefore);

        // Una intención de reserva tampoco identifica al Customer todavía: se recolecta
        // servicio/fecha/hora igual que con un cliente conocido, y recién se pide el nombre
        // después de la confirmación positiva.
        stubAiBookingReply("Quiero un corte el lunes a las 9", slotStart);
        String proposalResponse = converse(unknownPhone, "Quiero un corte el lunes a las 9");
        JsonNode proposal = objectMapper.readTree(proposalResponse).path("data");
        assertThat(proposal.path("reply").asText()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(proposal.path("reply").asText()).contains("¿Confirmás?");
        assertThat(customersCount()).isEqualTo(customersBefore);

        JsonNode afterYes = confirm(unknownPhone);
        assertThat(afterYes.path("reply").asText()).contains("¿Cuál es tu nombre?");
        assertThat(customersCount()).isEqualTo(customersBefore);

        converse(unknownPhone, "María López");
        assertThat(customersCount()).isEqualTo(customersBefore + 1);
    }

    private long customersCount() throws Exception {
        String response = mockMvc.perform(get("/api/customers?page=0&size=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("totalElements").asLong();
    }

    // ---------------------------------------------------------------------------------------
    // CASO B: cliente existente, mensaje único ya completo -> solo pregunta confirmación
    // ---------------------------------------------------------------------------------------

    @Test
    void existingCustomerCompleteMessageOnlyAsksForConfirmationThenBooks() throws Exception {
        String message = "Quiero reservar el lunes a las 9 con Juan para un Corte";
        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq(message))).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nEMPLOYEE_NAME: Juan\nSTART_AT: " + slotStart
                        + "\nCONFIDENCE: 0.9\nREPLY: no importa");

        String response = converse(message);
        JsonNode data = objectMapper.readTree(response).path("data");

        assertThat(data.path("reply").asText())
                .doesNotContain("¿Cuál es tu nombre?")
                .contains("¿Confirmás?");
        assertThat(data.path("appointmentId").isNull()).isTrue();

        JsonNode confirmed = confirm(customerPhone);
        assertThat(confirmed.path("appointmentId").asLong()).isPositive();
    }

    // ---------------------------------------------------------------------------------------
    // CASO C: negativa -> no crea cita, el conteo de Appointments no cambia
    // ---------------------------------------------------------------------------------------

    @Test
    void rejectingTheProposalDoesNotChangeAppointmentCount() throws Exception {
        stubAiBookingReply("Quiero reservar un corte el lunes a las 9", slotStart);
        String proposalResponse = converse("Quiero reservar un corte el lunes a las 9");
        assertThat(objectMapper.readTree(proposalResponse).path("data").path("reply").asText())
                .contains("¿Confirmás?");

        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("No")))
                .thenReturn("INTENT: UNKNOWN\nCONFIDENCE: 0.0\nREPLY: no debería usarse");
        String rejectionResponse = converse("No");
        JsonNode rejected = objectMapper.readTree(rejectionResponse).path("data");
        assertThat(rejected.path("appointmentId").isNull()).isTrue();

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ---------------------------------------------------------------------------------------
    // CASO D: cambio de horario durante la confirmación -> no crea la propuesta vieja
    // ---------------------------------------------------------------------------------------

    @Test
    void changingTheProposedTimeDuringConfirmationBooksTheNewTimeNotTheOldOne() throws Exception {
        stubAiBookingReply("Quiero reservar un corte el lunes a las 9", slotStart);
        converse("Quiero reservar un corte el lunes a las 9");

        String changeMessage = "Mejor a las 11";
        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq(changeMessage))).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 11\nCONFIDENCE: 0.6\nREPLY: no importa");
        String changeResponse = converse(changeMessage);
        JsonNode changed = objectMapper.readTree(changeResponse).path("data");
        assertThat(changed.path("reply").asText()).contains("11:00").contains("¿Confirmás?");
        assertThat(changed.path("appointmentId").isNull()).isTrue();

        JsonNode confirmed = confirm(customerPhone);
        long appointmentId = confirmed.path("appointmentId").asLong();
        assertThat(appointmentId).isPositive();

        String appointmentResponse = mockMvc.perform(get("/api/appointments/" + appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant persistedStartAt = Instant.parse(
                objectMapper.readTree(appointmentResponse).path("data").path("startAt").asText());
        assertThat(persistedStartAt.atZone(ZONE).toLocalTime()).isEqualTo(LocalTime.of(11, 0));

        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ---------------------------------------------------------------------------------------
    // TEST H: bug histórico. Un mensaje que corrige/amplía el servicio nunca debe interpretarse
    // como el nombre del cliente, esté o no identificado el Customer.
    // ---------------------------------------------------------------------------------------

    @Test
    void serviceCorrectionMessageIsNeverMisreadAsCustomerNameAndCustomerIsNotCreatedPrematurely() throws Exception {
        String newPhone = customerPhone + "-bug-historico";

        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("Hola quiero reservar un corte")))
                .thenReturn("INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nCONFIDENCE: 0.8\nREPLY: no importa");
        String firstResponse = converse(newPhone, "Hola quiero reservar un corte");
        assertThat(objectMapper.readTree(firstResponse).path("data").path("reply").asText())
                .doesNotContain("¿Cuál es tu nombre?");

        long customersBefore = customersCount();

        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("corte + barba")))
                .thenReturn("INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nCONFIDENCE: 0.6\nREPLY: no importa");
        String secondResponse = converse(newPhone, "corte + barba");

        assertThat(objectMapper.readTree(secondResponse).path("data").path("reply").asText())
                .doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersCount()).isEqualTo(customersBefore);
    }

    // ---------------------------------------------------------------------------------------
    // TEST J: la consulta previa (propuesta) y la revalidación final (confirmación) comparten
    // exactamente la misma fuente de verdad de disponibilidad (AppointmentService#isAvailable),
    // así que sin ningún cambio externo entre ambos turnos nunca debe aparecer un falso "ese
    // horario acaba de dejar de estar disponible".
    // ---------------------------------------------------------------------------------------

    @Test
    void proposalAndConfirmationAgreeOnAvailabilityWithNoExternalChange() throws Exception {
        stubAiBookingReply("Quiero reservar un corte el lunes a las 9", slotStart);
        String proposalResponse = converse("Quiero reservar un corte el lunes a las 9");
        assertThat(objectMapper.readTree(proposalResponse).path("data").path("reply").asText())
                .contains("¿Confirmás?");

        JsonNode confirmed = confirm(customerPhone);

        assertThat(confirmed.path("reply").asText()).doesNotContain("dejar de estar disponible");
        assertThat(confirmed.path("appointmentId").asLong()).isPositive();
    }

    // ---------------------------------------------------------------------------------------
    // TEST G (ampliado): /reset también limpia AWAITING_CUSTOMER_NAME, no solo
    // AWAITING_CONFIRMATION. Un nombre tardío después del reset no debe crear Customer ni cita.
    // ---------------------------------------------------------------------------------------

    @Test
    void resetDuringAwaitingCustomerNameClearsStateWithoutCreatingCustomerOrAppointment() throws Exception {
        String newPhone = customerPhone + "-reset-nombre";
        stubAiBookingReply("Quiero un corte el lunes a las 9", slotStart);
        converse(newPhone, "Quiero un corte el lunes a las 9");

        JsonNode afterYes = confirm(newPhone);
        assertThat(afterYes.path("reply").asText()).contains("¿Cuál es tu nombre?");

        String resetResponse = converse(newPhone, "/reset");
        assertThat(objectMapper.readTree(resetResponse).path("data").path("reply").asText())
                .isEqualTo("Conversación reiniciada.");

        long customersBefore = customersCount();
        when(aiProvider.generateResponse(anyString(), org.mockito.ArgumentMatchers.eq("María López")))
                .thenReturn("INTENT: UNKNOWN\nCONFIDENCE: 0.2\nREPLY: no logré entenderte");
        converse(newPhone, "María López");

        assertThat(customersCount()).isEqualTo(customersBefore);
        mockMvc.perform(get("/api/appointments?page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

}
