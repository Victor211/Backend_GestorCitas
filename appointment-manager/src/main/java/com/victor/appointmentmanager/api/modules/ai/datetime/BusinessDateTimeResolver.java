package com.victor.appointmentmanager.api.modules.ai.datetime;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.ai.exception.AmbiguousTimeException;
import com.victor.appointmentmanager.api.modules.ai.exception.MissingTimeException;
import org.springframework.stereotype.Component;

import java.time.Clock;
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

    private final Clock clock;

    public BusinessDateTimeResolver() {
        this(Clock.systemUTC());
    }

    /** Reloj inyectable (tests con fecha fija). La zona del negocio se aplica siempre sobre él. */
    public BusinessDateTimeResolver(Clock clock) {
        this.clock = clock;
    }

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
    private static final Pattern TIME_SUFFIX = Pattern.compile("(?i)a las\\s+(\\d{1,2})(?::(\\d{2}))?");
    private static final Pattern PM_MARKER = Pattern.compile("(?i)\\b(?:de|por|esta)\\s+(?:la\\s+)?(?:tarde|noche)\\b");
    private static final Pattern AM_MARKER = Pattern.compile("(?i)\\b(?:de|por)\\s+(?:la\\s+)?mañana\\b");
    /**
     * Rango de horas que se consideran genuinamente ambiguas sin contexto adicional. Se acota a
     * 1-8 (en vez de 1-11) a propósito: en el uso cotidiano de un negocio, "a las 9/10/11" casi
     * siempre se entiende como AM incluso sin aclaración, mientras que "a las 1-8" es ambiguo con
     * más frecuencia real (podría ser la 1pm-8pm). Esto evita pedir aclaraciones innecesarias en
     * el caso común ("el próximo lunes a las 9", "mañana a las 10").
     */
    private static final int AMBIGUOUS_HOUR_MIN = 1;
    private static final int AMBIGUOUS_HOUR_MAX = 8;
    private static final Pattern TOMORROW_PREFIX = Pattern.compile("(?i)^mañana(?:\\s+|$)");
    private static final Pattern TODAY_PREFIX = Pattern.compile("(?i)^hoy(?:\\s+|$)");
    private static final Pattern NEXT_WEEKDAY_PREFIX =
            Pattern.compile("(?i)^(?:el\\s+)?pr[oó]ximo\\s+(\\p{L}+)(?:\\s+|$)");
    private static final Pattern THIS_WEEKDAY_PREFIX = Pattern.compile("(?i)^este\\s+(\\p{L}+)(?:\\s+|$)");
    /**
     * Nombre de día "pelado", sin "próximo"/"este" (ej. "jueves a las 10", "el jueves"). Se
     * distingue de un día real chequeando el grupo capturado contra {@link #WEEKDAYS}: cualquier
     * otra primera palabra (ej. "a las 10") simplemente no matchea ningún día y sigue su curso
     * normal. Semántica igual a "este" ({@link TemporalAdjusters#nextOrSame}): "jueves" se entiende
     * como el próximo jueves que llega, incluyendo hoy si hoy es jueves.
     */
    private static final Pattern BARE_WEEKDAY_PREFIX = Pattern.compile("(?i)^(?:el\\s+)?(\\p{L}+)(?:\\s+|$)");
    private static final Pattern BARE_TIME_EXPRESSION = Pattern.compile(
            "(?i)^(?:(?:de|por|esta)\\s+(?:la\\s+)?(?:tarde|mañana|noche)\\s+)?a las\\s+\\d{1,2}(?::\\d{2})?$");

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

    private LocalDateTime parseAbsoluteOrRelative(String rawExpression, ZoneId businessZone) {
        // "para el 29 de octubre a las 15" / "para mañana a las 15": el "para" inicial no aporta nada.
        String expression = LEADING_PARA.matcher(rawExpression).replaceFirst("").trim();

        LocalDateTime explicitDate = tryParseExplicitDate(expression, businessZone);
        if (explicitDate != null) {
            return explicitDate;
        }

        LocalDateTime relative = tryParseRelative(expression, businessZone);
        if (relative != null) {
            return relative;
        }

        try {
            LocalDateTime literal = LocalDateTime.parse(expression, ABSOLUTE_DATE_TIME_FORMATTER);
            int resolvedHour = resolveAmbiguousHour(
                    literal.getHour(), literal.getMinute(), expression, literal.toLocalDate(), businessZone);
            return LocalDateTime.of(literal.toLocalDate(), LocalTime.of(resolvedHour, literal.getMinute()));
        } catch (DateTimeParseException ex) {
            throw new BusinessException("No pude interpretar la fecha y hora indicadas: '" + expression + "'");
        }
    }

    // ---------------------------------------------------------------------------------------
    // Fechas explícitas en español (GS-BOT-004)
    // ---------------------------------------------------------------------------------------

    /**
     * Fecha explícita al INICIO de la expresión: "29 de octubre", "el 29 de octubre de 2026",
     * "29/10", "29-10-2026" (día/mes, orden de Paraguay). El resto de la expresión ({@code tail}) se
     * valida aparte y la regex está anclada, así "servicio 29 de octubre" o un número suelto ("3")
     * nunca se interpretan como fecha.
     */
    private static final Pattern EXPLICIT_DATE = Pattern.compile(
            "(?i)^(?:el\\s+)?(\\d{1,2})\\s*(?:de\\s+(\\p{L}+)|[/\\-](\\d{1,2}))"
                    + "(?:\\s*(?:de\\s+|[/\\-])\\s*(\\d{4}))?(?<tail>.*)$");
    /** Lo único admitido después de la fecha: nada, o una hora "a las H[:mm]" con marcadores opcionales. */
    private static final Pattern EXPLICIT_DATE_TIME_TAIL = Pattern.compile(
            "(?i)^[\\s,]*(?:(?:de|por|esta)\\s+(?:la\\s+)?(?:tarde|mañana|noche)\\s+)?"
                    + "a\\s+las\\s+\\d{1,2}(?::\\d{2})?(?:\\s*(?:hs|horas))?"
                    + "(?:\\s+(?:de|por)\\s+la\\s+(?:tarde|mañana|noche))?\\s*$");
    private static final Pattern LEADING_PARA = Pattern.compile("(?i)^para\\s+");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3),
            Map.entry("abril", 4), Map.entry("mayo", 5), Map.entry("junio", 6),
            Map.entry("julio", 7), Map.entry("agosto", 8), Map.entry("septiembre", 9),
            Map.entry("setiembre", 9), Map.entry("octubre", 10), Map.entry("noviembre", 11),
            Map.entry("diciembre", 12));

    /**
     * Devuelve {@code null} si la expresión no empieza con una fecha explícita (sigue el flujo
     * normal). Si la reconoce:
     * <ul>
     *   <li>año explícito: se respeta tal cual (como siempre; una fecha pasada la rechaza después
     *       AppointmentService), salvo que no haya hora, caso en que una fecha pasada se rechaza acá;</li>
     *   <li>año omitido: se usa la primera ocurrencia FUTURA (hoy incluido) en la zona del negocio —
     *       el año en curso, o el siguiente si esa fecha ya pasó o no existe (29 de febrero);</li>
     *   <li>fecha inexistente (31 de febrero, mes 13): {@link BusinessException};</li>
     *   <li>sin hora: {@link MissingTimeException} con la fecha resuelta, para que el bot pida la hora.</li>
     * </ul>
     */
    private LocalDateTime tryParseExplicitDate(String expression, ZoneId businessZone) {
        Matcher matcher = EXPLICIT_DATE.matcher(expression);
        if (!matcher.matches()) {
            return null;
        }
        String tail = matcher.group("tail");
        boolean hasTime = EXPLICIT_DATE_TIME_TAIL.matcher(tail).matches();
        if (!hasTime && !tail.isBlank()) {
            return null;
        }

        int day = Integer.parseInt(matcher.group(1));
        Integer month = matcher.group(2) != null
                ? MONTHS.get(matcher.group(2).toLowerCase(Locale.ROOT))
                : Integer.valueOf(matcher.group(3));
        if (month == null) {
            return null; // "29 de algo": no es un mes, no es una fecha
        }
        String described = expression.trim();
        LocalDate today = now(businessZone).toLocalDate();

        LocalDate date;
        if (matcher.group(4) != null) {
            date = validDateOrNull(Integer.parseInt(matcher.group(4)), month, day);
            if (date == null) {
                throw new BusinessException("La fecha indicada no existe: '" + described + "'");
            }
            if (!hasTime && date.isBefore(today)) {
                throw new BusinessException("Esa fecha ya pasó. ¿Podés indicarme otra?");
            }
        } else {
            date = validDateOrNull(today.getYear(), month, day);
            if (date == null || date.isBefore(today)) {
                date = validDateOrNull(today.getYear() + 1, month, day);
            }
            if (date == null) {
                throw new BusinessException("La fecha indicada no existe: '" + described + "'");
            }
        }

        if (!hasTime) {
            throw new MissingTimeException(date);
        }
        try {
            return combineDateAndTime(expression, date, businessZone);
        } catch (DateTimeException ex) {
            throw new BusinessException("La hora indicada no es válida: '" + described + "'");
        }
    }

    private LocalDate validDateOrNull(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException ex) {
            return null;
        }
    }

    private ZonedDateTime now(ZoneId zone) {
        return ZonedDateTime.now(clock.withZone(zone));
    }

    private LocalDateTime tryParseRelative(String expression, ZoneId businessZone) {
        LocalDate today = now(businessZone).toLocalDate();

        Matcher tomorrow = TOMORROW_PREFIX.matcher(expression);
        if (tomorrow.find()) {
            return combineDateAndTime(expression, today.plusDays(1), businessZone);
        }

        Matcher todayMatcher = TODAY_PREFIX.matcher(expression);
        if (todayMatcher.find()) {
            return combineDateAndTime(expression, today, businessZone);
        }

        Matcher nextWeekday = NEXT_WEEKDAY_PREFIX.matcher(expression);
        if (nextWeekday.find()) {
            DayOfWeek dayOfWeek = resolveDayOfWeek(nextWeekday.group(1));
            LocalDate date = today.with(TemporalAdjusters.next(dayOfWeek));
            return combineDateAndTime(expression, date, businessZone);
        }

        Matcher thisWeekday = THIS_WEEKDAY_PREFIX.matcher(expression);
        if (thisWeekday.find()) {
            DayOfWeek dayOfWeek = resolveDayOfWeek(thisWeekday.group(1));
            LocalDate date = today.with(TemporalAdjusters.nextOrSame(dayOfWeek));
            return combineDateAndTime(expression, date, businessZone);
        }

        Matcher bareWeekday = BARE_WEEKDAY_PREFIX.matcher(expression);
        if (bareWeekday.find()) {
            DayOfWeek dayOfWeek = WEEKDAYS.get(bareWeekday.group(1).toLowerCase(Locale.ROOT));
            if (dayOfWeek != null) {
                LocalDate date = today.with(TemporalAdjusters.nextOrSame(dayOfWeek));
                return combineDateAndTime(expression, date, businessZone);
            }
        }

        if (BARE_TIME_EXPRESSION.matcher(expression.trim()).matches()) {
            // No se mencionó ninguna fecha: se asume "hoy" para poder aplicar la misma
            // desambiguación AM/PM contra la hora actual del negocio.
            return combineDateAndTime(expression, today, businessZone);
        }

        return null;
    }

    /**
     * Resuelve solo una hora (ej. "a las 16" o "16:00") contra una fecha ya conocida de un turno
     * anterior de la conversación (ej. el cliente ya dijo "hoy" y ahora responde solo la hora).
     * Aplica la misma desambiguación AM/PM que el resto de la clase.
     */
    public Instant resolveTimeOnly(String rawExpression, LocalDate contextDate, String businessTimezone) {
        if (rawExpression == null || rawExpression.isBlank()) {
            throw new BusinessException("No se indicó una hora para la reserva");
        }
        ZoneId businessZone = validateZoneId(businessTimezone);
        String expression = rawExpression.trim();

        Matcher matcher = TIME_SUFFIX.matcher(expression);
        if (!matcher.find()) {
            throw new BusinessException("No pude interpretar la hora indicada: '" + expression + "'");
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int resolvedHour = resolveAmbiguousHour(hour, minute, expression, contextDate, businessZone);
        return LocalDateTime.of(contextDate, LocalTime.of(resolvedHour, minute)).atZone(businessZone).toInstant();
    }

    private DayOfWeek resolveDayOfWeek(String dayName) {
        DayOfWeek dayOfWeek = WEEKDAYS.get(dayName.toLowerCase(Locale.ROOT));
        if (dayOfWeek == null) {
            throw new BusinessException("No reconozco el día indicado: '" + dayName + "'");
        }
        return dayOfWeek;
    }

    private LocalDateTime combineDateAndTime(String expression, LocalDate date, ZoneId businessZone) {
        Matcher matcher = TIME_SUFFIX.matcher(expression);
        if (!matcher.find()) {
            throw new MissingTimeException(date);
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
        int resolvedHour = resolveAmbiguousHour(hour, minute, expression, date, businessZone);
        return LocalDateTime.of(date, LocalTime.of(resolvedHour, minute));
    }

    /**
     * Desambigua una hora de 12 horas (1-11) sin AM/PM explícito, en este orden:
     * (1) marcadores textuales ("de la tarde", "por la mañana", etc.) en la expresión completa;
     * (2) si la fecha resuelta es hoy y la lectura AM ya pasó pero la PM sigue siendo futura, se
     *     prefiere PM; (3) si sigue sin poder resolverse, se pide aclaración al cliente. Horas
     *     fuera del rango 1-11 (0, 12-23) no son ambiguas y se devuelven tal cual.
     */
    private int resolveAmbiguousHour(int hour, int minute, String expression, LocalDate date, ZoneId businessZone) {
        if (hour < AMBIGUOUS_HOUR_MIN || hour > AMBIGUOUS_HOUR_MAX) {
            return hour;
        }
        if (PM_MARKER.matcher(expression).find()) {
            return hour + 12;
        }
        if (AM_MARKER.matcher(expression).find()) {
            return hour;
        }
        if (date.equals(now(businessZone).toLocalDate())) {
            ZonedDateTime now = now(businessZone);
            ZonedDateTime amCandidate = ZonedDateTime.of(date, LocalTime.of(hour, minute), businessZone);
            ZonedDateTime pmCandidate = ZonedDateTime.of(date, LocalTime.of(hour + 12, minute), businessZone);
            if (amCandidate.isBefore(now) && !pmCandidate.isBefore(now)) {
                return hour + 12;
            }
        }
        throw new AmbiguousTimeException(hour, hour + 12, minute);
    }

}
