package com.victor.appointmentmanager.api.modules.ai.datetime;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Único componente responsable de convertir una expresión de fecha/hora extraída del
 * mensaje de un cliente (texto crudo, tal como lo devuelve la IA) en el Instant UTC que
 * espera AppointmentService.
 *
 * <p>Si el cliente no indicó una zona horaria explícita, la expresión se interpreta en
 * {@code Business.timezone}. Si indicó una zona explícita (UTC o un instante/offset
 * ISO-8601 completo), esa zona se respeta y se aplica una única vez. Ninguna otra clase
 * del módulo debe hacer conversiones de zona horaria por su cuenta.</p>
 */
@Component
public class BusinessDateTimeResolver {

    private static final DateTimeFormatter ABSOLUTE_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d 'de' MMMM 'de' uuuu 'a las' H")
            .optionalStart()
            .appendPattern(":mm")
            .optionalEnd()
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.of("es", "ES"))
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern EXPLICIT_UTC_SUFFIX = Pattern.compile("(?i)^(.*?)\\s+UTC$");
    private static final Pattern TIME_SUFFIX = Pattern.compile("(?i)a las\\s+(\\d{1,2})(?::(\\d{2}))?\\s*$");
    private static final Pattern TOMORROW_PREFIX = Pattern.compile("(?i)^mañana\\s+");
    private static final Pattern TODAY_PREFIX = Pattern.compile("(?i)^hoy\\s+");
    private static final Pattern NEXT_WEEKDAY_PREFIX =
            Pattern.compile("(?i)^(?:el\\s+)?pr[oó]ximo\\s+(\\p{L}+)\\s+");
    private static final Pattern THIS_WEEKDAY_PREFIX = Pattern.compile("(?i)^este\\s+(\\p{L}+)\\s+");

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("lunes", DayOfWeek.MONDAY),
            Map.entry("martes", DayOfWeek.TUESDAY),
            Map.entry("miercoles", DayOfWeek.WEDNESDAY),
            Map.entry("miércoles", DayOfWeek.WEDNESDAY),
            Map.entry("jueves", DayOfWeek.THURSDAY),
            Map.entry("viernes", DayOfWeek.FRIDAY),
            Map.entry("sabado", DayOfWeek.SATURDAY),
            Map.entry("sábado", DayOfWeek.SATURDAY),
            Map.entry("domingo", DayOfWeek.SUNDAY));

    /**
     * Resuelve una expresión de fecha/hora hacia un Instant UTC.
     *
     * @param rawExpression     texto extraído del mensaje del cliente (absoluto o relativo,
     *                          con o sin zona horaria explícita)
     * @param businessTimezone  {@code Business.timezone}, usado cuando la expresión no
     *                          indica una zona explícita
     * @throws BusinessException si la zona del Business no es válida o la expresión no
     *                           puede interpretarse
     */
    public Instant resolve(String rawExpression, String businessTimezone) {
        if (rawExpression == null || rawExpression.isBlank()) {
            throw new BusinessException("No se indicó una fecha y hora para la reserva");
        }

        ZoneId businessZone = validateZoneId(businessTimezone);
        String expression = rawExpression.trim();

        Instant explicitInstant = tryParseExplicitInstant(expression);
        if (explicitInstant != null) {
            return explicitInstant;
        }

        Matcher utcSuffix = EXPLICIT_UTC_SUFFIX.matcher(expression);
        if (utcSuffix.matches()) {
            LocalDateTime localDateTime = parseAbsoluteOrRelative(utcSuffix.group(1).trim(), businessZone);
            return localDateTime.atZone(ZoneOffset.UTC).toInstant();
        }

        LocalDateTime localDateTime = parseAbsoluteOrRelative(expression, businessZone);
        return localDateTime.atZone(businessZone).toInstant();
    }

    private ZoneId validateZoneId(String businessTimezone) {
        try {
            return ZoneId.of(businessTimezone);
        } catch (DateTimeException ex) {
            throw new BusinessException("La zona horaria configurada en el negocio no es válida");
        }
    }

    private Instant tryParseExplicitInstant(String expression) {
        try {
            return Instant.parse(expression);
        } catch (DateTimeParseException ignored) {
            // no es un Instant ISO-8601 terminado en 'Z'; se intenta con offset explícito
        }
        try {
            return OffsetDateTime.parse(expression).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDateTime parseAbsoluteOrRelative(String expression, ZoneId businessZone) {
        LocalDateTime relative = tryParseRelative(expression, businessZone);
        if (relative != null) {
            return relative;
        }

        try {
            return LocalDateTime.parse(expression, ABSOLUTE_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("No pude interpretar la fecha y hora indicadas: '" + expression + "'");
        }
    }

    private LocalDateTime tryParseRelative(String expression, ZoneId businessZone) {
        LocalDate today = ZonedDateTime.now(businessZone).toLocalDate();

        Matcher tomorrow = TOMORROW_PREFIX.matcher(expression);
        if (tomorrow.find()) {
            return LocalDateTime.of(today.plusDays(1), extractTime(expression));
        }

        Matcher todayMatcher = TODAY_PREFIX.matcher(expression);
        if (todayMatcher.find()) {
            return LocalDateTime.of(today, extractTime(expression));
        }

        Matcher nextWeekday = NEXT_WEEKDAY_PREFIX.matcher(expression);
        if (nextWeekday.find()) {
            DayOfWeek dayOfWeek = resolveDayOfWeek(nextWeekday.group(1));
            LocalDate date = today.with(TemporalAdjusters.next(dayOfWeek));
            return LocalDateTime.of(date, extractTime(expression));
        }

        Matcher thisWeekday = THIS_WEEKDAY_PREFIX.matcher(expression);
        if (thisWeekday.find()) {
            DayOfWeek dayOfWeek = resolveDayOfWeek(thisWeekday.group(1));
            LocalDate date = today.with(TemporalAdjusters.nextOrSame(dayOfWeek));
            return LocalDateTime.of(date, extractTime(expression));
        }

        return null;
    }

    private DayOfWeek resolveDayOfWeek(String dayName) {
        DayOfWeek dayOfWeek = WEEKDAYS.get(dayName.toLowerCase(Locale.ROOT));
        if (dayOfWeek == null) {
            throw new BusinessException("No reconozco el día indicado: '" + dayName + "'");
        }
        return dayOfWeek;
    }

    private LocalTime extractTime(String expression) {
        Matcher matcher = TIME_SUFFIX.matcher(expression);
        if (!matcher.find()) {
            throw new BusinessException("No pude interpretar la hora indicada: '" + expression + "'");
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        return LocalTime.of(hour, minute);
    }

}
