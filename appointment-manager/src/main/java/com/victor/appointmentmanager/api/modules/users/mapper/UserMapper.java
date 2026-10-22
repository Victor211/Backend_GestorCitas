package com.victor.appointmentmanager.api.modules.users.mapper;

import com.victor.appointmentmanager.api.modules.users.dto.response.UserResponse;
import com.victor.appointmentmanager.api.modules.users.entity.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserResponse toDto(User entity);

}
