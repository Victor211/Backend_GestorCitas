package com.victor.appointmentmanager.api.modules.ai.format;

import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.shared.entity.Business;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Único responsable de redactar el texto final que ve el cliente por WhatsApp, a partir de datos
 * ya resueltos (entidades reales, nunca repositorios). Deliberadamente sin efectos secundarios:
 * no toca {@code ConversationState} ni decide ningún dato de negocio, solo lo traduce a texto
 * breve en español. {@code ConversationServiceImpl} sigue siendo quien decide QUÉ responder; esta
 * clase decide CÓMO redactarlo.
 */
@Component
@RequiredArgsConstructor
public class ConversationReplyFormatter {

    private static final DateTimeFormatter LOCAL_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"));
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String GREETING_PREFIX_TEMPLATE = "Hola 👋 Soy el asistente virtual de %s. ";
    private static final String NO_SERVICES_REPLY = "Por el momento no tenemos servicios cargados.";

    private final GuaraniAmountFormatter guaraniAmountFormatter;

    public String greetingPrefix(Business business) {
        return String.format(GREETING_PREFIX_TEMPLATE, business.getName());
    }

    /** Saludo completo y determinístico para un "Hola" puro en una conversación limpia. */
    public String pureGreeting(Business business) {
        return greetingPrefix(business)
                + "¿Te ayudo a reservar un turno o necesitás información sobre nuestros servicios?";
    }

    public String serviceList(List<Service> services) {
        if (services.isEmpty()) {
            return NO_SERVICES_REPLY;
        }
        String lines = services.stream()
                .map(service -> "• " + service.getName() + " — " + guaraniAmountFormatter.format(service.getPrice()))
                .collect(Collectors.joining("\n"));
        return "Claro 😊 Estos son nuestros servicios:\n\n" + lines + "\n\n¿Cuál te gustaría reservar?";
    }

    public String confirmationQuestion(Business business, Service service, Employee employee, Instant startAt) {
        ZonedDateTime localStart = startAt.atZone(ZoneId.of(business.getTimezone()));
        return employeeDisplayName(employee) + " está disponible " + describeDate(localStart)
                + " a las " + LOCAL_TIME_FORMATTER.format(localStart) + " para " + service.getName()
                + " por " + guaraniAmountFormatter.format(service.getPrice()) + ". ¿Confirmás?";
    }

    /**
     * Mensaje de confirmación construido enteramente a partir del {@link AppointmentResponse} real
     * ya persistido. Nunca se redacta a partir de lo que la IA hubiera escrito: solo el resultado
     * verificado en base de datos determina qué se le comunica al cliente.
     */
    public String confirmationReply(Business business, AppointmentResponse response) {
        ZonedDateTime localStart = response.getStartAt().atZone(ZoneId.of(business.getTimezone()));
        String customerFirstName = firstNameOf(response.getCustomerName());

        return "✅ Listo" + (customerFirstName != null ? ", " + customerFirstName : "") + ". Tu "
                + response.getServiceName() + " con " + response.getEmployeeName()
                + " quedó confirmado para " + describeDate(localStart)
                + " a las " + LOCAL_TIME_FORMATTER.format(localStart) + ".";
    }

    public String multipleEmployeesReply(Business business, List<Employee> candidates, Instant startAt) {
        ZonedDateTime local = startAt.atZone(ZoneId.of(business.getTimezone()));
        String names = joinWithAnd(candidates.stream().map(this::employeeDisplayName).toList());
        return "Para " + describeDate(local) + " a las " + LOCAL_TIME_FORMATTER.format(local)
                + " están disponibles " + names + ". ¿Con cuál prefieres reservar?";
    }

    public String employeeDisplayName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }

    public String employeeNotAvailableAtRequestedTime(Employee employee) {
        return employeeDisplayName(employee) + " no está disponible a esa hora. ¿Quieres probar otro horario?";
    }

    /** Respuesta directa de sí/no para una consulta puntual de disponibilidad (intent CHECK_AVAILABILITY). */
    public String availabilityAnswer(Employee employee, boolean available, ZonedDateTime localStart, String serviceName) {
        String time = LOCAL_TIME_FORMATTER.format(localStart);
        String serviceSuffix = serviceName != null ? " para " + serviceName : "";
        if (available) {
            return "Sí, " + employee.getFirstName() + " está disponible " + describeDate(localStart) + " a las "
                    + time + serviceSuffix + ". ¿Quieres reservar?";
        }
        return employee.getFirstName() + " no está disponible a las " + time
                + ". ¿Quieres que te muestre otro horario?";
    }

    public String describeDate(ZonedDateTime localStart) {
        return describeDateOnly(localStart.toLocalDate(), localStart.getZone());
    }

    public String describeDateOnly(LocalDate date, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (date.equals(today)) {
            return "hoy";
        }
        if (date.equals(today.plusDays(1))) {
            return "mañana";
        }
        return "el " + LOCAL_DATE_FORMATTER.format(date.atStartOfDay(zone));
    }

    private String joinWithAnd(List<String> items) {
        if (items.size() <= 1) {
            return items.isEmpty() ? "" : items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " y " + items.get(items.size() - 1);
    }

    private String firstNameOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+", 2)[0];
    }

}
