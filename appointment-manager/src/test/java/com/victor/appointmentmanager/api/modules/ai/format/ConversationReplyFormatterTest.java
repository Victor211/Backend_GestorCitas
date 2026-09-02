package com.victor.appointmentmanager.api.modules.ai.format;

import com.victor.appointmentmanager.api.modules.employees.entity.Employee;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import com.victor.appointmentmanager.api.shared.entity.Business;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formato de disponibilidad agrupado por franja (Mañana/Tarde) dentro de cada día. La entrada de
 * estos tests siempre son horarios ya "reales" (como si vinieran de AppointmentService): el
 * formatter nunca decide disponibilidad, solo cómo mostrarla.
 */
class ConversationReplyFormatterTest {

    private static final ZoneId ZONE = ZoneId.of("America/Asuncion");

    private final ConversationReplyFormatter formatter = new ConversationReplyFormatter(new GuaraniAmountFormatter());
    private Business business;
    private Service corteBarba;
    private Employee juan;
    private Employee victor;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Peluquería Elegance");
        business.setTimezone(ZONE.getId());

        corteBarba = new Service();
        corteBarba.setId(6L);
        corteBarba.setName("Corte Barba");
        corteBarba.setPrice(new BigDecimal("45000"));
        corteBarba.setDurationMinutes(30);
        corteBarba.setBusiness(business);

        juan = new Employee();
        juan.setId(3L);
        juan.setFirstName("Juan");
        juan.setLastName("Gómez");
        juan.setBusiness(business);

        victor = new Employee();
        victor.setId(7L);
        victor.setFirstName("Victor");
        victor.setLastName("Chamorro");
        victor.setBusiness(business);

        // Un día que nunca es "hoy" ni "mañana" en el momento de correr el test, para que
        // describeDayLabel no interfiera con las aserciones (no nos interesa qué texto de día usa,
        // solo la agrupación Mañana/Tarde).
        day = LocalDate.now(ZONE).plusDays(10);
    }

    private Instant at(LocalTime time) {
        return ZonedDateTime.of(day, time, ZONE).toInstant();
    }

    // Caso 1 del enunciado: mañana y tarde en el mismo día.
    @Test
    void groupsMorningAndAfternoonSlotsIntoSeparateBullets() {
        List<Instant> slots = List.of(
                at(LocalTime.of(8, 0)), at(LocalTime.of(8, 30)), at(LocalTime.of(10, 0)),
                at(LocalTime.of(14, 0)), at(LocalTime.of(14, 30)), at(LocalTime.of(16, 0)));

        String reply = formatter.formatAvailabilityList(business, corteBarba, slots);

        assertThat(reply).contains("• Mañana: 08:00, 08:30, 10:00");
        assertThat(reply).contains("• Tarde: 14:00, 14:30, 16:00");
        // Mañana antes que Tarde en el texto.
        assertThat(reply.indexOf("Mañana:")).isLessThan(reply.indexOf("Tarde:"));
    }

    // Caso 2: solo mañana -> no debe aparecer la franja Tarde.
    @Test
    void morningOnlySlotsOmitAfternoonBullet() {
        List<Instant> slots = List.of(
                at(LocalTime.of(8, 0)), at(LocalTime.of(9, 0)), at(LocalTime.of(10, 30)));

        String reply = formatter.formatAvailabilityList(business, corteBarba, slots);

        assertThat(reply).contains("• Mañana: 08:00, 09:00, 10:30");
        assertThat(reply).doesNotContain("Tarde");
    }

    // Caso 3: solo tarde -> no debe aparecer la franja Mañana.
    @Test
    void afternoonOnlySlotsOmitMorningBullet() {
        List<Instant> slots = List.of(
                at(LocalTime.of(13, 0)), at(LocalTime.of(14, 30)), at(LocalTime.of(17, 0)));

        String reply = formatter.formatAvailabilityList(business, corteBarba, slots);

        assertThat(reply).contains("• Tarde: 13:00, 14:30, 17:00");
        assertThat(reply).doesNotContain("Mañana");
    }

    @Test
    void chronologicalOrderIsPreservedWithinEachPeriod() {
        List<Instant> slots = List.of(
                at(LocalTime.of(9, 0)), at(LocalTime.of(8, 0)), at(LocalTime.of(11, 30)));

        String reply = formatter.formatAvailabilityList(business, corteBarba, slots);

        // Se conserva el orden de la lista de entrada (que AppointmentService ya entrega
        // cronológicamente); el formatter no reordena por su cuenta.
        assertThat(reply).contains("• Mañana: 09:00, 08:00, 11:30");
    }

    @Test
    void noonIsClassifiedAsAfternoonNotMorning() {
        Instant justBeforeNoon = at(LocalTime.NOON).minusSeconds(1);
        Instant exactlyNoon = at(LocalTime.NOON);

        String reply = formatter.formatAvailabilityList(business, corteBarba, List.of(justBeforeNoon, exactlyNoon));

        assertThat(reply).contains("• Mañana: 11:59");
        assertThat(reply).contains("• Tarde: 12:00");
    }

    @Test
    void groupingUsesBusinessTimezoneNotUtc() {
        // "America/Asuncion" está siempre detrás de UTC (UTC-3 o UTC-4 según la ley vigente al
        // momento). Las 20:00 UTC caen entre las 16:00 y las 17:00 locales en cualquiera de los dos
        // casos: siempre después del mediodía (Tarde), nunca antes (Mañana), sin importar en qué
        // zona corra la JVM del test ni el offset exacto vigente.
        Instant utcAfternoonForAsuncion = ZonedDateTime.of(day, LocalTime.of(20, 0), ZoneId.of("UTC")).toInstant();

        String reply = formatter.formatAvailabilityList(business, corteBarba, List.of(utcAfternoonForAsuncion));

        assertThat(reply).contains("Tarde:");
        assertThat(reply).doesNotContain("Mañana:");
    }

    @Test
    void perPeriodDisplayCapKeepsEarliestSlotsFirst() {
        List<Instant> manyMorningSlots = List.of(
                at(LocalTime.of(8, 0)), at(LocalTime.of(8, 30)), at(LocalTime.of(9, 0)),
                at(LocalTime.of(9, 30)), at(LocalTime.of(10, 0)), at(LocalTime.of(10, 30)),
                at(LocalTime.of(11, 0)), at(LocalTime.of(11, 30)));

        String reply = formatter.formatAvailabilityList(business, corteBarba, manyMorningSlots);

        // Tope de presentación por franja (6): se listan los 6 primeros (más tempranos), nunca
        // los 8 reales — el cálculo real de disponibilidad no cambia, solo cuántos se muestran.
        assertThat(reply).contains("• Mañana: 08:00, 08:30, 09:00, 09:30, 10:00, 10:30");
        assertThat(reply).doesNotContain("11:00");
        assertThat(reply).doesNotContain("11:30");
    }

    @Test
    void multipleEmployeesEachGetTheirOwnMorningAfternoonGrouping() {
        Map<Employee, List<Instant>> slotsByEmployee = new LinkedHashMap<>();
        slotsByEmployee.put(juan, List.of(at(LocalTime.of(10, 0)), at(LocalTime.of(15, 0))));
        slotsByEmployee.put(victor, List.of(at(LocalTime.of(9, 0))));

        String reply = formatter.formatEmployeeAvailability(business, corteBarba, slotsByEmployee);

        assertThat(reply).contains("Juan Gómez");
        assertThat(reply).contains("Victor Chamorro");
        assertThat(reply).contains("• Mañana: 10:00");
        assertThat(reply).contains("• Tarde: 15:00");
        assertThat(reply).contains("• Mañana: 09:00");
    }

    @Test
    void emptySlotListStillRepliesWithNoAvailabilityMessage() {
        String reply = formatter.formatAvailabilityList(business, corteBarba, List.of());

        assertThat(reply).contains("no encontré horarios disponibles");
        assertThat(reply).doesNotContain("•");
    }

}
