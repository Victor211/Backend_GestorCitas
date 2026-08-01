package com.victor.appointmentmanager.api.modules.ai.dto.response;

import com.victor.appointmentmanager.api.modules.ai.enums.ConversationIntent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private String reply;
    private ConversationIntent intent;
    private Double confidence;
    private Long appointmentId;

}
