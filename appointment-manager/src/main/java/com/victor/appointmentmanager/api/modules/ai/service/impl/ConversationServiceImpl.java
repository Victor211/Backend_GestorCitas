package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.ai.datetime.BusinessDateTimeResolver;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.entity.ConversationState;
import com.victor.appointmentmanager.api.modules.ai.enums.ConfirmationSignal;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationStage;
import com.victor.appointmentmanager.api.modules.ai.exception.AmbiguousTimeException;
import com.victor.appointmentmanager.api.modules.ai.exception.MissingTimeException;
import com.victor.appointmentmanager.api.modules.ai.format.GuaraniAmountFormatter;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String NONE_MARKER = "NONE";
    private static final String REPLY_MARKER = "REPLY:";
    private static final String DEFAULT_REPLY =
            "Disculpá, no pude procesar tu mensaje en este momento. ¿Podés reformularlo?";

    private static final String ASK_NAME_REPLY = "¿Cuál es tu nombre?";
    private static final String ASK_SERVICE_REPLY = "¿Qué servicio te gustaría reservar?";
    private static final String ASK_DATE_REPLY = "¿Para cuándo te gustaría el turno?";
    private static final String ASK_TIME_REPLY = "¿A qué hora te gustaría?";
    private static final String SERVICE_NOT_FOUND_REPLY =
            "No encontré ese servicio en nuestro catálogo. ¿Podés confirmarme el nombre exacto?";
    private static final String CUSTOMER_NOT_FOUND_REPLY =
            "No pude identificar tus datos como cliente. ¿Podés escribirme tu nombre de nuevo?";
    private static final String EMPLOYEE_NOT_AVAILABLE_REPLY =
            "En este momento no tenemos un profesional disponible para ese servicio.";
    private static final String BOOKING_FAILED_REPLY =
            "No pudimos completar la reserva en este momento. ¿Querés intentar con otro horario?";
    private static final String SLOT_NO_LONGER_AVAILABLE_REPLY =
            "Ese horario acaba de dejar de estar disponible. ¿Quieres elegir otra hora?";
    private static final String BOOKING_CANCELLED_REPLY =
            "Sin problema, cancelé la propuesta. ¿En qué más puedo ayudarte?";
    private static final String ASK_OTHER_TIME_REPLY = "Sin problema. ¿Qué otro horario prefieres?";
    private static final String ASK_OTHER_EMPLOYEE_REPLY = "Sin problema. ¿Con qué otro profesional prefieres?";
    private static final String NO_SERVICES_REPLY = "Por el momento no tenemos servicios cargados.";
    private static final String STALE_DRAFT_REPLY =
            "Se me perdieron los datos de la reserva. ¿Podés indicarme de nuevo el servicio y el horario?";
    private static final String PROPOSAL_EXPIRED_REPLY =
            "Esa propuesta ya venció. ¿Querés que revisemos de nuevo el horario?";

    private static final double CONFIRMED_BOOKING_CONFIDENCE = 1.0;
    private static final double DETERMINISTIC_CONFIDENCE = 1.0;
    private static final DateTimeFormatter LOCAL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String GREETING_PREFIX_TEMPLATE = "Hola 👋 Soy el asistente virtual de %s. ";

    /**
     * Expresión de hora "pelada" (sin fecha), opcionalmente con un marcador de tarde/mañana/noche.
     * Se usa para decidir si un START_AT de este turno debe combinarse con la fecha ya conocida de
     * un turno anterior (ConversationState.pendingDate) en lugar de interpretarse como una fecha
     * nueva completa.
     */
    private static final Pattern TIME_ONLY_EXPRESSION = Pattern.compile(
            "(?i)^(?:(?:de|por|esta)\\s+(?:la\\s+)?(?:tarde|mañana|noche)\\s+)?a las\\s+\\d{1,2}(?::\\d{2})?$");

    /**
     * Referencias pronominales al último profesional mencionado en la conversación ("con él",
     * "con ella", "el mismo", "ese profesional"), usadas cuando la IA no extrajo un EMPLOYEE_NAME
     * explícito en este turno. No sustituye NLP: es una resolución mínima y determinística contra
     * {@link ConversationState#getLastReferencedEmployeeId()}.
     */
    private static final Pattern EMPLOYEE_PRONOUN_REFERENCE = Pattern.compile(
            "(?i)\\bcon\\s+(?:el|él|ella|el\\s+mismo|la\\s+misma)\\b|\\b(?:ese|esa)\\s+(?:profesional|empleado|empleada)\\b");

    /**
     * Pedido explícito de reiniciar el flujo de reserva ("empezar de nuevo", "otra reserva",
     * etc.), reconocido fuera de AWAITING_CONFIRMATION (ahí ya lo cubre {@link ConfirmationClassifier}).
     * Solo limpia el borrador de reserva; nunca la identidad del Customer.
     */
    private static final Pattern EXPLICIT_RESTART_REQUEST = Pattern.compile(
            "(?i)\\bempezar\\s+de\\s+nuevo\\b|\\b(?:nueva|otra)\\s+reserva\\b|\\breiniciar\\b|"
                    + "\\bcomenzar\\s+otra\\s+vez\\b");

    private final AiProvider aiProvider;
    private final SystemPromptBuilder systemPromptBuilder;
    private final BusinessRepository businessRepository;
    private final ServiceRepository serviceRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final CurrentUserProvider currentUserProvider;
    private final BusinessDateTimeResolver businessDateTimeResolver;
    private final ConversationStateStore conversationStateStore;
    private final CustomerIdentityResolver customerIdentityResolver;
    private final ConfirmationClassifier confirmationClassifier;
    private final GuaraniAmountFormatter guaraniAmountFormatter;

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
        ConversationState state = conversationStateStore.loadOrCreate(businessId, customerPhone);

        ConversationResponse response = route(business, customerPhone, message, state);

        conversationStateStore.save(state);
        return response;
    }

    // ---------------------------------------------------------------------------------------
    // Enrutamiento principal
    // ---------------------------------------------------------------------------------------

    private ConversationResponse route(Business business, String customerPhone, String message,
                                        ConversationState state) {
        Optional<Customer> existingCustomer = customerIdentityResolver.findActive(business.getId(), customerPhone);
        existingCustomer.ifPresent(customer -> state.setCustomerId(customer.getId()));

        if (state.isExpiredPendingConfirmation()) {
            state.setExpiredPendingConfirmation(false);
            if (confirmationClassifier.classify(message) == ConfirmationSignal.POSITIVE) {
                // Un "sí" desfasado no debe crear una cita a ciegas: el borrador ya se limpió al
                // cargar el estado (expiró), así que no hay nada seguro que confirmar.
                return finish(state, business, PROPOSAL_EXPIRED_REPLY, ConversationIntent.REJECT_APPOINTMENT,
                        DETERMINISTIC_CONFIDENCE, null);
            }
            // Cualquier otro mensaje (uno nuevo con intención propia, un saludo, etc.) sigue el
            // flujo normal ya con el borrador limpio, sin arrastrar la propuesta vencida.
        }

        if (state.getStage() == ConversationStage.AWAITING_CONFIRMATION) {
            return respondToConfirmationStage(business, message, state, existingCustomer);
        }

        if (existingCustomer.isEmpty()) {
            return respondToUnknownCustomer(business, customerPhone, message, state);
        }

        return respondToKnownCustomer(business, existingCustomer.get(), message, state);
    }

    // ---------------------------------------------------------------------------------------
    // Cliente nuevo (AJUSTE 4): nunca se le pide teléfono, solo nombre
    // ---------------------------------------------------------------------------------------

    private ConversationResponse respondToUnknownCustomer(Business business, String customerPhone, String message,
                                                            ConversationState state) {
        if (state.isAwaitingName()) {
            Customer created = customerIdentityResolver.createFromName(business, customerPhone, message);
            state.setCustomerId(created.getId());
            state.setAwaitingName(false);
            DispatchOutcome outcome = advanceBookingFlow(business, state);
            return finishFromOutcome(state, business, outcome, ConversationIntent.BOOK_APPOINTMENT,
                    DETERMINISTIC_CONFIDENCE);
        }

        if (isExplicitCancellation(state, message)) {
            state.clearBookingDraft();
            return finish(state, business, BOOKING_CANCELLED_REPLY, ConversationIntent.REJECT_APPOINTMENT,
                    DETERMINISTIC_CONFIDENCE, null);
        }
        if (EXPLICIT_RESTART_REQUEST.matcher(message).find()) {
            state.clearBookingDraft();
        }

        // Primer contacto de un teléfono desconocido: igual se interpreta el mensaje para no
        // perder datos que el cliente ya haya mencionado (AJUSTE 6). No identificar a un
        // Customer nuevo NO implica pedirle el nombre de inmediato: eso solo debe pasar cuando
        // la intención detectada realmente requiere identificarlo (reservar/reprogramar/
        // cancelar). Una consulta puramente informativa (servicios, precios, horarios,
        // disponibilidad general) o un saludo se responden igual que a cualquier visitante,
        // sin crear ni pedir datos de Customer todavía.
        ParsedAiReply parsed = askAi(business, state, message);

        if (parsed.intent() == ConversationIntent.LIST_SERVICES) {
            return finish(state, business, buildServiceListReply(business), ConversationIntent.LIST_SERVICES,
                    parsed.confidence(), null);
        }

        if (parsed.intent() == ConversationIntent.CHECK_AVAILABILITY) {
            return handleCheckAvailability(business, state, parsed, message);
        }

        if (!intentRequiresCustomer(parsed.intent())) {
            return finish(state, business, parsed.reply(), parsed.intent(), parsed.confidence(), null);
        }

        String mergeFailure = mergeParsedIntoDraft(state, business, parsed, message);
        if (mergeFailure != null) {
            return finish(state, business, mergeFailure, ConversationIntent.BOOK_APPOINTMENT,
                    parsed.confidence(), null);
        }

        state.setAwaitingName(true);
        return finish(state, business, ASK_NAME_REPLY, ConversationIntent.BOOK_APPOINTMENT,
                parsed.confidence(), null);
    }

    /**
     * Intenciones que realmente necesitan identificar (o crear) al Customer. No confundir
     * "sender_phone desconocido" con "hay que registrar al Customer ya mismo": eso solo aplica
     * cuando la operación de negocio lo requiere (reservar, reprogramar, cancelar). Reutiliza el
     * enum de intents existente; no agrega un sistema paralelo de intenciones.
     */
    private boolean intentRequiresCustomer(ConversationIntent intent) {
        return intent == ConversationIntent.BOOK_APPOINTMENT
                || intent == ConversationIntent.RESCHEDULE_APPOINTMENT
                || intent == ConversationIntent.CANCEL_APPOINTMENT;
    }

    // ---------------------------------------------------------------------------------------
    // Cliente conocido (AJUSTE 5): nunca se le repiten preguntas ya respondidas
    // ---------------------------------------------------------------------------------------

    private ConversationResponse respondToKnownCustomer(Business business, Customer customer, String message,
                                                          ConversationState state) {
        if (isExplicitCancellation(state, message)) {
            state.clearBookingDraft();
            return finish(state, business, BOOKING_CANCELLED_REPLY, ConversationIntent.REJECT_APPOINTMENT,
                    DETERMINISTIC_CONFIDENCE, null);
        }
        if (EXPLICIT_RESTART_REQUEST.matcher(message).find()) {
            state.clearBookingDraft();
        }

        ParsedAiReply parsed = askAi(business, state, message);

        switch (parsed.intent()) {
            case LIST_SERVICES -> {
                return finish(state, business, buildServiceListReply(business), ConversationIntent.LIST_SERVICES,
                        parsed.confidence(), null);
            }
            case CHECK_AVAILABILITY -> {
                return handleCheckAvailability(business, state, parsed, message);
            }
            case RESCHEDULE_APPOINTMENT -> {
                Long appointmentId = handleReschedule(customer, business, parsed);
                return finish(state, business, parsed.reply(), ConversationIntent.RESCHEDULE_APPOINTMENT,
                        parsed.confidence(), appointmentId);
            }
            case CANCEL_APPOINTMENT -> {
                Long appointmentId = handleCancel(customer, business);
                return finish(state, business, parsed.reply(), ConversationIntent.CANCEL_APPOINTMENT,
                        parsed.confidence(), appointmentId);
            }
            default -> {
                // GREETING, BOOK_APPOINTMENT, UNKNOWN: cualquiera puede traer datos de reserva.
            }
        }

        String mergeFailure = mergeParsedIntoDraft(state, business, parsed, message);
        if (mergeFailure != null) {
            return finish(state, business, mergeFailure, ConversationIntent.BOOK_APPOINTMENT,
                    parsed.confidence(), null);
        }

        if (!hasBookingDraftProgress(state)) {
            // Charla libre (saludo, pregunta general) sin ningún dato de reserva: se usa el texto
            // de la IA tal cual, ya que no hay ninguna acción de negocio que verificar.
            return finish(state, business, parsed.reply(), parsed.intent(), parsed.confidence(), null);
        }

        DispatchOutcome outcome = advanceBookingFlow(business, state);
        return finishFromOutcome(state, business, outcome, ConversationIntent.BOOK_APPOINTMENT, parsed.confidence());
    }

    private boolean hasBookingDraftProgress(ConversationState state) {
        return state.hasPendingService() || state.hasPendingEmployee()
                || state.getPendingStartAt() != null || state.getPendingDate() != null;
    }

    /**
     * Cancelación explícita ("cancelar", "ya no quiero", "olvídalo") mientras se está
     * recopilando una reserva (fuera de AWAITING_CONFIRMATION, que ya la maneja
     * {@code respondToConfirmationStage}). Se decide en backend con el mismo clasificador
     * determinístico que la confirmación, nunca dejando que la IA decida si el cliente canceló.
     */
    private boolean isExplicitCancellation(ConversationState state, String message) {
        return hasBookingDraftProgress(state) && !state.isAwaitingName()
                && confirmationClassifier.classify(message) == ConfirmationSignal.NEGATIVE_FULL;
    }

    // ---------------------------------------------------------------------------------------
    // Confirmación obligatoria antes de crear (requisito crítico)
    // ---------------------------------------------------------------------------------------

    private ConversationResponse respondToConfirmationStage(Business business, String message,
                                                              ConversationState state,
                                                              Optional<Customer> customerOpt) {
        ConfirmationSignal signal = confirmationClassifier.classify(message);

        if (signal == ConfirmationSignal.POSITIVE) {
            if (customerOpt.isEmpty()) {
                state.clearBookingDraft();
                return finish(state, business, CUSTOMER_NOT_FOUND_REPLY, ConversationIntent.REJECT_APPOINTMENT,
                        DETERMINISTIC_CONFIDENCE, null);
            }
            DispatchOutcome outcome = handleConfirmation(business, customerOpt.get(), state);
            return finishFromOutcome(state, business, outcome, ConversationIntent.CONFIRM_APPOINTMENT,
                    DETERMINISTIC_CONFIDENCE);
        }

        if (signal != ConfirmationSignal.UNRECOGNIZED) {
            String reply = handleNegative(signal, state);
            return finish(state, business, reply, ConversationIntent.REJECT_APPOINTMENT,
                    DETERMINISTIC_CONFIDENCE, null);
        }

        DispatchOutcome outcome = handleModificationDuringConfirmation(business, state, message);
        return finishFromOutcome(state, business, outcome, ConversationIntent.BOOK_APPOINTMENT,
                DETERMINISTIC_CONFIDENCE);
    }

    private DispatchOutcome handleConfirmation(Business business, Customer customer, ConversationState state) {
        if (!state.hasPendingService() || !state.hasPendingEmployee() || state.getPendingStartAt() == null) {
            state.clearBookingDraft();
            return DispatchOutcome.reply(STALE_DRAFT_REPLY);
        }

        CreateAppointmentRequest createRequest = new CreateAppointmentRequest();
        createRequest.setCustomerId(customer.getId());
        createRequest.setEmployeeId(state.getPendingEmployeeId());
        createRequest.setServiceId(state.getPendingServiceId());
        createRequest.setStartAt(state.getPendingStartAt());

        log.info("Confirmación positiva: revalidando y creando cita. businessId={}, customerId={}, "
                        + "employeeId={}, serviceId={}, startAt={}",
                business.getId(), customer.getId(), state.getPendingEmployeeId(), state.getPendingServiceId(),
                state.getPendingStartAt());

        AppointmentResponse response;
        try {
            response = appointmentService.create(business.getId(), createRequest);
        } catch (BusinessException | ResourceNotFoundException ex) {
            log.info("Revalidación al confirmar rechazó la reserva. businessId={}, exceptionType={}, "
                            + "exceptionMessage={}",
                    business.getId(), ex.getClass().getSimpleName(), ex.getMessage());
            state.clearBookingDraft();
            return DispatchOutcome.reply(SLOT_NO_LONGER_AVAILABLE_REPLY);
        } catch (RuntimeException ex) {
            log.error("Error inesperado al confirmar una reserva. businessId={}, exceptionType={}, "
                            + "exceptionMessage={}",
                    business.getId(), ex.getClass().getSimpleName(), ex.getMessage());
            state.clearBookingDraft();
            return DispatchOutcome.reply(BOOKING_FAILED_REPLY);
        }

        if (response == null || response.getId() == null) {
            log.warn("AppointmentService.create devolvió una respuesta sin id al confirmar. businessId={}",
                    business.getId());
            state.clearBookingDraft();
            return DispatchOutcome.reply(BOOKING_FAILED_REPLY);
        }

        log.info("Reserva creada tras confirmación explícita. businessId={}, appointmentId={}",
                business.getId(), response.getId());
        state.clearBookingDraft();
        return DispatchOutcome.bookingConfirmed(response.getId(), buildConfirmationReply(business, response));
    }

    private String handleNegative(ConfirmationSignal signal, ConversationState state) {
        return switch (signal) {
            case NEGATIVE_TIME -> {
                state.setPendingStartAt(null);
                state.setPendingDate(null);
                state.setStage(ConversationStage.COLLECTING);
                yield ASK_OTHER_TIME_REPLY;
            }
            case NEGATIVE_EMPLOYEE -> {
                state.setPendingEmployeeId(null);
                state.setStage(ConversationStage.COLLECTING);
                yield ASK_OTHER_EMPLOYEE_REPLY;
            }
            default -> {
                state.clearBookingDraft();
                yield BOOKING_CANCELLED_REPLY;
            }
        };
    }

    private DispatchOutcome handleModificationDuringConfirmation(Business business, ConversationState state,
                                                                   String message) {
        ParsedAiReply parsed = askAi(business, state, message);

        boolean mentionedChange = parsed.serviceName() != null || parsed.employeeName() != null
                || parsed.startAtText() != null;
        if (!mentionedChange) {
            return DispatchOutcome.reply(parsed.reply());
        }

        String mergeFailure = mergeParsedIntoDraft(state, business, parsed, message);
        if (mergeFailure != null) {
            return DispatchOutcome.reply(mergeFailure);
        }

        return advanceBookingFlow(business, state);
    }

    // ---------------------------------------------------------------------------------------
    // Slot-filling de la reserva (AJUSTE 7/8): se pregunta un único dato por turno
    // ---------------------------------------------------------------------------------------

    private DispatchOutcome advanceBookingFlow(Business business, ConversationState state) {
        state.setStage(ConversationStage.COLLECTING);
        autoResolveEmployeeIfUnambiguous(business.getId(), state);

        if (!state.hasPendingService()) {
            return DispatchOutcome.reply(ASK_SERVICE_REPLY);
        }
        if (state.getPendingStartAt() == null) {
            return DispatchOutcome.reply(state.getPendingDate() != null ? ASK_TIME_REPLY : ASK_DATE_REPLY);
        }
        if (!state.hasPendingEmployee()) {
            List<Employee> candidates = employeesForService(business.getId(), state.getPendingServiceId());
            if (candidates.isEmpty()) {
                state.clearBookingDraft();
                return DispatchOutcome.reply(EMPLOYEE_NOT_AVAILABLE_REPLY);
            }
            String names = candidates.stream().map(this::employeeDisplayName).collect(Collectors.joining(", "));
            return DispatchOutcome.reply("¿Con qué profesional prefieres reservar? Puede ser " + names + ".");
        }

        Optional<Service> service = serviceRepository.findByIdAndBusinessIdAndActiveTrue(
                state.getPendingServiceId(), business.getId());
        Optional<Employee> employee = employeeRepository.findByIdAndBusinessIdAndActiveTrue(
                state.getPendingEmployeeId(), business.getId());
        if (service.isEmpty() || employee.isEmpty()) {
            state.clearBookingDraft();
            return DispatchOutcome.reply(BOOKING_FAILED_REPLY);
        }

        log.debug("Propuesta de reserva presentada. businessId={}, customerId={}, serviceId={}, employeeId={}, "
                        + "startAt={}",
                business.getId(), state.getCustomerId(), state.getPendingServiceId(), state.getPendingEmployeeId(),
                state.getPendingStartAt());

        state.setStage(ConversationStage.AWAITING_CONFIRMATION);
        return DispatchOutcome.reply(buildConfirmationQuestion(business, service.get(), employee.get(),
                state.getPendingStartAt()));
    }

    private void autoResolveEmployeeIfUnambiguous(Long businessId, ConversationState state) {
        if (!state.hasPendingService() || state.hasPendingEmployee()) {
            return;
        }
        List<Employee> candidates = employeesForService(businessId, state.getPendingServiceId());
        if (candidates.size() == 1) {
            Employee onlyCandidate = candidates.get(0);
            state.setPendingEmployeeId(onlyCandidate.getId());
            rememberEmployee(state, onlyCandidate);
        }
    }

    /**
     * Recuerda el último Employee mencionado o resuelto en la conversación, para poder resolver
     * referencias pronominales ("con él", "el mismo") sin volver a preguntar el profesional.
     */
    private void rememberEmployee(ConversationState state, Employee employee) {
        state.setLastReferencedEmployeeId(employee.getId());
        state.setLastReferencedEmployeeName(employeeDisplayName(employee));
    }

    private boolean referencesLastEmployee(String rawMessage) {
        return rawMessage != null && EMPLOYEE_PRONOUN_REFERENCE.matcher(rawMessage).find();
    }

    private List<Employee> employeesForService(Long businessId, Long serviceId) {
        return employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        businessId, "", Pageable.unpaged())
                .getContent().stream()
                .filter(employee -> employee.getServices().stream()
                        .anyMatch(assigned -> assigned.getId().equals(serviceId)))
                .toList();
    }

    /**
     * Aplica sobre el borrador persistido (ConversationState) los datos que la IA extrajo de
     * este turno, sin pisar nunca un dato ya confirmado por uno ausente (NONE). Devuelve un
     * mensaje de error si algo mencionado explícitamente no pudo resolverse contra el negocio
     * real (servicio inexistente, empleado inexistente, fecha/hora inválida).
     */
    private String mergeParsedIntoDraft(ConversationState state, Business business, ParsedAiReply parsed,
                                         String rawMessage) {
        if (parsed.serviceName() != null) {
            Optional<Service> service = findServiceByName(business.getId(), parsed.serviceName());
            if (service.isEmpty()) {
                return SERVICE_NOT_FOUND_REPLY;
            }
            state.setPendingServiceId(service.get().getId());
            state.setPendingEmployeeId(null);
        }

        if (parsed.employeeName() != null) {
            Optional<Employee> employee = findEmployeeByName(business.getId(), parsed.employeeName());
            if (employee.isEmpty()) {
                return EMPLOYEE_NOT_AVAILABLE_REPLY;
            }
            state.setPendingEmployeeId(employee.get().getId());
            rememberEmployee(state, employee.get());
        } else if (!state.hasPendingEmployee() && state.getLastReferencedEmployeeId() != null
                && referencesLastEmployee(rawMessage)) {
            // "con él"/"el mismo"/etc.: el usuario se refiere al último profesional mencionado,
            // no a uno nuevo. No hace falta volver a preguntar el nombre.
            state.setPendingEmployeeId(state.getLastReferencedEmployeeId());
        }

        if (parsed.startAtText() != null) {
            return resolveAndMergeStartAt(state, business, parsed.startAtText());
        }

        return null;
    }

    private String resolveAndMergeStartAt(ConversationState state, Business business, String startAtText) {
        String trimmed = startAtText.trim();
        LocalDate knownDate = knownContextDate(state, business);
        boolean canUseKnownDate = knownDate != null && TIME_ONLY_EXPRESSION.matcher(trimmed).matches();

        if (canUseKnownDate) {
            try {
                Instant resolved = businessDateTimeResolver.resolveTimeOnly(trimmed, knownDate, business.getTimezone());
                state.setPendingStartAt(resolved);
                state.setPendingDate(null);
                return null;
            } catch (AmbiguousTimeException ambiguous) {
                return ambiguous.getMessage();
            } catch (BusinessException ignored) {
                // No debería ocurrir dado el filtro de TIME_ONLY_EXPRESSION; se intenta la ruta
                // completa (fecha + hora) por si el mensaje también redefinió la fecha.
            }
        }

        try {
            Instant resolved = businessDateTimeResolver.resolve(trimmed, business.getTimezone());
            state.setPendingStartAt(resolved);
            state.setPendingDate(null);
            return null;
        } catch (MissingTimeException missingTime) {
            state.setPendingDate(missingTime.getResolvedDate());
            state.setPendingStartAt(null);
            return null;
        } catch (BusinessException ex) {
            return ex.getMessage() != null ? ex.getMessage() : BOOKING_FAILED_REPLY;
        }
    }

    /**
     * Fecha ya conocida de un turno anterior contra la cual combinar una hora "pelada" (sin
     * fecha) de este turno: o bien una fecha resuelta pero sin hora todavía
     * ({@link ConversationState#getPendingDate()}), o bien la fecha de una propuesta ya completa
     * que el cliente está corrigiendo (ej. "mejor a las 17" durante la confirmación).
     */
    private LocalDate knownContextDate(ConversationState state, Business business) {
        if (state.getPendingDate() != null) {
            return state.getPendingDate();
        }
        if (state.getPendingStartAt() != null) {
            return state.getPendingStartAt().atZone(ZoneId.of(business.getTimezone())).toLocalDate();
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------
    // AJUSTE 1: listado de servicios (siempre armado en backend, nunca por la IA)
    // ---------------------------------------------------------------------------------------

    private String buildServiceListReply(Business business) {
        List<Service> services = activeServices(business.getId());
        if (services.isEmpty()) {
            return NO_SERVICES_REPLY;
        }
        String lines = services.stream()
                .map(service -> "• " + service.getName() + " — " + guaraniAmountFormatter.format(service.getPrice()))
                .collect(Collectors.joining("\n"));
        return "Claro 😊 Estos son nuestros servicios:\n\n" + lines + "\n\n¿Cuál te gustaría reservar?";
    }

    // ---------------------------------------------------------------------------------------
    // Disponibilidad puntual (distinto de reservar): respuesta breve, sin crear ningún draft
    // ---------------------------------------------------------------------------------------

    /**
     * Consulta disponibilidad usando el borrador consolidado, no solo lo que la IA extrajo de
     * este mensaje puntual: "¿Está disponible?" (sin repetir servicio/profesional/horario) debe
     * resolverse con los datos ya conocidos de la conversación (CASO C/D), no volver a pedirlos
     * ni quedarse en una promesa ("déjame verificar") sin ejecutar la consulta real.
     */
    private ConversationResponse handleCheckAvailability(Business business, ConversationState state,
                                                           ParsedAiReply parsed, String rawMessage) {
        String mergeFailure = mergeParsedIntoDraft(state, business, parsed, rawMessage);
        if (mergeFailure != null) {
            return finish(state, business, mergeFailure, ConversationIntent.CHECK_AVAILABILITY,
                    parsed.confidence(), null);
        }
        autoResolveEmployeeIfUnambiguous(business.getId(), state);

        Optional<Employee> employee = state.hasPendingEmployee()
                ? employeeRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingEmployeeId(), business.getId())
                : Optional.empty();

        if (employee.isEmpty() || state.getPendingStartAt() == null) {
            return finish(state, business, parsed.reply(), ConversationIntent.CHECK_AVAILABILITY,
                    parsed.confidence(), null);
        }
        rememberEmployee(state, employee.get());

        String serviceName = state.hasPendingService()
                ? serviceRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingServiceId(), business.getId())
                        .map(Service::getName).orElse(null)
                : null;

        boolean available = isEmployeeAvailable(business, employee.get(), state.getPendingStartAt(), serviceName);
        ZonedDateTime localStart = state.getPendingStartAt().atZone(ZoneId.of(business.getTimezone()));
        String time = LOCAL_TIME_FORMATTER.format(localStart);
        String reply = available
                ? "Sí, " + employee.get().getFirstName() + " está disponible " + describeDate(localStart)
                        + " a las " + time + ". ¿Quieres reservar?"
                : employee.get().getFirstName() + " no está disponible a las " + time
                        + ". ¿Quieres que te muestre otro horario?";

        return finish(state, business, reply, ConversationIntent.CHECK_AVAILABILITY, parsed.confidence(), null);
    }

    private boolean isEmployeeAvailable(Business business, Employee employee, Instant startAt, String serviceName) {
        int durationMinutes = 30;
        if (serviceName != null) {
            Optional<Service> service = findServiceByName(business.getId(), serviceName);
            if (service.isPresent()) {
                durationMinutes = service.get().getDurationMinutes();
            }
        }
        Instant endAt = startAt.plus(durationMinutes, ChronoUnit.MINUTES);

        ZoneId zone = ZoneId.of(business.getTimezone());
        ZonedDateTime localStart = startAt.atZone(zone);
        ZonedDateTime localEnd = endAt.atZone(zone);

        List<Schedule> daySchedules = scheduleRepository.findByEmployeeIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                employee.getId(), localStart.getDayOfWeek());
        boolean withinWorkingHours = daySchedules.stream()
                .anyMatch(schedule -> !localStart.toLocalTime().isBefore(schedule.getStartTime())
                        && !localEnd.toLocalTime().isAfter(schedule.getEndTime()));
        if (!withinWorkingHours) {
            return false;
        }

        return !appointmentRepository.existsOverlapping(employee.getId(), startAt, endAt, null,
                AppointmentStatus.CANCELLED);
    }

    // ---------------------------------------------------------------------------------------
    // Reschedule / Cancel: sin cambios de comportamiento respecto de la versión anterior
    // ---------------------------------------------------------------------------------------

    private Long handleReschedule(Customer customer, Business business, ParsedAiReply parsed) {
        if (parsed.startAtText() == null) {
            return null;
        }

        Long appointmentId = parsed.appointmentId() != null
                ? parsed.appointmentId()
                : findNextUpcomingAppointmentId(customer, business).orElse(null);

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

    private Long handleCancel(Customer customer, Business business) {
        Long appointmentId = findNextUpcomingAppointmentId(customer, business).orElse(null);
        if (appointmentId == null) {
            return null;
        }

        Long id = appointmentId;
        return attempt(() -> appointmentService.cancel(business.getId(), id).getId());
    }

    private Optional<Long> findNextUpcomingAppointmentId(Customer customer, Business business) {
        Pageable pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "startAt"));
        Specification<Appointment> specification = AppointmentSpecifications.filterBy(
                business.getId(), null, customer.getId(), AppointmentStatus.CONFIRMED, Instant.now(), null);
        Page<Appointment> page = appointmentRepository.findAll(specification, pageable);

        return page.getContent().stream().findFirst().map(Appointment::getId);
    }

    private Long attempt(java.util.function.Supplier<Long> action) {
        try {
            return action.get();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------
    // Construcción de respuestas deterministas (nunca decididas por la IA)
    // ---------------------------------------------------------------------------------------

    /**
     * Mensaje de confirmación construido enteramente en backend a partir del
     * AppointmentResponse real. El modelo de IA nunca decide el texto final de una reserva
     * exitosa: solo el resultado persistido determina qué se le comunica al cliente.
     */
    private String buildConfirmationReply(Business business, AppointmentResponse response) {
        ZonedDateTime localStart = response.getStartAt().atZone(ZoneId.of(business.getTimezone()));
        String customerFirstName = firstNameOf(response.getCustomerName());

        return "✅ Listo" + (customerFirstName != null ? ", " + customerFirstName : "") + ". Tu "
                + response.getServiceName() + " con " + response.getEmployeeName()
                + " quedó confirmado para " + describeDate(localStart)
                + " a las " + LOCAL_TIME_FORMATTER.format(localStart) + ".";
    }

    private String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+", 2)[0];
    }

    private String buildConfirmationQuestion(Business business, Service service, Employee employee, Instant startAt) {
        ZonedDateTime localStart = startAt.atZone(ZoneId.of(business.getTimezone()));
        return "Entonces reservaríamos un " + service.getName() + " con " + employeeDisplayName(employee)
                + " para " + describeDate(localStart) + " a las " + LOCAL_TIME_FORMATTER.format(localStart)
                + ". ¿Confirmás?";
    }

    private String describeDate(ZonedDateTime localStart) {
        return describeDateOnly(localStart.toLocalDate(), localStart.getZone());
    }

    private String describeDateOnly(LocalDate date, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (date.equals(today)) {
            return "hoy";
        }
        if (date.equals(today.plusDays(1))) {
            return "mañana";
        }
        return "el " + LOCAL_DATE_FORMATTER.format(date.atStartOfDay(zone));
    }

    private String employeeDisplayName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }

    private String greetingPrefix(Business business) {
        return String.format(GREETING_PREFIX_TEMPLATE, business.getName());
    }

    private String withGreeting(ConversationState state, Business business, String continuation) {
        if (state.isGreeted()) {
            return continuation;
        }
        state.setGreeted(true);
        return greetingPrefix(business) + continuation;
    }

    private ConversationResponse finish(ConversationState state, Business business, String reply,
                                         ConversationIntent intent, double confidence, Long appointmentId) {
        String finalReply = withGreeting(state, business, reply != null ? reply : DEFAULT_REPLY);
        return new ConversationResponse(finalReply, intent, confidence, appointmentId);
    }

    private ConversationResponse finishFromOutcome(ConversationState state, Business business,
                                                     DispatchOutcome outcome, ConversationIntent intent,
                                                     double fallbackConfidence) {
        double confidence = outcome.confidenceOverride() != null ? outcome.confidenceOverride() : fallbackConfidence;
        return finish(state, business, outcome.replyOverride(), intent, confidence, outcome.appointmentId());
    }

    // ---------------------------------------------------------------------------------------
    // Interacción con la IA (extracción de intención/entidades, nunca decisiones de negocio)
    // ---------------------------------------------------------------------------------------

    private ParsedAiReply askAi(Business business, ConversationState state, String message) {
        String systemPrompt = systemPromptBuilder.build(buildBusinessContext(business) + buildDraftContext(business, state));
        String rawAiReply = aiProvider.generateResponse(systemPrompt, message);
        ParsedAiReply parsed = parseAiReply(rawAiReply);

        log.info("Conversación IA recibida. businessId={}, intent={}, confidence={}",
                business.getId(), parsed.intent(), parsed.confidence());
        log.debug("Estado de la conversación antes de fusionar este turno. businessId={}, customerId={}, "
                        + "stage={}, serviceId={}, employeeId={}, pendingDate={}, startAt={}",
                business.getId(), state.getCustomerId(), state.getStage(), state.getPendingServiceId(),
                state.getPendingEmployeeId(), state.getPendingDate(), state.getPendingStartAt());

        return parsed;
    }

    private Optional<Service> findServiceByName(Long businessId, String serviceName) {
        return serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                        businessId, serviceName, PageRequest.of(0, 1))
                .getContent()
                .stream()
                .findFirst();
    }

    private Optional<Employee> findEmployeeByName(Long businessId, String employeeName) {
        return employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        businessId, employeeName, PageRequest.of(0, 1))
                .getContent()
                .stream()
                .findFirst();
    }

    private List<Service> activeServices(Long businessId) {
        return serviceRepository.findByBusinessIdAndNameContainingIgnoreCaseAndActiveTrue(
                businessId, "", Pageable.unpaged()).getContent();
    }

    private String buildBusinessContext(Business business) {
        List<Service> services = activeServices(business.getId());
        List<Schedule> schedules = scheduleRepository.findActiveByBusiness(business.getId(), null, null);

        StringBuilder context = new StringBuilder("Negocio: ").append(business.getName()).append('\n');

        if (services.isEmpty()) {
            context.append("Todavía no hay servicios cargados.\n");
        } else {
            context.append("Servicios disponibles:\n");
            context.append(services.stream()
                    .map(service -> "- " + service.getName() + " (" + service.getDurationMinutes()
                            + " minutos, " + guaraniAmountFormatter.format(service.getPrice()) + ")")
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

    /**
     * Resume, en lenguaje natural, los datos de reserva ya recopilados en turnos anteriores
     * (servicio, profesional, fecha/hora) para que la IA nunca tenga que "adivinar" a partir de
     * un mensaje aislado. Sin este contexto, una respuesta elíptica como "para las 9" es
     * indistinguible de un mensaje sin ningún dato: la IA no tiene forma de saber que el servicio
     * y la fecha ya se resolvieron en un turno previo. Es solo contexto informativo para la IA;
     * el backend nunca delega en ella la decisión de qué conservar (eso lo hace
     * {@code mergeParsedIntoDraft}, que jamás pisa un dato ya resuelto con uno ausente).
     */
    private String buildDraftContext(Business business, ConversationState state) {
        List<String> lines = new java.util.ArrayList<>();

        if (state.hasPendingService()) {
            serviceRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingServiceId(), business.getId())
                    .ifPresent(service -> lines.add("- Servicio ya elegido: " + service.getName()));
        }

        if (state.hasPendingEmployee()) {
            employeeRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingEmployeeId(), business.getId())
                    .ifPresent(employee -> lines.add("- Profesional ya elegido: " + employeeDisplayName(employee)));
        } else if (state.getLastReferencedEmployeeName() != null) {
            lines.add("- Último profesional del que se habló (si el cliente dice \"con él\"/\"con ella\"/"
                    + "\"el mismo\", se refiere a esta persona): " + state.getLastReferencedEmployeeName());
        }

        if (state.getPendingStartAt() != null) {
            ZonedDateTime local = state.getPendingStartAt().atZone(ZoneId.of(business.getTimezone()));
            lines.add("- Fecha y hora ya definidas: " + describeDateOnly(local.toLocalDate(), local.getZone())
                    + " a las " + LOCAL_TIME_FORMATTER.format(local));
        } else if (state.getPendingDate() != null) {
            lines.add("- Fecha ya definida (todavía falta la hora): "
                    + describeDateOnly(state.getPendingDate(), ZoneId.of(business.getTimezone())));
        }

        if (lines.isEmpty()) {
            return "";
        }

        return "\nDatos que el cliente ya proporcionó en esta conversación (NO los vuelvas a pedir; si el "
                + "mensaje actual no menciona un dato nuevo, dejalo como NONE en tu respuesta, no repitas ni "
                + "cambies el valor ya conocido salvo que el cliente lo esté corrigiendo explícitamente):\n"
                + String.join("\n", lines) + "\n";
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
                valueOrNull(fields.get("EMPLOYEE_NAME")),
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
                                  String employeeName, String startAtText, Long appointmentId, String reply) {
    }

    /**
     * Resultado de avanzar el flujo de reserva. replyOverride es el texto que el backend decidió
     * mostrar (pregunta del siguiente dato, confirmación, error de negocio). confidenceOverride
     * distinto de null fuerza la confianza reportada (una reserva confirmada por
     * AppointmentService es un hecho verificado, no una estimación del modelo).
     */
    private record DispatchOutcome(Long appointmentId, String replyOverride, Double confidenceOverride) {

        static DispatchOutcome reply(String replyOverride) {
            return new DispatchOutcome(null, replyOverride, null);
        }

        static DispatchOutcome bookingConfirmed(Long appointmentId, String reply) {
            return new DispatchOutcome(appointmentId, reply, CONFIRMED_BOOKING_CONFIDENCE);
        }
    }

}
