package com.victor.appointmentmanager.api.modules.conversations.mapper;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationModeResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationReadResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationSummaryResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationMode;
import com.victor.appointmentmanager.api.modules.conversations.enums.ConversationStatus;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageDirection;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageSenderType;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageType;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMapperTest {

    private ConversationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ConversationMapperImpl();
    }

    private Conversation buildConversation() {
        Conversation conversation = new Conversation();
        conversation.setId(15L);
        conversation.setBusinessId(1L);
        conversation.setCustomerId(8L);
        conversation.setSenderPhone("595981123456");
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setLastMessageAt(Instant.parse("2026-09-01T23:55:45Z"));
        conversation.setLastMessagePreview("Perfecto, tu turno quedó reservado...");
        conversation.setUnreadCount(3);
        return conversation;
    }

    @Test
    void mapsCustomerNameWhenCustomerExists() {
        Customer customer = new Customer();
        customer.setId(8L);
        customer.setFirstName("Juan");
        customer.setLastName("Pérez");

        ConversationSummaryResponse response = mapper.toSummaryResponse(buildConversation(), customer);

        assertThat(response.getId()).isEqualTo(15L);
        assertThat(response.getCustomerId()).isEqualTo(8L);
        assertThat(response.getCustomerName()).isEqualTo("Juan Pérez");
        assertThat(response.getSenderPhone()).isEqualTo("595981123456");
        assertThat(response.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(response.getMode()).isEqualTo(ConversationMode.BOT);
        assertThat(response.getLastMessagePreview()).isEqualTo("Perfecto, tu turno quedó reservado...");
        assertThat(response.getUnreadCount()).isEqualTo(3);
    }

    @Test
    void customerNameIsNullWhenCustomerIsNull() {
        ConversationSummaryResponse response = mapper.toSummaryResponse(buildConversation(), null);

        assertThat(response.getCustomerName()).isNull();
        // customerId viene de la Conversation, no del Customer: puede quedar poblado aunque el
        // Customer resuelto sea null si la asociación es inconsistente, pero en el camino real
        // (ver ConversationQueryServiceImpl) siempre coinciden.
        assertThat(response.getCustomerId()).isEqualTo(8L);
    }

    @Test
    void customerNameHandlesMissingLastName() {
        Customer customer = new Customer();
        customer.setId(8L);
        customer.setFirstName("Carlos");
        customer.setLastName(null);

        ConversationSummaryResponse response = mapper.toSummaryResponse(buildConversation(), customer);

        assertThat(response.getCustomerName()).isEqualTo("Carlos");
    }

    @Test
    void mapsMessageFieldsDirectly() {
        ConversationMessage message = new ConversationMessage();
        message.setId(120L);
        message.setConversationId(15L);
        message.setBusinessId(1L);
        message.setCustomerId(8L);
        message.setExternalMessageId("wamid.ABC");
        message.setDirection(MessageDirection.INBOUND);
        message.setSenderType(MessageSenderType.CUSTOMER);
        message.setMessageType(MessageType.TEXT);
        message.setContent("Quiero reservar un turno");

        ConversationMessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.getId()).isEqualTo(120L);
        assertThat(response.getConversationId()).isEqualTo(15L);
        assertThat(response.getDirection()).isEqualTo(MessageDirection.INBOUND);
        assertThat(response.getSenderType()).isEqualTo(MessageSenderType.CUSTOMER);
        assertThat(response.getMessageType()).isEqualTo(MessageType.TEXT);
        assertThat(response.getContent()).isEqualTo("Quiero reservar un turno");
    }

    @Test
    void mapsReadResponse() {
        Conversation conversation = buildConversation();
        conversation.setUnreadCount(0);

        ConversationReadResponse response = mapper.toReadResponse(conversation);

        assertThat(response.getId()).isEqualTo(15L);
        assertThat(response.getUnreadCount()).isZero();
    }

    @Test
    void mapsModeResponse() {
        Conversation conversation = buildConversation();
        conversation.setMode(ConversationMode.HUMAN);

        ConversationModeResponse response = mapper.toModeResponse(conversation);

        assertThat(response.getId()).isEqualTo(15L);
        assertThat(response.getMode()).isEqualTo(ConversationMode.HUMAN);
    }

}
