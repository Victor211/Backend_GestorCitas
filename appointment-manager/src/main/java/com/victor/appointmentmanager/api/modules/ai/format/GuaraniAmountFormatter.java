package com.victor.appointmentmanager.api.modules.ai.format;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Único punto del módulo conversacional que formatea montos en guaraníes. Ningún otro lugar
 * (prompt, IA, replies armados a mano) debe construir el símbolo de moneda por su cuenta: el
 * guaraní no usa decimales y separa los miles con punto (ej. "Gs. 65.000").
 */
@Component
public class GuaraniAmountFormatter {

    private static final String PREFIX = "Gs. ";
    private static final String PATTERN = "#,##0";

    public String format(BigDecimal amount) {
        if (amount == null) {
            return PREFIX + "0";
        }
        BigDecimal rounded = amount.setScale(0, RoundingMode.HALF_UP);
        return PREFIX + newFormat().format(rounded);
    }

    private DecimalFormat newFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat(PATTERN, symbols);
    }

}
