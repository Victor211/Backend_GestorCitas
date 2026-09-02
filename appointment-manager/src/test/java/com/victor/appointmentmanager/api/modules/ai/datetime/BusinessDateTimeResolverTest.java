package com.victor.appointmentmanager.api.modules.ai.datetime;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.ai.exception.AmbiguousTimeException;
import com.victor.appointmentmanager.api.modules.ai.exception.MissingTimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BusinessDateTimeResolverTest {

    private static final String ASUNCION = "America/Asuncion";
    private final BusinessDateTimeResolver resolver = new BusinessDateTimeResolver();
    private final TimeZone originalDefaultTimeZone = TimeZone.getDefault();

    @AfterEach
    void restoreDefaultTimeZone() {
        TimeZone.setDefault(originalDefaultTimeZone);
    }

    @Test
    void interpretsLocalTimeInBusinessTimezoneNotAsUtc() {
        Instant resolved = resolver.resolve("10 de agosto de 2026 a las 15:00", ASUNCION);

        Instant expected = ZonedDateTime.of(
                LocalDate.of(2026, 8, 10), LocalTime.of(15, 0), ZoneId.of(ASUNCION)).toInstant();

        assertThat(resolved).isEqualTo(expected);
        assertThat(resolved).isNotEqualTo(Instant.parse("2026-08-10T15:00:00Z"));
    }

    @Test
    void convertingResolvedInstantBackShowsTheSameLocalTimeRequested() {
        Instant resolved = resolver.resolve("10 de agosto de 2026 a las 15:00", ASUNCION);

        ZonedDateTime localAgain = resolved.atZone(ZoneId.of(ASUNCION));

        assertThat(localAgain.toLocalTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(localAgain.toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void respectsExplicitUtcSuffixInsteadOfBusinessTimezone() {
        Instant resolved = resolver.resolve("10 de agosto de 2026 a las 13:00 UTC", ASUNCION);

        assertThat(resolved).isEqualTo(Instant.parse("2026-08-10T13:00:00Z"));
    }

    @Test
    void doesNotApplyBusinessTimezoneOnTopOfExplicitIsoInstant() {
        Instant resolved = resolver.resolve("2026-08-10T13:00:00Z", ASUNCION);

        assertThat(resolved).isEqualTo(Instant.parse("2026-08-10T13:00:00Z"));
    }

    @Test
    void resolvesTomorrowRelativeToCurrentDateInBusinessTimezone() {
        ZoneId zone = ZoneId.of(ASUNCION);
        LocalDate expectedDate = ZonedDateTime.now(zone).toLocalDate().plusDays(1);

        Instant resolved = resolver.resolve("mañana a las 10", ASUNCION);

        ZonedDateTime local = resolved.atZone(zone);
        assertThat(local.toLocalDate()).isEqualTo(expectedDate);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void resolvesNextMondayNeverInThePastUsingBusinessTimezoneReference() {
        ZoneId zone = ZoneId.of(ASUNCION);
        LocalDate expectedDate = ZonedDateTime.now(zone).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Instant resolved = resolver.resolve("el próximo lunes a las 10", ASUNCION);

        ZonedDateTime local = resolved.atZone(zone);
        assertThat(local.toLocalDate()).isEqualTo(expectedDate);
        assertThat(local.toLocalDate()).isAfter(ZonedDateTime.now(zone).toLocalDate().minusDays(1));
        assertThat(local.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    // AJUSTE 11: "jueves"/"el jueves" pelado (sin "próximo"/"este") también debe resolverse a una
    // fecha concreta usando Business.timezone, no quedar como texto ambiguo. Antes solo se
    // reconocían "próximo X"/"este X"; un nombre de día suelto caía directo al parser de fecha
    // absoluta y fallaba con BusinessException.
    @Test
    void resolvesBareWeekdayNameWithoutProximoOrEstePrefix() {
        ZoneId zone = ZoneId.of(ASUNCION);
        LocalDate expectedDate = ZonedDateTime.now(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));

        Instant resolved = resolver.resolve("jueves a las 10", ASUNCION);

        ZonedDateTime local = resolved.atZone(zone);
        assertThat(local.toLocalDate()).isEqualTo(expectedDate);
        assertThat(local.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void resolvesBareWeekdayNameWithElPrefix() {
        ZoneId zone = ZoneId.of(ASUNCION);
        LocalDate expectedDate = ZonedDateTime.now(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));

        Instant resolved = resolver.resolve("el jueves a las 10:00", ASUNCION);

        ZonedDateTime local = resolved.atZone(zone);
        assertThat(local.toLocalDate()).isEqualTo(expectedDate);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void throwsControlledErrorForInvalidBusinessTimezone() {
        assertThatThrownBy(() -> resolver.resolve("10 de agosto de 2026 a las 15:00", "Not/AValidZone"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void throwsControlledErrorForUnparseableExpression() {
        assertThatThrownBy(() -> resolver.resolve("no tengo idea cuándo", ASUNCION))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void serverDefaultTimezoneNeverAffectsTheResult() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

        Instant resolved = resolver.resolve("10 de agosto de 2026 a las 15:00", ASUNCION);

        Instant expected = ZonedDateTime.of(
                LocalDate.of(2026, 8, 10), LocalTime.of(15, 0), ZoneId.of(ASUNCION)).toInstant();
        assertThat(resolved).isEqualTo(expected);
    }

    @Test
    void bareTimeOnAFutureDateWithoutMarkerAsksForClarification() {
        // Sin "de la tarde"/"de la mañana" y sin ser hoy, no hay forma segura de elegir AM o PM.
        assertThatThrownBy(() -> resolver.resolve("el próximo lunes a las 4", ASUNCION))
                .isInstanceOf(AmbiguousTimeException.class)
                .hasMessageContaining("04:00")
                .hasMessageContaining("16:00");
    }

    @Test
    void afternoonMarkerResolvesAmbiguousHourAsPm() {
        ZoneId zone = ZoneId.of(ASUNCION);
        LocalDate expectedDate = ZonedDateTime.now(zone).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        Instant resolved = resolver.resolve("el próximo lunes de tarde a las 4", ASUNCION);

        ZonedDateTime local = resolved.atZone(zone);
        assertThat(local.toLocalDate()).isEqualTo(expectedDate);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void morningMarkerResolvesAmbiguousHourAsAm() {
        Instant resolved = resolver.resolve("el próximo lunes de la mañana a las 4", ASUNCION);

        ZonedDateTime local = resolved.atZone(ZoneId.of(ASUNCION));
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(4, 0));
    }

    @Test
    void bareAmbiguousHourWithNoDateWordDefaultsToTodayAndAppliesSameDisambiguation() {
        Instant resolved = resolver.resolve("por la tarde a las 4", ASUNCION);

        ZonedDateTime local = resolved.atZone(ZoneId.of(ASUNCION));
        assertThat(local.toLocalDate()).isEqualTo(ZonedDateTime.now(ZoneId.of(ASUNCION)).toLocalDate());
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void prefersPmForTodayWhenAmReadingAlreadyPassedAndPmHasNot() {
        ZoneId zone = ZoneId.of(ASUNCION);
        ZonedDateTime now = ZonedDateTime.now(zone);
        int hour = pickAmbiguousHourAlreadyPassedInAmForm(now);
        assumeTrue(hour > 0, "No se pudo elegir una hora válida para el horario actual de ejecución del test");

        Instant resolved = resolver.resolve("hoy a las " + hour, ASUNCION);

        ZonedDateTime local = resolved.atZone(zone);
        assertThat(local.toLocalDate()).isEqualTo(now.toLocalDate());
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(hour + 12, 0));
    }

    @Test
    void missingTimeExceptionCarriesTheResolvedDateForTodayAndTomorrow() {
        ZoneId zone = ZoneId.of(ASUNCION);
        LocalDate today = ZonedDateTime.now(zone).toLocalDate();

        assertThatThrownBy(() -> resolver.resolve("hoy", ASUNCION))
                .isInstanceOf(MissingTimeException.class)
                .satisfies(ex -> assertThat(((MissingTimeException) ex).getResolvedDate()).isEqualTo(today));

        assertThatThrownBy(() -> resolver.resolve("mañana", ASUNCION))
                .isInstanceOf(MissingTimeException.class)
                .satisfies(ex -> assertThat(((MissingTimeException) ex).getResolvedDate()).isEqualTo(today.plusDays(1)));
    }

    @Test
    void resolveTimeOnlyCombinesWithAnAlreadyKnownDate() {
        LocalDate knownDate = LocalDate.of(2026, 8, 10);

        Instant resolved = resolver.resolveTimeOnly("a las 16:30", knownDate, ASUNCION);

        ZonedDateTime local = resolved.atZone(ZoneId.of(ASUNCION));
        assertThat(local.toLocalDate()).isEqualTo(knownDate);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(16, 30));
    }

    @Test
    void resolveTimeOnlyAppliesAmPmMarkersAgainstTheKnownDate() {
        LocalDate knownDate = LocalDate.of(2026, 8, 10);

        Instant resolved = resolver.resolveTimeOnly("de tarde a las 4", knownDate, ASUNCION);

        ZonedDateTime local = resolved.atZone(ZoneId.of(ASUNCION));
        assertThat(local.toLocalDate()).isEqualTo(knownDate);
        assertThat(local.toLocalTime()).isEqualTo(LocalTime.of(16, 0));
    }

    private int pickAmbiguousHourAlreadyPassedInAmForm(ZonedDateTime now) {
        for (int hour = 1; hour <= 8; hour++) {
            if (now.getHour() > hour && now.getHour() < hour + 12) {
                return hour;
            }
        }
        return -1;
    }

}
