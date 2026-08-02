package com.victor.appointmentmanager.api.modules.whatsapp.controller;

import com.victor.appointmentmanager.api.modules.whatsapp.service.WhatsAppWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints invocados exclusivamente por Meta (WhatsApp Cloud API). No usan el formato
 * ApiResponse: Meta espera el formato exacto documentado en su API de webhooks.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Webhooks", description = "Endpoints invocados por Meta (WhatsApp Cloud API). "
        + "No requieren Bearer JWT: se protegen mediante verify token (GET) y validación de firma HMAC (POST).")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService whatsAppWebhookService;

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Verificación del webhook",
            description = "Invocado por Meta al configurar la integración. Compara hub.verify_token contra "
                    + "WHATSAPP_VERIFY_TOKEN y, si coincide, devuelve hub.challenge como texto plano.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Verificación exitosa: devuelve hub.challenge en texto plano"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "hub.mode o hub.verify_token inválidos")
    })
    public ResponseEntity<String> verify(
            @Parameter(description = "Debe ser \"subscribe\"") @RequestParam("hub.mode") String mode,
            @Parameter(description = "Debe coincidir con WHATSAPP_VERIFY_TOKEN")
            @RequestParam("hub.verify_token") String verifyToken,
            @Parameter(description = "Valor que Meta espera recibir de vuelta si la verificación es exitosa")
            @RequestParam("hub.challenge") String challenge) {

        if (whatsAppWebhookService.verifyWebhook(mode, verifyToken)) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Recepción de eventos de WhatsApp",
            description = "Invocado por Meta para cada evento (mensajes entrantes, estados, etc.). "
                    + "Valida la firma HMAC del header X-Hub-Signature-256 usando WHATSAPP_APP_SECRET antes de procesar.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "Evento recibido (procesado, ignorado o ya registrado)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Firma X-Hub-Signature-256 ausente o inválida")
    })
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawBody) {

        if (!whatsAppWebhookService.isSignatureValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        whatsAppWebhookService.processWebhook(rawBody);
        return ResponseEntity.ok().build();
    }

}
