package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.modules.ai.entity.ConversationState;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationStage;
import com.victor.appointmentmanager.api.modules.ai.repository.ConversationStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Único punto de lectura/escritura de {@link ConversationState}. Aísla las transacciones cortas
 * (mismo motivo que {@code WhatsAppInboundEventTracker}: evitar mantener una transacción abierta
 * durante la llamada HTTP a OpenAI que ocurre entre cargar y guardar el estado) y concentra la
 * política de expiración de una propuesta pendiente de confirmación.
 */
@Component
@RequiredArgsConstructor
public class ConversationStateStore {

    /**
     * Tiempo máximo que una propuesta de reserva permanece esperando confirmación antes de
     * descartarse. El proyecto no usa Redis ni ningún scheduler adicional, así que la expiración
     * se resuelve de forma perezosa (al leer el estado) comparando contra {@code updatedAt}, sin
     * agregar infraestructura nueva.
     */
    static final Duration PENDING_CONFIRMATION_TTL = Duration.ofMinutes(20);

    private final ConversationStateRepository repository;

    @Transactional
    public ConversationState loadOrCreate(Long businessId, String customerPhone) {
        ConversationState state = repository.findByBusinessIdAndCustomerPhone(businessId, customerPhone)
                .orElseGet(() -> newState(businessId, customerPhone));
        expireIfStale(state);
        return state;
    }

    @Transactional
    public void save(ConversationState state) {
        repository.save(state);
    }

    private ConversationState newState(Long businessId, String customerPhone) {
        ConversationState state = new ConversationState();
        state.setBusinessId(businessId);
        state.setCustomerPhone(customerPhone);
        return state;
    }

    private void expireIfStale(ConversationState state) {
        if (state.getStage() != ConversationStage.AWAITING_CONFIRMATION) {
            return;
        }
        Instant lastActivity = state.getUpdatedAt() != null ? state.getUpdatedAt() : state.getCreatedAt();
        if (lastActivity != null && lastActivity.isBefore(Instant.now().minus(PENDING_CONFIRMATION_TTL))) {
            state.clearBookingDraft();
        }
    }

}
