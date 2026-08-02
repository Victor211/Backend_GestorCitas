package com.victor.appointmentmanager.api.modules.whatsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Encapsula exclusivamente la validación criptográfica de la firma de los webhooks
 * de WhatsApp (X-Hub-Signature-256), para que el Controller no contenga esta lógica.
 */
@Component
public class WhatsAppSignatureValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final String appSecret;

    public WhatsAppSignatureValidator(@Value("${app.whatsapp.app-secret}") String appSecret) {
        this.appSecret = appSecret;
    }

    public boolean isValid(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String providedSignature = signatureHeader.substring(SIGNATURE_PREFIX.length());
        String computedSignature = computeHmac(rawBody);

        return MessageDigest.isEqual(
                providedSignature.getBytes(StandardCharsets.UTF_8),
                computedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmac(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC de WhatsApp", ex);
        }
    }

}
