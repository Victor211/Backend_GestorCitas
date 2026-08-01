package com.victor.appointmentmanager.api.modules.ai.client;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Toda la comunicación con OpenAI queda encapsulada en esta clase: nadie más en el
 * proyecto conoce el formato de request/response de la API de OpenAI.
 */
@Component
public class OpenAiProvider implements AiProvider {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;

    private final RestClient restClient;
    private final String model;

    public OpenAiProvider(@Value("${app.openai.api-key}") String apiKey,
                           @Value("${app.openai.model}") String model,
                           @Value("${app.openai.base-url}") String baseUrl) {
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    @Override
    public String generateResponse(String systemPrompt, String userMessage) {
        try {
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .body(new OpenAiChatRequest(model, List.of(
                            new OpenAiChatMessage("system", systemPrompt),
                            new OpenAiChatMessage("user", userMessage))))
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                throw new BusinessException("El asistente no pudo generar una respuesta en este momento");
            }

            return response.choices().get(0).message().content();
        } catch (RestClientException ex) {
            throw new BusinessException("El asistente no pudo generar una respuesta en este momento");
        }
    }

    private record OpenAiChatRequest(String model, List<OpenAiChatMessage> messages) {
    }

    private record OpenAiChatMessage(String role, String content) {
    }

    private record OpenAiChatResponse(List<OpenAiChoice> choices) {
    }

    private record OpenAiChoice(OpenAiChatResponseMessage message) {
    }

    private record OpenAiChatResponseMessage(String content) {
    }

}
