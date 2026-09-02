package com.victor.appointmentmanager.api.modules.conversations.repository;

import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByBusinessIdAndSenderPhone(Long businessId, String senderPhone);

}
