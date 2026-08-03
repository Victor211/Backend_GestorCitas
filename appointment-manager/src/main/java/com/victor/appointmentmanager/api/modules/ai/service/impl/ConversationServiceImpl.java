package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.ai.datetime.BusinessDateTimeResolver;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.prompt.SystemPromptBuilder;
import com.victor.appointmentmanager.api.modules.ai.service.ConversationService;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.RescheduleAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import com.victor.appointmentmanager.api.modules.appointments.repository.AppointmentRepository;
import com.victor.appointmentmanager.api.modules.appointments.service.AppointmentService;
import com.victor.appointmentmanager.api.modules.appointments.specification.AppointmentSpecifications;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String NONE_MARKER = "NONE";
    private static final String REPLY_MARKER = "REPLY:";
    private static final String DEFAULT_REPLY =
            "Disculpá, no pude procesar tu mensaje en este momento. ¿Podés reformularlo?";

    private static final String MISSING_BOOKING_DATA_REPLY =
            "Me faltan datos para completar tu reserva. ¿Podés indicarme el servicio y la fecha/hora que preferís?";
    private static final String SERVICE_NOT_FOUND_REPLY =
            "No encontré ese servicio en nuestro catálogo. ¿Podés confirmarme el nombre exacto?";
    private static final String CUSTOMER_NOT_FOUND_REPLY =
            "No pude identificar tus datos como cliente. ¿Podés confirmarme tu nombre y teléfono?";
    private static final String EMPLOYEE_NOT_AVAILABLE_REPLY =
            "En este momento no tenemos un profesional disponible para ese servicio.";
    private static final String BOOKING_FAILED_REPLY =
            "No pudimos completar la reserva en este momento. ¿Querés intentar con otro horario?";
    private static final double CONFIRMED_BOOKING_CONFIDENCE = 1.0;
    private static final DateTimeFormatter LOCAL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AiProvider aiProvider;
    private final SystemPromptBuilder systemPromptBuilder;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final ServiceRepository serviceRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessDateTimeResolver businessDateTimeResolver;

    @Override
    public ConversationResponse processAuthenticatedConversation(ConversationRequest request) {
        return process(currentUserProvider.getCurrentBusinessId(), request.getCustomerPhone(), request.getMessage());
    }

    @Override
    public ConversationResponse processChannelConversation(Long businessId, String customerPhone, String message) {
        return process(businessId, customerPhone, message);
    }

    private ConversationResponse process(Long businessId, String customerPhone, String message) {
        Business business = findActiveBusinessOrThrow(businessId);

        String systemPrompt = systemPromptBuilder.build(buildBusinessContext(business));
        String rawAiReply = aiProvider.generateResponse(systemPrompt, message);

        ParsedAiReply parsed = parseAiReply(rawAiReply);

        log.info("Conversación IA recibida. businessId={}, intent={}, confidence={}",
                business.getId(), parsed.intent(), parsed.confidence());

        DispatchOutcome outcome = dispatch(customerPhone, business, parsed);

        // El texto libre generado por el modelo (parsed.reply()) solo se usa cuando la
        // acción de negocio correspondiente no tiene un resultado verificable propio (por
        // ejemplo GREETING). En cuanto AppointmentService confirma o rechaza una reserva,
        // replyOverride reemplaza ese texto por completo: el modelo puede redactar mensajes
        // y detectar intención, pero nunca puede decidir por sí solo si una reserva fue
        // exitosa ni contradecir el resultado real ya persistido.
        String finalReply = outcome.replyOverride() != null ? outcome.replyOverride() : parsed.reply();
        double finalConfidence = outcome.confidenceOverride() != null
                ? outcome.confidenceOverride()
                : parsed.confidence();

        return new ConversationResponse(finalReply, parsed.intent(), finalConfidence, outcome.appointmentId());
    }

    private DispatchOutcome dispatch(String customerPhone, Business business, ParsedAiReply parsed) {
        return switch (parsed.intent()) {
            case BOOK_APPOINTMENT -> handleBookAppointment(customerPhone, business, parsed);
            case RESCHEDULE_APPOINTMENT ->
                    DispatchOutcome.unchanged(handleReschedule(customerPhone, business, parsed));
            case CANCEL_APPOINTMENT -> DispatchOutcome.unchanged(handleCancel(customerPhone, business));
            case CHECK_AVAILABILITY, GREETING, UNKNOWN -> DispatchOutcome.none();
        };
    }

    private DispatchOutcome handleBookAppointment(String customerPhone, Business business, ParsedAiReply parsed) {
        if (parsed.serviceName() == null || parsed.startAtText() == null) {
            log.info("Reserva no iniciada: la IA no extrajo servicio y/o fecha/hora. businessId={}",
                    business.getId());
            return DispatchOutcome.failure(MISSING_BOOKING_DATA_REPLY);
        }

        Instant startAt;
        try {
            startAt = businessDateTimeResolver.resolve(parsed.startAtText(), business.getTimezone());
        } catch (BusinessException ex) {
            log.info("Reserva rechazada: no se pudo interpretar la fecha/hora indicada. businessId={}, "
                            + "exceptionMessage={}",
                    business.getId(), ex.getMessage());
            return DispatchOutcome.failure(ex.getMessage() != null ? ex.getMessage() : BOOKING_FAILED_REPLY);
        }

        Optional<Service> service = findServiceByName(business.getId(), parsed.serviceName());
        if (service.isEmpty()) {
            log.info("Reserva rechazada: servicio no encontrado en el Business. businessId={}", business.getId());
            return DispatchOutcome.failure(SERVICE_NOT_FOUND_REPLY);
        }

        Optional<Customer> customer = customerRepository.findByBusinessIdAndPhoneAndActiveTrue(
                business.getId(), customerPhone);
        if (customer.isEmpty()) {
            log.info("Reserva rechazada: cliente no encontrado en el Business. businessId={}", business.getId());
            return DispatchOutcome.failure(CUSTOMER_NOT_FOUND_REPLY);
        }

        Optional<Employee> employee = findEmployeeForService(business.getId(), service.get());
        if (employee.isEmpty()) {
            log.info("Reserva rechazada: ningún empleado habilitado para el servicio. businessId={}, serviceId={}",
                    business.getId(), service.get().getId());
            return DispatchOutcome.failure(EMPLOYEE_NOT_AVAILABLE_REPLY);
        }

        CreateAppointmentRequest createRequest = new CreateAppointmentRequest();
        createRequest.setCustomerId(customer.get().getId());
        createRequest.setEmployeeId(employee.get().getId());
        createRequest.setServiceId(service.get().getId());
        createRequest.setStartAt(startAt);

        log.info("Invocando AppointmentService.create. businessId={}, customerId={}, employeeId={}, serviceId={}, "
                        + "startAt={}",
                business.getId(), customer.get().getId(), employee.get().getId(), service.get().getId(),
                startAt);

        AppointmentResponse response;
        try {
            response = appointmentService.create(business.getId(), createRequest);
        } catch (BusinessException | ResourceNotFoundException ex) {
            log.info("AppointmentService.create rechazó la reserva. businessId={}, exceptionType={}, "
                            + "exceptionMessage={}",
                    business.getId(), ex.getClass().getSimpleName(), ex.getMessage());
            String reason = ex.getMessage() != null
                    ? "No pudimos completar la reserva: " + ex.getMessage()
                    : BOOKING_FAILED_REPLY;
            return DispatchOutcome.failure(reason);
        } catch (RuntimeException ex) {
            log.error("Error inesperado al invocar AppointmentService.create. businessId={}, exceptionType={}, "
                            + "exceptionMessage={}",
                    business.getId(), ex.getClass().getSimpleName(), ex.getMessage());
            return DispatchOutcome.failure(BOOKING_FAILED_REPLY);
        }

        if (response == null || response.getId() == null) {
            log.warn("AppointmentService.create devolvió una respuesta sin id. businessId={}, resultado={}",
                    business.getId(), response == null ? "null" : "sin id");
            return DispatchOutcome.failure(BOOKING_FAILED_REPLY);
        }

        log.info("Reserva creada exitosamente por la IA. businessId={}, appointmentId={}",
                business.getId(), response.getId());
        return DispatchOutcome.bookingConfirmed(response.getId(), buildConfirmationReply(business, response));
    }

    /**
     * Mensaje de confirmación construido enteramente en backend a partir del
     * AppointmentResponse real. El modelo de IA nunca decide el texto final de una reserva
     * exitosa: solo el resultado persistido determina qué se le comunica al cliente.
     */
    private String buildConfirmationReply(Business business, AppointmentResponse response) {
        ZonedDateTime localStart = response.getStartAt().atZone(ZoneId.of(business.getTimezone()));

        return "Tu cita para " + response.getServiceName() + " con " + response.getEmployeeName()
                + " quedó confirmada para el " + LOCAL_DATE_FORMATTER.format(localStart)
                + " a las " + LOCAL_TIME_FORMATTER.format(localStart) + ". ¡Te esperamos en "
                + business.getName() + "!";
    }

    private Long handleReschedule(String customerPhone, Business business, ParsedAiReply parsed) {
        if (parsed.startAtText() == null) {
            return null;
        }

        Long appointmentId = parsed.appointmentId() != null
                ? parsed.appointmentId()
                : findNextUpcomingAppointmentId(customerPhone, business).orElse(null);

        if (appointmentId == null) {
            return null;
        }

        Long id = appointmentId;
        return attempt(() -> {
            Instant startAt = businessDateTimeResolver.resolve(parsed.startAtText(), business.getTimezone());
            RescheduleAppointmentRequest rescheduleRequest = new RescheduleAppointmentRequest();
            rescheduleRequest.setStartAt(startAt);
            return appointmentService.reschedule(business.getId(), id, rescheduleRequest).getId();
        });
    }

    private Long handleCancel(String customerPhone, Business business) {
        Long appointmentId = findNextUpcomingAppointmentId(customerPhone, business).orElse(null);
        if (appointmentId == null) {
            return null;
        }

        Long id = appointmentId;
        return attempt(() -> appointmentService.cancel(business.getId(), id).getId());
    }

    private Optional<Long> findNextUpcomingAppointmentId(String customerPhone, Business business) {
        Optional<Customer> customer = customerRepository.findByBusinessIdAndPhoneAndActiveTrue(
                business.getId(), customerPhone);
        if (customer.isEmpty()) {
            return Optional.empty();
        }

        Pageable pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "startAt"));
        Specification<Appointment> specification = AppointmentSpecifications.filterBy(
                business.getId(), null, customer.get().getId(), AppointmentStatus.CONFIRMED, Instant.now(), null);
        Page<Appointment> page = appointmentRepository.findAll(specification, pageable);

        return page.getContent().stream().findFirst().map(Appointment::getId);
    }

    private Optional<Service> findServiceByName(Long businessId, String serviceName) {
        return serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        businessId, serviceName, PageRequest.of(0, 1))
                .getContent()
                .stream()
                .findFirst();
    }

    private Optional<Employee> findEmployeeForService(Long businessId, Service service) {
        return employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        businessId, "", Pageable.unpaged())
                .getContent()
                .stream()
                .filter(employee -> employee.getServices().stream()
                        .anyMatch(assigned -> assigned.getId().equals(service.getId())))
                .findFirst();
    }

    private Long attempt(java.util.function.Supplier<Long> action) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String buildBusinessContext(Business business) {
        List<Service> services = serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                business.getId(), "", Pageable.unpaged()).getContent();

        List<Schedule> schedules = scheduleRepository.findActiveByBusiness(business.getId(), null, null);

        StringBuilder context = new StringBuilder("Negocio: ").append(business.getName()).append('\n');

        if (services.isEmpty()) {
            context.append("Todavía no hay servicios cargados.\n");
        } else {
            context.append("Servicios disponibles:\n");
            context.append(services.stream()
                    .map(service -> "- " + service.getName() + " (" + service.getDurationMinutes()
                            + " minutos, $" + service.getPrice() + ")")
                    .collect(Collectors.joining("\n")));
            context.append('\n');
        }

        if (schedules.isEmpty()) {
            context.append("Todavía no hay horarios de atención cargados.\n");
        } else {
            context.append("Horarios de atención por empleado:\n");
            context.append(schedules.stream()
                    .map(schedule -> "- " + schedule.getEmployee().getFirstName() + " "
                            + schedule.getEmployee().getLastName() + ": " + schedule.getDayOfWeek() + " "
                            + schedule.getStartTime() + " a " + schedule.getEndTime())
                    .collect(Collectors.joining("\n")));
            context.append('\n');
        }

        return context.toString();
    }

    private ParsedAiReply parseAiReply(String raw) {
        String text = raw == null ? "" : raw;
        int replyIndex = text.indexOf(REPLY_MARKER);

        String headerSection = replyIndex >= 0 ? text.substring(0, replyIndex) : text;
        String reply = replyIndex >= 0 ? text.substring(replyIndex + REPLY_MARKER.length()).trim() : text.trim();

        Map<String, String> fields = new HashMap<>();
        for (String line : headerSection.split("\\R")) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                fields.put(line.substring(0, separator).trim().toUpperCase(Locale.ROOT),
                        line.substring(separator + 1).trim());
            }
        }

        return new ParsedAiReply(
                parseIntent(fields.get("INTENT")),
                parseConfidence(fields.get("CONFIDENCE")),
                valueOrNull(fields.get("SERVICE_NAME")),
                valueOrNull(fields.get("START_AT")),
                parseLong(fields.get("APPOINTMENT_ID")),
                reply.isBlank() ? DEFAULT_REPLY : reply);
    }

    private ConversationIntent parseIntent(String value) {
        if (value == null) {
            return ConversationIntent.UNKNOWN;
        }
        try {
            return ConversationIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ConversationIntent.UNKNOWN;
        }
    }

    private double parseConfidence(String value) {
        if (value == null) {
            return 0.0;
        }
        try {
            return Math.min(1.0, Math.max(0.0, Double.parseDouble(value.trim())));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private String valueOrNull(String value) {
        if (value == null || value.isBlank() || NONE_MARKER.equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private Long parseLong(String value) {
        String cleaned = valueOrNull(value);
        if (cleaned == null) {
            return null;
        }
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Business findActiveBusinessOrThrow(Long businessId) {
        return businessRepository.findByIdAndActiveTrue(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado con id " + businessId));
    }

    private record ParsedAiReply(ConversationIntent intent, double confidence, String serviceName,
                                  String startAtText, Long appointmentId, String reply) {
    }

    /**
     * Resultado de despachar una intención hacia el módulo de negocio correspondiente.
     * replyOverride distinto de null indica que el texto libre del modelo NO debe usarse
     * como respuesta final, y debe reemplazarse por un mensaje conversacional que refleje
     * el estado real. confidenceOverride distinto de null fuerza la confianza reportada
     * (una reserva confirmada por AppointmentService es un hecho verificado, no una
     * estimación del modelo).
     */
    private record DispatchOutcome(Long appointmentId, String replyOverride, Double confidenceOverride) {

        static DispatchOutcome none() {
            return new DispatchOutcome(null, null, null);
        }

        static DispatchOutcome unchanged(Long appointmentId) {
            return new DispatchOutcome(appointmentId, null, null);
        }

        static DispatchOutcome bookingConfirmed(Long appointmentId, String reply) {
            return new DispatchOutcome(appointmentId, reply, CONFIRMED_BOOKING_CONFIDENCE);
        }

        static DispatchOutcome failure(String replyOverride) {
            return new DispatchOutcome(null, replyOverride, null);
        }
    }

}
