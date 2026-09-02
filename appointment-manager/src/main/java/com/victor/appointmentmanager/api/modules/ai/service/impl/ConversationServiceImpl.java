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
import com.victor.appointmentmanager.api.modules.ai.format.ConversationReplyFormatter;
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
import com.victor.appointmentmanager.api.modules.appointments.service.AvailabilityReason;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orquestador del flujo conversacional. Decide QUÉ responder y en qué orden pedir cada dato;
 * delega el CÓMO redactarlo a {@link ConversationReplyFormatter} y el "¿está disponible?" a la
 * única fuente de verdad de disponibilidad, {@link AppointmentService#isAvailable}, la misma que
 * usa la revalidación final antes de crear una cita.
 *
 * <p>Customer nunca es un prerequisito para conversar ni para armar el borrador de reserva:
 * servicio, fecha/hora y profesional se recolectan igual con o sin Customer identificado. La
 * identidad solo entra en juego al confirmar (ver {@link #respondToConfirmationStage}) y, si el
 * Customer todavía no existe, se resuelve en una única etapa dedicada,
 * {@link ConversationStage#AWAITING_CUSTOMER_NAME}, después de la confirmación positiva — nunca
 * antes, y nunca por el solo hecho de que el teléfono sea desconocido.</p>
 */
@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String NONE_MARKER = "NONE";
    private static final String REPLY_MARKER = "REPLY:";
    private static final String DEFAULT_REPLY =
            "Disculpá, no pude procesar tu mensaje en este momento. ¿Podés reformularlo?";

    private static final String ASK_NAME_BEFORE_BOOKING_REPLY = "Perfecto 👌 Antes de confirmar, ¿Cuál es tu nombre?";
    private static final String ASK_DATE_REPLY = "¿Para cuándo te gustaría el turno?";
    private static final String ASK_TIME_REPLY = "¿A qué hora te gustaría?";
    private static final String SERVICE_NOT_FOUND_REPLY =
            "No encontré ese servicio en nuestro catálogo. ¿Podés confirmarme el nombre exacto?";
    /**
     * Nombre de profesional mencionado por el cliente que no coincide con ningún Employee activo
     * del negocio (no confundir con "existe pero no está libre a esa hora/no realiza el servicio":
     * esos casos usan {@link ConversationReplyFormatter#formatUnavailableReason}, que sí conoce la
     * causa real).
     */
    private static final String EMPLOYEE_NOT_FOUND_REPLY =
            "No encontré a ese profesional en nuestro equipo. ¿Podés confirmarme el nombre?";
    private static final String BOOKING_FAILED_REPLY =
            "No pudimos completar la reserva en este momento. ¿Querés intentar con otro horario?";
    private static final String SLOT_NO_LONGER_AVAILABLE_REPLY =
            "Ese horario acaba de dejar de estar disponible. ¿Quieres elegir otra hora?";
    private static final String BOOKING_CANCELLED_REPLY =
            "Sin problema, cancelé la propuesta. ¿En qué más puedo ayudarte?";
    private static final String ASK_OTHER_TIME_REPLY = "Sin problema. ¿Qué otro horario prefieres?";
    private static final String ASK_OTHER_EMPLOYEE_REPLY = "Sin problema. ¿Con qué otro profesional prefieres?";
    private static final String STALE_DRAFT_REPLY =
            "Se me perdieron los datos de la reserva. ¿Podés indicarme de nuevo el servicio y el horario?";
    private static final String PROPOSAL_EXPIRED_REPLY =
            "Esa propuesta ya venció. ¿Querés que revisemos de nuevo el horario?";
    private static final String NO_AVAILABILITY_AT_TIME_REPLY =
            "No hay disponibilidad para ese horario. ¿Quieres probar otra hora?";
    private static final String NO_APPOINTMENT_FOUND_REPLY =
            "No encontré ninguna reserva asociada a este número.";

    private static final double CONFIRMED_BOOKING_CONFIDENCE = 1.0;
    private static final double DETERMINISTIC_CONFIDENCE = 1.0;

    /** Cuántos días hacia adelante se recorren para armar una lista de disponibilidad sin fecha fija. */
    private static final int MAX_AVAILABILITY_DAYS_LOOKAHEAD = 7;
    /**
     * Cuántos horarios reales se le piden como máximo a AppointmentService por profesional/día. NO
     * es el tope de presentación (eso vive en ConversationReplyFormatter#MAX_SLOTS_PER_PERIOD, que
     * recorta por franja Mañana/Tarde): este valor solo evita traer una cantidad desmedida de
     * horarios de un día con jornada muy extensa, sin cortar arbitrariamente antes de llegar a la
     * tarde (antes era 5, lo que en la práctica truncaba casi siempre en horarios de la mañana).
     */
    private static final int MAX_AVAILABILITY_SLOTS_PER_EMPLOYEE = 20;

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
    private final ConversationReplyFormatter replyFormatter;
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

        ConversationResponse response = isResetCommand(message)
                ? handleResetCommand(business, customerPhone, state)
                : route(business, message, state);

        conversationStateStore.save(state);
        return response;
    }

    // ---------------------------------------------------------------------------------------
    // Comandos internos de QA (/reset, /terminate): se procesan antes que cualquier otra cosa
    // — nunca llegan a OpenAI ni a la clasificación de intención/extracción de entidades — para
    // poder limpiar manualmente el ConversationState de un business+senderPhone entre escenarios
    // de prueba, sin tocar Customer/Appointments/whatsapp_inbound_events ni el timeout automático.
    // ---------------------------------------------------------------------------------------

    private static final Set<String> RESET_COMMANDS = Set.of("/reset", "/terminate");
    private static final String CONVERSATION_RESET_REPLY = "Conversación reiniciada.";

    private boolean isResetCommand(String message) {
        return message != null && RESET_COMMANDS.contains(message.trim().toLowerCase(Locale.ROOT));
    }

    private ConversationResponse handleResetCommand(Business business, String customerPhone, ConversationState state) {
        log.info("Conversation reset requested. businessId={}, senderPhone={}",
                business.getId(), maskPhone(customerPhone));
        state.resetForNewConversation();
        return new ConversationResponse(CONVERSATION_RESET_REPLY, ConversationIntent.UNKNOWN,
                DETERMINISTIC_CONFIDENCE, null);
    }

    private static final int VISIBLE_PHONE_SUFFIX_LENGTH = 3;

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= VISIBLE_PHONE_SUFFIX_LENGTH) {
            return "***";
        }
        String suffix = phone.substring(phone.length() - VISIBLE_PHONE_SUFFIX_LENGTH);
        return "*".repeat(phone.length() - VISIBLE_PHONE_SUFFIX_LENGTH) + suffix;
    }

    // ---------------------------------------------------------------------------------------
    // Enrutamiento principal — un único despacho por stage, sin bifurcar por identidad
    // ---------------------------------------------------------------------------------------

    private ConversationResponse route(Business business, String message, ConversationState state) {
        Optional<Customer> existingCustomer = customerIdentityResolver.findActive(business.getId(), state.getCustomerPhone());
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

        if (state.getStage() == ConversationStage.AWAITING_CUSTOMER_NAME) {
            return respondToCustomerNameStage(business, message, state);
        }

        return respondCollecting(business, message, state, existingCustomer);
    }

    // ---------------------------------------------------------------------------------------
    // Slot-filling: Customer identificado o no siguen exactamente el mismo camino. Solo
    // RESCHEDULE/CANCEL necesitan un Customer real, porque operan sobre una cita ya existente.
    // ---------------------------------------------------------------------------------------

    private ConversationResponse respondCollecting(Business business, String message, ConversationState state,
                                                     Optional<Customer> existingCustomer) {
        if (isExplicitCancellation(state, message)) {
            state.clearBookingDraft();
            return finish(state, business, BOOKING_CANCELLED_REPLY, ConversationIntent.REJECT_APPOINTMENT,
                    DETERMINISTIC_CONFIDENCE, null);
        }
        if (EXPLICIT_RESTART_REQUEST.matcher(message).find()) {
            state.clearBookingDraft();
        }

        // Resolución determinista PRIMERO: si todavía no hay servicio elegido y el mensaje
        // coincide, exacto y normalizado, con un único Service activo, se resuelve sin pasar por
        // la IA — antes de que cualquier clasificación de intent (incluido un LIST_SERVICES mal
        // asignado a una respuesta corta) pueda cortar el flujo. Ver matchSingleActiveService.
        if (!state.hasPendingService()) {
            Optional<Service> directMatch = matchSingleActiveService(business.getId(), message);
            if (directMatch.isPresent()) {
                state.setPendingServiceId(directMatch.get().getId());
                DispatchOutcome outcome = advanceBookingFlow(business, state);
                return finishFromOutcome(state, business, outcome, ConversationIntent.BOOK_APPOINTMENT,
                        DETERMINISTIC_CONFIDENCE);
            }
        }

        ParsedAiReply parsed = askAi(business, state, message);

        if (parsed.intent() == ConversationIntent.LIST_SERVICES) {
            return finish(state, business, replyFormatter.serviceList(activeServices(business.getId())),
                    ConversationIntent.LIST_SERVICES, parsed.confidence(), null);
        }

        if (parsed.intent() == ConversationIntent.CHECK_AVAILABILITY) {
            return handleCheckAvailability(business, state, parsed, message);
        }

        if (parsed.intent() == ConversationIntent.RESCHEDULE_APPOINTMENT
                || parsed.intent() == ConversationIntent.CANCEL_APPOINTMENT) {
            if (existingCustomer.isEmpty()) {
                return finish(state, business, NO_APPOINTMENT_FOUND_REPLY, parsed.intent(), parsed.confidence(), null);
            }
            Long appointmentId = parsed.intent() == ConversationIntent.RESCHEDULE_APPOINTMENT
                    ? handleReschedule(existingCustomer.get(), business, parsed)
                    : handleCancel(existingCustomer.get(), business);
            return finish(state, business, parsed.reply(), parsed.intent(), parsed.confidence(), appointmentId);
        }

        String mergeFailure = mergeParsedIntoDraft(state, business, parsed, message);
        if (mergeFailure != null) {
            return finish(state, business, mergeFailure, ConversationIntent.BOOK_APPOINTMENT,
                    parsed.confidence(), null);
        }

        if (parsed.intent() == ConversationIntent.GREETING && !hasBookingDraftProgress(state)) {
            return respondToPureGreeting(business, state, parsed);
        }

        if (parsed.intent() != ConversationIntent.BOOK_APPOINTMENT && !hasBookingDraftProgress(state)) {
            // Charla libre (pregunta general, UNKNOWN) sin ningún dato de reserva ni intención
            // explícita de reservar: se usa el texto de la IA tal cual, no hay nada que resolver
            // en el flujo de reserva todavía. Un BOOK_APPOINTMENT explícito SIEMPRE entra al
            // flujo (aunque no haya extraído ningún dato) para mostrar el catálogo de una vez
            // en vez de responder con una pregunta genérica de la IA.
            return finish(state, business, parsed.reply(), parsed.intent(), parsed.confidence(), null);
        }

        DispatchOutcome outcome = advanceBookingFlow(business, state);
        return finishFromOutcome(state, business, outcome, ConversationIntent.BOOK_APPOINTMENT, parsed.confidence());
    }

    private ConversationResponse respondToPureGreeting(Business business, ConversationState state, ParsedAiReply parsed) {
        if (state.isGreeted()) {
            return finish(state, business, parsed.reply(), ConversationIntent.GREETING, parsed.confidence(), null);
        }
        // "Hola" puro en conversación limpia: se responde con un saludo determinístico en vez de
        // confiar en el texto libre de la IA. state.greeted se marca aquí (no en withGreeting) para
        // que el saludo no se anteponga dos veces, ya que este texto ya lo incluye.
        state.setGreeted(true);
        return finish(state, business, replyFormatter.pureGreeting(business), ConversationIntent.GREETING,
                parsed.confidence(), null);
    }

    private boolean hasBookingDraftProgress(ConversationState state) {
        return state.hasPendingService() || state.hasPendingEmployee()
                || state.getPendingStartAt() != null || state.getPendingDate() != null;
    }

    /**
     * Cancelación explícita ("cancelar", "ya no quiero", "olvídalo") mientras se está
     * recopilando una reserva (fuera de AWAITING_CONFIRMATION, que ya la maneja
     * {@code respondToConfirmationStage}, y fuera de AWAITING_CUSTOMER_NAME, que nunca llega a
     * este método). Se decide en backend con el mismo clasificador determinístico que la
     * confirmación, nunca dejando que la IA decida si el cliente canceló.
     */
    private boolean isExplicitCancellation(ConversationState state, String message) {
        return hasBookingDraftProgress(state)
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
            if (customerOpt.isPresent()) {
                DispatchOutcome outcome = handleConfirmation(business, customerOpt.get(), state);
                return finishFromOutcome(state, business, outcome, ConversationIntent.CONFIRM_APPOINTMENT,
                        DETERMINISTIC_CONFIDENCE);
            }
            // Propuesta completa confirmada, pero el Customer todavía no existe: se pide el nombre
            // sin tocar el borrador ni volver a preguntar "¿Confirmás?" más adelante.
            state.setStage(ConversationStage.AWAITING_CUSTOMER_NAME);
            return finish(state, business, ASK_NAME_BEFORE_BOOKING_REPLY, ConversationIntent.BOOK_APPOINTMENT,
                    DETERMINISTIC_CONFIDENCE, null);
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
        return DispatchOutcome.bookingConfirmed(response.getId(), replyFormatter.confirmationReply(business, response));
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
    // Etapa dedicada a resolver el nombre del cliente — la única en la que el mensaje completo
    // se interpreta incondicionalmente como nombre. Solo se llega aquí tras una confirmación
    // positiva con customerId nulo (ver respondToConfirmationStage), nunca antes.
    // ---------------------------------------------------------------------------------------

    private ConversationResponse respondToCustomerNameStage(Business business, String message, ConversationState state) {
        Customer customer = customerIdentityResolver.createFromName(business, state.getCustomerPhone(), message);
        state.setCustomerId(customer.getId());

        // El borrador (servicio/profesional/horario) sigue intacto desde la confirmación positiva
        // anterior: se reusa exactamente el mismo método de creación, sin volver a preguntar
        // "¿Confirmás?".
        DispatchOutcome outcome = handleConfirmation(business, customer, state);
        return finishFromOutcome(state, business, outcome, ConversationIntent.CONFIRM_APPOINTMENT,
                DETERMINISTIC_CONFIDENCE);
    }

    // ---------------------------------------------------------------------------------------
    // Slot-filling de la reserva: se pregunta un único dato por turno
    // ---------------------------------------------------------------------------------------

    private DispatchOutcome advanceBookingFlow(Business business, ConversationState state) {
        state.setStage(ConversationStage.COLLECTING);

        if (!state.hasPendingService()) {
            // Mostrar el catálogo real (con precio) en vez de una pregunta genérica.
            return DispatchOutcome.reply(replyFormatter.serviceList(activeServices(business.getId())));
        }
        if (state.getPendingStartAt() == null) {
            // Sin fecha/hora todavía no tiene sentido preguntar profesional.
            return DispatchOutcome.reply(state.getPendingDate() != null ? ASK_TIME_REPLY : ASK_DATE_REPLY);
        }

        DispatchOutcome employeeIssue = resolveEmployeeForProposal(business, state);
        if (employeeIssue != null) {
            return employeeIssue;
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
        return DispatchOutcome.reply(replyFormatter.confirmationQuestion(business, service.get(), employee.get(),
                state.getPendingStartAt()));
    }

    /**
     * Resuelve el profesional contra disponibilidad real (misma fuente de verdad que la
     * revalidación final: {@link AppointmentService#isAvailable}) para el servicio y horario ya
     * conocidos — nunca solo por capacidad de realizar el servicio. Prioridad: un profesional ya
     * elegido (explícito, por pronombre, o de un turno anterior) NUNCA se reemplaza en silencio;
     * si ya no está libre a la hora resuelta, se avisa y se vuelve a pedir horario en lugar de
     * autoasignar otro. Devuelve {@code null} cuando el empleado queda resuelto y se puede
     * continuar.
     */
    private DispatchOutcome resolveEmployeeForProposal(Business business, ConversationState state) {
        if (state.hasPendingEmployee()) {
            Optional<Employee> chosen = employeeRepository.findByIdAndBusinessIdAndActiveTrue(
                    state.getPendingEmployeeId(), business.getId());
            if (chosen.isPresent()) {
                boolean available = appointmentService.isAvailable(business.getId(), chosen.get().getId(),
                        state.getPendingServiceId(), state.getPendingStartAt());
                if (available) {
                    return null;
                }
                // isAvailable ya confirmó que no puede reservarse; una segunda consulta (misma
                // fuente de verdad, checkAvailability) trae el motivo exacto para no colapsar
                // "ocupado"/"fuera de horario"/"no realiza el servicio" en un único mensaje genérico.
                AvailabilityReason reason = appointmentService.checkAvailability(business.getId(), chosen.get().getId(),
                        state.getPendingServiceId(), state.getPendingStartAt()).reason();
                String reply = describeUnavailability(business, chosen.get(), state.getPendingServiceId(),
                        reason, state.getPendingStartAt());
                state.setPendingStartAt(null);
                state.setPendingDate(null);
                if (reason == AvailabilityReason.EMPLOYEE_CANNOT_PERFORM_SERVICE) {
                    // Un profesional explícito que no realiza el servicio no puede quedar "pegado":
                    // hay que volver a resolverlo (o preguntar) para el nuevo intento.
                    state.setPendingEmployeeId(null);
                }
                return DispatchOutcome.reply(reply);
            }
            // El empleado guardado ya no existe/está activo: se resuelve de nuevo abajo.
            state.setPendingEmployeeId(null);
        }

        List<Employee> available = resolveAvailableEmployees(business, state.getPendingServiceId(),
                state.getPendingStartAt());
        if (available.isEmpty()) {
            state.setPendingStartAt(null);
            state.setPendingDate(null);
            return DispatchOutcome.reply(NO_AVAILABILITY_AT_TIME_REPLY);
        }
        if (available.size() > 1) {
            return DispatchOutcome.reply(replyFormatter.multipleEmployeesReply(business, available,
                    state.getPendingStartAt()));
        }

        Employee onlyCandidate = available.get(0);
        state.setPendingEmployeeId(onlyCandidate.getId());
        rememberEmployee(state, onlyCandidate);
        return null;
    }

    /**
     * Redacta el motivo específico por el que un profesional puntual no está disponible. {@code
     * reason} puede llegar {@code null} en la ventana (extremadamente improbable) entre la consulta
     * boolean y la de motivo detallado; se trata igual que OVERLAPPING (el motivo más común) para
     * no romper el flujo con un mensaje vacío.
     */
    private String describeUnavailability(Business business, Employee employee, Long serviceId,
                                           AvailabilityReason reason, Instant startAt) {
        Service service = serviceId != null
                ? serviceRepository.findByIdAndBusinessIdAndActiveTrue(serviceId, business.getId()).orElse(null)
                : null;
        ZonedDateTime localStart = startAt.atZone(ZoneId.of(business.getTimezone()));
        AvailabilityReason resolvedReason = reason != null ? reason : AvailabilityReason.OVERLAPPING;
        return replyFormatter.formatUnavailableReason(employee, service, resolvedReason, localStart);
    }

    /** Empleados activos, habilitados para el servicio, y realmente libres en ese horario. */
    private List<Employee> resolveAvailableEmployees(Business business, Long serviceId, Instant startAt) {
        List<Employee> candidates = employeesForService(business.getId(), serviceId);
        if (candidates.isEmpty()) {
            return candidates;
        }
        return candidates.stream()
                .filter(employee -> appointmentService.isAvailable(business.getId(), employee.getId(), serviceId, startAt))
                .toList();
    }

    /**
     * Recuerda el último Employee mencionado o resuelto en la conversación, para poder resolver
     * referencias pronominales ("con él", "el mismo") sin volver a preguntar el profesional.
     */
    private void rememberEmployee(ConversationState state, Employee employee) {
        state.setLastReferencedEmployeeId(employee.getId());
        state.setLastReferencedEmployeeName(replyFormatter.employeeDisplayName(employee));
    }

    private boolean referencesLastEmployee(String rawMessage) {
        return rawMessage != null && EMPLOYEE_PRONOUN_REFERENCE.matcher(rawMessage).find();
    }

    private List<Employee> employeesForService(Long businessId, Long serviceId) {
        return activeEmployees(businessId).stream()
                .filter(employee -> employee.getServices().stream()
                        .anyMatch(assigned -> assigned.getId().equals(serviceId)))
                .toList();
    }

    private List<Employee> activeEmployees(Long businessId) {
        return employeeRepository.findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue(
                        businessId, "", Pageable.unpaged())
                .getContent();
    }

    private boolean employeeCanPerformService(Long businessId, Long employeeId, Long serviceId) {
        return employeeRepository.findByIdAndBusinessIdAndActiveTrue(employeeId, businessId)
                .map(employee -> employee.getServices().stream()
                        .anyMatch(assigned -> assigned.getId().equals(serviceId)))
                .orElse(false);
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
            Long newServiceId = service.get().getId();
            boolean serviceActuallyChanged = !newServiceId.equals(state.getPendingServiceId());
            state.setPendingServiceId(newServiceId);
            // Un profesional ya elegido (explícito, por pronombre, o de un turno anterior) NUNCA
            // se pisa solo porque el mensaje repite/reafirma el servicio. Solo se limpia si el
            // servicio realmente cambió Y el profesional ya elegido no puede realizar el nuevo
            // servicio.
            if (serviceActuallyChanged && state.hasPendingEmployee()
                    && !employeeCanPerformService(business.getId(), state.getPendingEmployeeId(), newServiceId)) {
                state.setPendingEmployeeId(null);
            }
        }

        if (parsed.employeeName() != null) {
            Optional<Employee> employee = findEmployeeByName(business.getId(), parsed.employeeName());
            if (employee.isEmpty()) {
                return EMPLOYEE_NOT_FOUND_REPLY;
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

    private static final Pattern SERVICE_MATCH_VERB_LEAD_IN = Pattern.compile(
            "(?i)^(?:quiero|prefiero|quisiera|dame)\\s+");
    private static final Pattern SERVICE_MATCH_ARTICLE_LEAD_IN = Pattern.compile("(?i)^(?:el|la)\\s+");

    /**
     * Resuelve el mensaje del cliente directamente contra los Services activos, sin pasar por la
     * IA. Se consulta en {@code respondCollecting} ANTES de clasificar con el modelo — no como
     * respaldo posterior — porque una respuesta corta y sin verbo ("Corte premium") puede ser
     * reclasificada por la IA como {@code LIST_SERVICES} (no solo como {@code BOOK_APPOINTMENT}
     * sin SERVICE_NAME), y esa rama corta el flujo antes de llegar a cualquier fusión de datos. Al
     * resolver el match determinístico primero, el resultado deja de depender de qué intent le
     * asigne el modelo a un mensaje ambiguo. Match exacto/normalizado únicamente (mayúsculas,
     * acentos, espacios externos y un prefijo simple de verbo/artículo) — nunca fuzzy matching. Si
     * el mensaje normalizado coincide con más de un servicio activo, no arriesga una elección
     * ambigua: devuelve vacío y el flujo sigue su curso normal (clasificación por IA).
     */
    private Optional<Service> matchSingleActiveService(Long businessId, String rawMessage) {
        if (rawMessage == null) {
            return Optional.empty();
        }
        String normalizedMessage = normalizeForServiceMatch(rawMessage);
        if (normalizedMessage.isEmpty()) {
            return Optional.empty();
        }
        List<Service> matches = activeServices(businessId).stream()
                .filter(candidate -> normalizedMessage.equals(normalizeForServiceMatch(candidate.getName())))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private String normalizeForServiceMatch(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        String withoutAccents = java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String withoutVerbLeadIn = SERVICE_MATCH_VERB_LEAD_IN.matcher(withoutAccents).replaceFirst("");
        String withoutArticleLeadIn = SERVICE_MATCH_ARTICLE_LEAD_IN.matcher(withoutVerbLeadIn).replaceFirst("");
        return withoutArticleLeadIn.replaceAll("\\s+", " ").trim();
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
    // Disponibilidad puntual (distinto de reservar): respuesta breve, sin crear ningún draft
    // ---------------------------------------------------------------------------------------

    /**
     * Consulta disponibilidad usando el borrador consolidado, no solo lo que la IA extrajo de
     * este mensaje puntual: "¿Está disponible?" (sin repetir servicio/profesional/horario) debe
     * resolverse con los datos ya conocidos de la conversación, no volver a pedirlos ni quedarse
     * en una promesa ("déjame verificar") sin ejecutar la consulta real. serviceId es opcional:
     * {@link AppointmentService#isAvailable} tolera consultarlo sin servicio conocido (ej. "¿Juan
     * está libre a las 4?" antes de que el cliente mencione qué quiere hacerse).
     */
    private ConversationResponse handleCheckAvailability(Business business, ConversationState state,
                                                           ParsedAiReply parsed, String rawMessage) {
        String mergeFailure = mergeParsedIntoDraft(state, business, parsed, rawMessage);
        if (mergeFailure != null) {
            return finish(state, business, mergeFailure, ConversationIntent.CHECK_AVAILABILITY,
                    parsed.confidence(), null);
        }

        // Consulta genérica ("¿qué horarios tienen disponibles?", "¿para cuándo tienen lugar?"):
        // el servicio ya se conoce pero todavía no hay un horario puntual que validar. En vez de
        // caer en el texto libre de la IA (que solo tiene el Schedule bruto como contexto, nunca
        // Appointments existentes: ver AJUSTE 1/2/3), se arma una lista real de horarios libres.
        if (state.getPendingStartAt() == null && state.hasPendingService()) {
            return respondWithAvailabilityList(business, state, parsed);
        }

        if (state.getPendingStartAt() != null && !state.hasPendingEmployee() && state.hasPendingService()) {
            List<Employee> available = resolveAvailableEmployees(business, state.getPendingServiceId(),
                    state.getPendingStartAt());
            if (available.size() == 1) {
                state.setPendingEmployeeId(available.get(0).getId());
                rememberEmployee(state, available.get(0));
            }
        }

        Optional<Employee> employee = state.hasPendingEmployee()
                ? employeeRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingEmployeeId(), business.getId())
                : Optional.empty();

        if (employee.isEmpty() || state.getPendingStartAt() == null) {
            return finish(state, business, parsed.reply(), ConversationIntent.CHECK_AVAILABILITY,
                    parsed.confidence(), null);
        }
        rememberEmployee(state, employee.get());

        Long serviceId = state.hasPendingService() ? state.getPendingServiceId() : null;
        String serviceName = state.hasPendingService()
                ? serviceRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingServiceId(), business.getId())
                        .map(Service::getName).orElse(null)
                : null;

        boolean available = appointmentService.isAvailable(business.getId(), employee.get().getId(), serviceId,
                state.getPendingStartAt());
        ZonedDateTime localStart = state.getPendingStartAt().atZone(ZoneId.of(business.getTimezone()));
        String reply = replyFormatter.availabilityAnswer(employee.get(), available, localStart, serviceName);

        return finish(state, business, reply, ConversationIntent.CHECK_AVAILABILITY, parsed.confidence(), null);
    }

    /**
     * Arma una lista de horarios REALMENTE libres (Schedule menos Appointments existentes,
     * respetando la duración real del Service) para el servicio ya conocido del borrador. Nunca
     * delega esta cuenta a la IA ni a un volcado del Schedule: cada candidato pasa por {@link
     * AppointmentService#findAvailableSlots}, la misma fuente de verdad que valida la reserva
     * final. Si el cliente ya mencionó un profesional explícito, la lista se acota a ese único
     * profesional (no se le ofrecen horarios de otro sin que lo haya pedido); si no, se listan
     * todos los que realizan el servicio. Si ya hay una fecha conocida (el cliente dijo "el
     * jueves" pero no la hora), se busca solo ese día; si no hay fecha, se recorren los próximos
     * días hasta juntar un puñado de opciones por profesional.
     */
    private ConversationResponse respondWithAvailabilityList(Business business, ConversationState state,
                                                               ParsedAiReply parsed) {
        Optional<Service> service = serviceRepository.findByIdAndBusinessIdAndActiveTrue(
                state.getPendingServiceId(), business.getId());
        if (service.isEmpty()) {
            return finish(state, business, SERVICE_NOT_FOUND_REPLY, ConversationIntent.CHECK_AVAILABILITY,
                    parsed.confidence(), null);
        }

        List<Employee> candidates = state.hasPendingEmployee()
                ? employeeRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingEmployeeId(), business.getId())
                        .map(List::of).orElse(List.of())
                : employeesForService(business.getId(), state.getPendingServiceId());

        boolean singleDayOnly = state.getPendingDate() != null;
        LocalDate startDate = singleDayOnly
                ? state.getPendingDate()
                : ZonedDateTime.now(ZoneId.of(business.getTimezone())).toLocalDate();

        Map<Employee, List<Instant>> slotsByEmployee = new LinkedHashMap<>();
        for (Employee employee : candidates) {
            List<Instant> slots = singleDayOnly
                    ? appointmentService.findAvailableSlots(business.getId(), employee.getId(),
                            state.getPendingServiceId(), startDate, MAX_AVAILABILITY_SLOTS_PER_EMPLOYEE)
                    : collectUpcomingSlots(business, employee, state.getPendingServiceId(), startDate);
            slotsByEmployee.put(employee, slots);
        }

        String reply = candidates.size() <= 1
                ? replyFormatter.formatAvailabilityList(business, service.get(),
                        slotsByEmployee.values().stream().findFirst().orElse(List.of()))
                : replyFormatter.formatEmployeeAvailability(business, service.get(), slotsByEmployee);

        return finish(state, business, reply, ConversationIntent.CHECK_AVAILABILITY, parsed.confidence(), null);
    }

    /** Recorre los próximos días (hasta MAX_AVAILABILITY_DAYS_LOOKAHEAD) juntando horarios reales hasta el tope. */
    private List<Instant> collectUpcomingSlots(Business business, Employee employee, Long serviceId, LocalDate fromDate) {
        List<Instant> slots = new ArrayList<>();
        LocalDate date = fromDate;
        for (int day = 0; day < MAX_AVAILABILITY_DAYS_LOOKAHEAD && slots.size() < MAX_AVAILABILITY_SLOTS_PER_EMPLOYEE; day++) {
            List<Instant> daySlots = appointmentService.findAvailableSlots(business.getId(), employee.getId(),
                    serviceId, date, MAX_AVAILABILITY_SLOTS_PER_EMPLOYEE - slots.size());
            slots.addAll(daySlots);
            date = date.plusDays(1);
        }
        return slots;
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
    // Construcción de respuestas finales — el saludo es la única pieza de formateo con efecto
    // secundario (state.greeted), por eso se queda en el orquestador en vez de en el formatter.
    // ---------------------------------------------------------------------------------------

    private String withGreeting(ConversationState state, Business business, String continuation) {
        if (state.isGreeted()) {
            return continuation;
        }
        state.setGreeted(true);
        return replyFormatter.greetingPrefix(business) + continuation;
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

    /**
     * Resuelve el nombre de profesional extraído por la IA contra los Employee activos del
     * negocio. Se busca por coincidencia (normalizada: minúsculas, sin acentos) del NOMBRE
     * COMPLETO, no solo del firstName: la IA puede extraer "Juan" o "Juan Gómez" según lo que haya
     * dicho el cliente, y el nombre completo de un empleado con apellido nunca "empieza por" un
     * firstName-only-contains si el cliente dio el apellido también, así que se compara el nombre
     * completo normalizado en ambas direcciones. Antes se buscaba solo por firstName (ver
     * EmployeeRepository#findByBusinessIdAndFirstNameContainingIgnoreCaseAndActiveTrue), lo que
     * hacía fallar cualquier búsqueda que incluyera el apellido (ej. "Juan Gómez") y colapsaba en
     * EMPLOYEE_NOT_FOUND_REPLY aunque el profesional sí existiera y sí tuviera horario ese día.
     */
    private Optional<Employee> findEmployeeByName(Long businessId, String employeeName) {
        String normalizedQuery = normalizeForNameMatch(employeeName);
        if (normalizedQuery.isEmpty()) {
            return Optional.empty();
        }
        return activeEmployees(businessId).stream()
                .filter(candidate -> matchesEmployeeName(candidate, normalizedQuery))
                .findFirst();
    }

    private boolean matchesEmployeeName(Employee employee, String normalizedQuery) {
        String normalizedFull = normalizeForNameMatch(employee.getFirstName() + " " + employee.getLastName());
        String normalizedFirst = normalizeForNameMatch(employee.getFirstName());
        return normalizedFull.contains(normalizedQuery) || normalizedQuery.contains(normalizedFirst);
    }

    private String normalizeForNameMatch(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return java.text.Normalizer.normalize(lower, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim();
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
     * un mensaje aislado. Es solo contexto informativo para la IA; el backend nunca delega en
     * ella la decisión de qué conservar (eso lo hace {@code mergeParsedIntoDraft}, que jamás pisa
     * un dato ya resuelto con uno ausente).
     */
    private String buildDraftContext(Business business, ConversationState state) {
        List<String> lines = new java.util.ArrayList<>();

        if (state.hasPendingService()) {
            serviceRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingServiceId(), business.getId())
                    .ifPresent(service -> lines.add("- Servicio ya elegido: " + service.getName()));
        }

        if (state.hasPendingEmployee()) {
            employeeRepository.findByIdAndBusinessIdAndActiveTrue(state.getPendingEmployeeId(), business.getId())
                    .ifPresent(employee -> lines.add("- Profesional ya elegido: " + replyFormatter.employeeDisplayName(employee)));
        } else if (state.getLastReferencedEmployeeName() != null) {
            lines.add("- Último profesional del que se habló (si el cliente dice \"con él\"/\"con ella\"/"
                    + "\"el mismo\", se refiere a esta persona): " + state.getLastReferencedEmployeeName());
        }

        if (state.getPendingStartAt() != null) {
            ZonedDateTime local = state.getPendingStartAt().atZone(ZoneId.of(business.getTimezone()));
            lines.add("- Fecha y hora ya definidas: " + replyFormatter.describeDate(local));
        } else if (state.getPendingDate() != null) {
            lines.add("- Fecha ya definida (todavía falta la hora): "
                    + replyFormatter.describeDateOnly(state.getPendingDate(), ZoneId.of(business.getTimezone())));
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
