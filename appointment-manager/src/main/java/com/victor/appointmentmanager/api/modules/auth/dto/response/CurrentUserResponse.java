package com.victor.appointmentmanager.api.modules.auth.dto.response;

import com.victor.appointmentmanager.api.modules.users.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private UserRole role;
    private Long businessId;
    private String businessName;

}
