package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.modules.ai.enums.ConfirmationSignal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConfirmationClassifierTest {

    private final ConfirmationClassifier classifier = new ConfirmationClassifier();

    @ParameterizedTest
    @ValueSource(strings = {"si", "sí", "Sí", "confirmo", "dale", "correcto", "está bien", "esta bien", "ok",
            "de acuerdo", "Dale!", "Sí, dale"})
    void recognizesPositiveConfirmations(String message) {
        assertThat(classifier.classify(message)).isEqualTo(ConfirmationSignal.POSITIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "No.", "cancelar", "mejor no", "cambiemos"})
    void recognizesFullNegativeConfirmations(String message) {
        assertThat(classifier.classify(message)).isEqualTo(ConfirmationSignal.NEGATIVE_FULL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"otra hora", "otro horario", "prefiero otra hora", "otro día", "otra fecha"})
    void recognizesTimeRedirectNegativeConfirmations(String message) {
        assertThat(classifier.classify(message)).isEqualTo(ConfirmationSignal.NEGATIVE_TIME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"otro profesional", "otro empleado", "prefiero otro profesional"})
    void recognizesEmployeeRedirectNegativeConfirmations(String message) {
        assertThat(classifier.classify(message)).isEqualTo(ConfirmationSignal.NEGATIVE_EMPLOYEE);
    }

    @Test
    void modificationMessagesAreUnrecognized() {
        assertThat(classifier.classify("mejor a las 17")).isEqualTo(ConfirmationSignal.UNRECOGNIZED);
        assertThat(classifier.classify("con María mejor")).isEqualTo(ConfirmationSignal.UNRECOGNIZED);
        assertThat(classifier.classify("quiero Coloración")).isEqualTo(ConfirmationSignal.UNRECOGNIZED);
    }

    @Test
    void blankMessageIsUnrecognized() {
        assertThat(classifier.classify("   ")).isEqualTo(ConfirmationSignal.UNRECOGNIZED);
    }

}
