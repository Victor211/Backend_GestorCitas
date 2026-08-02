package com.victor.appointmentmanager.api.modules.whatsapp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Usa la cadena de filtros de seguridad real (a diferencia de los *SmokeTest de módulos
 * anteriores a la autenticación): confirma que los webhooks de WhatsApp quedan públicos
 * sin abrir el resto de la API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.jwt.secret=test-only-dummy-secret-never-used-in-production-0123456789",
        "app.jwt.expiration=3600000",
        "app.openai.api-key=test-key",
        "app.whatsapp.access-token=test-access-token",
        "app.whatsapp.verify-token=expected-verify-token",
        "app.whatsapp.app-secret=test-app-secret",
        "app.whatsapp.graph-api-version=v21.0",
        "app.whatsapp.base-url=https://graph.example.com"
})
class WhatsAppWebhookControllerTest {

    private static final String APP_SECRET = "test-app-secret";
    private static final String EMPTY_PAYLOAD = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

    @Autowired
    private MockMvc mockMvc;

    private String computeSignature(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    }

    @Test
    void verificationSucceedsAndReturnsChallengeAsPlainText() throws Exception {
        mockMvc.perform(get("/api/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "expected-verify-token")
                        .param("hub.challenge", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("12345"));
    }

    @Test
    void verificationWithWrongVerifyTokenReturns403() throws Exception {
        mockMvc.perform(get("/api/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong-token")
                        .param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithValidSignatureIsProcessedAndAccessibleWithoutJwt() throws Exception {
        String signature = computeSignature(EMPTY_PAYLOAD);

        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_PAYLOAD))
                .andExpect(status().isOk());
    }

    @Test
    void postWithInvalidSignatureIsRejected() throws Exception {
        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .header("X-Hub-Signature-256", "sha256=0000invalidsignature0000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    void postWithoutSignatureHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherProtectedEndpointsStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

}
