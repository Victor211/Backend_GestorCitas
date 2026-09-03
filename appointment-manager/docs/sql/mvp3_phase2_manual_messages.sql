-- MVP 3 - Fase 2: envío manual de mensajes hacia WhatsApp.
-- No agrega tablas ni columnas nuevas: reutiliza "conversation_messages" tal cual quedó en el
-- MVP 2 (direction OUTBOUND, messageType TEXT). El único cambio de esquema es ampliar el CHECK
-- constraint de "sender_type" para admitir el nuevo valor 'HUMAN' (mensaje escrito manualmente por
-- un operador, distinto de 'BOT').
--
-- Revisar manualmente antes de aplicar en producción. No es destructivo: no borra filas ni columnas.
-- Con spring.jpa.hibernate.ddl-auto=update, Hibernate no elimina/recrea constraints existentes de
-- forma confiable (ver docs/sql/mvp2_conversation_history.sql), así que se entrega el DDL explícito.

BEGIN;

ALTER TABLE conversation_messages
    DROP CONSTRAINT IF EXISTS chk_conversation_messages_sender_type;

ALTER TABLE conversation_messages
    ADD CONSTRAINT chk_conversation_messages_sender_type
        CHECK (sender_type IN ('CUSTOMER', 'BOT', 'HUMAN', 'SYSTEM'));

COMMIT;

-- Nota: "sender_type varchar(10)" ya soporta 'HUMAN' (5 caracteres) sin necesidad de ampliar la
-- columna. Ninguna fila existente se ve afectada: 'HUMAN' es un valor nuevo, no un reemplazo.
