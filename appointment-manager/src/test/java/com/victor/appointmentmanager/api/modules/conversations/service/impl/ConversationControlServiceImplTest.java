package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationModeResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.exception.ConversationNotFoundException;
import com.victor.appointmentmanager.api.modules.conversations.mapper.ConversationMapper;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MVP 3 - Fase 1: control BOT/HUMAN. Cubre los escenarios obligatorios de takeover/release
 * (idempotencia y aislamiento multiempresa) a nivel unitario; el flujo HTTP + JWT real se cubre en
 * {@code ConversationControlControllerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ConversationControlServiceImplTest {

    private static final Long BUSINESS_ID = 1L;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ConversationMapper conversationMapper;

    @InjectMocks
    private ConversationControlServiceImpl service;

    @Captor
    private ArgumentCaptor<Conversation> conversationCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(BUSINESS_ID);
    }

    private Conversation conversation(Long id, ConversationMode mode) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setBusinessId(BUSINESS_ID);
        conversation.setSenderPhone("595981000000");
        conversation.setMode(mode);
        return conversation;
    }

    // ------------------------------------------------------------------
    // takeover
    // ------------------------------------------------------------------

    @Test
    void takeoverChangesBotToHuman() {
        Conversation existing = conversation(15L, ConversationMode.BOT);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toModeResponse(existing)).thenAnswer(invocation ->
                new ConversationModeResponse(existing.getId(), existing.getMode()));

        ConversationModeResponse response = service.takeover(15L);

        assertThat(response.getMode()).isEqualTo(ConversationMode.HUMAN);
        verify(conversationRepository).save(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getMode()).isEqualTo(ConversationMode.HUMAN);
    }

    @Test
    void takeoverOnAlreadyHumanConversationIsIdempotent() {
        Conversation existing = conversation(15L, ConversationMode.HUMAN);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toModeResponse(existing)).thenAnswer(invocation ->
                new ConversationModeResponse(existing.getId(), existing.getMode()));

        ConversationModeResponse response = service.takeover(15L);

        assertThat(response.getMode()).isEqualTo(ConversationMode.HUMAN);
        verify(conversationRepository).save(existing);
    }

    @Test
    void takeoverOnAnotherBusinessConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.takeover(15L)).isInstanceOf(ConversationNotFoundException.class);

        verify(conversationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void takeoverOnNonexistentConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(999L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.takeover(999L)).isInstanceOf(ConversationNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // release
    // ------------------------------------------------------------------

    @Test
    void releaseChangesHumanToBot() {
        Conversation existing = conversation(15L, ConversationMode.HUMAN);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toModeResponse(existing)).thenAnswer(invocation ->
                new ConversationModeResponse(existing.getId(), existing.getMode()));

        ConversationModeResponse response = service.release(15L);

        assertThat(response.getMode()).isEqualTo(ConversationMode.BOT);
        verify(conversationRepository).save(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getMode()).isEqualTo(ConversationMode.BOT);
    }

    @Test
    void releaseOnAlreadyBotConversationIsIdempotent() {
        Conversation existing = conversation(15L, ConversationMode.BOT);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toModeResponse(existing)).thenAnswer(invocation ->
                new ConversationModeResponse(existing.getId(), existing.getMode()));

        ConversationModeResponse response = service.release(15L);

        assertThat(response.getMode()).isEqualTo(ConversationMode.BOT);
        verify(conversationRepository).save(existing);
    }

    @Test
    void releaseOnAnotherBusinessConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release(15L)).isInstanceOf(ConversationNotFoundException.class);

        verify(conversationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void releaseOnNonexistentConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(999L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release(999L)).isInstanceOf(ConversationNotFoundException.class);
    }

}
