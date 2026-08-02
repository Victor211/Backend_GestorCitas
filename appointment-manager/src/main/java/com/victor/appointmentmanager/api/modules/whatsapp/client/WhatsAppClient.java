package com.victor.appointmentmanager.api.modules.whatsapp.client;

import com.victor.appointmentmanager.api.modules.whatsapp.dto.response.WhatsAppSendMessageResponse;

public interface WhatsAppClient {

    WhatsAppSendMessageResponse sendTextMessage(String phoneNumberId, String recipientPhone, String message);

}
