package com.victor.appointmentmanager.api.common.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringNormalizer {

    public String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
