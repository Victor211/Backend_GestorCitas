package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;
import com.victor.appointmentmanager.api.modules.ai.client.AiProvider;
import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import com.victor.appointmentmanager.api.modules.ai.prompt.SystemPromptBuilder;
import com.victor.appointmentmanager.api.modules.ai.service.ConversationService;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.CreateAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.dto.request.RescheduleAppointmentRequest;
import com.victor.appointmentmanager.api.modules.appointments.entity.Appointment;
import com.victor.appointmentmanager.api.modules.appointments.enums.AppointmentStatus;
import com.victor.appointmentmanager.api.modules.appointments.repository.AppointmentRepository;
import com.victor.appointmentmanager.api.modules.appointments.service.AppointmentService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final String NONE_MARKER = "NONE";
    private static final String REPLY_MARKER = "REPLY:";
    private static final String DEFAULT_REPLY =
            "Disculpá, no pude procesar tu mensaje en este momento. ¿Podés reformularlo?";

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

    @Override
    @Transactional
    public ConversationResponse processAuthenticatedConversation(ConversationRequest request) {
        return process(currentUserProvider.getCurrentBusinessId(), request.getCustomerPhone(), request.getMessage());
    }

    @Override
    @Transactional
    public ConversationResponse processChannelConversation(Long businessId, String customerPhone, String message) {
        return process(businessId, customerPhone, message);
    }

    private ConversationResponse process(Long businessId, String customerPhone, String message) {
        Business business = findActiveBusinessOrThrow(businessId);

        String systemPrompt = systemPromptBuilder.build(buildBusinessContext(business));
        String rawAiReply = aiProvider.generateResponse(systemPrompt, message);

        ParsedAiReply parsed = parseAiReply(rawAiReply);

        Long appointmentId = dispatch(customerPhone, business, parsed);

        return new ConversationResponse(parsed.reply(), parsed.intent(), parsed.confidence(), appointmentId);
    }

    private Long dispatch(String customerPhone, Business business, ParsedAiReply parsed) {
        return switch (parsed.intent()) {
            case BOOK_APPOINTMENT -> handleBookAppointment(customerPhone, business, parsed);
            case RESCHEDULE_APPOINTMENT -> handleReschedule(customerPhone, business, parsed);
            case CANCEL_APPOINTMENT -> handleCancel(customerPhone, business);
            case CHECK_AVAILABILITY, GREETING, UNKNOWN -> null;
        };
    }

    private Long handleBookAppointment(String customerPhone, Business business, ParsedAiReply parsed) {
        if (parsed.serviceName() == null || parsed.startAt() == null) {
            return null;
        }

        Optional<Service> service = findServiceByName(business.getId(), parsed.serviceName());
        Optional<Customer> customer = customerRepository.findByBusinessIdAndPhoneAndActiveTrue(
                business.getId(), customerPhone);

        if (service.isEmpty() || customer.isEmpty()) {
            return null;
        }

        Optional<Employee> employee = findEmployeeForService(business.getId(), service.get());
        if (employee.isEmpty()) {
            return null;
        }

        CreateAppointmentRequest createRequest = new CreateAppointmentRequest();
        createRequest.setCustomerId(customer.get().getId());
        createRequest.setEmployeeId(employee.get().getId());
        createRequest.setServiceId(service.get().getId());
        createRequest.setStartAt(parsed.startAt());

        return attempt(() -> appointmentService.create(business.getId(), createRequest).getId());
    }

    private Long handleReschedule(String customerPhone, Business business, ParsedAiReply parsed) {
        if (parsed.startAt() == null) {
            return null;
        }

        Long appointmentId = parsed.appointmentId() != null
                ? parsed.appointmentId()
                : findNextUpcomingAppointmentId(customerPhone, business).orElse(null);

        if (appointmentId == null) {
            return null;
        }

        RescheduleAppointmentRequest rescheduleRequest = new RescheduleAppointmentRequest();
        rescheduleRequest.setStartAt(parsed.startAt());

        Long id = appointmentId;
        return attempt(() -> appointmentService.reschedule(business.getId(), id, rescheduleRequest).getId());
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
        Page<Appointment> page = appointmentRepository.search(business.getId(), null, customer.get().getId(),
                AppointmentStatus.CONFIRMED, Instant.now(), null, pageable);

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
                parseInstant(fields.get("START_AT")),
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

    private Instant parseInstant(String value) {
        String cleaned = valueOrNull(value);
        if (cleaned == null) {
            return null;
        }
        try {
            return Instant.parse(cleaned);
        } catch (DateTimeParseException ex) {
            return null;
        }
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
                                  Instant startAt, Long appointmentId, String reply) {
    }

}
