package com.victor.appointmentmanager.api.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRequest {

    @NotBlank
    private String customerPhone;

    @NotBlank
    private String message;

}
