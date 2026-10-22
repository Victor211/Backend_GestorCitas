package com.victor.appointmentmanager.api.modules.ai.datetime;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.ai.exception.AmbiguousTimeException;
import com.victor.appointmentmanager.api.modules.ai.exception.MissingTimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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


    // ---------------------------------------------------------------------------------------
    // GS-BOT-004: fechas explícitas en español, con reloj fijo.
    // Hoy = jueves 22/10/2026 10:00 en Asunción (13:00 UTC).
    // ---------------------------------------------------------------------------------------

    private static final ZoneId ASU = ZoneId.of(ASUNCION);

    private BusinessDateTimeResolver resolverAt(String utcInstant) {
        return new BusinessDateTimeResolver(Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC));
    }

    private BusinessDateTimeResolver today() {
        return resolverAt("2026-10-22T13:00:00Z");
    }

    private Instant local(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ASU).toInstant();
    }

    @Test
    void relativeExpressionsKeepWorkingWithFixedClock() {
        assertThat(today().resolve("mañana a las 15", ASUNCION)).isEqualTo(local(2026, 10, 23, 15, 0));
        assertThat(today().resolve("para mañana a las 15", ASUNCION)).isEqualTo(local(2026, 10, 23, 15, 0));
        assertThat(today().resolve("próximo viernes a las 16", ASUNCION)).isEqualTo(local(2026, 10, 23, 16, 0));
        assertThat(today().resolve("el próximo lunes a las 9", ASUNCION)).isEqualTo(local(2026, 10, 26, 9, 0));
    }

    @Test
    void explicitDayMonthAndHourAreInterpretedInSpanish() {
        Instant expected = local(2026, 10, 29, 15, 0);
        assertThat(today().resolve("29 de octubre a las 15", ASUNCION)).isEqualTo(expected);
        assertThat(today().resolve("para el 29 de octubre a las 15", ASUNCION)).isEqualTo(expected);
        assertThat(today().resolve("el 29 de octubre a las 15", ASUNCION)).isEqualTo(expected);
        assertThat(today().resolve("Para el 29 de Octubre a las 15", ASUNCION)).isEqualTo(expected);
        assertThat(today().resolve("29 de octubre, a las 15", ASUNCION)).isEqualTo(expected);
        assertThat(today().resolve("5 de noviembre a las 10:30", ASUNCION)).isEqualTo(local(2026, 11, 5, 10, 30));
        assertThat(today().resolve("5 de setiembre a las 10", ASUNCION)).isEqualTo(local(2027, 9, 5, 10, 0));
    }

    @Test
    void numericDayMonthFormatsAreDayFirst() {
        assertThat(today().resolve("29/10 a las 15", ASUNCION)).isEqualTo(local(2026, 10, 29, 15, 0));
        assertThat(today().resolve("para el 29/10 a las 15:30", ASUNCION)).isEqualTo(local(2026, 10, 29, 15, 30));
        assertThat(today().resolve("29-10-2026 a las 15", ASUNCION)).isEqualTo(local(2026, 10, 29, 15, 0));
        assertThat(today().resolve("5/11 a las 10", ASUNCION)).isEqualTo(local(2026, 11, 5, 10, 0));
    }

    @Test
    void explicitYearIsStillAcceptedAndTimeMarkersWork() {
        assertThat(today().resolve("10 de agosto de 2027 a las 15:00", ASUNCION)).isEqualTo(local(2027, 8, 10, 15, 0));
        assertThat(today().resolve("5 de noviembre a las 3 de la tarde", ASUNCION))
                .isEqualTo(local(2026, 11, 5, 15, 0));
        assertThatThrownBy(() -> today().resolve("5 de noviembre a las 3", ASUNCION))
                .isInstanceOf(AmbiguousTimeException.class);
    }

    @Test
    void dateWithoutTimeThrowsMissingTimeExceptionWithTheResolvedDate() {
        assertThatThrownBy(() -> today().resolve("29 de octubre", ASUNCION))
                .isInstanceOf(MissingTimeException.class)
                .satisfies(ex -> assertThat(((MissingTimeException) ex).getResolvedDate())
                        .isEqualTo(LocalDate.of(2026, 10, 29)));
        assertThatThrownBy(() -> today().resolve("para el 29/10", ASUNCION))
                .isInstanceOf(MissingTimeException.class);
        assertThatThrownBy(() -> today().resolve("mañana", ASUNCION))
                .isInstanceOf(MissingTimeException.class);
    }

    @Test
    void timeWithoutDateKeepsExistingBehaviorOfUsingToday() {
        assertThat(today().resolve("a las 15", ASUNCION)).isEqualTo(local(2026, 10, 22, 15, 0));
    }

    @Test
    void omittedYearPicksTheFirstFutureOccurrenceNeverAPastDate() {
        assertThat(today().resolve("22 de octubre a las 15", ASUNCION)).isEqualTo(local(2026, 10, 22, 15, 0));
        assertThat(today().resolve("21 de octubre a las 15", ASUNCION)).isEqualTo(local(2027, 10, 21, 15, 0));
        assertThat(today().resolve("5 de marzo a las 10", ASUNCION)).isEqualTo(local(2027, 3, 5, 10, 0));
        assertThat(resolverAt("2027-10-22T13:00:00Z").resolve("29 de febrero a las 10", ASUNCION))
                .isEqualTo(local(2028, 2, 29, 10, 0));
    }

    @Test
    void explicitPastYearWithoutTimeIsRejectedAndWithTimeIsLeftToAppointmentValidation() {
        assertThatThrownBy(() -> today().resolve("1 de enero de 2020", ASUNCION))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya pasó");
        // Comportamiento previo conservado: la fecha pasada explícita con hora la rechaza AppointmentService.
        assertThat(today().resolve("1 de enero de 2020 a las 10", ASUNCION)).isEqualTo(local(2020, 1, 1, 10, 0));
    }

    @Test
    void yearRolloverIsHandledAroundNewYear() {
        BusinessDateTimeResolver dec30 = resolverAt("2026-12-30T13:00:00Z");
        assertThat(dec30.resolve("2 de enero a las 10", ASUNCION)).isEqualTo(local(2027, 1, 2, 10, 0));
        assertThat(dec30.resolve("30 de diciembre a las 10", ASUNCION)).isEqualTo(local(2026, 12, 30, 10, 0));
        assertThat(dec30.resolve("29 de diciembre a las 10", ASUNCION)).isEqualTo(local(2027, 12, 29, 10, 0));

        BusinessDateTimeResolver jan2 = resolverAt("2027-01-02T13:00:00Z");
        assertThat(jan2.resolve("30 de diciembre a las 10", ASUNCION)).isEqualTo(local(2027, 12, 30, 10, 0));
        assertThat(jan2.resolve("1 de enero a las 10", ASUNCION)).isEqualTo(local(2028, 1, 1, 10, 0));
    }

    @Test
    void invalidDatesAreRejectedWithAControlledMessage() {
        for (String invalid : new String[] {"31 de febrero a las 10", "29 de febrero a las 10", "31 de abril a las 10",
                "32/10 a las 10", "10/13 a las 10", "0/10 a las 10"}) {
            assertThatThrownBy(() -> today().resolve(invalid, ASUNCION))
                    .as(invalid)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("no existe");
        }
        assertThatThrownBy(() -> today().resolve("29 de octubre a las 25", ASUNCION))
                .isInstanceOf(BusinessException.class).hasMessageContaining("hora indicada no es válida");
        assertThatThrownBy(() -> today().resolve("29 de foo a las 10", ASUNCION))
                .isInstanceOf(BusinessException.class).hasMessageContaining("No pude interpretar");
    }

    @Test
    void numbersAndPartialMatchesAreNeverTreatedAsDates() {
        for (String notADate : new String[] {"3", "15", "2", "servicio 29 de octubre a las 15",
                "29 de octubre a las 15 y algo más", "29 de octubre en la tarde", "abc 29/10"}) {
            assertThatThrownBy(() -> today().resolve(notADate, ASUNCION))
                    .as(notADate)
                    .isInstanceOf(BusinessException.class)
                    .isNotInstanceOf(MissingTimeException.class);
        }
    }

    @Test
    void businessTimezoneDecidesTodayRegardlessOfServerTimezone() {
        // 31/12/2026 23:30 UTC: en Asunción (UTC-3) todavía es 31/12; en Auckland (UTC+13) ya es 1/1/2027.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        BusinessDateTimeResolver newYearEve = resolverAt("2026-12-31T23:30:00Z");

        assertThat(newYearEve.resolve("31 de diciembre a las 15", ASUNCION)).isEqualTo(local(2026, 12, 31, 15, 0));
        assertThat(newYearEve.resolve("31 de diciembre a las 15", "Pacific/Auckland"))
                .isEqualTo(ZonedDateTime.of(2027, 12, 31, 15, 0, 0, 0, ZoneId.of("Pacific/Auckland")).toInstant());
        assertThat(newYearEve.resolve("mañana a las 15", "Pacific/Auckland"))
                .isEqualTo(ZonedDateTime.of(2027, 1, 2, 15, 0, 0, 0, ZoneId.of("Pacific/Auckland")).toInstant());
    }

    @Test
    void isoFormatsAlreadySupportedAreUnchanged() {
        assertThat(today().resolve("2026-10-29T18:00:00Z", ASUNCION)).isEqualTo(Instant.parse("2026-10-29T18:00:00Z"));
        assertThat(today().resolve("2026-10-29T15:00:00-03:00", ASUNCION))
                .isEqualTo(Instant.parse("2026-10-29T18:00:00Z"));
        assertThat(today().resolve("29 de octubre de 2026 a las 13:00 UTC", ASUNCION))
                .isEqualTo(Instant.parse("2026-10-29T13:00:00Z"));
    }
}
