-- MVP 2 - Fase 1: historial de conversaciones de WhatsApp.
-- Crea EXCLUSIVAMENTE las tablas nuevas "conversations" y "conversation_messages".
-- No modifica ninguna tabla existente (customers, appointments, conversation_states,
-- whatsapp_inbound_events, etc.).
--
-- Revisar manualmente antes de aplicar en producción. No es destructivo: solo CREATE.
-- Con spring.jpa.hibernate.ddl-auto=update, Hibernate normalmente generaría este mismo esquema al
-- desplegar, pero dado el historial de columnas/constraints obsoletos en producción (ver docs del
-- proyecto), se entrega el DDL explícito para poder revisarlo y aplicarlo a mano.

BEGIN;

CREATE TABLE IF NOT EXISTS conversations (
    id                    BIGSERIAL PRIMARY KEY,
    business_id           BIGINT NOT NULL,
    customer_id           BIGINT NULL,
    sender_phone          VARCHAR(30) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_message_at       TIMESTAMP(6) WITH TIME ZONE NULL,
    last_message_preview  VARCHAR(300) NULL,
    unread_count          INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP(6) WITH TIME ZONE NULL,

    CONSTRAINT uk_conversations_business_sender UNIQUE (business_id, sender_phone),
    CONSTRAINT chk_conversations_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX IF NOT EXISTS idx_conversations_business_last_message
    ON conversations (business_id, last_message_at);

CREATE TABLE IF NOT EXISTS conversation_messages (
    id                   BIGSERIAL PRIMARY KEY,
    conversation_id      BIGINT NOT NULL,
    business_id          BIGINT NOT NULL,
    customer_id          BIGINT NULL,
    external_message_id  VARCHAR(100) NULL,
    direction            VARCHAR(10) NOT NULL,
    sender_type          VARCHAR(10) NOT NULL,
    message_type         VARCHAR(10) NOT NULL,
    content              TEXT NOT NULL,
    created_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITH TIME ZONE NULL,

    CONSTRAINT fk_conversation_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT chk_conversation_messages_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT chk_conversation_messages_sender_type CHECK (sender_type IN ('CUSTOMER', 'BOT', 'SYSTEM')),
    CONSTRAINT chk_conversation_messages_message_type CHECK (message_type IN ('TEXT'))
);

-- UNIQUE en Postgres no aplica entre múltiples NULL: cualquier cantidad de OUTBOUND con
-- external_message_id NULL conviven sin problema; un externalMessageId no nulo repetido sí falla.
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_messages_external_message_id
    ON conversation_messages (external_message_id)
    WHERE external_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_conv_messages_conversation_created
    ON conversation_messages (conversation_id, created_at);

CREATE INDEX IF NOT EXISTS idx_conv_messages_business_created
    ON conversation_messages (business_id, created_at);

COMMIT;

-- Nota de diseño: a diferencia de "appointments" (que sí usa FKs reales hacia customers/employees/
-- services vía @ManyToOne), "business_id" y "customer_id" acá se dejan como BIGINT simples, sin FK,
-- igual que ya hacen "conversation_states" y "whatsapp_inbound_events" -- entidades de la misma
-- naturaleza (tracking/historial de alto volumen, con customer_id nullable). Ver reporte de la fase.
