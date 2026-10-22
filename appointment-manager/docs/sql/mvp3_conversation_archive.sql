-- MVP 3 - Fase 3: archivado/restauración de conversaciones.
-- Agrega EXCLUSIVAMENTE la columna "archived_at" a "conversations". No modifica ninguna otra
-- tabla, no borra ni actualiza datos existentes (a diferencia de mvp3_conversation_mode.sql, acá
-- no hace falta ningún UPDATE: la columna es NULLABLE y NULL es justamente el valor correcto para
-- toda conversación existente - significa "activa, nunca archivada").
--
-- Revisar manualmente antes de aplicar en producción. No es destructivo.
-- Con spring.jpa.hibernate.ddl-auto=update, Hibernate normalmente agregaría esta columna al
-- desplegar, pero (ver docs/sql/mvp2_conversation_history.sql) se entrega el DDL explícito para
-- poder revisarlo y aplicarlo a mano.

BEGIN;

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ NULL;

COMMIT;

-- Nota: "archived_at" es un concepto independiente de "status" y de "mode" (ver Conversation.java)
-- - no reutiliza ni reemplaza ninguna columna ni constraint existente. NULL = activa; un timestamp
-- = archivada. No lleva CHECK constraint (a diferencia de "mode"): cualquier timestamp válido es un
-- valor aceptable.
