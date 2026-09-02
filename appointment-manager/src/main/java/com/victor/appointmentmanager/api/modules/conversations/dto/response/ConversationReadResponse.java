package com.victor.appointmentmanager.api.modules.conversations.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationReadResponse {

    private Long id;
    private int unreadCount;

}
