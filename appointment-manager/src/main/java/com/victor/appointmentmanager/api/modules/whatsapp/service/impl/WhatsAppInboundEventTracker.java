package com.victor.appointmentmanager.api.modules.whatsapp.service.impl;

import com.victor.appointmentmanager.api.modules.whatsapp.entity.WhatsAppInboundEvent;
import com.victor.appointmentmanager.api.modules.whatsapp.enums.WhatsAppEventProcessingStatus;
import com.victor.appointmentmanager.api.modules.whatsapp.repository.WhatsAppInboundEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Aísla en un bean propio las escrituras de WhatsAppInboundEvent para que cada una tenga
 * su propia transacción corta (vía proxy de Spring), evitando mantener una transacción
 * abierta durante las llamadas HTTP largas a ConversationService/WhatsAppClient que ocurren
 * entre "registrar recibido" y "marcar procesado/fallido".
 */
@Component
@RequiredArgsConstructor
class WhatsAppInboundEventTracker {

    private final WhatsAppInboundEventRepository inboundEventRepository;

    @Transactional
    WhatsAppInboundEvent registerReceived(Long businessId, String externalMessageId, String senderPhone,
                                           String eventType) {
        WhatsAppInboundEvent event = new WhatsAppInboundEvent();
        event.setExternalMessageId(externalMessageId);
        event.setBusinessId(businessId);
        event.setSenderPhone(senderPhone);
        event.setEventType(eventType);
        event.setProcessingStatus(WhatsAppEventProcessingStatus.RECEIVED);
        return inboundEventRepository.save(event);
    }

    @Transactional
    void markProcessed(Long eventId) {
        inboundEventRepository.findById(eventId).ifPresent(event -> {
            event.setProcessingStatus(WhatsAppEventProcessingStatus.PROCESSED);
            event.setProcessedAt(Instant.now());
        });
    }

    @Transactional
    void markFailed(Long eventId, String sanitizedErrorMessage) {
        inboundEventRepository.findById(eventId).ifPresent(event -> {
            event.setProcessingStatus(WhatsAppEventProcessingStatus.FAILED);
            event.setErrorMessage(sanitizedErrorMessage);
            event.setProcessedAt(Instant.now());
        });
    }

}
