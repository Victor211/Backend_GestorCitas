package com.victor.appointmentmanager.api.common.response;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ResponseFactory {

    private final String DEFAULT_SUCCESS_MESSAGE = "Operación exitosa";

    public <T> ApiResponse<T> success(T data) {
        return success(DEFAULT_SUCCESS_MESSAGE, data);
    }

    public <T> ApiResponse<T> success(String message, T data) {
        return build(true, message, data);
    }

    public <T> ApiResponse<T> created(String message, T data) {
        return build(true, message, data);
    }

    public <T> ApiResponse<T> error(String message) {
        return build(false, message, null);
    }

    private <T> ApiResponse<T> build(boolean success, String message, T data) {
        return ApiResponse.<T>builder()
                .success(success)
                .message(message)
                .data(data)
                .build();
    }

}
