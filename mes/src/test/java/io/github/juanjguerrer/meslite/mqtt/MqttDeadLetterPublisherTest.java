package io.github.juanjguerrer.meslite.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;

import tools.jackson.databind.json.JsonMapper;

class MqttDeadLetterPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    private final MessageChannel channel = mock(MessageChannel.class);
    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final MqttDeadLetterPublisher publisher = new MqttDeadLetterPublisher(
        channel,
        objectMapper,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    private Message<?> sentMessage() {
        var captor = ArgumentCaptor.forClass(Message.class);
        verify(channel).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void publishesEnvelopeWithTopicPayloadReasonAndTimestamp() {
        publisher.reject("mes-lite/L/M/state", "{\"bad\":true}".getBytes(StandardCharsets.UTF_8), "boom");

        var json = objectMapper.readTree((String) sentMessage().getPayload());
        assertThat(json.get("topic").asString()).isEqualTo("mes-lite/L/M/state");
        assertThat(json.get("payload").asString()).isEqualTo("{\"bad\":true}");
        assertThat(json.get("reason").asString()).isEqualTo("boom");
        assertThat(Instant.parse(json.get("timestamp").asString())).isEqualTo(NOW);
    }

    @Test
    void doesNotOverrideTheDestinationTopic() {
        publisher.reject("mes-lite/L/M/state", new byte[] {1}, "boom");

        assertThat(sentMessage().getHeaders()).doesNotContainKeys("mqtt_topic", "mqtt_receivedTopic");
    }

    @Test
    void keepsNonJsonPayloadAsText() {
        publisher.reject("mes-lite/L/M/state", "not json".getBytes(StandardCharsets.UTF_8), "boom");

        var json = objectMapper.readTree((String) sentMessage().getPayload());
        assertThat(json.get("payload").asString()).isEqualTo("not json");
    }

    @Test
    void toleratesPayloadThatIsNotValidUtf8() {
        publisher.reject("mes-lite/L/M/state", new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xFF}, "boom");

        var json = objectMapper.readTree((String) sentMessage().getPayload());
        assertThat(json.get("payload").asString()).isNotNull();
    }

    @Test
    void propagatesChannelFailures() {
        doThrow(new MessageDeliveryException("broker unavailable")).when(channel).send(any());

        assertThatThrownBy(() -> publisher.reject("mes-lite/L/M/state", new byte[] {1}, "boom"))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
