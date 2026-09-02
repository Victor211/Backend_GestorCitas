package com.victor.appointmentmanager.api.modules.conversations.service;

import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.conversations.enums.MessageDirection;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationMessageRepository;
import com.victor.appointmentmanager.api.modules.conversations.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reproduce contra una base de datos real (H2, esquema generado por Hibernate igual que en
 * Postgres) el fallo de producción: "null value in column conversation_id ... violates not-null
 * constraint". Un test puramente Mockito no puede detectar este tipo de bug porque un mock de
 * {@code save()} no reproduce la semántica de {@code GenerationType.IDENTITY} (el id solo existe
 * después del INSERT real) ni las restricciones NOT NULL/FK de la tabla. Este test sí ejercita el
 * flujo completo contra JPA/H2.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-dummy-secret-never-used-in-production-0123456789",
        "app.jwt.expiration=3600000",
        "app.openai.api-key=test-key",
        "app.whatsapp.access-token=test-access-token",
        "app.whatsapp.verify-token=test-verify-token",
        "app.whatsapp.app-secret=test-app-secret",
        "app.whatsapp.graph-api-version=v21.0",
        "app.whatsapp.base-url=https://graph.example.com"
})
class ConversationMessagePersistenceIntegrationTest {

    private static final Long BUSINESS_ID = 1L;

    @Autowired
    private ConversationMessageService conversationMessageService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Test
    void firstInboundMessageIsPersistedWithoutViolatingConversationIdNotNullConstraint() {
        String senderPhone = uniquePhone();

        assertThatCode(() -> conversationMessageService.recordInbound(
                BUSINESS_ID, senderPhone, "wamid.FIRST." + senderPhone, "Hola"))
                .doesNotThrowAnyException();

        Optional<Conversation> conversation = conversationRepository
                .findByBusinessIdAndSenderPhone(BUSINESS_ID, senderPhone);
        assertThat(conversation).isPresent();
        assertThat(conversation.get().getId()).isNotNull();

        List<ConversationMessage> messages = conversationMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.get().getId());
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getConversationId()).isEqualTo(conversation.get().getId());
        assertThat(messages.get(0).getContent()).isEqualTo("Hola");
        assertThat(messages.get(0).getDirection()).isEqualTo(MessageDirection.INBOUND);
    }

    @Test
    void inboundAndOutboundOfSameConversationShareTheSameConversationId() {
        String senderPhone = uniquePhone();

        conversationMessageService.recordInbound(BUSINESS_ID, senderPhone, "wamid.IN1." + senderPhone, "Hola");
        conversationMessageService.recordOutbound(BUSINESS_ID, senderPhone, "wamid.OUT1." + senderPhone,
                "¡Hola! ¿En qué puedo ayudarte?");
        conversationMessageService.recordInbound(BUSINESS_ID, senderPhone, "wamid.IN2." + senderPhone,
                "Quiero un turno");
        conversationMessageService.recordOutbound(BUSINESS_ID, senderPhone, "wamid.OUT2." + senderPhone,
                "¿Para cuándo te gustaría el turno?");

        Conversation conversation = conversationRepository
                .findByBusinessIdAndSenderPhone(BUSINESS_ID, senderPhone)
                .orElseThrow();

        List<ConversationMessage> messages = conversationMessageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        assertThat(messages).hasSize(4);
        assertThat(messages).allSatisfy(
                message -> assertThat(message.getConversationId()).isEqualTo(conversation.getId()));
    }

    @Test
    void secondMessageReusesTheSamePersistedConversation() {
        String senderPhone = uniquePhone();

        conversationMessageService.recordInbound(BUSINESS_ID, senderPhone, "wamid.1." + senderPhone, "Hola");
        Long firstConversationId = conversationRepository
                .findByBusinessIdAndSenderPhone(BUSINESS_ID, senderPhone)
                .orElseThrow()
                .getId();

        conversationMessageService.recordInbound(BUSINESS_ID, senderPhone, "wamid.2." + senderPhone,
                "Quiero un turno");

        assertThat(conversationRepository.findAll()).filteredOn(
                c -> BUSINESS_ID.equals(c.getBusinessId()) && senderPhone.equals(c.getSenderPhone())
        ).hasSize(1);

        Long secondConversationId = conversationRepository
                .findByBusinessIdAndSenderPhone(BUSINESS_ID, senderPhone)
                .orElseThrow()
                .getId();
        assertThat(secondConversationId).isEqualTo(firstConversationId);
    }

    /**
     * Cada test necesita su propio {@code senderPhone}: {@code @SpringBootTest} reutiliza el mismo
     * contexto (y la misma base H2) entre los métodos de esta clase, así que reusar un teléfono fijo
     * entre tests acumularía mensajes de ejecuciones anteriores en la misma Conversation.
     */
    private String uniquePhone() {
        return "5959" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);
    }

}
