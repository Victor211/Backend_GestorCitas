package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.ai.datetime.BusinessDateTimeResolver;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.prompt.SystemPromptBuilder;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private CustomerRepository customerRepository;

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

    @Spy
    private BusinessDateTimeResolver businessDateTimeResolver = new BusinessDateTimeResolver();

    @InjectMocks
    private ConversationServiceImpl conversationService;

    private Business business;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");
        business.setTimezone("America/Asuncion");

        lenient().when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        lenient().when(systemPromptBuilder.build(anyString())).thenReturn("system prompt");
        lenient().when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(Page.empty());
        lenient().when(scheduleRepository.findActiveByBusiness(1L, null, null)).thenReturn(List.of());
    }

    @Test
    void processAuthenticatedConversationDerivesBusinessIdFromJwt() {
        when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
        when(aiProvider.generateResponse(anyString(), eq("Hola"))).thenReturn(
                "INTENT: GREETING\nCONFIDENCE: 0.95\nREPLY: ¡Hola! ¿En qué puedo ayudarte?");

        ConversationRequest request = new ConversationRequest();
        request.setCustomerPhone("595981000000");
        request.setMessage("Hola");

        ConversationResponse result = conversationService.processAuthenticatedConversation(request);

        assertThat(result.getReply()).isEqualTo("¡Hola! ¿En qué puedo ayudarte?");
        assertThat(result.getIntent()).isEqualTo(ConversationIntent.GREETING);
        assertThat(result.getAppointmentId()).isNull();
        verify(businessRepository).findByIdAndActiveTrue(1L);
    }

    @Test
    void processChannelConversationUsesExplicitBusinessIdNotJwt() {
        when(aiProvider.generateResponse(anyString(), eq("Hola"))).thenReturn(
                "INTENT: GREETING\nCONFIDENCE: 0.95\nREPLY: ¡Hola!");

        conversationService.processChannelConversation(1L, "595981000000", "Hola");

        verify(businessRepository).findByIdAndActiveTrue(1L);
        verify(currentUserProvider, never()).getCurrentBusinessId();
    }

    @Test
    void bookAppointmentIntentCreatesAppointmentUsingExplicitBusinessIdOverload() {
        Service service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setBusiness(business);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Employee employee = new Employee();
        employee.setId(3L);
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 2026-08-10T14:00:00Z\n"
                        + "CONFIDENCE: 0.8\nREPLY: ¡Listo! Te reservé el turno.");
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq("Corte"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));

        Instant startAt = Instant.parse("2026-08-10T13:00:00Z");
        AppointmentResponse appointmentResponse = new AppointmentResponse();
        appointmentResponse.setId(100L);
        appointmentResponse.setServiceName("Corte Premium");
        appointmentResponse.setEmployeeName("Juan Gómez");
        appointmentResponse.setStartAt(startAt);
        when(appointmentService.create(eq(1L), any())).thenReturn(appointmentResponse);

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "Quiero un turno para un corte mañana a las 14");

        assertThat(result.getAppointmentId()).isEqualTo(100L);
        assertThat(result.getIntent()).isEqualTo(ConversationIntent.BOOK_APPOINTMENT);
        assertThat(result.getConfidence()).isEqualTo(1.0);
        // El reply no proviene del texto libre de la IA ("¡Listo! Te reservé el turno."):
        // se construye determinísticamente en backend a partir del AppointmentResponse real.
        assertThat(result.getReply()).isNotEqualTo("¡Listo! Te reservé el turno.");
        assertThat(result.getReply()).contains("Corte Premium", "Juan Gómez", "confirmada", "Barbería Central");
        verify(appointmentService).create(eq(1L), any());
    }

    @Test
    void confirmationReplyShowsLocalBusinessTimezoneNotUtc() {
        Service service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setBusiness(business);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Employee employee = new Employee();
        employee.setId(3L);
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 2026-08-10T14:00:00Z\n"
                        + "CONFIDENCE: 0.8\nREPLY: ¡Listo!");
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq("Corte"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));

        Instant startAt = Instant.parse("2026-08-10T13:00:00Z");
        ZonedDateTime expectedLocal = startAt.atZone(ZoneId.of("America/Asuncion"));
        String expectedTime = DateTimeFormatter.ofPattern("HH:mm").format(expectedLocal);

        AppointmentResponse appointmentResponse = new AppointmentResponse();
        appointmentResponse.setId(100L);
        appointmentResponse.setServiceName("Corte Premium");
        appointmentResponse.setEmployeeName("Juan Gómez");
        appointmentResponse.setStartAt(startAt);
        when(appointmentService.create(eq(1L), any())).thenReturn(appointmentResponse);

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "Quiero un turno para un corte mañana a las 14");

        assertThat(result.getReply()).contains("a las " + expectedTime);
        assertThat(result.getReply()).doesNotContain("13:00");
    }

    @Test
    void aiCannotForceNegativeReplyWhenAppointmentWasActuallyCreated() {
        Service service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setBusiness(business);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Employee employee = new Employee();
        employee.setId(3L);
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        // El modelo redacta un texto negativo aunque la creación real vaya a ser exitosa.
        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 2026-08-10T14:00:00Z\n"
                        + "CONFIDENCE: 0.5\nREPLY: Lo siento, el empleado no está disponible. "
                        + "No pudimos reservar, elige otra hora.");
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq("Corte"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));

        AppointmentResponse appointmentResponse = new AppointmentResponse();
        appointmentResponse.setId(100L);
        appointmentResponse.setServiceName("Corte Premium");
        appointmentResponse.setEmployeeName("Juan Gómez");
        appointmentResponse.setStartAt(Instant.parse("2026-08-10T13:00:00Z"));
        when(appointmentService.create(eq(1L), any())).thenReturn(appointmentResponse);

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "Quiero un turno para un corte mañana a las 14");

        assertThat(result.getAppointmentId()).isEqualTo(100L);
        assertThat(result.getReply().toLowerCase(Locale.ROOT))
                .doesNotContain("no está disponible")
                .doesNotContain("no pudimos reservar")
                .doesNotContain("elige otra hora");
        assertThat(result.getReply()).contains("confirmada");
    }

    @Test
    void doesNotCallAiProviderASecondTimeAfterCreatingAppointment() {
        Service service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setBusiness(business);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Employee employee = new Employee();
        employee.setId(3L);
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 2026-08-10T14:00:00Z\n"
                        + "CONFIDENCE: 0.8\nREPLY: ¡Listo!");
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq("Corte"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));

        AppointmentResponse appointmentResponse = new AppointmentResponse();
        appointmentResponse.setId(100L);
        appointmentResponse.setServiceName("Corte Premium");
        appointmentResponse.setEmployeeName("Juan Gómez");
        appointmentResponse.setStartAt(Instant.parse("2026-08-10T13:00:00Z"));
        when(appointmentService.create(eq(1L), any())).thenReturn(appointmentResponse);

        conversationService.processChannelConversation(
                1L, "595981000000", "Quiero un turno para un corte mañana a las 14");

        verify(aiProvider, times(1)).generateResponse(anyString(), anyString());
    }

    @Test
    void doesNotConfirmBookingWhenAiDidNotExtractRequiredData() {
        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nCONFIDENCE: 0.8\nREPLY: ¡Tu cita ha sido confirmada!");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "quiero un turno");

        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply()).isNotEqualTo("¡Tu cita ha sido confirmada!");
        verify(appointmentService, never()).create(any(), any());
    }

    @Test
    void doesNotConfirmBookingWhenAppointmentServiceReturnsNull() {
        Service service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setBusiness(business);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Employee employee = new Employee();
        employee.setId(3L);
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 2026-08-10T14:00:00Z\n"
                        + "CONFIDENCE: 0.8\nREPLY: ¡Tu cita ha sido confirmada!");
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq("Corte"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));
        when(appointmentService.create(eq(1L), any())).thenReturn(null);

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "Quiero un turno para un corte mañana a las 14");

        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply()).isNotEqualTo("¡Tu cita ha sido confirmada!");
    }

    @Test
    void doesNotConfirmBookingWhenAppointmentResponseHasNullId() {
        Service service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setBusiness(business);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Employee employee = new Employee();
        employee.setId(3L);
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 2026-08-10T14:00:00Z\n"
                        + "CONFIDENCE: 0.8\nREPLY: ¡Tu cita ha sido confirmada!");
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq("Corte"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));
        when(appointmentService.create(eq(1L), any())).thenReturn(new AppointmentResponse());

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "Quiero un turno para un corte mañana a las 14");

        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply()).isNotEqualTo("¡Tu cita ha sido confirmada!");
    }

    @Test
    void doesNotConfirmBookingWhenAppointmentServiceThrowsAvailabilityError() {
        Service service = new Service();
        service.setId(4L);
        service.setName("Corte");
        service.setBusiness(business);

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Employee employee = new Employee();
        employee.setId(3L);
        employee.setBusiness(business);
        employee.setServices(Set.of(service));

        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: BOOK_APPOINTMENT\nSERVICE_NAME: Corte\nSTART_AT: 2026-08-10T14:00:00Z\n"
                        + "CONFIDENCE: 0.8\nREPLY: ¡Tu cita ha sido confirmada!");
        when(serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq("Corte"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(service)));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        eq(1L), eq(""), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(employee)));
        when(appointmentService.create(eq(1L), any()))
                .thenThrow(new com.victor.appointmentmanager.api.common.exception.BusinessException(
                        "La cita se superpone con otra cita existente del empleado"));

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "Quiero un turno para un corte mañana a las 14");

        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply()).isNotEqualTo("¡Tu cita ha sido confirmada!");
        assertThat(result.getReply()).contains("La cita se superpone con otra cita existente del empleado");
    }

    @Test
    void cancelAppointmentIntentCancelsNextUpcomingAppointment() {
        Customer customer = new Customer();
        customer.setId(2L);
        customer.setBusiness(business);

        Appointment appointment = new Appointment();
        appointment.setId(100L);
        appointment.setBusiness(business);

        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn(
                "INTENT: CANCEL_APPOINTMENT\nCONFIDENCE: 0.7\nREPLY: Listo, cancelé tu turno.");
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "595981000000"))
                .thenReturn(Optional.of(customer));
        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        AppointmentResponse appointmentResponse = new AppointmentResponse();
        appointmentResponse.setId(100L);
        when(appointmentService.cancel(1L, 100L)).thenReturn(appointmentResponse);

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "Cancelá mi turno");

        assertThat(result.getAppointmentId()).isEqualTo(100L);
        verify(appointmentService).cancel(1L, 100L);
    }

    @Test
    void fallsBackToDefaultReplyWhenAiResponseIsBlank() {
        when(aiProvider.generateResponse(anyString(), anyString())).thenReturn("");

        ConversationResponse result = conversationService.processChannelConversation(
                1L, "595981000000", "???");

        assertThat(result.getIntent()).isEqualTo(ConversationIntent.UNKNOWN);
        assertThat(result.getAppointmentId()).isNull();
        assertThat(result.getReply()).isNotBlank();
    }

}
