package com.victor.appointmentmanager.api.modules.appointments.service;

/**
 * Motivo exacto por el cual {@link AppointmentService#checkAvailability} determinó que un
 * horario NO puede reservarse. Permite distinguir, en el flujo conversacional de WhatsApp,
 * entre "ocupado", "fuera de horario laboral" y "el empleado no realiza ese servicio" en vez
 * de colapsar todos los casos en un único mensaje genérico.
 */
public enum AvailabilityReason {
    EMPLOYEE_NOT_FOUND,
    SERVICE_NOT_FOUND,
    EMPLOYEE_CANNOT_PERFORM_SERVICE,
    IN_PAST,
    OUTSIDE_SCHEDULE,
    OVERLAPPING
}
