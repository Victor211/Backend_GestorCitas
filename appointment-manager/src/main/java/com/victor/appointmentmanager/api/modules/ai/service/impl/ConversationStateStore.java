package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.modules.ai.entity.ConversationState;
import com.victor.appointmentmanager.api.modules.ai.enums.ConversationStage;
import com.victor.appointmentmanager.api.modules.ai.repository.ConversationStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Único punto de lectura/escritura de {@link ConversationState}. Aísla las transacciones cortas
 * (mismo motivo que {@code WhatsAppInboundEventTracker}: evitar mantener una transacción abierta
 * durante la llamada HTTP a OpenAI que ocurre entre cargar y guardar el estado) y concentra la
 * política de expiración de la conversación completa: un borrador abandonado (en cualquier etapa,
 * no solo esperando confirmación) no debe sobrevivir indefinidamente.
 *
 * <p>La expiración se resuelve de forma perezosa (al leer el estado, comparando contra
 * {@code updatedAt}) sin agregar Redis ni ningún scheduler: {@code updatedAt} ya se actualiza en
 * cada turno porque {@link #save} vuelve a persistir el estado siempre, y JPA auditing
 * ({@code @LastModifiedDate}) lo refresca en cada merge.</p>
 */
@Slf4j
@Component
public class ConversationStateStore {

    private final ConversationStateRepository repository;
    private final Duration conversationTimeout;

    public ConversationStateStore(
            ConversationStateRepository repository,
            @Value("${app.whatsapp.conversation-timeout-minutes:30}") long conversationTimeoutMinutes) {
        this.repository = repository;
        this.conversationTimeout = Duration.ofMinutes(conversationTimeoutMinutes);
    }

    @Transactional
    public ConversationState loadOrCreate(Long businessId, String customerPhone) {
        ConversationState state = repository.findByBusinessIdAndCustomerPhone(businessId, customerPhone)
                .orElseGet(() -> newState(businessId, customerPhone));

        Instant lastActivity = lastActivityOf(state);
        boolean expired = lastActivity != null
                && lastActivity.isBefore(Instant.now().minus(conversationTimeout));

        log.debug("Conversation state loaded. businessId={}, phone={}, stage={}, expired={}, serviceId={}, "
                        + "employeeId={}, pendingDate={}, startAt={}, lastInteractionAt={}",
                businessId, maskPhone(customerPhone), state.getStage(), expired, state.getPendingServiceId(),
                state.getPendingEmployeeId(), state.getPendingDate(), state.getPendingStartAt(), lastActivity);

        if (expired) {
            if (state.getStage() == ConversationStage.AWAITING_CONFIRMATION) {
                // Se resuelve en el orquestador: un "sí" contra una propuesta vencida no debe
                // crear la cita ni tampoco procesarse en silencio como si nada hubiera pasado.
                state.setExpiredPendingConfirmation(true);
            }
            state.resetForNewConversation();
        }

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

    private Instant lastActivityOf(ConversationState state) {
        return state.getUpdatedAt() != null ? state.getUpdatedAt() : state.getCreatedAt();
    }

    private static final int VISIBLE_PHONE_SUFFIX_LENGTH = 3;

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= VISIBLE_PHONE_SUFFIX_LENGTH) {
            return "***";
        }
        String suffix = phone.substring(phone.length() - VISIBLE_PHONE_SUFFIX_LENGTH);
        return "*".repeat(phone.length() - VISIBLE_PHONE_SUFFIX_LENGTH) + suffix;
    }

}
