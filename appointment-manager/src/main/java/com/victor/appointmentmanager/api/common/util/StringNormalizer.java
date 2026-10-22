package com.victor.appointmentmanager.api.common.util;

import lombok.experimental.UtilityClass;

import java.util.Locale;

@UtilityClass
public class StringNormalizer {

    public String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT).trim();
    }

}
