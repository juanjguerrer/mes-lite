package io.github.juanjguerrer.meslite.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;

class CycleTimeRulesTest {

    @ParameterizedTest
    @CsvSource({
        "800, MILLISECONDS, 800",
        "12, SECONDS, 12000",
        "1, MINUTES, 60000",
        "1, HOURS, 3600000",
        "24, HOURS, 86400000"
    })
    void convertsToMillis(int value, CycleTimeUnit unit, int expectedMs) {
        assertThat(CycleTimeRules.toMillis(value, unit)).isEqualTo(expectedMs);
    }

    @ParameterizedTest
    @EnumSource(CycleTimeUnit.class)
    void returnsNullWhenValueIsNull(CycleTimeUnit unit) {
        assertThat(CycleTimeRules.toMillis(null, unit)).isNull();
    }

    @Test
    void returnsNullWhenValueAndUnitAreNull() {
        assertThat(CycleTimeRules.toMillis(null, null)).isNull();
    }

    @Test
    void rejectsMissingUnit() {
        assertThatThrownBy(() -> CycleTimeRules.toMillis(12, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void rejectsNonPositiveValues(int value) {
        assertThatThrownBy(() -> CycleTimeRules.toMillis(value, CycleTimeUnit.SECONDS))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsValueJustOverMax() {
        assertThatThrownBy(() -> CycleTimeRules.toMillis(1441, CycleTimeUnit.MINUTES))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsValueThatWouldOverflowInt() {
        // 600 h = 2,160,000,000 ms: above Integer.MAX_VALUE
        assertThatThrownBy(() -> CycleTimeRules.toMillis(600, CycleTimeUnit.HOURS))
                .isInstanceOf(BusinessRuleException.class);
    }
}
