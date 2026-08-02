package com.victor.appointmentmanager.api.modules.whatsapp.service;

public interface WhatsAppWebhookService {

    boolean verifyWebhook(String mode, String verifyToken);

    boolean isSignatureValid(String rawBody, String signatureHeader);

    void processWebhook(String rawBody);

}
