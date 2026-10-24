package io.github.juanjguerrer.meslite.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

@ConditionalOnProperty(prefix = "mes.mqtt", name = "enabled", havingValue = "true")
@Component
public class MqttDeadLetterPublisher {
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MessageChannel deadLetterChannel;
    private static final Logger log = LoggerFactory.getLogger(MqttDeadLetterPublisher.class);

    public MqttDeadLetterPublisher(
        MessageChannel mqttDeadLetterChannel,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.deadLetterChannel = mqttDeadLetterChannel;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private record DeadLetterMessage(
        String topic,
        String payload,
        String reason,
        Instant timestamp
    ) { }

    public void reject(
        String topic,
        byte[] payload,
        String reason
    ) {
        log.warn("Rejecting MQTT message from topic {} due to reason: {}", topic, reason);
        String formattedPayload = new String(payload, StandardCharsets.UTF_8);
        Instant now = clock.instant();
        DeadLetterMessage deadLetterMessage = new DeadLetterMessage(
            topic,
            formattedPayload,
            reason,
            now
        );
        String deadLetterPayload = objectMapper.writeValueAsString(deadLetterMessage);
        Message<String> deadLetter = MessageBuilder.withPayload(deadLetterPayload)
            .build();
        deadLetterChannel.send(deadLetter);
    }
}
