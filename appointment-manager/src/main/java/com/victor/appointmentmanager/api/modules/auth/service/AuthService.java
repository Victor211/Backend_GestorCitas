package com.victor.appointmentmanager.api.modules.auth.service;

import com.victor.appointmentmanager.api.modules.auth.dto.request.LoginRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.request.RegisterRequest;
import com.victor.appointmentmanager.api.modules.auth.dto.response.AuthResponse;
import com.victor.appointmentmanager.api.modules.auth.dto.response.CurrentUserResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    CurrentUserResponse getCurrentUser();

}
