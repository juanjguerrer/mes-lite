package io.github.juanjguerrer.meslite.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;

class CodeRulesTest {

    @ParameterizedTest
    @CsvSource({
        "LINE-01, LINE-01",
        "line-01, LINE-01",
        "' PRS-003 ', PRS-003",
        "A, A",
        "LINE--01, LINE--01",
        "line_01, LINE_01"
    })
    void normalizesValidCodes(String input, String expected) {
        assertThat(CodeRules.normalizeCode(input)).isEqualTo(expected);
    }

    @Test
    void acceptsCodeAtMaxLength() {
        String code = "A".repeat(50);

        assertThat(CodeRules.normalizeCode(code)).isEqualTo(code);
    }

    @Test
    void measuresLengthAfterTrimming() {
        String code = "A".repeat(50);

        assertThat(CodeRules.normalizeCode(" " + code)).isEqualTo(code);
    }

    @Test
    void rejectsCodeOverMaxLength() {
        assertThatThrownBy(() -> CodeRules.normalizeCode("A".repeat(51)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "-LINE", "LINE-", "_LINE", "LINE_", "LINE 01", "LÍNEA", "LINE.01"})
    void rejectsInvalidCodes(String input) {
        assertThatThrownBy(() -> CodeRules.normalizeCode(input))
                .isInstanceOf(BusinessRuleException.class);
    }
}
