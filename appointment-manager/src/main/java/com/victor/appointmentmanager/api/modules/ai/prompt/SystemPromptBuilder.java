package com.victor.appointmentmanager.api.modules.ai.prompt;

import org.springframework.stereotype.Component;

/**
 * Única clase responsable de construir el System Prompt. Ningún otro lugar del código
 * debe escribir texto de prompt directamente.
 */
@Component
public class SystemPromptBuilder {

    private static final String BASE_INSTRUCTIONS = """
            Sos un asistente virtual de reservas para un negocio que gestiona citas (turnos).

            Reglas que debés seguir siempre:
            - Nunca inventes disponibilidad, horarios, servicios ni precios que no te hayan sido proporcionados explícitamente a continuación.
            - Utilizá únicamente la información de contexto que se te entregue en este mensaje.
            - Respondé siempre en español.
            - Sé amable, profesional y conciso.

            Formato de respuesta obligatorio, exactamente estas líneas y en este orden:
            INTENT: <una de estas opciones: GREETING, BOOK_APPOINTMENT, RESCHEDULE_APPOINTMENT, CANCEL_APPOINTMENT, CHECK_AVAILABILITY, UNKNOWN>
            CONFIDENCE: <número entre 0.0 y 1.0 que indique qué tan seguro estás de la intención detectada>
            SERVICE_NAME: <nombre del servicio que el usuario mencionó, exactamente como aparece en la información disponible, o NONE si no lo mencionó>
            START_AT: <fecha y hora que el usuario pidió, en formato ISO-8601 UTC (ejemplo: 2026-08-10T14:00:00Z), o NONE si no fue clara>
            APPOINTMENT_ID: <número de cita si el usuario lo mencionó explícitamente, o NONE>
            REPLY: <tu respuesta en lenguaje natural para el usuario, puede ocupar varias líneas>
            """;

    public String build(String businessContext) {
        if (businessContext == null || businessContext.isBlank()) {
            return BASE_INSTRUCTIONS;
        }
        return BASE_INSTRUCTIONS + "\nInformación disponible sobre el negocio:\n" + businessContext;
    }

}
