package com.victor.appointmentmanager.api.modules.ai.format;

import com.victor.appointmentmanager.api.modules.appointments.dto.response.AppointmentResponse;
import com.victor.appointmentmanager.api.modules.appointments.service.AvailabilityReason;
import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.shared.entity.Business;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Franja "Mañana" = antes de esta hora local; "Tarde" = esta hora en adelante. Regla simple, sin turno "Noche". */
    private static final LocalTime AFTERNOON_START = LocalTime.NOON;
    /**
     * Tope de horarios mostrados por franja (Mañana/Tarde) dentro de un mismo día, puramente de
     * presentación: WhatsApp no debe recibir un mensaje larguísimo aunque un día tenga muchísimos
     * slots libres. No limita cuántos slots calcula/trae AppointmentService (ver
     * ConversationServiceImpl#MAX_AVAILABILITY_SLOTS_PER_EMPLOYEE para ese tope, que ahora solo
     * evita traer de más, no trunca la presentación).
     */
    private static final int MAX_SLOTS_PER_PERIOD = 6;

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

    /**
     * Mensaje específico para cuando un horario puntual (empleado + fecha/hora ya resueltos) no
     * puede reservarse, distinguiendo el motivo real ({@link AvailabilityReason}) en vez de un
     * único texto genérico: "ocupado" (OVERLAPPING) es un mensaje distinto de "fuera de horario"
     * (OUTSIDE_SCHEDULE), que a su vez es distinto de "no realiza ese servicio"
     * (EMPLOYEE_CANNOT_PERFORM_SERVICE).
     */
    public String formatUnavailableReason(Employee employee, Service service, AvailabilityReason reason,
                                           ZonedDateTime localStart) {
        String time = LOCAL_TIME_FORMATTER.format(localStart);
        String dateDesc = describeDate(localStart);
        String name = employeeDisplayName(employee);
        return switch (reason) {
            case OVERLAPPING -> name + " no está disponible " + dateDesc + " a las " + time
                    + ". ¿Querés elegir otro horario?";
            case OUTSIDE_SCHEDULE -> name + " no atiende " + dateDesc + " a las " + time
                    + ". ¿Querés ver otros horarios disponibles?";
            case EMPLOYEE_CANNOT_PERFORM_SERVICE -> name + " no realiza "
                    + (service != null ? service.getName() : "ese servicio")
                    + ". ¿Querés ver qué profesionales están disponibles para ese servicio?";
            case IN_PAST -> "Esa hora ya pasó. ¿Querés elegir otro horario?";
            case EMPLOYEE_NOT_FOUND, SERVICE_NOT_FOUND ->
                    name + " no está disponible en este momento. ¿Querés probar otro horario?";
        };
    }

    /**
     * Lista de horarios reales (ya descontando Appointments existentes y respetando la duración
     * del Service) para un único conjunto de candidatos, agrupados por día. Usado cuando solo un
     * empleado realiza el servicio consultado (o el cliente ya especificó cuál).
     */
    public String formatAvailabilityList(Business business, Service service, List<Instant> slots) {
        if (slots.isEmpty()) {
            return "Por ahora no encontré horarios disponibles para " + service.getName()
                    + ". ¿Querés que probemos otro servicio o día?";
        }
        ZoneId zone = ZoneId.of(business.getTimezone());
        return "Claro 😊 Para " + service.getName() + " tenemos estas opciones:\n\n"
                + formatSlotsByDay(slots, zone) + "\n\n¿qué día y hora preferís?";
    }

    /**
     * Lista de horarios reales agrupados por empleado (y, dentro de cada uno, por día). Usado
     * cuando más de un empleado realiza el servicio consultado.
     */
    public String formatEmployeeAvailability(Business business, Service service,
                                              Map<Employee, List<Instant>> slotsByEmployee) {
        ZoneId zone = ZoneId.of(business.getTimezone());
        String blocks = slotsByEmployee.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> employeeDisplayName(entry.getKey()) + "\n" + formatSlotsByDay(entry.getValue(), zone))
                .collect(Collectors.joining("\n\n"));
        if (blocks.isBlank()) {
            return "Por ahora no encontré horarios disponibles para " + service.getName()
                    + ". ¿Querés que probemos otro servicio o día?";
        }
        return "Para " + service.getName() + ":\n\n" + blocks + "\n\n¿Cuál preferís?";
    }

    /**
     * Agrupa los slots reales, primero por día y, dentro de cada día, por franja horaria
     * (Mañana/Tarde) en vez de una única lista plana — puramente presentación: la entrada ya viene
     * filtrada/calculada por AppointmentService, esto solo decide cómo mostrarla.
     */
    private String formatSlotsByDay(List<Instant> slots, ZoneId zone) {
        Map<LocalDate, List<Instant>> byDate = slots.stream()
                .collect(Collectors.groupingBy(instant -> instant.atZone(zone).toLocalDate(),
                        LinkedHashMap::new, Collectors.toList()));
        return byDate.entrySet().stream()
                .map(entry -> describeDayLabel(entry.getKey(), zone) + ":\n" + formatByPeriod(entry.getValue(), zone))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Divide los slots (ya en orden cronológico) de un mismo día en "Mañana" (antes de las 12:00
     * hora local) y "Tarde" (12:00 en adelante), omitiendo la franja que no tenga horarios. Cada
     * franja se recorta a {@link #MAX_SLOTS_PER_PERIOD} para no alargar el mensaje de WhatsApp,
     * conservando siempre los horarios más tempranos de esa franja.
     */
    private String formatByPeriod(List<Instant> daySlots, ZoneId zone) {
        List<Instant> morning = daySlots.stream()
                .filter(instant -> instant.atZone(zone).toLocalTime().isBefore(AFTERNOON_START))
                .toList();
        List<Instant> afternoon = daySlots.stream()
                .filter(instant -> !instant.atZone(zone).toLocalTime().isBefore(AFTERNOON_START))
                .toList();

        List<String> lines = new ArrayList<>();
        if (!morning.isEmpty()) {
            lines.add("• Mañana: " + formatTimes(morning, zone));
        }
        if (!afternoon.isEmpty()) {
            lines.add("• Tarde: " + formatTimes(afternoon, zone));
        }
        return String.join("\n", lines);
    }

    private String formatTimes(List<Instant> periodSlots, ZoneId zone) {
        return periodSlots.stream()
                .limit(MAX_SLOTS_PER_PERIOD)
                .map(instant -> LOCAL_TIME_FORMATTER.format(instant.atZone(zone)))
                .collect(Collectors.joining(", "));
    }

    /** "Hoy"/"Mañana" para esos dos casos especiales; el nombre del día (ej. "Jueves") en el resto. */
    private String describeDayLabel(LocalDate date, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        if (date.equals(today)) {
            return "Hoy";
        }
        if (date.equals(today.plusDays(1))) {
            return "Mañana";
        }
        String name = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.of("es", "ES"));
        return name.substring(0, 1).toUpperCase(Locale.of("es", "ES")) + name.substring(1);
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
