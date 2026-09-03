package com.victor.appointmentmanager.api.modules.conversations.service;

import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageDirection;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageSenderType;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageType;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationMessageRepository;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationMessageServiceTest {

    private static final Long BUSINESS_ID = 1L;
    private static final Long OTHER_BUSINESS_ID = 2L;
    private static final String SENDER_PHONE = "595981000000";

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Captor
    private ArgumentCaptor<Conversation> conversationCaptor;

    @Captor
    private ArgumentCaptor<ConversationMessage> messageCaptor;

    private ConversationMessageService service;
    private AtomicLong nextConversationId;

    @BeforeEach
    void setUp() {
        service = new ConversationMessageService(conversationRepository, conversationMessageRepository,
                customerRepository);
        nextConversationId = new AtomicLong(1000L);
        // Simula la semántica real de GenerationType.IDENTITY: el id solo se asigna cuando la
        // entidad se guarda por primera vez (id == null antes del save). Un stub que simplemente
        // devolviera el mismo objeto sin asignar id (como hacía la versión anterior de este test)
        // no distingue "conversation.getId() ya existía" de "nunca se persistió", y por eso no
        // detectó el bug real de producción (conversation_id NULL en el primer mensaje).
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            if (conversation.getId() == null) {
                conversation.setId(nextConversationId.getAndIncrement());
            }
            return conversation;
        });
        when(conversationMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------
    // Conversation: creación / reutilización / aislamiento por business
    // ------------------------------------------------------------------

    @Test
    void createsConversationOnFirstMessage() {
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");

        // La Conversation nueva se guarda dos veces: una para obtener el id (findOrCreateConversation)
        // ANTES de poder asociarse a un mensaje, y otra al final para persistir lastMessageAt/
        // preview/unreadCount. Ambas mutan la misma instancia.
        verify(conversationRepository, times(2)).save(conversationCaptor.capture());
        Conversation saved = conversationCaptor.getValue();
        assertThat(saved.getBusinessId()).isEqualTo(BUSINESS_ID);
        assertThat(saved.getSenderPhone()).isEqualTo(SENDER_PHONE);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getId()).isNotNull();
        // MVP 3 - Fase 1: toda Conversation nueva empieza en BOT; nadie la tomó todavía.
        assertThat(saved.getMode()).isEqualTo(ConversationMode.BOT);
    }

    /**
     * Regresión directa del fallo de producción: "null value in column conversation_id... violates
     * not-null constraint". La causa era que {@code findOrCreateConversation} devolvía una
     * Conversation recién construida (sin id, sin persistir) y {@code recordInbound}/
     * {@code recordOutbound} usaban ese id inexistente para armar el ConversationMessage. Este test
     * verifica explícitamente el orden correcto: la Conversation nueva se persiste (obtiene id)
     * ANTES de que el ConversationMessage se guarde, y que el mensaje queda con ese mismo id no nulo.
     */
    @Test
    void newConversationIsPersistedBeforeItsFirstMessageIsSaved() {
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");

        InOrder order = inOrder(conversationRepository, conversationMessageRepository);
        order.verify(conversationRepository).save(any(Conversation.class));
        order.verify(conversationMessageRepository).save(messageCaptor.capture());

        ConversationMessage savedMessage = messageCaptor.getValue();
        assertThat(savedMessage.getConversationId()).isNotNull();
    }

    @Test
    void reusesExistingConversationForSameBusinessAndPhone() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, null, 0);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");
        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.2", "Quiero un turno");

        verify(conversationMessageRepository, times(2)).save(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).allSatisfy(
                message -> assertThat(message.getConversationId()).isEqualTo(10L));
        // Nunca se crea una segunda Conversation: siempre se reutiliza la misma fila.
        verify(conversationRepository, times(2)).save(eq(existing));
    }

    @Test
    void samePhoneInTwoBusinessesProducesDifferentConversations() {
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());
        when(conversationRepository.findByBusinessIdAndSenderPhone(OTHER_BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(any(), eq(SENDER_PHONE)))
                .thenReturn(Optional.empty());

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");
        service.recordInbound(OTHER_BUSINESS_ID, SENDER_PHONE, "wamid.2", "Hola");

        // Cada Conversation nueva se guarda dos veces (creación + actualización de metadata).
        verify(conversationRepository, times(2)).save(argThatBusinessIs(BUSINESS_ID));
        verify(conversationRepository, times(2)).save(argThatBusinessIs(OTHER_BUSINESS_ID));
    }

    // ------------------------------------------------------------------
    // customerId nullable / asociación
    // ------------------------------------------------------------------

    @Test
    void customerIdIsNullWhenCustomerDoesNotExistYet() {
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");

        verify(conversationRepository, times(2)).save(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getCustomerId()).isNull();
        verify(conversationMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getCustomerId()).isNull();
    }

    @Test
    void customerIdIsAssociatedWhenCustomerAlreadyExists() {
        Customer customer = new Customer();
        customer.setId(77L);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(customer));

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");

        verify(conversationRepository, times(2)).save(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getCustomerId()).isEqualTo(77L);
        verify(conversationMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getCustomerId()).isEqualTo(77L);
    }

    @Test
    void customerIdOnceSetIsNeverLookedUpAgain() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, 77L, 0);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");

        verify(customerRepository, never()).findByBusinessIdAndPhoneAndActiveTrue(any(), any());
    }

    // ------------------------------------------------------------------
    // INBOUND
    // ------------------------------------------------------------------

    @Test
    void inboundMessageIsSavedWithExactTextAndCorrectFlags() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, null, 0);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.EXTERNAL", "Quiero un Corte Premium");

        verify(conversationMessageRepository).save(messageCaptor.capture());
        ConversationMessage saved = messageCaptor.getValue();
        assertThat(saved.getContent()).isEqualTo("Quiero un Corte Premium");
        assertThat(saved.getDirection()).isEqualTo(MessageDirection.INBOUND);
        assertThat(saved.getSenderType()).isEqualTo(MessageSenderType.CUSTOMER);
        assertThat(saved.getMessageType()).isEqualTo(MessageType.TEXT);
        assertThat(saved.getExternalMessageId()).isEqualTo("wamid.EXTERNAL");
        assertThat(saved.getBusinessId()).isEqualTo(BUSINESS_ID);
    }

    @Test
    void inboundUpdatesConversationMetadataAndIncrementsUnread() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, null, 3);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola de nuevo");

        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation saved = conversationCaptor.getValue();
        assertThat(saved.getLastMessageAt()).isNotNull();
        assertThat(saved.getLastMessagePreview()).isEqualTo("Hola de nuevo");
        assertThat(saved.getUnreadCount()).isEqualTo(4);
    }

    /**
     * MVP 3 - Fase 1: el takeover solo desactiva la respuesta automática (ver
     * WhatsAppWebhookServiceImplTest), nunca el historial. Con la conversación en HUMAN,
     * recordInbound debe seguir persistiendo el mensaje e incrementando unreadCount exactamente
     * igual que en BOT.
     */
    @Test
    void inboundStillIncrementsUnreadCountAndReturnsConversationWhenModeIsHuman() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, null, 0);
        existing.setMode(ConversationMode.HUMAN);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        Conversation result = service.recordInbound(BUSINESS_ID, SENDER_PHONE, "wamid.1", "Hola");

        assertThat(result.getMode()).isEqualTo(ConversationMode.HUMAN);
        assertThat(result.getUnreadCount()).isEqualTo(1);
        assertThat(result.getLastMessagePreview()).isEqualTo("Hola");
    }

    // ------------------------------------------------------------------
    // OUTBOUND
    // ------------------------------------------------------------------

    @Test
    void outboundMessageIsSavedWithFinalTextAndCorrectFlags() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, null, 1);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordOutbound(BUSINESS_ID, SENDER_PHONE, "wamid.OUT",
                "Juan Gómez está disponible mañana a las 10:00. ¿Confirmás?");

        verify(conversationMessageRepository).save(messageCaptor.capture());
        ConversationMessage saved = messageCaptor.getValue();
        assertThat(saved.getContent()).isEqualTo("Juan Gómez está disponible mañana a las 10:00. ¿Confirmás?");
        assertThat(saved.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(saved.getSenderType()).isEqualTo(MessageSenderType.BOT);
        assertThat(saved.getMessageType()).isEqualTo(MessageType.TEXT);
        assertThat(saved.getExternalMessageId()).isEqualTo("wamid.OUT");
    }

    @Test
    void outboundDoesNotIncrementUnreadCount() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, null, 2);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordOutbound(BUSINESS_ID, SENDER_PHONE, null, "Respuesta del bot");

        verify(conversationRepository).save(conversationCaptor.capture());
        Conversation saved = conversationCaptor.getValue();
        assertThat(saved.getUnreadCount()).isEqualTo(2);
        assertThat(saved.getLastMessageAt()).isNotNull();
        assertThat(saved.getLastMessagePreview()).isEqualTo("Respuesta del bot");
    }

    @Test
    void outboundAcceptsNullExternalMessageId() {
        Conversation existing = existingConversation(10L, BUSINESS_ID, SENDER_PHONE, null, 0);
        when(conversationRepository.findByBusinessIdAndSenderPhone(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.of(existing));
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(BUSINESS_ID, SENDER_PHONE))
                .thenReturn(Optional.empty());

        service.recordOutbound(BUSINESS_ID, SENDER_PHONE, null, "Respuesta sin id de Meta");

        verify(conversationMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getExternalMessageId()).isNull();
    }

    private Conversation existingConversation(Long id, Long businessId, String senderPhone, Long customerId,
                                               int unreadCount) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setBusinessId(businessId);
        conversation.setSenderPhone(senderPhone);
        conversation.setCustomerId(customerId);
        conversation.setUnreadCount(unreadCount);
        return conversation;
    }

    private Conversation argThatBusinessIs(Long businessId) {
        return org.mockito.ArgumentMatchers.argThat(
                conversation -> conversation != null && businessId.equals(conversation.getBusinessId()));
    }

}
