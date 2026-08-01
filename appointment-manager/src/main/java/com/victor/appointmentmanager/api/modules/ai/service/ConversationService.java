package com.victor.appointmentmanager.api.modules.ai.service;

import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;

public interface ConversationService {

    ConversationResponse handleMessage(ConversationRequest request);

}
