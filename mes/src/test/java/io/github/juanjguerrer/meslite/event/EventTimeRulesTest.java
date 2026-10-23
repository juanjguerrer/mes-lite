package io.github.juanjguerrer.meslite.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.juanjguerrer.meslite.common.BusinessRuleException;

class EventTimeRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "PT1M", "PT8H", "P3650D"})
    void acceptsPastAndPresentInstants(String age) {
        Instant occurredAt = NOW.minus(Duration.parse(age));

        assertThatCode(() -> EventTimeRules.validateOccurredAt(CLOCK, occurredAt))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT1S", "PT4M59S", "PT5M"})
    void acceptsSmallClockSkewUpToTolerance(String ahead) {
        Instant occurredAt = NOW.plus(Duration.parse(ahead));

        assertThatCode(() -> EventTimeRules.validateOccurredAt(CLOCK, occurredAt))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT5M0.001S", "PT6M", "PT1H", "P1D"})
    void rejectsInstantsBeyondTolerance(String ahead) {
        Instant occurredAt = NOW.plus(Duration.parse(ahead));

        assertThatThrownBy(() -> EventTimeRules.validateOccurredAt(CLOCK, occurredAt))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsNullInstant() {
        assertThatThrownBy(() -> EventTimeRules.validateOccurredAt(CLOCK, null))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void usesTheGivenClockNotTheSystemClock() {
        Clock lateClock = Clock.fixed(NOW.plus(Duration.ofDays(1)), ZoneOffset.UTC);
        Instant occurredAt = NOW.plus(Duration.ofHours(12));

        assertThatThrownBy(() -> EventTimeRules.validateOccurredAt(CLOCK, occurredAt))
            .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> EventTimeRules.validateOccurredAt(lateClock, occurredAt))
            .doesNotThrowAnyException();
    }
}
