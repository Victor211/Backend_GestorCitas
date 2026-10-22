package com.victor.appointmentmanager.api.modules.users.dto.response;

import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Deliberadamente sin password ni hash. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private UserRole role;
    private Boolean active;

}
