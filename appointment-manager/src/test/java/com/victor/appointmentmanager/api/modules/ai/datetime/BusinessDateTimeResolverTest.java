package com.victor.appointmentmanager.api.modules.ai.datetime;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
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

}
