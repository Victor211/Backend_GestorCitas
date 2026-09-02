package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationReadResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationSummaryResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.mapper.ConversationMapper;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationMessageRepository;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.conversations.service.ConversationQueryService;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationQueryServiceImpl implements ConversationQueryService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ConversationMapper conversationMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationSummaryResponse> listConversations(Pageable pageable) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Page<Conversation> conversations = conversationRepository.findByBusinessId(businessId, pageable);

        // Evita N+1: en vez de resolver el Customer conversación por conversación, se hace UNA sola
        // consulta "IN" con los customerId no nulos de toda la página (como mucho, page size + 1
        // queries en total, sin importar cuántas conversaciones tengan Customer).
        Map<Long, Customer> customersById = loadCustomersById(conversations.getContent());

        return conversations.map(conversation ->
                conversationMapper.toSummaryResponse(conversation, customersById.get(conversation.getCustomerId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConversationMessageResponse> listMessages(Long conversationId, Pageable pageable) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Conversation conversation = findOwnedByIdOrThrow(conversationId, businessId);

        return conversationMessageRepository
                .findByConversationIdAndBusinessId(conversation.getId(), businessId, pageable)
                .map(conversationMapper::toMessageResponse);
    }

    @Override
    @Transactional
    public ConversationReadResponse markAsRead(Long conversationId) {
        Long businessId = currentUserProvider.getCurrentBusinessId();
        Conversation conversation = findOwnedByIdOrThrow(conversationId, businessId);

        conversation.setUnreadCount(0);
        conversationRepository.save(conversation);

        return conversationMapper.toReadResponse(conversation);
    }

    private Conversation findOwnedByIdOrThrow(Long id, Long businessId) {
        return conversationRepository.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversación no encontrada con id " + id));
    }

    private Map<Long, Customer> loadCustomersById(List<Conversation> conversations) {
        List<Long> customerIds = conversations.stream()
                .map(Conversation::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (customerIds.isEmpty()) {
            // Collections.emptyMap() (no Map.of()): Conversation.getCustomerId() suele ser null
            // -- justo el caso de un cliente nuevo -- y customersById.get(null) se llama igual más
            // abajo. Map.of() prohíbe claves null y su get(null) lanza NullPointerException; un
            // Map "normal" simplemente devuelve null para una clave ausente.
            return Collections.emptyMap();
        }

        return customerRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
    }

}
