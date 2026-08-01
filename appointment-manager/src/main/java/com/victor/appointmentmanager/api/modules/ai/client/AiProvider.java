package com.victor.appointmentmanager.api.modules.ai.client;

public interface AiProvider {

    /**
     * Genera la respuesta cruda del modelo para un mensaje de usuario, dado un system prompt.
     */
    String generateResponse(String systemPrompt, String userMessage);

}
