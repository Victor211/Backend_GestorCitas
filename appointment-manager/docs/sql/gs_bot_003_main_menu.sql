-- GS-BOT-003 - Menú principal de WhatsApp.
-- Agrega EXCLUSIVAMENTE la columna "menu_pending" a "conversation_states": indica que el menú
-- principal fue lo último mostrado al cliente (habilita interpretar "1".."4" como opciones).
-- NOT NULL con DEFAULT false: las filas existentes quedan sin menú pendiente (comportamiento
-- actual). Idempotente, no modifica datos existentes.
--
-- Orden de despliegue: (1) aplicar este script, (2) desplegar la aplicación. Es compatible hacia
-- atrás: la versión anterior ignora la columna. Scripts previos requeridos:
-- mvp3_service_option_selection.sql y gs_bot_002_appointment_operations.sql.

BEGIN;

ALTER TABLE conversation_states
    ADD COLUMN IF NOT EXISTS menu_pending BOOLEAN NOT NULL DEFAULT FALSE;

COMMIT;
