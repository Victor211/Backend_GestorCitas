package com.victor.appointmentmanager.api.modules.appointments.service;

/**
 * Resultado detallado de {@link AppointmentService#checkAvailability}: no solo si el horario
 * está disponible, sino, si no lo está, por qué exactamente ({@link AvailabilityReason}).
 * {@code reason} es {@code null} cuando {@code available} es {@code true}.
 */
public record AvailabilityCheck(boolean available, AvailabilityReason reason) {

    /** Un método estático llamado igual que el accessor del record (available()) no compila. */
    public static final AvailabilityCheck AVAILABLE = new AvailabilityCheck(true, null);

    public static AvailabilityCheck unavailable(AvailabilityReason reason) {
        return new AvailabilityCheck(false, reason);
    }
}
