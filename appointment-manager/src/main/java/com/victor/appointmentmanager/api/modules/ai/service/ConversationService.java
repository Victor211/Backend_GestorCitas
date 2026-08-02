package com.victor.appointmentmanager.api.modules.ai.service;

import com.victor.appointmentmanager.api.modules.ai.dto.request.ConversationRequest;
import com.victor.appointmentmanager.api.modules.ai.dto.response.ConversationResponse;

public interface ConversationService {

    /**
     * Entry point for the authenticated panel: Business is resolved from the JWT.
     */
    ConversationResponse processAuthenticatedConversation(ConversationRequest request);

    /**
     * Entry point for non-JWT channels (e.g. WhatsApp) that already resolved the Business
     * by other means (phone_number_id) and cannot rely on the SecurityContext.
     */
    ConversationResponse processChannelConversation(Long businessId, String customerPhone, String message);

}
