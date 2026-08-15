package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.modules.ai.enums.ConfirmationSignal;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Clasifica de forma determinística (sin llamar a la IA) si un mensaje confirma, rechaza o no
 * reconoce una propuesta de reserva. La confirmación de una cita es una decisión de negocio y
 * nunca puede depender de que el modelo de IA "entienda" correctamente un "sí": este componente
 * es la única fuente de verdad para esa decisión.
 */
@Component
public class ConfirmationClassifier {

    private static final Pattern NON_LETTER = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private static final List<String> NEGATIVE_EMPLOYEE_PHRASES = List.of("otro profesional", "otro empleado");
    private static final List<String> NEGATIVE_TIME_PHRASES =
            List.of("otra hora", "otro horario", "otro dia", "otra fecha");
    private static final List<String> NEGATIVE_FULL_PHRASES = List.of("mejor no");
    private static final Set<String> NEGATIVE_FULL_WORDS =
            Set.of("no", "cancelar", "cambiemos", "olvidalo", "dejemos");

    private static final List<String> POSITIVE_PHRASES = List.of("esta bien", "de acuerdo");
    private static final Set<String> POSITIVE_WORDS = Set.of("si", "confirmo", "dale", "correcto", "ok");

    public ConfirmationSignal classify(String message) {
        String normalized = normalize(message);
        if (normalized.isEmpty()) {
            return ConfirmationSignal.UNRECOGNIZED;
        }

        Set<String> tokens = Set.of(MULTI_SPACE.split(normalized));

        if (containsAny(normalized, NEGATIVE_EMPLOYEE_PHRASES)) {
            return ConfirmationSignal.NEGATIVE_EMPLOYEE;
        }
        if (containsAny(normalized, NEGATIVE_TIME_PHRASES)) {
            return ConfirmationSignal.NEGATIVE_TIME;
        }
        if (containsAny(normalized, NEGATIVE_FULL_PHRASES) || containsAnyToken(tokens, NEGATIVE_FULL_WORDS)) {
            return ConfirmationSignal.NEGATIVE_FULL;
        }
        if (containsAny(normalized, POSITIVE_PHRASES) || containsAnyToken(tokens, POSITIVE_WORDS)) {
            return ConfirmationSignal.POSITIVE;
        }
        return ConfirmationSignal.UNRECOGNIZED;
    }

    private boolean containsAny(String normalized, List<String> phrases) {
        return phrases.stream().anyMatch(normalized::contains);
    }

    private boolean containsAnyToken(Set<String> tokens, Set<String> words) {
        return words.stream().anyMatch(tokens::contains);
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }
        String lower = message.trim().toLowerCase(java.util.Locale.ROOT);
        String withoutAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String lettersOnly = NON_LETTER.matcher(withoutAccents).replaceAll(" ");
        return MULTI_SPACE.matcher(lettersOnly).replaceAll(" ").trim();
    }

}
