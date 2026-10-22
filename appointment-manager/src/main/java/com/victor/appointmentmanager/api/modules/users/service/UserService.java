package com.victor.appointmentmanager.api.modules.users.service;

import com.victor.appointmentmanager.api.modules.users.dto.request.CreateUserRequest;
import com.victor.appointmentmanager.api.modules.users.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    Page<UserResponse> findAll(Pageable pageable);

    UserResponse updateStatus(Long id, boolean active);

}
