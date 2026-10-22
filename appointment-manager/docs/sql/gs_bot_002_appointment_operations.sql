-- GS-BOT-002 - Cancelación y reprogramación seguras por WhatsApp.
-- Agrega EXCLUSIVAMENTE columnas NULLABLE a "conversation_states" (estado de la operación
-- pendiente sobre una cita existente, separado del borrador de reserva nueva) y amplía la
-- restricción de valores de "stage" para las 4 etapas nuevas. No borra ni reescribe datos:
-- las filas existentes quedan sin operación pendiente (comportamiento actual).
--
-- Revisar manualmente antes de aplicar en producción (ver nota sobre ddl-auto=update en
-- docs/sql/mvp3_conversation_mode.sql). Es idempotente.

BEGIN;

ALTER TABLE conversation_states
    ADD COLUMN IF NOT EXISTS pending_operation VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pending_operation_appointment_id BIGINT,
    ADD COLUMN IF NOT EXISTS pending_operation_options VARCHAR(200),
    ADD COLUMN IF NOT EXISTS pending_operation_new_start_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS pending_operation_new_date DATE;

-- Hibernate 6 crea un CHECK sobre los valores del enum ConversationStage. Si existe, impediría
-- guardar las etapas nuevas (AWAITING_APPOINTMENT_CHOICE, AWAITING_CANCEL_CONFIRM,
-- AWAITING_RESCHEDULE_TIME, AWAITING_RESCHEDULE_CONFIRM; la más larga mide 28 caracteres y
-- "stage" admite 30). Se reemplaza por uno que incluye todas las etapas.
ALTER TABLE conversation_states
    DROP CONSTRAINT IF EXISTS conversation_states_stage_check;

ALTER TABLE conversation_states
    ADD CONSTRAINT conversation_states_stage_check CHECK (stage IN (
        'COLLECTING', 'AWAITING_CONFIRMATION', 'AWAITING_CUSTOMER_NAME',
        'AWAITING_APPOINTMENT_CHOICE', 'AWAITING_CANCEL_CONFIRM',
        'AWAITING_RESCHEDULE_TIME', 'AWAITING_RESCHEDULE_CONFIRM'));

COMMIT;

-- Nota: la selección de servicio por número (offered_service_ids) tiene su propio script,
-- docs/sql/mvp3_service_option_selection.sql.
