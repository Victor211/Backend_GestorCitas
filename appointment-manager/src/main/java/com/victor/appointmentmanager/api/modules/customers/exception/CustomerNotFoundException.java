package com.victor.appointmentmanager.api.modules.customers.exception;

import com.victor.appointmentmanager.api.common.exception.ResourceNotFoundException;

public class CustomerNotFoundException extends ResourceNotFoundException {

    public CustomerNotFoundException(String message) {
        super(message);
    }

}
