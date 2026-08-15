package com.victor.appointmentmanager.api.modules.ai.format;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GuaraniAmountFormatterTest {

    private final GuaraniAmountFormatter formatter = new GuaraniAmountFormatter();

    @Test
    void formatsWithThousandsSeparatorAndNoDecimals() {
        assertThat(formatter.format(new BigDecimal("65000"))).isEqualTo("Gs. 65.000");
        assertThat(formatter.format(new BigDecimal("150000"))).isEqualTo("Gs. 150.000");
        assertThat(formatter.format(new BigDecimal("10000"))).isEqualTo("Gs. 10.000");
    }

    @Test
    void neverUsesDollarSignOrCommaDecimals() {
        String formatted = formatter.format(new BigDecimal("65000.00"));

        assertThat(formatted).isEqualTo("Gs. 65.000");
        assertThat(formatted).doesNotContain("$");
        assertThat(formatted).doesNotContain(",");
        assertThat(formatted).startsWith("Gs. ");
    }

    @Test
    void roundsFractionalAmountsToTheNearestInteger() {
        assertThat(formatter.format(new BigDecimal("65000.60"))).isEqualTo("Gs. 65.001");
    }

    @Test
    void handlesNullAmountGracefully() {
        assertThat(formatter.format(null)).isEqualTo("Gs. 0");
    }

}
