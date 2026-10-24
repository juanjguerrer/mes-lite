package io.github.juanjguerrer.meslite.mqtt;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import tools.jackson.core.JacksonException;

public class InvalidMqttMessageException extends RuntimeException {
    public InvalidMqttMessageException(String message) {
        super(message);
    }

    public InvalidMqttMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public static InvalidMqttMessageException invalidTopicFormat(String topic, String message) {
        if (topic == null || topic.isBlank()) {
            return new InvalidMqttMessageException("Topic cannot be null or blank");
        }
        return new InvalidMqttMessageException("Invalid topic format: " + topic + " - " + message);
    }

    public static InvalidMqttMessageException unknownMessageType(String messageType, String topic) {
        return new InvalidMqttMessageException("Unknown message type: " + messageType + " in topic: " + topic);
    }

    public static InvalidMqttMessageException emptyPayload(String topic) {
        return new InvalidMqttMessageException("Empty payload for topic: " + topic);
    }

    public static InvalidMqttMessageException failedValidation(Set<? extends ConstraintViolation<?>> violations) {
        var violationsString  = violations.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        return new InvalidMqttMessageException("Invalid MQTT message: " + violationsString);
    }

    public static InvalidMqttMessageException failedParsing(JacksonException e) {
        return new InvalidMqttMessageException("Failed to parse MQTT message: " + e.getMessage(), e);
    }
}
