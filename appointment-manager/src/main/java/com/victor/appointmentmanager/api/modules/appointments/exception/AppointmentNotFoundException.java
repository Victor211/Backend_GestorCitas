package com.victor.appointmentmanager.api.modules.appointments.exception;

import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;

public class AppointmentNotFoundException extends ResourceNotFoundException {

    public AppointmentNotFoundException(String message) {
        super(message);
    }

}
