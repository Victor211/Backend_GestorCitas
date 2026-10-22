package com.victor.appointmentmanager.api.modules.conversations.service.impl;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationArchiveResponse;
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

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MVP 3 - Fase 3: archivado/restauración. Cubre los escenarios obligatorios (idempotencia sin
 * pisar el archivedAt original, aislamiento multiempresa, y que ninguno de los dos métodos toca
 * mode/status/unreadCount) a nivel unitario; el flujo HTTP + JWT real se cubre en
 * {@code ConversationArchiveControllerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ConversationArchiveServiceImplTest {

    private static final Long BUSINESS_ID = 1L;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ConversationMapper conversationMapper;

    @InjectMocks
    private ConversationArchiveServiceImpl service;

    @Captor
    private ArgumentCaptor<Conversation> conversationCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(BUSINESS_ID);
    }

    private Conversation conversation(Long id, Instant archivedAt) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setBusinessId(BUSINESS_ID);
        conversation.setSenderPhone("595981000000");
        conversation.setMode(ConversationMode.BOT);
        conversation.setUnreadCount(2);
        conversation.setArchivedAt(archivedAt);
        return conversation;
    }

    // ------------------------------------------------------------------
    // archive
    // ------------------------------------------------------------------

    @Test
    void archiveSetsArchivedAtOnActiveConversation() {
        Conversation existing = conversation(15L, null);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toArchiveResponse(existing)).thenAnswer(invocation ->
                new ConversationArchiveResponse(existing.getId(), existing.getArchivedAt()));

        ConversationArchiveResponse response = service.archive(15L);

        assertThat(response.getArchivedAt()).isNotNull();
        verify(conversationRepository).save(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getArchivedAt()).isNotNull();
    }

    @Test
    void archiveDoesNotChangeModeStatusOrUnreadCount() {
        Conversation existing = conversation(15L, null);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toArchiveResponse(existing)).thenReturn(new ConversationArchiveResponse());

        service.archive(15L);

        assertThat(existing.getMode()).isEqualTo(ConversationMode.BOT);
        assertThat(existing.getUnreadCount()).isEqualTo(2);
    }

    @Test
    void archiveOnAlreadyArchivedConversationIsIdempotentAndKeepsOriginalTimestamp() {
        Instant originalTimestamp = Instant.parse("2026-09-01T00:00:00Z");
        Conversation existing = conversation(15L, originalTimestamp);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toArchiveResponse(existing)).thenAnswer(invocation ->
                new ConversationArchiveResponse(existing.getId(), existing.getArchivedAt()));

        ConversationArchiveResponse response = service.archive(15L);

        assertThat(response.getArchivedAt()).isEqualTo(originalTimestamp);
        // No debe modificar innecesariamente su estado: ni siquiera se llama a save().
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void archiveOnAnotherBusinessConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(15L)).isInstanceOf(ConversationNotFoundException.class);

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void archiveOnNonexistentConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(999L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(999L)).isInstanceOf(ConversationNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // restore
    // ------------------------------------------------------------------

    @Test
    void restoreClearsArchivedAt() {
        Conversation existing = conversation(15L, Instant.parse("2026-09-01T00:00:00Z"));
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toArchiveResponse(existing)).thenAnswer(invocation ->
                new ConversationArchiveResponse(existing.getId(), existing.getArchivedAt()));

        ConversationArchiveResponse response = service.restore(15L);

        assertThat(response.getArchivedAt()).isNull();
        verify(conversationRepository).save(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getArchivedAt()).isNull();
    }

    @Test
    void restoreDoesNotChangeModeStatusOrUnreadCount() {
        Conversation existing = conversation(15L, Instant.parse("2026-09-01T00:00:00Z"));
        existing.setMode(ConversationMode.HUMAN);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toArchiveResponse(existing)).thenReturn(new ConversationArchiveResponse());

        service.restore(15L);

        assertThat(existing.getMode()).isEqualTo(ConversationMode.HUMAN);
        assertThat(existing.getUnreadCount()).isEqualTo(2);
    }

    @Test
    void restoreOnAlreadyActiveConversationIsIdempotent() {
        Conversation existing = conversation(15L, null);
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.of(existing));
        when(conversationMapper.toArchiveResponse(existing)).thenAnswer(invocation ->
                new ConversationArchiveResponse(existing.getId(), existing.getArchivedAt()));

        ConversationArchiveResponse response = service.restore(15L);

        assertThat(response.getArchivedAt()).isNull();
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void restoreOnAnotherBusinessConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(15L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(15L)).isInstanceOf(ConversationNotFoundException.class);

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void restoreOnNonexistentConversationThrowsNotFound() {
        when(conversationRepository.findByIdAndBusinessId(999L, BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(999L)).isInstanceOf(ConversationNotFoundException.class);
    }

}
