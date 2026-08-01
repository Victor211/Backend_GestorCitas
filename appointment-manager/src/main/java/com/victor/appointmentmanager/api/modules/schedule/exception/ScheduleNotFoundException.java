package com.victor.appointmentmanager.api.modules.schedule.exception;

import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;

public class ScheduleNotFoundException extends ResourceNotFoundException {

    public ScheduleNotFoundException(String message) {
        super(message);
    }

}
