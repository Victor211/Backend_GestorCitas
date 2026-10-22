-- MVP 3 - Selección de servicio por número en WhatsApp.
-- Agrega EXCLUSIVAMENTE la columna "offered_service_ids" a "conversation_states": los ids reales
-- de los servicios en el orden numerado que se le mostró al cliente (ej. '12,7,9'). Es necesaria
-- porque cada mensaje es una request independiente y el catálogo puede cambiar entre el listado
-- y la respuesta del cliente. Nullable, sin default: las filas existentes quedan sin selección
-- pendiente (comportamiento actual). No es destructivo.
--
-- Revisar manualmente antes de aplicar en producción (ver nota en mvp3_conversation_mode.sql
-- sobre spring.jpa.hibernate.ddl-auto=update).

BEGIN;

ALTER TABLE conversation_states
    ADD COLUMN IF NOT EXISTS offered_service_ids VARCHAR(1000);

COMMIT;
