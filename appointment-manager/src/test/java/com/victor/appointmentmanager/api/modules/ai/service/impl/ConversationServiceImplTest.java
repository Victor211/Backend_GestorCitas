package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.ai.datetime.BusinessDateTimeResolver;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.entity.ConversationState;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.prompt.SystemPromptBuilder;
import com.victor.appointmentmanager.api.modules.ai.repository.ConversationStateRepository;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.repository.AppointmentRepository;
import com.victor.appointmentmanager.api.modules.appointments.service.AppointmentService;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.employees.repository.EmployeeRepository;
import com.victor.appointmentmanager.api.modules.schedule.repository.ScheduleRepository;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.modules.services.repository.ServiceRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        lenient().when(scheduleRepository.findActiveByBusiness(1L, null, null)).thenReturn(List.of());

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

        ConversationStateStore conversationStateStore = new ConversationStateStore(conversationStateRepository);
        CustomerIdentityResolver customerIdentityResolver = new CustomerIdentityResolver(customerRepository);
        ConfirmationClassifier confirmationClassifier = new ConfirmationClassifier();
        com.victor.appointmentmanager.api.modules.ai.format.GuaraniAmountFormatter guaraniAmountFormatter =
                new com.victor.appointmentmanager.api.modules.ai.format.GuaraniAmountFormatter();

        conversationService = new ConversationServiceImpl(aiProvider, systemPromptBuilder, businessRepository,
                serviceRepository, employeeRepository, scheduleRepository, appointmentRepository, appointmentService,
                currentUserProvider, businessDateTimeResolver, conversationStateStore, customerIdentityResolver,
                confirmationClassifier, guaraniAmountFormatter);
    }

    private static String key(Long businessId, String phone) {
        return businessId + ":" + phone;
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
        }
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
    // AJUSTE 4/5: identificación de cliente por sender_phone
    // ---------------------------------------------------------------------------------------

    @Test
    void newCustomerIsNeverAskedForPhoneAndGetsCreatedUsingSenderPhone() {
        String phone = "+595987000001";
        stubAi("Quiero reservar un turno", "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.6\nREPLY: no importa");

        ConversationResponse first = conversationService.processChannelConversation(
                1L, phone, "Quiero reservar un turno");

        assertThat(first.getReply().toLowerCase()).doesNotContain("teléfono").doesNotContain("telefono");
        assertThat(first.getReply()).contains("¿Cuál es tu nombre?");

        ConversationResponse second = conversationService.processChannelConversation(1L, phone, "María López");

        assertThat(customersByPhone).containsKey(phone);
        Customer created = customersByPhone.get(phone);
        assertThat(created.getPhone()).isEqualTo(phone);
        assertThat(created.getFirstName()).isEqualTo("María");
        assertThat(created.getLastName()).isEqualTo("López");
        assertThat(second.getReply().toLowerCase()).doesNotContain("teléfono").doesNotContain("telefono");
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
    // AJUSTE 6: saludo inicial
    // ---------------------------------------------------------------------------------------

    @Test
    void initialGreetingUsesBusinessNameAndStillAddressesTheOriginalMessageInTheSameTurn() {
        String phone = "+595987000002";
        stubAi("Hola, quiero reservar mañana a las 10",
                "INTENT: BOOK_APPOINTMENT\nSTART_AT: mañana a las 10\nCONFIDENCE: 0.8\nREPLY: no importa");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, phone, "Hola, quiero reservar mañana a las 10");

        assertThat(result.getReply()).contains("Peluquería Elegance");
        assertThat(result.getReply()).contains("¿Cuál es tu nombre?");
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

}
