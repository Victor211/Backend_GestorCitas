package com.victor.appointmentmanager.api.modules.ai.repository;

import com.victor.appointmentmanager.api.modules.ai.entity.ConversationState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationStateRepository extends JpaRepository<ConversationState, Long> {

    Optional<ConversationState> findByBusinessIdAndCustomerPhone(Long businessId, String customerPhone);

}
