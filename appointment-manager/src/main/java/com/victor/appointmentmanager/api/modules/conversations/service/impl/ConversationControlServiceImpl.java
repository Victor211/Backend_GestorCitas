package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationModeResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.mapper.ConversationMapper;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationControlService;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationControlServiceImpl implements ConversationControlService {

    private final ConversationRepository conversationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ConversationMapper conversationMapper;

    @Override
    @Transactional
    public ConversationModeResponse takeover(Long conversationId) {
        return setMode(conversationId, ConversationMode.HUMAN);
    }

    @Override
    @Transactional
    public ConversationModeResponse release(Long conversationId) {
        return setMode(conversationId, ConversationMode.BOT);
    }

    private ConversationModeResponse setMode(Long conversationId, ConversationMode mode) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Conversation conversation = findOwnedByIdOrThrow(conversationId, businessId);

        // Idempotente a propósito: si ya está en el modo pedido, igual se persiste (no-op a nivel
        // de datos) y se responde 200 con el estado actual, sin distinguir el caso "ya estaba así".
        conversation.setMode(mode);
        conversationRepository.save(conversation);

        log.info("Conversation {} cambió a modo {}. businessId={}", conversation.getId(), mode, businessId);
        return conversationMapper.toModeResponse(conversation);
    }

    private Conversation findOwnedByIdOrThrow(Long id, Long businessId) {
        return conversationRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversación no encontrada con id " + id));
    }

}
