package com.victor.appointmentmanager.api.modules.conversations.repository;

import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    Optional<ConversationMessage> findByExternalMessageId(String externalMessageId);

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * {@code businessId} se repite acá aunque la Conversation ya se validó como propia del negocio
     * autenticado (ver {@code ConversationQueryServiceImpl#findOwnedByIdOrThrow}): es una segunda
     * barrera defensiva a nivel de consulta, no un mecanismo de autorización alternativo. El orden
     * cronológico (createdAt ASC) lo aporta el {@code Pageable} recibido desde el controller
     * ({@code @PageableDefault}), igual que en {@code AppointmentController#findAll}.
     */
    Page<ConversationMessage> findByConversationIdAndBusinessId(Long conversationId, Long businessId,
                                                                  Pageable pageable);

}
