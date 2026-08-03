package com.victor.appointmentmanager.api.modules.ai.client;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Toda la comunicación con OpenAI queda encapsulada en esta clase: nadie más en el
 * proyecto conoce el formato de request/response de la API de OpenAI.
 */
@Slf4j
@Component
public class OpenAiProvider implements AiProvider {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/chat/completions";
    private static final String GENERIC_FAILURE_MESSAGE =
            "El asistente no pudo generar una respuesta en este momento";
    private static final int MAX_LOGGED_BODY_LENGTH = 500;
    private static final String REDACTED_SECRET = "***REDACTED***";

    /**
     * Cubre API keys de OpenAI (sk-..., sk-proj-...) y, en general, cualquier token tipo
     * "Bearer xxxxx" que pudiera aparecer eco en un cuerpo de error.
     */
    private static final Pattern SECRET_PATTERN =
            Pattern.compile("sk-[A-Za-z0-9_-]{4,}|Bearer\\s+[A-Za-z0-9._-]{4,}", Pattern.CASE_INSENSITIVE);

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
                    .uri(CHAT_COMPLETIONS_ENDPOINT)
                    .body(new OpenAiChatRequest(model, List.of(
                            new OpenAiChatMessage("system", systemPrompt),
                            new OpenAiChatMessage("user", userMessage))))
                    .retrieve()
                    .body(OpenAiChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()
                    || response.choices().get(0).message() == null) {
                log.error("OpenAI devolvió una respuesta vacía o con formato inesperado. endpoint={}, model={}",
                        CHAT_COMPLETIONS_ENDPOINT, model);
                throw new BusinessException(GENERIC_FAILURE_MESSAGE);
            }

            return response.choices().get(0).message().content();
        } catch (HttpClientErrorException ex) {
            log.error("OpenAI devolvió un error HTTP 4xx. status={}, endpoint={}, model={}, exceptionType={}, "
                            + "exceptionMessage={}, responseBody={}",
                    ex.getStatusCode().value(), CHAT_COMPLETIONS_ENDPOINT, model, ex.getClass().getSimpleName(),
                    sanitize(ex.getMessage()), sanitize(ex.getResponseBodyAsString()));
            throw new BusinessException(GENERIC_FAILURE_MESSAGE);
        } catch (HttpServerErrorException ex) {
            log.error("OpenAI devolvió un error HTTP 5xx. status={}, endpoint={}, model={}, exceptionType={}, "
                            + "exceptionMessage={}, responseBody={}",
                    ex.getStatusCode().value(), CHAT_COMPLETIONS_ENDPOINT, model, ex.getClass().getSimpleName(),
                    sanitize(ex.getMessage()), sanitize(ex.getResponseBodyAsString()));
            throw new BusinessException(GENERIC_FAILURE_MESSAGE);
        } catch (RestClientResponseException ex) {
            log.error("OpenAI devolvió un error HTTP no clasificado. status={}, endpoint={}, model={}, "
                            + "exceptionType={}, exceptionMessage={}, responseBody={}",
                    ex.getStatusCode().value(), CHAT_COMPLETIONS_ENDPOINT, model, ex.getClass().getSimpleName(),
                    sanitize(ex.getMessage()), sanitize(ex.getResponseBodyAsString()));
            throw new BusinessException(GENERIC_FAILURE_MESSAGE);
        } catch (ResourceAccessException ex) {
            if (ex.getCause() instanceof SocketTimeoutException) {
                log.error("Timeout al invocar a OpenAI. endpoint={}, model={}, exceptionType={}, "
                                + "exceptionMessage={}",
                        CHAT_COMPLETIONS_ENDPOINT, model, ex.getClass().getSimpleName(), sanitize(ex.getMessage()));
            } else {
                log.error("Error de conexión al invocar a OpenAI. endpoint={}, model={}, exceptionType={}, "
                                + "exceptionMessage={}",
                        CHAT_COMPLETIONS_ENDPOINT, model, ex.getClass().getSimpleName(), sanitize(ex.getMessage()));
            }
            throw new BusinessException(GENERIC_FAILURE_MESSAGE);
        } catch (HttpMessageNotReadableException ex) {
            log.error("No se pudo deserializar la respuesta de OpenAI. endpoint={}, model={}, exceptionType={}, "
                            + "exceptionMessage={}",
                    CHAT_COMPLETIONS_ENDPOINT, model, ex.getClass().getSimpleName(), sanitize(ex.getMessage()));
            throw new BusinessException(GENERIC_FAILURE_MESSAGE);
        } catch (RestClientException ex) {
            log.error("Error inesperado al invocar a OpenAI. endpoint={}, model={}, exceptionType={}, "
                            + "exceptionMessage={}",
                    CHAT_COMPLETIONS_ENDPOINT, model, ex.getClass().getSimpleName(), sanitize(ex.getMessage()));
            throw new BusinessException(GENERIC_FAILURE_MESSAGE);
        }
    }

    /**
     * Redacta cualquier valor con forma de API key/token y trunca el resultado, para poder
     * loguear el cuerpo de error de OpenAI sin arriesgar la exposición de secretos: OpenAI
     * a veces incluye eco de la key recibida (parcial o completa) en el mensaje de error 401.
     */
    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "(vacío)";
        }
        String redacted = SECRET_PATTERN.matcher(value).replaceAll(REDACTED_SECRET);
        return redacted.length() > MAX_LOGGED_BODY_LENGTH
                ? redacted.substring(0, MAX_LOGGED_BODY_LENGTH) + "...(truncado)"
                : redacted;
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
