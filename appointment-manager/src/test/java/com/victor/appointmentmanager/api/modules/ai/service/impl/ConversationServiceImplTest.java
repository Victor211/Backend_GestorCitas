package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.ai.datetime.BusinessDateTimeResolver;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.entity.ConversationState;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationStage;
import com.victor.appointmentmanager.api.modules.ai.format.ConversationReplyFormatter;
import com.victor.appointmentmanager.api.modules.ai.format.GuaraniAmountFormatter;
import com.victor.appointmentmanager.api.modules.ai.prompt.SystemPromptBuilder;
import com.victor.appointmentmanager.api.modules.ai.repository.ConversationStateRepository;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.repository.AppointmentRepository;
import com.victor.appointmentmanager.api.modules.appointments.service.AppointmentService;
import com.victor.appointmentmanager.api.modules.appointments.service.AvailabilityCheck;
import com.victor.appointmentmanager.api.modules.appointments.service.AvailabilityReason;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.schedule.entity.Schedule;
import com.victor.appointmentmanager.api.modules.schedule.repository.ScheduleRepository;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private AiProvider aiProvider;

    @Mock
    private SystemPromptBuilder systemPromptBuilder;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentService appointmentService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ConversationStateRepository conversationStateRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Spy
    private BusinessDateTimeResolver businessDateTimeResolver = new BusinessDateTimeResolver();

    private ConversationServiceImpl conversationService;
    private Business business;

    private final Map<String, ConversationState> statesByKey = new HashMap<>();
    private final Map<String, Customer> customersByPhone = new HashMap<>();
    private long nextCustomerId = 100L;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Peluquería Elegance");
        business.setTimezone("America/Asuncion");

        lenient().when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        lenient().when(systemPromptBuilder.build(anyString())).thenReturn("system prompt");
        lenient().when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(Page.empty());
        lenient().when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(Page.empty());
        lenient().when(scheduleRepository.findActiveByBusiness(1L, null, null)).thenReturn(List.of());
        // Por defecto, cualquier empleado se considera disponible todo el día: los tests que
        // necesiten simular horario limitado u ocupado lo sobreescriben explícitamente.
        Schedule openAllDay = new Schedule();
        openAllDay.setStartTime(LocalTime.of(0, 0));
        openAllDay.setEndTime(LocalTime.of(23, 59));
        lenient().when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        anyLong(), any(DayOfWeek.class)))
                .thenReturn(List.of(openAllDay));
        // Única fuente de verdad de disponibilidad: por defecto cualquier empleado se considera
        // disponible (equivalente al "horario abierto todo el día" que antes simulaba el stub de
        // arriba). Los tests que necesiten simular indisponibilidad lo sobreescriben explícitamente
        // para el businessId/employeeId que corresponda.
        lenient().when(appointmentService.isAvailable(anyLong(), anyLong(), nullable(Long.class), any(Instant.class)))
                .thenReturn(true);
        // Mismo default "disponible" para la variante con motivo detallado (AJUSTE 6/7): los tests
        // que necesiten un motivo específico (ocupado, fuera de horario, no realiza el servicio) lo
        // sobreescriben explícitamente.
        lenient().when(appointmentService.checkAvailability(anyLong(), anyLong(), nullable(Long.class), any(Instant.class)))
                .thenReturn(AvailabilityCheck.AVAILABLE);
        // Sin horarios reales por defecto: los tests de listado de disponibilidad (AJUSTE 1/8/9)
        // stubean explícitamente los slots esperados.
        lenient().when(appointmentService.findAvailableSlots(anyLong(), anyLong(), anyLong(), any(LocalDate.class), anyInt()))
                .thenReturn(List.of());

        lenient().when(conversationStateRepository.findByBusinessIdAndCustomerPhone(anyLong(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(statesByKey.get(key(inv.getArgument(0), inv.getArgument(1)))));
        lenient().when(conversationStateRepository.save(any(ConversationState.class))).thenAnswer(inv -> {
            ConversationState state = inv.getArgument(0);
            statesByKey.put(key(state.getBusinessId(), state.getCustomerPhone()), state);
            return state;
        });

        lenient().when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(anyLong(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(customersByPhone.get(inv.getArgument(1))));
        lenient().when(customerRepository.findByBusinessIdAndPhone(anyLong(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(customersByPhone.get(inv.getArgument(1))));
        lenient().when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer customer = inv.getArgument(0);
            if (customer.getId() == null) {
                customer.setId(nextCustomerId++);
            }
            customersByPhone.put(customer.getPhone(), customer);
            return customer;
        });

        ConversationStateStore conversationStateStore = new ConversationStateStore(conversationStateRepository, 30L);
        CustomerIdentityResolver customerIdentityResolver = new CustomerIdentityResolver(customerRepository);
        ConfirmationClassifier confirmationClassifier = new ConfirmationClassifier();
        GuaraniAmountFormatter guaraniAmountFormatter = new GuaraniAmountFormatter();
        ConversationReplyFormatter replyFormatter = new ConversationReplyFormatter(guaraniAmountFormatter);

        conversationService = new ConversationServiceImpl(aiProvider, systemPromptBuilder, businessRepository,
                serviceRepository, employeeRepository, scheduleRepository, appointmentRepository, appointmentService,
                currentUserProvider, businessDateTimeResolver, conversationStateStore, customerIdentityResolver,
                confirmationClassifier, replyFormatter, guaraniAmountFormatter);
    }

    private static String key(Long businessId, String phone) {
        return businessId + ":" + phone;
    }

    /**
     * Simula que pasó tiempo desde el último turno, retrocediendo {@code updatedAt} del estado
     * persistido. En una app real ese campo lo actualiza Hibernate auditing en cada
     * {@code save()}; en estos tests unitarios (sin contexto JPA) hay que fijarlo a mano para
     * poder probar la política de expiración de {@code ConversationStateStore}.
     */
    private void backdateState(Long businessId, String phone, Duration ago) {
        ConversationState state = statesByKey.get(key(businessId, phone));
        state.setUpdatedAt(Instant.now().minus(ago));
    }

    private void stubAi(String userMessage, String rawResponse) {
        when(aiProvider.generateResponse(anyString(), eq(userMessage))).thenReturn(rawResponse);
    }

    private Customer existingCustomer(String phone, String firstName) {
        Customer customer = new Customer();
        customer.setId(nextCustomerId++);
        customer.setBusiness(business);
        customer.setFirstName(firstName);
        customer.setPhone(phone);
        customersByPhone.put(phone, customer);
        return customer;
    }

    private Service service(Long id, String name, BigDecimal price) {
        Service service = new Service();
        service.setId(id);
        service.setName(name);
        service.setPrice(price);
        service.setDurationMinutes(30);
        service.setBusiness(business);
        return service;
    }

    private Employee employee(Long id, String firstName, String lastName, Service... services) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setBusiness(business);
        employee.setServices(new HashSet<>(List.of(services)));
        return employee;
    }

    private void stubServiceLookup(String name, Service service) {
        lenient().when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(name), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        lenient().when(serviceRepository.findByIdAndBusinessIdAndActiveTrue(service.getId(), 1L))
                .thenReturn(Optional.of(service));
    }

    private void stubEmployeeListing(Employee... employees) {
        lenient().when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employees)));
        for (Employee employee : employees) {
            lenient().when(employeeRepository.findByIdAndBusinessIdAndActiveTrue(employee.getId(), 1L))
                    .thenReturn(Optional.of(employee));
            lenient().when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                            eq(1L), eq(employee.getFirstName()), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(employee)));
        }
    }

    private void stubFullDaySchedule(Employee employee) {
        Schedule schedule = new Schedule();
        schedule.setStartTime(LocalTime.of(0, 0));
        schedule.setEndTime(LocalTime.of(23, 59));
        lenient().when(scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        eq(employee.getId()), any(DayOfWeek.class)))
                .thenReturn(List.of(schedule));
    }

    /** Lleva la conversación hasta AWAITING_CONFIRMATION con un único empleado habilitado. */
    private void proposeCorteBooking(String phone, Service corte, Employee juan) {
        existingCustomer(phone, "Cristian");
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);
        stubAi("Quiero un corte mañana a las 14",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: mañana a las 14\n"
                        + "CONFIDENCE: 0.9\nREPLY: no importa");

        ConversationResponse proposal = conversationService.processChannelConversation(
                1L, phone, "Quiero un corte mañana a las 14");

        assertThat(proposal.getReply()).contains("¿Confirmás?");
        assertThat(proposal.getAppointmentId()).isNull();
    }

    // ---------------------------------------------------------------------------------------
    // Entry points (comportamiento heredado, sin cambios)
    // ---------------------------------------------------------------------------------------

    @Test
    void processAuthenticatedConversationDerivesBusinessIdFromJwt() {
        when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
        existingCustomer("595981000000", "Ana");
        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.95\nREPLY: ¿En qué puedo ayudarte?");

        ConversationRequest request = new ConversationRequest();
        request.setCustomerPhone("595981000000");
        request.setMessage("Hola");

        ConversationResponse result = conversationService.processAuthenticatedConversation(request);

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.GREETING);
        assertThat(result.getAppointmentId()).isNull();
        verify(businessRepository).findByIdAndActiveTrue(1L);
    }

    @Test
    void processChannelConversationUsesExplicitBusinessIdNotJwt() {
        existingCustomer("595981000000", "Ana");
        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.95\nREPLY: ¡Hola!");

        conversationService.processChannelConversation(1L, "595981000000", "Hola");

        verify(businessRepository).findByIdAndActiveTrue(1L);
        verify(currentUserProvider, never()).getCurrentBusinessId();
    }

    // ---------------------------------------------------------------------------------------
    // AJUSTE 1: listado de servicios
    // ---------------------------------------------------------------------------------------

    @Test
    void serviceListIntentRepliesWithBulletListFormattedInGuaranies() {
        existingCustomer("+595981000001", "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Service color = service(5L, "Coloración", new BigDecimal("150000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(corte, color)));
        stubAi("¿Qué servicios ofrecen?", "INTENT: LIST_SERVICES\nCONFIDENCE: 0.9\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "+595981000001", "¿Qué servicios ofrecen?");

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.LIST_SERVICES);
        assertThat(result.getReply()).contains("• Corte Premium — Gs. 65.000");
        assertThat(result.getReply()).contains("• Coloración — Gs. 150.000");
        assertThat(result.getReply()).doesNotContain("$");
    }

    // ---------------------------------------------------------------------------------------
    // Customer no es prerequisito para conversar: se identifica (y crea, si hace falta) recién
    // al confirmar, nunca antes. sender_phone es la única fuente de verdad para el teléfono.
    // ---------------------------------------------------------------------------------------

    @Test
    void newCustomerIsNeverAskedForPhoneAndConfirmsBeforeCreatingCustomerFromSenderPhone() {
        String phone = "+595987000001";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);
        stubAi("Quiero un corte mañana a las 14",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: mañana a las 14\n"
                        + "CONFIDENCE: 0.9\nREPLY: no importa");

        ConversationResponse proposal = conversationService.processChannelConversation(
                1L, phone, "Quiero un corte mañana a las 14");
        assertThat(proposal.getReply()).contains("¿Confirmás?");
        assertThat(customersByPhone).doesNotContainKey(phone);

        ConversationResponse afterYes = conversationService.processChannelConversation(1L, phone, "Sí");
        assertThat(afterYes.getReply().toLowerCase()).doesNotContain("teléfono").doesNotContain("telefono");
        assertThat(afterYes.getReply()).contains("¿Cuál es tu nombre?");
        assertThat(afterYes.getAppointmentId()).isNull();
        assertThat(customersByPhone).doesNotContainKey(phone);

        AppointmentResponse response = new AppointmentResponse();
        response.setId(900L);
        response.setServiceName("Corte");
        response.setEmployeeName("Juan Gómez");
        response.setCustomerName("María López");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), any())).thenReturn(response);

        ConversationResponse afterName = conversationService.processChannelConversation(1L, phone, "María López");

        // El "sí" anterior sigue siendo válido: no se vuelve a pedir confirmación.
        assertThat(afterName.getReply()).doesNotContain("¿Confirmás?");
        assertThat(afterName.getAppointmentId()).isEqualTo(900L);
        assertThat(customersByPhone).containsKey(phone);
        Customer created = customersByPhone.get(phone);
        assertThat(created.getPhone()).isEqualTo(phone);
        assertThat(created.getFirstName()).isEqualTo("María");
        assertThat(created.getLastName()).isEqualTo("López");
    }

    @Test
    void existingCustomerIsNeverAskedForNameOrPhoneAgain() {
        String phone = "+595981222333";
        existingCustomer(phone, "Cristian");
        stubAi("Quiero reservar un turno mañana",
                "INTENT: BOOK_APPOINTMENT\nSTART_AT: mañana\nCONFIDENCE: 0.7\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero reservar un turno mañana");

        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(result.getReply().toLowerCase()).doesNotContain("teléfono").doesNotContain("telefono");
    }

    // ---------------------------------------------------------------------------------------
    // Corrección: "sender_phone desconocido" no implica "identificar al Customer ya mismo".
    // Solo las intenciones que realmente lo requieren (reservar/reprogramar/cancelar) piden
    // nombre; una consulta informativa o un saludo se responden sin crear ni pedir Customer.
    // ---------------------------------------------------------------------------------------

    @Test
    void unknownNumberGreetingDoesNotAskForNameNorCreatesCustomer() {
        String phone = "+595987111001";
        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\n"
                + "REPLY: ¿Te ayudo a reservar un turno o necesitas información sobre nuestros servicios?");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(result.getReply()).contains("Peluquería Elegance");
        assertThat(customersByPhone).doesNotContainKey(phone);
    }

    @Test
    void unknownNumberServiceQueryRespondsWithoutAskingIdentityOrCreatingCustomer() {
        String phone = "+595987111002";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(corte)));
        stubAi("¿Qué servicios ofrecen?", "INTENT: LIST_SERVICES\nCONFIDENCE: 0.9\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "¿Qué servicios ofrecen?");

        assertThat(result.getReply()).contains("Corte");
        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).doesNotContainKey(phone);
    }

    @Test
    void unknownNumberGeneralAvailabilityQueryDoesNotAskForIdentityOrCreateCustomer() {
        String phone = "+595987111005";
        stubAi("¿Tienen lugar mañana?",
                "INTENT: CHECK_AVAILABILITY\nCONFIDENCE: 0.7\nREPLY: Sí, tenemos varios horarios disponibles.");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "¿Tienen lugar mañana?");

        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).doesNotContainKey(phone);
    }

    @Test
    void unknownNumberBookingIntentCollectsDataNormallyWithoutAskingNameUpfront() {
        String phone = "+595987111003";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(corte)));
        stubAi("Quiero reservar un turno", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");

        ConversationResponse first = conversationService.processChannelConversation(
                1L, phone, "Quiero reservar un turno");

        // Sin servicio identificado, se muestra el catálogo — nunca se pide el nombre en este
        // punto, esté o no identificado el Customer.
        assertThat(first.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(first.getReply()).contains("Corte");
        assertThat(customersByPhone).doesNotContainKey(phone);
    }

    @Test
    void firstMessageGreetingPlusServiceQueryAnswersImmediatelyWithoutAskingName() {
        String phone = "+595987111004";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(corte)));
        stubAi("Hola, ¿qué servicios ofrecen?", "INTENT: LIST_SERVICES\nCONFIDENCE: 0.9\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Hola, ¿qué servicios ofrecen?");

        assertThat(result.getReply()).contains("Peluquería Elegance");
        assertThat(result.getReply()).contains("Corte");
        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).doesNotContainKey(phone);
    }

    @Test
    void existingCustomerGreetingDoesNotAskForNameOrStartBookingAutomatically() {
        String phone = "+595981222444";
        existingCustomer(phone, "Cristian");
        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\n"
                + "REPLY: ¿Te ayudo a reservar un turno o necesitas información?");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
    }

    // ---------------------------------------------------------------------------------------
    // AJUSTE 6: saludo inicial
    // ---------------------------------------------------------------------------------------

    @Test
    void initialGreetingUsesBusinessNameAndStillAddressesTheOriginalMessageInTheSameTurn() {
        String phone = "+595987000002";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(corte)));
        stubAi("Hola, quiero reservar mañana a las 10",
                "INTENT: BOOK_APPOINTMENT\nSTART_AT: mañana a las 10\nCONFIDENCE: 0.8\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Hola, quiero reservar mañana a las 10");

        // El saludo del negocio acompaña la primera respuesta, pero el turno avanza el flujo de
        // reserva (catálogo) en vez de detenerse a pedir el nombre: Customer no es prerequisito.
        assertThat(result.getReply()).contains("Peluquería Elegance");
        assertThat(result.getReply()).contains("Corte");
        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
    }

    @Test
    void greetingIsOnlySentOnceForTheSameConversation() {
        String phone = "+595987000003";
        existingCustomer(phone, "Ana");
        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: Estoy para ayudarte.");
        stubAi("Otra vez hola", "INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: Decime en qué te ayudo.");

        ConversationResponse first = conversationService.processChannelConversation(1L, phone, "Hola");
        ConversationResponse second = conversationService.processChannelConversation(1L, phone, "Otra vez hola");

        assertThat(first.getReply()).contains("Peluquería Elegance");
        assertThat(second.getReply()).doesNotContain("Peluquería Elegance");
    }

    // ---------------------------------------------------------------------------------------
    // Confirmación obligatoria antes de crear (requisito crítico)
    // ---------------------------------------------------------------------------------------

    @Test
    void appointmentIsNotCreatedBeforeExplicitConfirmation() {
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking("+595981333444", corte, juan);

        verify(appointmentService, never()).create(any(), any());
    }

    @Test
    void positiveConfirmationCreatesTheAppointment() {
        String phone = "+595981333555";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        AppointmentResponse response = new AppointmentResponse();
        response.setId(200L);
        response.setServiceName("Corte");
        response.setEmployeeName("Juan Gómez");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), any())).thenReturn(response);

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Sí");

        assertThat(result.getAppointmentId()).isEqualTo(200L);
        assertThat(result.getIntent()).isEqualTo(ConversationIntent.CONFIRM_APPOINTMENT);
        verify(appointmentService).create(eq(1L), any());
    }

    @Test
    void negativeConfirmationDoesNotCreateTheAppointment() {
        String phone = "+595981333666";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "No");

        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getIntent()).isEqualTo(ConversationIntent.REJECT_APPOINTMENT);
        verify(appointmentService, never()).create(any(), any());
    }

    @Test
    void changingTheTimeDuringConfirmationDropsThePreviousProposalAndAsksToConfirmTheNewOne() {
        String phone = "+595981333777";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);
        stubAi("Mejor a las 17", "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 17\nCONFIDENCE: 0.6\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Mejor a las 17");

        assertThat(result.getReply()).contains("17:00");
        assertThat(result.getReply()).contains("¿Confirmás?");
        assertThat(result.getAppointmentId()).isNull();
        verify(appointmentService, never()).create(any(), any());

        AppointmentResponse response = new AppointmentResponse();
        response.setId(300L);
        response.setServiceName("Corte");
        response.setEmployeeName("Juan Gómez");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), any())).thenReturn(response);

        ConversationResponse confirmed = conversationService.processChannelConversation(1L, phone, "Sí");

        assertThat(confirmed.getAppointmentId()).isEqualTo(300L);
        verify(appointmentService, org.mockito.Mockito.times(1)).create(eq(1L), any());
    }

    @Test
    void revalidatesOnConfirmationAndDoesNotCreateWhenTheSlotBecameUnavailable() {
        String phone = "+595981333888";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);
        when(appointmentService.create(eq(1L), any())).thenThrow(
                new BusinessException("La cita se superpone con otra cita existente del empleado"));

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Sí");

        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply().toLowerCase()).contains("disponible");
        verify(appointmentService).create(eq(1L), any());
    }

    @Test
    void fallsBackToDefaultReplyWhenAiResponseIsBlank() {
        existingCustomer("595981000000", "Ana");
        stubAi("???", "");

        ConversationResponse result = conversationService.processChannelConversation(1L, "595981000000", "???");

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.UNKNOWN);
        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply()).isNotBlank();
    }

    // ---------------------------------------------------------------------------------------
    // Corrección: estado conversacional acumulativo. Un dato ya recopilado (servicio, fecha,
    // profesional) no debe perderse porque un mensaje posterior solo aporte un dato nuevo.
    // ---------------------------------------------------------------------------------------

    // TEST 1 / TEST 5
    @Test
    void serviceAndDateSurviveAcrossTurnsWhenOnlyTimeIsAddedNext() {
        String phone = "+595981555001";
        existingCustomer(phone, "Cristian");
        Service corteBarba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", corteBarba);
        stubServiceLookup("Corte Barba", corteBarba);
        stubEmployeeListing(juan);

        stubAi("Quiero un corte barba hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nSTART_AT: hoy\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        ConversationResponse first = conversationService.processChannelConversation(
                1L, phone, "Quiero un corte barba hoy");
        assertThat(first.getReply()).contains("¿A qué hora te gustaría?");

        stubAi("Para las 9", "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 9\nCONFIDENCE: 0.7\nREPLY: no importa");
        ConversationResponse second = conversationService.processChannelConversation(1L, phone, "Para las 9");

        // No debe pedir de nuevo servicio ni fecha, ni "olvidarlos": el mensaje debe reflejar
        // ambos datos ya conocidos en la propuesta de confirmación.
        assertThat(second.getReply()).doesNotContain("¿Qué servicio");
        assertThat(second.getReply()).doesNotContain("¿Para cuándo");
        assertThat(second.getReply()).contains("Corte Barba");
        assertThat(second.getReply()).contains("Juan Gómez");
        assertThat(second.getReply()).contains("¿Confirmás?");
    }

    // TEST 2
    @Test
    void pronounReferenceResolvesToLastMentionedEmployeeWithoutAskingAgain() {
        String phone = "+595981555003";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);
        stubFullDaySchedule(juan);

        stubAi("¿Juan está disponible hoy a las 10?",
                "INTENT: CHECK_AVAILABILITY\nEMPLOYEE_NAME: Juan\nSTART_AT: hoy a las 10\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        ConversationResponse availability = conversationService.processChannelConversation(
                1L, phone, "¿Juan está disponible hoy a las 10?");
        assertThat(availability.getReply()).contains("disponible");

        stubAi("Quiero reservar con él",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nCONFIDENCE: 0.6\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero reservar con él");

        assertThat(result.getReply()).doesNotContain("¿Con qué profesional");
        assertThat(result.getReply()).contains("Juan Gómez");
    }

    // TEST 3 / TEST 8
    @Test
    void checkAvailabilityQuestionReusesExistingDraftAndExecutesImmediately() {
        String phone = "+595981555004";
        existingCustomer(phone, "Cristian");
        Employee juan = employee(3L, "Juan", "Gómez");
        stubEmployeeListing(juan);
        stubFullDaySchedule(juan);

        stubAi("¿Juan está disponible hoy a las 10?",
                "INTENT: CHECK_AVAILABILITY\nEMPLOYEE_NAME: Juan\nSTART_AT: hoy a las 10\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "¿Juan está disponible hoy a las 10?");

        stubAi("¿Está disponible?",
                "INTENT: CHECK_AVAILABILITY\nCONFIDENCE: 0.6\nREPLY: Déjame verificar la disponibilidad.");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "¿Está disponible?");

        // Debe ejecutar la consulta real con los datos ya conocidos (empleado/horario), no
        // repetir la promesa de la IA ni volver a pedir servicio/día/horario.
        assertThat(result.getReply()).doesNotContain("Déjame verificar");
        assertThat(result.getReply()).contains("disponible");
        assertThat(result.getReply()).doesNotContain("¿Qué servicio");
    }

    // TEST 8 (variante desde el flujo de reserva normal)
    @Test
    void sufficientDataInASingleMessageNeverLeavesAPlaceholderPromiseUnexecuted() {
        String phone = "+595981555005";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        existingCustomer(phone, "Cristian");
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);

        stubAi("Déjame reservar con Juan hoy a las 9",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nEMPLOYEE_NAME: Juan\nSTART_AT: hoy a las 9\n"
                        + "CONFIDENCE: 0.8\nREPLY: Déjame verificar la disponibilidad para ese horario.");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Déjame reservar con Juan hoy a las 9");

        assertThat(result.getReply()).doesNotContain("Déjame verificar");
        assertThat(result.getReply()).contains("¿Confirmás?");
        assertThat(result.getAppointmentId()).isNull();
    }

    // TEST 4
    @Test
    void dateDoesNotMutateAcrossSeveralFollowUpMessages() {
        String phone = "+595981555002";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);

        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        stubAi("¿Cuánto cuesta?", "INTENT: UNKNOWN\nCONFIDENCE: 0.3\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "¿Cuánto cuesta?");

        stubAi("Para las 9", "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 9\nCONFIDENCE: 0.7\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Para las 9");

        assertThat(result.getReply()).contains("hoy");
        assertThat(result.getReply()).doesNotContain("mañana");
    }

    // TEST 6 / TEST 7: durante la confirmación, cambiar un solo dato conserva el resto
    @Test
    void changingOnlyTheTimeDuringConfirmationKeepsServiceAndEmployee() {
        String phone = "+595981555008";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        stubAi("Mejor a las 10", "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 10\nCONFIDENCE: 0.6\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Mejor a las 10");

        assertThat(result.getReply()).contains("10:00");
        assertThat(result.getReply()).contains("Corte");
        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    @Test
    void changingOnlyTheDateDuringConfirmationKeepsServiceEmployeeAndTime() {
        String phone = "+595981555007";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        stubAi("Mejor el próximo lunes",
                "INTENT: BOOK_APPOINTMENT\nSTART_AT: el próximo lunes a las 14\nCONFIDENCE: 0.6\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Mejor el próximo lunes");

        assertThat(result.getReply()).contains("Corte");
        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).contains("¿Confirmás?");
        assertThat(result.getAppointmentId()).isNull();
    }

    // TEST 9
    @Test
    void confirmationUsesExactlyTheServiceEmployeeAndStartAtFromTheProposal() {
        String phone = "+595981555006";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        ArgumentCaptor<CreateAppointmentRequest> captor = ArgumentCaptor.forClass(CreateAppointmentRequest.class);
        AppointmentResponse response = new AppointmentResponse();
        response.setId(500L);
        response.setServiceName("Corte");
        response.setEmployeeName("Juan Gómez");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), captor.capture())).thenReturn(response);

        conversationService.processChannelConversation(1L, phone, "Sí");

        CreateAppointmentRequest used = captor.getValue();
        assertThat(used.getServiceId()).isEqualTo(4L);
        assertThat(used.getEmployeeId()).isEqualTo(3L);
        assertThat(used.getStartAt()).isNotNull();
    }

    // TEST 10
    @Test
    void plainGreetingNeverAsksForServiceNameOrCreatesCustomer() {
        String phone = "+595981555009";
        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\n"
                + "REPLY: ¿Te ayudo a reservar un turno o necesitas información sobre nuestros servicios?");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).doesNotContain("¿Qué servicio");
        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).doesNotContainKey(phone);
    }

    // ---------------------------------------------------------------------------------------
    // Corrección: ciclo de vida de ConversationState. La memoria conversacional no debe
    // sobrevivir indefinidamente: un borrador abandonado (en cualquier etapa, no solo
    // AWAITING_CONFIRMATION) debe expirar por inactividad, y una reserva completada o cancelada
    // debe dejar la conversación limpia para el próximo mensaje.
    // ---------------------------------------------------------------------------------------

    // TEST 1
    @Test
    void activeDraftWithinTimeoutPreservesServiceAndDateWhenOnlyTimeArrives() {
        String phone = "+595981666001";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);

        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        backdateState(1L, phone, Duration.ofMinutes(5));

        stubAi("Para las 10", "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 10\nCONFIDENCE: 0.7\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Para las 10");

        assertThat(result.getReply()).contains("Corte");
        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    // TEST 2
    @Test
    void expiredDraftIsNotReusedWhenPlainGreetingArrivesLater() {
        String phone = "+595981666002";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        stubServiceLookup("Corte", corte);

        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        backdateState(1L, phone, Duration.ofMinutes(60));

        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\n"
                + "REPLY: ¿Te ayudo a reservar un turno o necesitas información?");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).doesNotContain("¿A qué hora");
        assertThat(result.getReply()).contains("Peluquería Elegance");
    }

    // TEST 3
    @Test
    void expiredStatePlusNewBookingMessageStartsFreshUsingOnlyNewData() {
        String phone = "+595981666003";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Service color = service(5L, "Coloración", new BigDecimal("150000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte, color);
        stubServiceLookup("Corte", corte);
        stubServiceLookup("Coloración", color);
        stubEmployeeListing(juan);

        stubAi("Quiero una coloración hoy", "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Coloración\nSTART_AT: hoy\n"
                + "CONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero una coloración hoy");

        backdateState(1L, phone, Duration.ofMinutes(60));

        stubAi("Quiero reservar un corte mañana a las 11",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: mañana a las 11\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero reservar un corte mañana a las 11");

        assertThat(result.getReply()).contains("Corte");
        assertThat(result.getReply()).doesNotContain("Coloración");
        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    // TEST 4
    @Test
    void completedAppointmentDoesNotLeakIntoTheNextGreeting() {
        String phone = "+595981666004";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        AppointmentResponse response = new AppointmentResponse();
        response.setId(700L);
        response.setServiceName("Corte");
        response.setEmployeeName("Juan Gómez");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), any())).thenReturn(response);
        conversationService.processChannelConversation(1L, phone, "Sí");

        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: ¿En qué puedo ayudarte?");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).doesNotContain("¿Confirmás?");
        assertThat(result.getReply()).doesNotContain("Corte");
        assertThat(result.getReply()).doesNotContain("¿A qué hora");
    }

    // TEST 5
    @Test
    void explicitCancellationDuringCollectionClearsDraftAndNextGreetingIsClean() {
        String phone = "+595981666005";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        stubServiceLookup("Corte", corte);

        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        ConversationResponse cancelled = conversationService.processChannelConversation(1L, phone, "Cancelar");
        assertThat(cancelled.getReply()).contains("cancelé");
        assertThat(cancelled.getIntent()).isEqualTo(ConversationIntent.REJECT_APPOINTMENT);

        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: ¿En qué puedo ayudarte?");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).doesNotContain("¿A qué hora");
    }

    // TEST 6
    @Test
    void expiredStateStillReusesExistingCustomerWithoutAskingPhoneOrDuplicating() {
        String phone = "+595981666006";
        Customer cristian = existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        stubServiceLookup("Corte", corte);

        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        backdateState(1L, phone, Duration.ofMinutes(60));

        stubAi("Quiero reservar", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Quiero reservar");

        assertThat(result.getReply().toLowerCase()).doesNotContain("teléfono").doesNotContain("telefono");
        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).hasSize(1);
        assertThat(customersByPhone.get(phone).getId()).isEqualTo(cristian.getId());
    }

    // TEST 7
    @Test
    void validPendingConfirmationStillCreatesAppointmentWhenConfirmedInTime() {
        String phone = "+595981666007";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        backdateState(1L, phone, Duration.ofMinutes(10));

        AppointmentResponse response = new AppointmentResponse();
        response.setId(800L);
        response.setServiceName("Corte");
        response.setEmployeeName("Juan Gómez");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), any())).thenReturn(response);

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Sí");

        assertThat(result.getAppointmentId()).isEqualTo(800L);
        verify(appointmentService).create(eq(1L), any());
    }

    // TEST 8
    @Test
    void expiredPendingConfirmationDoesNotCreateAppointmentEvenWithPositiveReply() {
        String phone = "+595981666008";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        backdateState(1L, phone, Duration.ofMinutes(60));

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Sí");

        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply()).contains("venció");
        verify(appointmentService, never()).create(any(), any());
    }

    // TEST 9
    @Test
    void greetingCombinedWithNewDataWithinTimeoutStillExtractsTheData() {
        String phone = "+595981666009";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);

        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        stubAi("Hola, perdón, a las 10",
                "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 10\nCONFIDENCE: 0.7\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Hola, perdón, a las 10");

        assertThat(result.getReply()).contains("10:00");
        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    // TEST 10
    @Test
    void pureGreetingInACleanConversationOnlyOffersHelp() {
        String phone = "+595981666010";
        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\n"
                + "REPLY: ¿Te ayudo a reservar un turno o necesitas información sobre nuestros servicios?");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(result.getReply()).doesNotContain("¿Qué servicio");
        assertThat(result.getReply()).doesNotContain("¿A qué hora");
        assertThat(result.getReply()).doesNotContain("¿Para cuándo");
        // El saludo puro se redacta determinísticamente en backend, no con el texto libre de la IA.
        assertThat(result.getReply()).contains("¿Te ayudo a reservar un turno");
    }

    // ---------------------------------------------------------------------------------------
    // Corrección: flujo corto y determinista. El backend debe resolver con datos reales
    // (Employee-Service, Schedule, overlap) en lugar de convertir la reserva en un formulario
    // campo por campo, y una selección de profesional ya hecha nunca debe pisarse en silencio.
    // ---------------------------------------------------------------------------------------

    // TEST 1 / TEST 2
    @Test
    void bookingIntentWithoutServiceShowsFullCatalogWithPricesImmediately() {
        String phone = "+595981777001";
        existingCustomer(phone, "Cristian");
        Service premium = service(4L, "Corte Premium", new BigDecimal("65000"));
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(premium, barba)));

        stubAi("Quiero reservar un turno", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero reservar un turno");

        assertThat(result.getReply()).contains("• Corte Premium — Gs. 65.000");
        assertThat(result.getReply()).contains("• Corte Barba — Gs. 45.000");
        assertThat(result.getReply()).contains("¿Cuál te gustaría reservar?");
        assertThat(result.getReply()).doesNotContain("profesional");
    }

    // ---------------------------------------------------------------------------------------
    // Bug reportado en QA (segunda vuelta): la respuesta corta "Corte premium"/"Corte barba"
    // seguía re-listando los servicios pese al ajuste anterior, porque la IA la clasificaba como
    // LIST_SERVICES — una rama de respondCollecting que corta el flujo ANTES de llegar a
    // mergeParsedIntoDraft (donde vivía el fix anterior). La resolución determinista ahora corre
    // ANTES de invocar a la IA: si el mensaje coincide exacto/normalizado con un único Service
    // activo, se resuelve sin siquiera preguntarle al modelo, así el resultado no depende de qué
    // intent le asigne a un mensaje ambiguo. No se stubea la IA para el mensaje corto en estos
    // tests a propósito: si el código intentara clasificarlo, el mock lanzaría un mismatch.
    // ---------------------------------------------------------------------------------------

    @Test
    void shortServiceSelectionReplyResolvesServiceWithoutRelistingCatalogAndContinuesToDateTime() {
        String phone = "+595981999001";
        existingCustomer(phone, "Cristian");
        Service premium = service(4L, "Corte Premium", new BigDecimal("65000"));
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", premium);
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(premium, barba)));
        stubServiceLookup("Corte Premium", premium);
        stubEmployeeListing(juan);

        stubAi("Me gustaría reservar un turno", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");
        ConversationResponse first = conversationService.processChannelConversation(
                1L, phone, "Me gustaría reservar un turno");
        assertThat(first.getReply()).contains("• Corte Premium — Gs. 65.000");
        assertThat(first.getReply()).contains("• Corte Barba — Gs. 45.000");

        ConversationResponse second = conversationService.processChannelConversation(1L, phone, "Corte premium");

        assertThat(second.getReply()).doesNotContain("• Corte Premium");
        assertThat(second.getReply()).doesNotContain("• Corte Barba");
        assertThat(second.getReply()).doesNotContain("¿Cuál te gustaría reservar?");
        assertThat(second.getReply()).contains("¿Para cuándo te gustaría el turno?");
        verify(aiProvider, never()).generateResponse(anyString(), eq("Corte premium"));

        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.getPendingServiceId()).isEqualTo(4L);

        stubAi("Mañana a las 10", "INTENT: BOOK_APPOINTMENT\nSTART_AT: mañana a las 10\nCONFIDENCE: 0.7\n"
                + "REPLY: no importa");
        ConversationResponse third = conversationService.processChannelConversation(1L, phone, "Mañana a las 10");
        assertThat(third.getReply()).contains("¿Confirmás?");
        assertThat(third.getAppointmentId()).isNull();

        AppointmentResponse response = new AppointmentResponse();
        response.setId(600L);
        response.setServiceName("Corte Premium");
        response.setEmployeeName("Juan Gómez");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), any())).thenReturn(response);

        ConversationResponse fourth = conversationService.processChannelConversation(1L, phone, "Sí");
        assertThat(fourth.getAppointmentId()).isEqualTo(600L);
    }

    @Test
    void secondShortServiceSelectionReplySelectsCorteBarbaWithoutRelistingCatalog() {
        String phone = "+595981999002";
        existingCustomer(phone, "Cristian");
        Service premium = service(4L, "Corte Premium", new BigDecimal("65000"));
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(premium, barba)));

        stubAi("Me gustaría reservar un turno", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Me gustaría reservar un turno");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Corte barba");

        assertThat(result.getReply()).doesNotContain("¿Cuál te gustaría reservar?");
        assertThat(result.getReply()).doesNotContain("• Corte Premium");
        verify(aiProvider, never()).generateResponse(anyString(), eq("Corte barba"));

        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.getPendingServiceId()).isEqualTo(6L);
    }

    @Test
    void fullSentenceServiceSelectionStillGoesThroughAiExtraction() {
        String phone = "+595981999003";
        existingCustomer(phone, "Cristian");
        Service premium = service(4L, "Corte Premium", new BigDecimal("65000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(premium)));
        stubServiceLookup("Corte Premium", premium);

        stubAi("Me gustaría reservar corte premium",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Premium\nCONFIDENCE: 0.85\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Me gustaría reservar corte premium");

        assertThat(result.getReply()).contains("¿Para cuándo te gustaría el turno?");
        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.getPendingServiceId()).isEqualTo(4L);
    }

    @Test
    void shortServiceSelectionReplyIsCaseInsensitive() {
        String phone = "+595981999004";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(barba)));

        stubAi("Quiero reservar un turno", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero reservar un turno");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "CORTE BARBA");

        assertThat(result.getReply()).doesNotContain("¿Cuál te gustaría reservar?");
        verify(aiProvider, never()).generateResponse(anyString(), eq("CORTE BARBA"));
        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.getPendingServiceId()).isEqualTo(6L);
    }

    @Test
    void newCustomerShortServiceSelectionIsNeverMisreadAsCustomerName() {
        String phone = "+595981999005";
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(barba)));

        stubAi("Quiero reservar un turno", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero reservar un turno");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Corte barba");

        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).doesNotContainKey(phone);
        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.getPendingServiceId()).isEqualTo(6L);
    }

    // TEST 3
    @Test
    void selectingServiceDoesNotAskForEmployeeBeforeDateAndTime() {
        String phone = "+595981777003";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", barba);
        Employee victor = employee(7L, "Victor", "Chamorro", barba);
        stubServiceLookup("Corte Barba", barba);
        stubEmployeeListing(juan, victor);

        stubAi("Corte Barba", "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nCONFIDENCE: 0.8\n"
                + "REPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Corte Barba");

        assertThat(result.getReply()).doesNotContain("profesional");
        assertThat(result.getReply()).contains("¿Para cuándo");
    }

    // TEST 4
    @Test
    void oneAvailableEmployeeIsAutoAssignedWhenServiceDateAndTimeAreKnown() {
        String phone = "+595981777004";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", barba);
        stubServiceLookup("Corte Barba", barba);
        stubEmployeeListing(juan);

        stubAi("Corte Barba hoy a las 09:00",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nSTART_AT: hoy a las 09:00\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Corte Barba hoy a las 09:00");

        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).contains("Gs. 45.000");
        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    // TEST 5
    @Test
    void multipleAvailableEmployeesAsksWhichOneToUse() {
        String phone = "+595981777005";
        existingCustomer(phone, "Cristian");
        Service premium = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", premium);
        Employee victor = employee(7L, "Victor", "Chamorro", premium);
        stubServiceLookup("Corte Premium", premium);
        stubEmployeeListing(juan, victor);

        stubAi("Corte Premium mañana a las 15:00",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Premium\nSTART_AT: mañana a las 15:00\n"
                        + "CONFIDENCE: 0.8\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Corte Premium mañana a las 15:00");

        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).contains("Victor Chamorro");
        assertThat(result.getReply()).contains("¿Con cuál prefieres reservar?");
    }

    // TEST 6 / TEST 7: el bug real de QA — Juan nunca debe terminar reemplazado por Victor
    @Test
    void explicitlySelectedEmployeeSurvivesLaterServiceRestatementAndIsNeverAutoReplaced() {
        String phone = "+595981777006";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", barba);
        Employee victor = employee(7L, "Victor", "Chamorro", barba);
        stubServiceLookup("Corte Barba", barba);
        stubEmployeeListing(juan, victor);

        stubAi("Quiero con Juan", "INTENT: BOOK_APPOINTMENT\nEMPLOYEE_NAME: Juan\nCONFIDENCE: 0.7\n"
                + "REPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero con Juan");

        // El servicio se afirma DESPUÉS del profesional: no debe pisar a Juan.
        stubAi("Corte Barba", "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nCONFIDENCE: 0.7\n"
                + "REPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Corte Barba");

        stubAi("Hoy a las 09:00", "INTENT: BOOK_APPOINTMENT\nSTART_AT: hoy a las 09:00\nCONFIDENCE: 0.7\n"
                + "REPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hoy a las 09:00");

        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).doesNotContain("Victor");
        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    // TEST 10
    @Test
    void providingOnlyTheMissingTimeTriggersAvailabilityCheckInTheSameTurn() {
        String phone = "+595981777010";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", barba);
        stubServiceLookup("Corte Barba", barba);
        stubEmployeeListing(juan);

        stubAi("Corte Barba con Juan hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nEMPLOYEE_NAME: Juan\nSTART_AT: hoy\n"
                        + "CONFIDENCE: 0.8\nREPLY: no importa");
        ConversationResponse first = conversationService.processChannelConversation(
                1L, phone, "Corte Barba con Juan hoy");
        assertThat(first.getReply()).contains("¿A qué hora");

        stubAi("Para las 14 horas", "INTENT: BOOK_APPOINTMENT\nSTART_AT: a las 14\nCONFIDENCE: 0.6\n"
                + "REPLY: Déjame verificar la disponibilidad.");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Para las 14 horas");

        assertThat(result.getReply()).doesNotContain("Déjame verificar");
        assertThat(result.getReply()).contains("14:00");
        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    // TEST 17: disponibilidad debe considerar la relación Employee-Service
    @Test
    void availabilityExcludesEmployeesNotAssignedToTheRequestedService() {
        String phone = "+595981777017";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Service premium = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", barba);
        Employee victor = employee(7L, "Victor", "Chamorro", premium);
        stubServiceLookup("Corte Barba", barba);
        stubEmployeeListing(juan, victor);

        stubAi("Corte Barba hoy a las 09:00",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nSTART_AT: hoy a las 09:00\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Corte Barba hoy a las 09:00");

        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).doesNotContain("Victor");
    }

    // TEST 18: disponibilidad debe considerar el Schedule real del empleado. La regla de horario
    // laboral vive en AppointmentServiceImpl (única fuente de verdad, expuesta vía
    // AppointmentService#isAvailable); este test simula ese resultado a través del mismo método
    // que ConversationServiceImpl consulta, sin duplicar la lógica de Schedule aquí.
    @Test
    void availabilityExcludesEmployeesOutsideTheirWorkingHours() {
        String phone = "+595981777018";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", barba);
        stubServiceLookup("Corte Barba", barba);
        stubEmployeeListing(juan);
        when(appointmentService.isAvailable(eq(1L), eq(3L), nullable(Long.class), any(Instant.class)))
                .thenReturn(false);

        stubAi("Corte Barba hoy a las 15:00",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nSTART_AT: hoy a las 15:00\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Corte Barba hoy a las 15:00");

        assertThat(result.getReply()).contains("No hay disponibilidad");
    }

    // TEST 19: disponibilidad debe considerar superposición con otras citas. Misma nota que el
    // test anterior: la regla de overlap vive en AppointmentServiceImpl, no aquí.
    @Test
    void availabilityExcludesEmployeesWithOverlappingAppointment() {
        String phone = "+595981777019";
        existingCustomer(phone, "Cristian");
        Service barba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", barba);
        stubServiceLookup("Corte Barba", barba);
        stubEmployeeListing(juan);
        when(appointmentService.isAvailable(eq(1L), eq(3L), nullable(Long.class), any(Instant.class)))
                .thenReturn(false);

        stubAi("Corte Barba hoy a las 09:00",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nSTART_AT: hoy a las 09:00\nCONFIDENCE: 0.8\n"
                        + "REPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Corte Barba hoy a las 09:00");

        assertThat(result.getReply()).contains("No hay disponibilidad");
    }

    // ---------------------------------------------------------------------------------------
    // Comandos internos de QA: /reset y /terminate limpian el ConversationState manualmente,
    // sin pasar nunca por OpenAI, sin borrar Customer/Appointments.
    // ---------------------------------------------------------------------------------------

    // TEST 1 / TEST 7
    @Test
    void resetCommandClearsActiveDraftAndNeverInvokesOpenAi() {
        String phone = "+595981888001";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        stubServiceLookup("Corte", corte);
        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "/reset");

        assertThat(result.getReply()).isEqualTo("Conversación reiniciada.");
        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.hasPendingService()).isFalse();
        assertThat(state.getPendingDate()).isNull();
        assertThat(state.getPendingStartAt()).isNull();
        assertThat(state.isGreeted()).isFalse();
        verify(aiProvider, never()).generateResponse(anyString(), eq("/reset"));
    }

    // TEST 2 / TEST 3
    @Test
    void terminateCommandBehavesExactlyLikeReset() {
        String phone = "+595981888002";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        stubServiceLookup("Corte", corte);
        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "  /Terminate  ");

        assertThat(result.getReply()).isEqualTo("Conversación reiniciada.");
        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.hasPendingService()).isFalse();
        assertThat(state.getPendingDate()).isNull();
        verify(aiProvider, never()).generateResponse(anyString(), eq("/Terminate"));
    }

    // TEST 4
    @Test
    void resetCommandDoesNotAffectPersistedCustomer() {
        String phone = "+595981888004";
        Customer cristian = existingCustomer(phone, "Cristian");

        conversationService.processChannelConversation(1L, phone, "/reset");

        assertThat(customersByPhone).containsKey(phone);
        assertThat(customersByPhone.get(phone).getId()).isEqualTo(cristian.getId());
    }

    // TEST 6
    @Test
    void repeatingResetCommandIsSafeAndIdempotent() {
        String phone = "+595981888006";

        ConversationResponse first = conversationService.processChannelConversation(1L, phone, "/reset");
        ConversationResponse second = conversationService.processChannelConversation(1L, phone, "/reset");

        assertThat(first.getReply()).isEqualTo("Conversación reiniciada.");
        assertThat(second.getReply()).isEqualTo("Conversación reiniciada.");
    }

    // TEST 8
    @Test
    void greetingAfterResetStartsACleanConversation() {
        String phone = "+595981888008";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        stubServiceLookup("Corte", corte);
        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        conversationService.processChannelConversation(1L, phone, "/reset");

        stubAi("Hola", "INTENT: GREETING\nCONFIDENCE: 0.9\nREPLY: ¿En qué puedo ayudarte hoy?");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "Hola");

        assertThat(result.getReply()).contains("Peluquería Elegance");
        assertThat(result.getReply()).doesNotContain("¿A qué hora");
    }

    // TEST 9
    @Test
    void bookingIntentAfterResetStartsFromScratch() {
        String phone = "+595981888009";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Service color = service(5L, "Coloración", new BigDecimal("150000"));
        stubServiceLookup("Corte", corte);
        stubServiceLookup("Coloración", color);
        stubAi("Quiero un corte hoy",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: hoy\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte hoy");

        conversationService.processChannelConversation(1L, phone, "/reset");

        stubAi("Quiero reservar una coloración",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Coloración\nCONFIDENCE: 0.7\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero reservar una coloración");

        assertThat(result.getReply()).contains("¿Para cuándo");
        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.getPendingServiceId()).isEqualTo(5L);
        assertThat(state.getPendingDate()).isNull();
    }

    // TEST 10
    @Test
    void resetCommandWorksEvenDuringPendingConfirmation() {
        String phone = "+595981888010";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        proposeCorteBooking(phone, corte, juan);

        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "/reset");

        assertThat(result.getReply()).isEqualTo("Conversación reiniciada.");
        ConversationState state = statesByKey.get(key(1L, phone));
        assertThat(state.getStage()).isEqualTo(ConversationStage.COLLECTING);
        assertThat(state.getPendingStartAt()).isNull();

        // Un "Sí" tardío después del reset no debe crear ninguna cita.
        ConversationResponse afterReset = conversationService.processChannelConversation(1L, phone, "Sí");
        assertThat(afterReset.getAppointmentId()).isNull();
        verify(appointmentService, never()).create(any(), any());
    }

    // ---------------------------------------------------------------------------------------
    // AWAITING_CUSTOMER_NAME: Customer nunca es prerequisito para conversar ni armar el
    // borrador; solo entra en juego al confirmar. Un mensaje solo se interpreta como nombre
    // cuando el estado está exactamente en esta etapa, nunca por el solo hecho de que el
    // Customer sea desconocido.
    // ---------------------------------------------------------------------------------------

    @Test
    void serviceCorrectionMessageIsNeverMisreadAsCustomerNameForUnknownPhone() {
        String phone = "+595987000098";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        stubServiceLookup("Corte", corte);
        stubAi("Hola quiero reservar un corte",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nCONFIDENCE: 0.8\nREPLY: no importa");

        ConversationResponse first = conversationService.processChannelConversation(
                1L, phone, "Hola quiero reservar un corte");
        assertThat(first.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).doesNotContainKey(phone);

        // Antes de esta simplificación, este mensaje se interpretaba como el nombre del cliente
        // (firstName="corte", lastName="+ barba") porque un booleano oculto (awaitingName) se
        // activaba apenas se detectaba una intención de reserva en un teléfono desconocido. Ahora
        // un mensaje solo se interpreta como nombre en ConversationStage.AWAITING_CUSTOMER_NAME.
        stubAi("corte + barba", "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nCONFIDENCE: 0.6\nREPLY: no importa");
        ConversationResponse second = conversationService.processChannelConversation(1L, phone, "corte + barba");

        assertThat(customersByPhone).doesNotContainKey(phone);
        assertThat(second.getReply()).doesNotContain("¿Cuál es tu nombre?");
    }

    @Test
    void expiredAwaitingCustomerNameStageDoesNotCreateCustomerOrAppointmentFromStaleReply() {
        String phone = "+595987000099";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);
        stubAi("Quiero un corte mañana a las 14",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: mañana a las 14\n"
                        + "CONFIDENCE: 0.9\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte mañana a las 14");

        ConversationResponse afterYes = conversationService.processChannelConversation(1L, phone, "Sí");
        assertThat(afterYes.getReply()).contains("¿Cuál es tu nombre?");

        backdateState(1L, phone, Duration.ofMinutes(60));

        stubAi("María López", "INTENT: UNKNOWN\nCONFIDENCE: 0.3\nREPLY: No logré entenderte, ¿podés repetirlo?");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "María López");

        assertThat(customersByPhone).doesNotContainKey(phone);
        assertThat(result.getAppointmentId()).isNull();
        verify(appointmentService, never()).create(any(), any());
    }

    @Test
    void confirmationIsNeverAskedTwiceForANewCustomer() {
        String phone = "+595987000096";
        Service corte = service(4L, "Corte", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte", corte);
        stubEmployeeListing(juan);
        stubAi("Quiero un corte mañana a las 14",
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: mañana a las 14\n"
                        + "CONFIDENCE: 0.9\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Quiero un corte mañana a las 14");
        conversationService.processChannelConversation(1L, phone, "Sí");

        AppointmentResponse response = new AppointmentResponse();
        response.setId(950L);
        response.setServiceName("Corte");
        response.setEmployeeName("Juan Gómez");
        response.setCustomerName("María López");
        response.setStartAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(appointmentService.create(eq(1L), any())).thenReturn(response);

        ConversationResponse afterName = conversationService.processChannelConversation(1L, phone, "María López");

        assertThat(afterName.getReply()).doesNotContain("¿Confirmás?");
        assertThat(afterName.getAppointmentId()).isEqualTo(950L);
        verify(appointmentService, org.mockito.Mockito.times(1)).create(eq(1L), any());
    }

    @Test
    void rescheduleIntentForUnknownCustomerRepliesWithNoAppointmentFoundInsteadOfAskingIdentity() {
        String phone = "+595987000097";
        stubAi("Quiero cambiar mi turno a mañana",
                "INTENT: RESCHEDULE_APPOINTMENT\nSTART_AT: mañana a las 10\nCONFIDENCE: 0.7\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero cambiar mi turno a mañana");

        assertThat(result.getReply()).contains("No encontré ninguna reserva");
        assertThat(result.getReply()).doesNotContain("¿Cuál es tu nombre?");
        assertThat(customersByPhone).doesNotContainKey(phone);
        verify(appointmentService, never()).reschedule(anyLong(), anyLong(), any());
    }

    // ---------------------------------------------------------------------------------------
    // AJUSTE 1-15: disponibilidad real en lista + interpretación correcta de día/hora/profesional
    // ---------------------------------------------------------------------------------------

    private static final ZoneId ASUNCION = ZoneId.of("America/Asuncion");

    private void selectCortePremiumOnly(String phone) {
        stubAi("Corte Premium", "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Premium\nCONFIDENCE: 0.9\n"
                + "REPLY: no importa");
        ConversationResponse afterService = conversationService.processChannelConversation(1L, phone, "Corte Premium");
        assertThat(afterService.getReply()).contains("¿Para cuándo te gustaría el turno?");
    }

    // TEST 1/2/4: servicio ya conocido + "¿qué horarios tienen disponibles?" no vuelve a listar
    // servicios, responde en formato lista, y la lista viene de horarios reales (findAvailableSlots),
    // nunca de un volcado del Schedule bruto.
    @Test
    void checkAvailabilityListWithKnownServiceDoesNotRelistServicesAndUsesRealSlots() {
        String phone = "+595981900001";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan);
        selectCortePremiumOnly(phone);

        LocalDate today = LocalDate.now(ASUNCION);
        Instant slot1 = ZonedDateTime.of(today, LocalTime.of(10, 0), ASUNCION).toInstant();
        Instant slot2 = ZonedDateTime.of(today, LocalTime.of(11, 0), ASUNCION).toInstant();
        when(appointmentService.findAvailableSlots(eq(1L), eq(3L), eq(4L), eq(today), anyInt()))
                .thenReturn(List.of(slot1, slot2));

        stubAi("¿Para cuándo tienen disponible y qué horarios?",
                "INTENT: CHECK_AVAILABILITY\nCONFIDENCE: 0.8\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "¿Para cuándo tienen disponible y qué horarios?");

        assertThat(result.getReply()).doesNotContain("¿Cuál te gustaría reservar?");
        assertThat(result.getReply()).doesNotContain("• Corte Premium");
        assertThat(result.getReply()).contains("•");
        assertThat(result.getReply()).contains("10:00");
        assertThat(result.getReply()).contains("11:00");
        assertThat(result.getReply()).contains("Corte Premium");
    }

    // TEST 8 (AJUSTE 8): varios profesionales realizan el servicio -> se listan agrupados por
    // profesional, cada uno con sus propios horarios reales.
    @Test
    void checkAvailabilityListGroupsByEmployeeWhenMultipleQualify() {
        String phone = "+595981900002";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        Employee victor = employee(7L, "Victor", "Chamorro", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan, victor);
        selectCortePremiumOnly(phone);

        LocalDate today = LocalDate.now(ASUNCION);
        when(appointmentService.findAvailableSlots(eq(1L), eq(3L), eq(4L), eq(today), anyInt()))
                .thenReturn(List.of(ZonedDateTime.of(today, LocalTime.of(10, 0), ASUNCION).toInstant()));
        when(appointmentService.findAvailableSlots(eq(1L), eq(7L), eq(4L), eq(today), anyInt()))
                .thenReturn(List.of(ZonedDateTime.of(today, LocalTime.of(9, 0), ASUNCION).toInstant()));

        stubAi("¿Qué horarios tienen?", "INTENT: CHECK_AVAILABILITY\nCONFIDENCE: 0.8\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "¿Qué horarios tienen?");

        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).contains("Victor Chamorro");
        assertThat(result.getReply()).contains("10:00");
        assertThat(result.getReply()).contains("09:00");
    }

    // TEST 13: dentro de COLLECTING, tras pedir fecha/hora, una pregunta genérica de disponibilidad
    // se interpreta contra el borrador actual (servicio ya elegido), no como una intención nueva
    // desconectada ni como un volcado de texto libre de la IA.
    @Test
    void genericOptionsQuestionWhileCollectingUsesKnownDraftInsteadOfFreeText() {
        String phone = "+595981900003";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan);
        selectCortePremiumOnly(phone);

        LocalDate today = LocalDate.now(ASUNCION);
        when(appointmentService.findAvailableSlots(eq(1L), eq(3L), eq(4L), eq(today), anyInt()))
                .thenReturn(List.of(ZonedDateTime.of(today, LocalTime.of(10, 0), ASUNCION).toInstant()));

        stubAi("¿Qué opciones tienen?", "INTENT: CHECK_AVAILABILITY\nCONFIDENCE: 0.7\n"
                + "REPLY: Déjame revisar la disponibilidad.");
        ConversationResponse result = conversationService.processChannelConversation(1L, phone, "¿Qué opciones tienen?");

        assertThat(result.getReply()).doesNotContain("Déjame revisar");
        assertThat(result.getReply()).doesNotContain("¿Para cuándo te gustaría el turno?");
        assertThat(result.getReply()).contains("10:00");
    }

    // TEST 7 (AJUSTE 5/7): el nombre completo del profesional ("Juan Gómez") se resuelve
    // correctamente contra el Employee real (antes solo se buscaba por firstName y colapsaba en el
    // mensaje genérico), y el servicio ya elegido en el draft se conserva.
    @Test
    void fullNameEmployeeResolvesCorrectlyAndKeepsPreviouslyChosenService() {
        String phone = "+595981900004";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan);
        selectCortePremiumOnly(phone);

        stubAi("Quiero para el jueves con Juan Gómez a las 10:00",
                "INTENT: BOOK_APPOINTMENT\nEMPLOYEE_NAME: Juan Gómez\nSTART_AT: el próximo jueves a las 10:00\n"
                        + "CONFIDENCE: 0.85\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero para el jueves con Juan Gómez a las 10:00");

        assertThat(result.getReply()).doesNotContain("No encontré a ese profesional");
        assertThat(result.getReply()).doesNotContain("no tenemos un profesional");
        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).contains("Corte Premium");
    }

    // TEST 9: si el profesional explícito está realmente disponible, se pasa a confirmación
    // (Resultado A), sin quedarse en una pregunta ni en un mensaje de error.
    @Test
    void explicitEmployeeAvailableAtRequestedTimeReachesConfirmation() {
        String phone = "+595981900005";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan);
        selectCortePremiumOnly(phone);

        stubAi("Quiero para el jueves con Juan Gómez a las 10:00",
                "INTENT: BOOK_APPOINTMENT\nEMPLOYEE_NAME: Juan Gómez\nSTART_AT: el próximo jueves a las 10:00\n"
                        + "CONFIDENCE: 0.85\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero para el jueves con Juan Gómez a las 10:00");

        assertThat(result.getReply()).contains("¿Confirmás?");
    }

    // TEST 8: un profesional explícito nunca se reemplaza en silencio por otro, aunque haya más de
    // uno habilitado para el servicio.
    @Test
    void explicitEmployeeAmongSeveralNeverAutoSwapsToAnother() {
        String phone = "+595981900006";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        Employee victor = employee(7L, "Victor", "Chamorro", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan, victor);
        selectCortePremiumOnly(phone);

        stubAi("Quiero para el jueves con Juan Gómez a las 10:00",
                "INTENT: BOOK_APPOINTMENT\nEMPLOYEE_NAME: Juan Gómez\nSTART_AT: el próximo jueves a las 10:00\n"
                        + "CONFIDENCE: 0.85\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero para el jueves con Juan Gómez a las 10:00");

        assertThat(result.getReply()).contains("Juan Gómez");
        assertThat(result.getReply()).doesNotContain("Victor");
    }

    // TEST 10 (Resultado B): el profesional explícito existe y realiza el servicio, pero está
    // ocupado a esa hora -> mensaje específico de horario ocupado, nunca el genérico "no tenemos
    // un profesional disponible para ese servicio".
    @Test
    void explicitEmployeeOccupiedGetsSpecificOccupiedMessage() {
        String phone = "+595981900007";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan);
        selectCortePremiumOnly(phone);

        when(appointmentService.isAvailable(eq(1L), eq(3L), eq(4L), any(Instant.class))).thenReturn(false);
        when(appointmentService.checkAvailability(eq(1L), eq(3L), eq(4L), any(Instant.class)))
                .thenReturn(AvailabilityCheck.unavailable(AvailabilityReason.OVERLAPPING));

        stubAi("Quiero para el jueves con Juan Gómez a las 10:00",
                "INTENT: BOOK_APPOINTMENT\nEMPLOYEE_NAME: Juan Gómez\nSTART_AT: el próximo jueves a las 10:00\n"
                        + "CONFIDENCE: 0.85\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero para el jueves con Juan Gómez a las 10:00");

        assertThat(result.getReply()).contains("no está disponible");
        assertThat(result.getReply()).contains("¿Querés elegir otro horario?");
        assertThat(result.getReply()).doesNotContain("no tenemos un profesional disponible para ese servicio");
    }

    // TEST 11 (Resultado C): el profesional explícito existe y realiza el servicio, pero el
    // horario pedido cae fuera de su Schedule -> mensaje específico distinto del de "ocupado".
    @Test
    void explicitEmployeeOutsideScheduleGetsSpecificMessage() {
        String phone = "+595981900008";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan);
        selectCortePremiumOnly(phone);

        when(appointmentService.isAvailable(eq(1L), eq(3L), eq(4L), any(Instant.class))).thenReturn(false);
        when(appointmentService.checkAvailability(eq(1L), eq(3L), eq(4L), any(Instant.class)))
                .thenReturn(AvailabilityCheck.unavailable(AvailabilityReason.OUTSIDE_SCHEDULE));

        stubAi("Quiero para el jueves con Juan Gómez a las 10:00",
                "INTENT: BOOK_APPOINTMENT\nEMPLOYEE_NAME: Juan Gómez\nSTART_AT: el próximo jueves a las 10:00\n"
                        + "CONFIDENCE: 0.85\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero para el jueves con Juan Gómez a las 10:00");

        assertThat(result.getReply()).contains("no atiende");
        assertThat(result.getReply()).contains("¿Querés ver otros horarios disponibles?");
    }

    // TEST 12 (Resultado D): el profesional explícito existe pero no realiza ese servicio ->
    // mensaje específico, distinto de "ocupado" y de "fuera de horario".
    @Test
    void explicitEmployeeCannotPerformServiceGetsSpecificMessage() {
        String phone = "+595981900009";
        existingCustomer(phone, "Cristian");
        Service corte = service(4L, "Corte Premium", new BigDecimal("65000"));
        Employee juan = employee(3L, "Juan", "Gómez", corte);
        stubServiceLookup("Corte Premium", corte);
        stubEmployeeListing(juan);
        selectCortePremiumOnly(phone);

        when(appointmentService.isAvailable(eq(1L), eq(3L), eq(4L), any(Instant.class))).thenReturn(false);
        when(appointmentService.checkAvailability(eq(1L), eq(3L), eq(4L), any(Instant.class)))
                .thenReturn(AvailabilityCheck.unavailable(AvailabilityReason.EMPLOYEE_CANNOT_PERFORM_SERVICE));

        stubAi("Quiero para el jueves con Juan Gómez a las 10:00",
                "INTENT: BOOK_APPOINTMENT\nEMPLOYEE_NAME: Juan Gómez\nSTART_AT: el próximo jueves a las 10:00\n"
                        + "CONFIDENCE: 0.85\nREPLY: no importa");
        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Quiero para el jueves con Juan Gómez a las 10:00");

        assertThat(result.getReply()).contains("no realiza Corte Premium");
        assertThat(result.getReply()).contains("¿Querés ver qué profesionales están disponibles para ese servicio?");
    }

    // TEST 14: cuando el servicio ya se conoce, la disponibilidad se consulta con su duración real
    // (nunca el default de 30 minutos de una consulta sin servicio): serviceId siempre viaja al
    // pedir la lista de horarios.
    @Test
    void availabilityListAlwaysPassesKnownServiceIdNeverDefaultsToNoService() {
        String phone = "+595981900013";
        existingCustomer(phone, "Cristian");
        Service corteBarba = service(6L, "Corte Barba", new BigDecimal("45000"));
        Employee juan = employee(3L, "Juan", "Gómez", corteBarba);
        stubServiceLookup("Corte Barba", corteBarba);
        stubEmployeeListing(juan);

        stubAi("Corte Barba", "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte Barba\nCONFIDENCE: 0.9\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "Corte Barba");

        stubAi("¿Qué horarios tienen?", "INTENT: CHECK_AVAILABILITY\nCONFIDENCE: 0.8\nREPLY: no importa");
        conversationService.processChannelConversation(1L, phone, "¿Qué horarios tienen?");

        verify(appointmentService, never()).findAvailableSlots(anyLong(), anyLong(), isNull(), any(LocalDate.class), anyInt());
        verify(appointmentService, org.mockito.Mockito.atLeastOnce())
                .findAvailableSlots(eq(1L), eq(3L), eq(6L), any(LocalDate.class), anyInt());
    }

}
