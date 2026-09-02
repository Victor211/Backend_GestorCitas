package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationReadResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationSummaryResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.mapper.ConversationMapper;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationMessageRepository;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationQueryServiceImplTest {

    private static final Long BUSINESS_ID = 1L;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ConversationMapper conversationMapper;

    @InjectMocks
    private ConversationQueryServiceImpl service;

    @Captor
    private ArgumentCaptor<List<Long>> customerIdsCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(BUSINESS_ID);
    }

    private Conversation conversation(Long id, Long customerId) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setBusinessId(BUSINESS_ID);
        conversation.setCustomerId(customerId);
        conversation.setSenderPhone("59598100000" + id);
        conversation.setUnreadCount(2);
        return conversation;
    }

    // ------------------------------------------------------------------
    // listConversations
    // ------------------------------------------------------------------

    @Test
    void listConversationsUsesCurrentBusinessIdAndPassesThroughPageable() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation conv = conversation(1L, null);
        when(conversationRepository.findByBusinessId(BUSINESS_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(conv), pageable, 1));
        when(conversationMapper.toSummaryResponse(eq(conv), isNull()))
                .thenReturn(new ConversationSummaryResponse());

        Page<ConversationSummaryResponse> result = service.listConversations(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(conversationRepository).findByBusinessId(BUSINESS_ID, pageable);
    }

    @Test
    void listConversationsResolvesCustomerNamesWithoutNPlusOne() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation withCustomer1 = conversation(1L, 8L);
        Conversation withCustomer2 = conversation(2L, 9L);
        Conversation withoutCustomer = conversation(3L, null);
        when(conversationRepository.findByBusinessId(BUSINESS_ID, pageable)).thenReturn(
                new PageImpl<>(List.of(withCustomer1, withCustomer2, withoutCustomer), pageable, 3));

        Customer customer8 = new Customer();
        customer8.setId(8L);
        Customer customer9 = new Customer();
        customer9.setId(9L);
        when(customerRepository.findAllById(any())).thenReturn(List.of(customer8, customer9));
        when(conversationMapper.toSummaryResponse(any(), any())).thenReturn(new ConversationSummaryResponse());

        service.listConversations(pageable);

        // Una sola consulta "IN" para toda la página, sin importar cuántas conversaciones tengan
        // Customer: nunca N consultas individuales (N+1).
        verify(customerRepository).findAllById(customerIdsCaptor.capture());
        assertThat(customerIdsCaptor.getValue()).containsExactlyInAnyOrder(8L, 9L);
        verify(conversationMapper).toSummaryResponse(withCustomer1, customer8);
        verify(conversationMapper).toSummaryResponse(withCustomer2, customer9);
        verify(conversationMapper).toSummaryResponse(withoutCustomer, null);
    }

    @Test
    void listConversationsSkipsCustomerLookupWhenNoneHaveCustomerId() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation withoutCustomer = conversation(1L, null);
        when(conversationRepository.findByBusinessId(BUSINESS_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(withoutCustomer), pageable, 1));
        when(conversationMapper.toSummaryResponse(any(), any())).thenReturn(new ConversationSummaryResponse());

        service.listConversations(pageable);

        verify(customerRepository, never()).findAllById(any());
    }

    // ------------------------------------------------------------------
    // listMessages / ownership
    // ------------------------------------------------------------------

    @Test
    void listMessagesValidatesOwnershipByBusinessBeforeQuerying() {
        Pageable pageable = PageRequest.of(0, 50);
        Conversation owned = conversation(15L, null);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(owned));

        ConversationMessage message = new ConversationMessage();
        when(conversationMessageRepository.findByConversationIdAndBusinessId(15L, BUSINESS_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(message), pageable, 1));
        when(conversationMapper.toMessageResponse(message)).thenReturn(new ConversationMessageResponse());

        Page<ConversationMessageResponse> result = service.listMessages(15L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(conversationMessageRepository).findByConversationIdAndBusinessId(15L, BUSINESS_ID, pageable);
    }

    @Test
    void listMessagesThrowsNotFoundWhenConversationBelongsToAnotherBusiness() {
        Pageable pageable = PageRequest.of(0, 50);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMessages(15L, pageable))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(conversationMessageRepository, never()).findByConversationIdAndBusinessId(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // markAsRead
    // ------------------------------------------------------------------

    @Test
    void markAsReadResetsUnreadCountAndPersists() {
        Conversation owned = conversation(15L, null);
        owned.setUnreadCount(5);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(owned));
        when(conversationMapper.toReadResponse(owned)).thenAnswer(invocation ->
                new ConversationReadResponse(owned.getId(), owned.getUnreadCount()));

        ConversationReadResponse response = service.markAsRead(15L);

        assertThat(owned.getUnreadCount()).isZero();
        assertThat(response.getUnreadCount()).isZero();
        verify(conversationRepository).save(owned);
    }

    @Test
    void markAsReadThrowsNotFoundWhenConversationBelongsToAnotherBusiness() {
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(15L))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(conversationRepository, never()).save(any());
    }

}
