package com.victor.appointmentmanager.api.modules.ai.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import com.victor.appointmentmanager.api.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que OpenAiProvider registre suficiente información de diagnóstico ante errores
 * de OpenAI (401, 429, 500) sin exponer la API key ni el prompt del usuario, y que hacia el
 * llamador siempre se propague el mismo BusinessException con mensaje genérico. Usa un
 * com.sun.net.httpserver.HttpServer embebido (JDK, sin dependencias nuevas) para simular
 * respuestas de OpenAI, y un ListAppender de Logback para inspeccionar lo efectivamente logueado.
 */
class OpenAiProviderTest {

    private static final String FAKE_API_KEY = "sk-test-SECRET1234567890abcdef";
    private static final String GENERIC_MESSAGE = "El asistente no pudo generar una respuesta en este momento";

    private HttpServer server;
    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();

        logger = (Logger) LoggerFactory.getLogger(OpenAiProvider.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        server.stop(0);
    }

    private OpenAiProvider buildProvider() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        return new OpenAiProvider(FAKE_API_KEY, "gpt-4o-mini", baseUrl);
    }

    private void stubChatCompletionsResponse(int status, String body) {
        server.createContext("/chat/completions", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
    }

    private List<String> loggedMessages() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertNoSecretsLogged() {
        for (String message : loggedMessages()) {
            assertThat(message).doesNotContain(FAKE_API_KEY);
            assertThat(message).doesNotContain("Bearer " + FAKE_API_KEY);
        }
    }

    @Test
    void capturesHttp401ErrorLogsDiagnosticsAndThrowsGenericMessage() {
        stubChatCompletionsResponse(401,
                "{\"error\":{\"message\":\"Incorrect API key provided: " + FAKE_API_KEY + "\"}}");
        OpenAiProvider provider = buildProvider();

        assertThatThrownBy(() -> provider.generateResponse("system prompt", "hola"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GENERIC_MESSAGE);

        List<String> messages = loggedMessages();
        assertThat(messages).anyMatch(msg -> msg.contains("401") && msg.contains("4xx")
                && msg.contains("/chat/completions") && msg.contains("gpt-4o-mini")
                && msg.contains("Unauthorized"));
        assertNoSecretsLogged();
    }

    @Test
    void capturesHttp429ErrorLogsDiagnosticsAndThrowsGenericMessage() {
        stubChatCompletionsResponse(429,
                "{\"error\":{\"message\":\"Rate limit exceeded for key " + FAKE_API_KEY + "\"}}");
        OpenAiProvider provider = buildProvider();

        assertThatThrownBy(() -> provider.generateResponse("system prompt", "hola"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GENERIC_MESSAGE);

        List<String> messages = loggedMessages();
        assertThat(messages).anyMatch(msg -> msg.contains("429") && msg.contains("4xx")
                && msg.contains("TooManyRequests") && msg.contains("Rate limit exceeded"));
        assertNoSecretsLogged();
    }

    @Test
    void capturesHttp500ErrorLogsDiagnosticsAndThrowsGenericMessage() {
        stubChatCompletionsResponse(500, "{\"error\":{\"message\":\"Internal server error\"}}");
        OpenAiProvider provider = buildProvider();

        assertThatThrownBy(() -> provider.generateResponse("system prompt", "hola"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GENERIC_MESSAGE);

        List<String> messages = loggedMessages();
        assertThat(messages).anyMatch(msg -> msg.contains("500") && msg.contains("5xx")
                && msg.contains("InternalServerError"));
        assertNoSecretsLogged();
    }

    @Test
    void neverLogsApiKeyOrUserPromptContent() {
        stubChatCompletionsResponse(429,
                "{\"error\":{\"message\":\"Incorrect API key provided: " + FAKE_API_KEY + "\"}}");
        OpenAiProvider provider = buildProvider();

        String secretUserPrompt = "MI-PROMPT-SUPER-SECRETO-DEL-USUARIO";
        assertThatThrownBy(() -> provider.generateResponse("system prompt", secretUserPrompt))
                .isInstanceOf(BusinessException.class);

        List<String> messages = loggedMessages();
        assertNoSecretsLogged();
        assertThat(messages).noneMatch(msg -> msg.contains(secretUserPrompt));
        assertThat(messages).noneMatch(msg -> msg.contains("system prompt"));
    }

    @Test
    void emptyChoicesIsLoggedAsUnexpectedResponse() {
        stubChatCompletionsResponse(200, "{\"choices\":[]}");
        OpenAiProvider provider = buildProvider();

        assertThatThrownBy(() -> provider.generateResponse("system prompt", "hola"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(GENERIC_MESSAGE);

        List<String> messages = loggedMessages();
        assertThat(messages).anyMatch(msg -> msg.contains("vacía") && msg.contains("/chat/completions"));
        assertNoSecretsLogged();
    }

}
