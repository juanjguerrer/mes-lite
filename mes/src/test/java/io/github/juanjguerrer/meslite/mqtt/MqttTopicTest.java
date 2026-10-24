package io.github.juanjguerrer.meslite.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MqttTopicTest {

    @Test
    void parsesStateTopic() {
        var topic = MqttTopic.parse("mes-lite/LINE1/CNC1/state");

        assertThat(topic.lineCode()).isEqualTo("LINE1");
        assertThat(topic.machineCode()).isEqualTo("CNC1");
        assertThat(topic.messageType()).isEqualTo(MqttMessageType.STATE);
    }

    @Test
    void parsesProductionTopic() {
        var topic = MqttTopic.parse("mes-lite/LINE1/CNC1/production");

        assertThat(topic.messageType()).isEqualTo(MqttMessageType.PRODUCTION);
    }

    @Test
    void keepsCodesAsReceived() {
        var topic = MqttTopic.parse("mes-lite/line1/cnc1/state");

        assertThat(topic.lineCode()).isEqualTo("line1");
        assertThat(topic.machineCode()).isEqualTo("cnc1");
    }

    @Test
    void messageTypeIsCaseInsensitive() {
        assertThat(MqttTopic.parse("mes-lite/L/M/STATE").messageType()).isEqualTo(MqttMessageType.STATE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "mes-lite",
        "mes-lite/LINE1/CNC1",
        "mes-lite/LINE1/CNC1/state/extra",
        "mes-lite/LINE1/CNC1/state/",
        "other/LINE1/CNC1/state",
        "mes-lite//CNC1/state",
        "mes-lite/ /CNC1/state",
        "mes-lite/LINE1//state",
        "mes-lite/LINE1/ /state",
        "mes-lite/LINE1/CNC1/",
        "mes-lite/LINE1/CNC1/unknown",
        "mes-lite/dead-letter"
    })
    void rejectsInvalidTopics(String topic) {
        assertThatThrownBy(() -> MqttTopic.parse(topic))
                .isInstanceOf(InvalidMqttMessageException.class);
    }

    @Test
    void unknownMessageTypeMentionsTypeAndTopic() {
        assertThatThrownBy(() -> MqttTopic.parse("mes-lite/LINE1/CNC1/unknown"))
                .isInstanceOf(InvalidMqttMessageException.class)
                .hasMessageContaining("unknown")
                .hasMessageContaining("mes-lite/LINE1/CNC1/unknown");
    }
}
