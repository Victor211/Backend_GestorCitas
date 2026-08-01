package com.victor.appointmentmanager.api.common.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ApiError {

    private final boolean success;
    private final String message;

    @Builder.Default
    private final List<ValidationError> errors = List.of();

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    private final int status;
    private final String path;

}
