package io.github.juanjguerrer.meslite.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MqttMessageTypeTest {

    @ParameterizedTest
    @CsvSource({
        "state, STATE",
        "STATE, STATE",
        "State, STATE",
        "production, PRODUCTION",
        "PRODUCTION, PRODUCTION"
    })
    void resolvesIgnoringCase(String text, MqttMessageType expected) {
        assertThat(MqttMessageType.fromString(text, "mes-lite/L/M/" + text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(MqttMessageType.class)
    void roundTripsThroughItsTopicSegment(MqttMessageType type) {
        assertThat(MqttMessageType.fromString(type.getType(), "topic")).isEqualTo(type);
    }

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> MqttMessageType.fromString("telemetry", "mes-lite/L/M/telemetry"))
                .isInstanceOf(InvalidMqttMessageException.class)
                .hasMessageContaining("telemetry")
                .hasMessageContaining("mes-lite/L/M/telemetry");
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> MqttMessageType.fromString(null, "topic"))
                .isInstanceOf(InvalidMqttMessageException.class);
    }
}
