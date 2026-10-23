package io.github.juanjguerrer.meslite.common;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CodeRules {
    private static final int MAX_CODE_LENGTH = 50;
    private static final Pattern CODE_PATTERN = Pattern.compile("(?![-_])[A-Z0-9_-]+(?<![-_])");

    private CodeRules() {
        // Private constructor to prevent instantiation
    }

    public static String normalizeCode(String code) {
        if (code == null) {
            throw new BusinessRuleException("Code cannot be null");
        }
        if (code.isBlank()) {
            throw new BusinessRuleException("Code cannot be blank");
        }
        String formattedCode = code.trim().toUpperCase(Locale.ROOT);
        if (formattedCode.length() > MAX_CODE_LENGTH) {
            throw new BusinessRuleException("Code cannot exceed " + MAX_CODE_LENGTH + " characters");
        }
        if (!CODE_PATTERN.matcher(formattedCode).matches()) {
            throw new BusinessRuleException("Code must consist of alphanumeric characters, hyphens(-), or underscores(_), and cannot start or end with a hyphen(-) or underscore(_)");
        }
        return formattedCode;
    }
}
