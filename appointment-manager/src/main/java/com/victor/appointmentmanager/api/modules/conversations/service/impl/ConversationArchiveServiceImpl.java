package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationArchiveResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.mapper.ConversationMapper;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationArchiveService;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationArchiveServiceImpl implements ConversationArchiveService {

    private final ConversationRepository conversationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ConversationMapper conversationMapper;

    @Override
    @Transactional
    public ConversationArchiveResponse archive(Long conversationId) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Conversation conversation = findOwnedByIdOrThrow(conversationId, businessId);

        // Idempotente a propósito: si ya estaba archivada, se conserva el archivedAt original (no
        // se pisa con un instante nuevo) y se evita un save de más.
        if (conversation.getArchivedAt() == null) {
            conversation.setArchivedAt(Instant.now());
            conversationRepository.save(conversation);
            log.info("Conversation {} archivada. businessId={}", conversation.getId(), businessId);
        }

        return conversationMapper.toArchiveResponse(conversation);
    }

    @Override
    @Transactional
    public ConversationArchiveResponse restore(Long conversationId) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Conversation conversation = findOwnedByIdOrThrow(conversationId, businessId);

        if (conversation.getArchivedAt() != null) {
            conversation.setArchivedAt(null);
            conversationRepository.save(conversation);
            log.info("Conversation {} restaurada. businessId={}", conversation.getId(), businessId);
        }

        return conversationMapper.toArchiveResponse(conversation);
    }

    private Conversation findOwnedByIdOrThrow(Long id, Long businessId) {
        return conversationRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversación no encontrada con id " + id));
    }

}
