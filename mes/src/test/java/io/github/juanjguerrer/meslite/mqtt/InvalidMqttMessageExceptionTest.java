package io.github.juanjguerrer.meslite.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

class InvalidMqttMessageExceptionTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void failedValidationListsEveryViolationSortedByField() {
        var request = new CreateProductionCountRequest(null, -1L, null, null, null);
        Set<ConstraintViolation<CreateProductionCountRequest>> violations = validator.validate(request);

        var exception = InvalidMqttMessageException.failedValidation(violations);

        assertThat(exception.getMessage())
                .startsWith("Invalid MQTT message: ")
                .containsSubsequence("eventId", "goodCount", "intervalEnd", "intervalStart", "scrapCount");
    }

    @Test
    void invalidTopicFormatMentionsTopicAndReason() {
        var exception = InvalidMqttMessageException.invalidTopicFormat("a/b", "too short");

        assertThat(exception.getMessage()).contains("a/b").contains("too short");
    }

    @Test
    void invalidTopicFormatWithBlankTopicDoesNotEchoIt() {
        var exception = InvalidMqttMessageException.invalidTopicFormat("  ", "ignored");

        assertThat(exception.getMessage()).isEqualTo("Topic cannot be null or blank");
    }

    @Test
    void emptyPayloadMentionsTopic() {
        var exception = InvalidMqttMessageException.emptyPayload("mes-lite/L/M/state");

        assertThat(exception.getMessage()).contains("mes-lite/L/M/state");
    }
}
