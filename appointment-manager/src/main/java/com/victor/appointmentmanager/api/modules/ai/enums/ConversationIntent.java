package com.victor.appointmentmanager.api.modules.ai.enums;

public enum ConversationIntent {
    GREETING,
    BOOK_APPOINTMENT,
    RESCHEDULE_APPOINTMENT,
    CANCEL_APPOINTMENT,
    CHECK_AVAILABILITY,
    LIST_SERVICES,
    /** Solo la reporta el backend (nunca la IA) al clasificar determinísticamente una confirmación. */
    CONFIRM_APPOINTMENT,
    /** Solo la reporta el backend (nunca la IA) al clasificar determinísticamente un rechazo. */
    REJECT_APPOINTMENT,
    UNKNOWN
}
