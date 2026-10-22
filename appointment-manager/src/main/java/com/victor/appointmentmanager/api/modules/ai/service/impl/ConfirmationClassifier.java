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

    /** Palabras que, SOLAS, forman una afirmación simple ("sí", "dale, confirmo", "está bien"). */
    private static final Set<String> SIMPLE_AFFIRMATION_TOKENS = Set.of(
            "si", "sii", "dale", "ok", "okey", "okay", "confirmo", "confirmado", "correcto", "claro",
            "perfecto", "de", "acuerdo", "esta", "bien", "por", "favor", "listo", "gracias");
    private static final Set<String> SIMPLE_NEGATION_TOKENS = Set.of(
            "no", "nop", "nope", "mejor", "gracias", "olvidalo", "dejalo", "dejemos", "asi", "esta", "bien");

    /**
     * {@code true} solo si el mensaje ENTERO es una afirmación simple. A diferencia de {@link
     * #classify}, que detecta una palabra clave en cualquier parte del mensaje, acá cualquier
     * palabra extra ("sí, pero a las 17", "confirmo aunque prefiero mañana") lo descarta: esos
     * mensajes modifican o condicionan la propuesta y nunca deben ejecutarla. Se usa para
     * cancelar/reprogramar citas existentes, donde una confirmación equivocada es irreversible.
     */
    public boolean isSimpleAffirmation(String message) {
        List<String> tokens = tokensOf(message);
        if (tokens.isEmpty() || !SIMPLE_AFFIRMATION_TOKENS.containsAll(tokens)) {
            return false;
        }
        String normalized = String.join(" ", tokens);
        return tokens.stream().anyMatch(POSITIVE_WORDS::contains) || POSITIVE_PHRASES.contains(normalized);
    }

    /** {@code true} solo si el mensaje entero es un rechazo simple ("no", "mejor no", "no gracias"). */
    public boolean isSimpleNegation(String message) {
        List<String> tokens = tokensOf(message);
        return !tokens.isEmpty()
                && SIMPLE_NEGATION_TOKENS.containsAll(tokens)
                && tokens.stream().anyMatch(token -> token.equals("no") || token.equals("nop")
                        || token.equals("nope") || token.equals("olvidalo") || token.equals("dejalo")
                        || token.equals("dejemos"));
    }

    private List<String> tokensOf(String message) {
        String normalized = normalize(message);
        return normalized.isEmpty() ? List.of() : List.of(MULTI_SPACE.split(normalized));
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
