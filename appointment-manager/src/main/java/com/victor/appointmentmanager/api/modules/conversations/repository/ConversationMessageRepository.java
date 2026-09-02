package com.victor.appointmentmanager.api.modules.conversations.repository;

import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    Optional<ConversationMessage> findByExternalMessageId(String externalMessageId);

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

}
