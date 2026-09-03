-- MVP 3 - Fase 1: control BOT/HUMAN de conversaciones.
-- Agrega EXCLUSIVAMENTE la columna "mode" a "conversations". No modifica ninguna otra tabla ni
-- borra datos.
--
-- Revisar manualmente antes de aplicar en producción. No es destructivo.
-- Con spring.jpa.hibernate.ddl-auto=update, Hibernate normalmente agregaría esta columna al
-- desplegar, pero dado el historial de columnas/constraints obsoletos en producción (ver
-- docs/sql/mvp2_conversation_history.sql), se entrega el DDL explícito para poder revisarlo y
-- aplicarlo a mano.

BEGIN;

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS mode VARCHAR(10) NOT NULL DEFAULT 'BOT';

-- Toda conversación existente antes de esta fase debe quedar en BOT (comportamiento actual,
-- ningún operador la tomó todavía). El DEFAULT del ALTER ya cubre esto para Postgres (aplica el
-- default a las filas existentes al agregar la columna), pero se deja explícito por claridad y
-- para no depender de ese detalle de implementación.
UPDATE conversations SET mode = 'BOT' WHERE mode IS NULL;

ALTER TABLE conversations
    ALTER COLUMN mode SET NOT NULL;

ALTER TABLE conversations
    DROP CONSTRAINT IF EXISTS chk_conversations_mode;

ALTER TABLE conversations
    ADD CONSTRAINT chk_conversations_mode CHECK (mode IN ('BOT', 'HUMAN'));

COMMIT;

-- Nota: a diferencia de "status" (que ya tenía su propio chk_conversations_status), "mode" es un
-- concepto nuevo e independiente (ver Conversation.java) - no reutiliza ni reemplaza ninguna
-- columna ni constraint existente.
