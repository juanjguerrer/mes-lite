package io.github.juanjguerrer.meslite.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import io.github.juanjguerrer.meslite.common.exception.BusinessRuleException;
import io.github.juanjguerrer.meslite.common.exception.ConflictException;
import io.github.juanjguerrer.meslite.common.exception.NotFoundException;
import io.github.juanjguerrer.meslite.event.MachineState;
import io.github.juanjguerrer.meslite.event.MachineStateEventService;
import io.github.juanjguerrer.meslite.event.dto.CreateStateEventRequest;
import io.github.juanjguerrer.meslite.production.ProductionCountService;
import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;
import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;

class MqttInboundHandlerTest {

    private static final String STATE_TOPIC = "mes-lite/line1/cnc1/state";
    private static final String PRODUCTION_TOPIC = "mes-lite/line1/cnc1/production";
    private static final UUID EVENT_ID = UUID.fromString("0d5f1c3e-7a4b-4a53-9a0e-2f6f3f5d9b11");

    private final ProductionCountService productionCountService = mock(ProductionCountService.class);
    private final MachineStateEventService stateService = mock(MachineStateEventService.class);
    private final MqttDeadLetterPublisher deadLetterPublisher = mock(MqttDeadLetterPublisher.class);
    private final MqttInboundHandler handler = new MqttInboundHandler(
        productionCountService,
        stateService,
        Validation.buildDefaultValidatorFactory().getValidator(),
        JsonMapper.builder().build(),
        deadLetterPublisher
    );

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String stateJson(String state) {
        return """
            {"eventId":"%s","state":"%s","occurredAt":"2026-10-23T10:00:00Z","reason":"test"}
            """.formatted(EVENT_ID, state);
    }

    private static String productionJson(long good, long scrap) {
        return """
            {"eventId":"%s","goodCount":%d,"scrapCount":%d,
             "intervalStart":"2026-10-23T10:00:00Z","intervalEnd":"2026-10-23T10:01:00Z"}
            """.formatted(EVENT_ID, good, scrap);
    }

    @Test
    void ingestsValidStateEvent() {
        handler.handle(bytes(stateJson("RUNNING")), STATE_TOPIC);

        var captor = ArgumentCaptor.forClass(CreateStateEventRequest.class);
        verify(stateService).ingest(eq("line1"), eq("cnc1"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateStateEventRequest(
            EVENT_ID, MachineState.RUNNING, Instant.parse("2026-10-23T10:00:00Z"), "test"));
        verifyNoInteractions(productionCountService, deadLetterPublisher);
    }

    @Test
    void ingestsValidProductionCount() {
        handler.handle(bytes(productionJson(10, 2)), PRODUCTION_TOPIC);

        var captor = ArgumentCaptor.forClass(CreateProductionCountRequest.class);
        verify(productionCountService).ingest(eq("line1"), eq("cnc1"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateProductionCountRequest(
            EVENT_ID, 10L, 2L, Instant.parse("2026-10-23T10:00:00Z"), Instant.parse("2026-10-23T10:01:00Z")));
        verifyNoInteractions(stateService, deadLetterPublisher);
    }

    @Test
    void sendsInvalidTopicToDeadLetterWithoutTouchingServices() {
        var payload = bytes(stateJson("RUNNING"));

        handler.handle(payload, "mes-lite/line1/state");

        verify(deadLetterPublisher).reject(eq("mes-lite/line1/state"), eq(payload), contains("InvalidMqttMessageException"));
        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void sendsNullTopicToDeadLetter() {
        var payload = bytes(stateJson("RUNNING"));

        handler.handle(payload, null);

        verify(deadLetterPublisher).reject(eq(null), eq(payload), contains("InvalidMqttMessageException"));
        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void sendsEmptyPayloadToDeadLetter() {
        var payload = new byte[0];

        handler.handle(payload, STATE_TOPIC);

        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), contains(STATE_TOPIC));
        verifyNoInteractions(stateService, productionCountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "  null  "})
    void sendsJsonNullLiteralToDeadLetter(String json) {
        var payload = bytes(json);

        handler.handle(payload, STATE_TOPIC);

        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), contains("InvalidMqttMessageException"));
        verifyNoInteractions(stateService, productionCountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{", "[1,2,3]", "\"text\"", "{\"eventId\":\"not-a-uuid\"}"})
    void sendsMalformedJsonToDeadLetter(String json) {
        var payload = bytes(json);

        handler.handle(payload, STATE_TOPIC);

        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), contains("Failed to parse"));
        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void sendsUnknownStateValueToDeadLetter() {
        var payload = bytes(stateJson("EXPLODING"));

        handler.handle(payload, STATE_TOPIC);

        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), contains("InvalidMqttMessageException"));
        verifyNoInteractions(stateService);
    }

    @Test
    void sendsStateEventMissingRequiredFieldsToDeadLetter() {
        var payload = bytes("{}");

        handler.handle(payload, STATE_TOPIC);

        var reason = ArgumentCaptor.forClass(String.class);
        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), reason.capture());
        assertThat(reason.getValue()).contains("eventId", "state", "occurredAt");
        verifyNoInteractions(stateService);
    }

    @Test
    void sendsNegativeProductionCountsToDeadLetter() {
        var payload = bytes(productionJson(-1, -2));

        handler.handle(payload, PRODUCTION_TOPIC);

        var reason = ArgumentCaptor.forClass(String.class);
        verify(deadLetterPublisher).reject(eq(PRODUCTION_TOPIC), eq(payload), reason.capture());
        assertThat(reason.getValue()).contains("goodCount", "scrapCount");
        verifyNoInteractions(productionCountService);
    }

    @Test
    void sendsPayloadOfTheOtherMessageTypeToDeadLetter() {
        var payload = bytes(productionJson(1, 0));

        handler.handle(payload, STATE_TOPIC);

        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), contains("InvalidMqttMessageException"));
        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void sendsUnknownMachineToDeadLetter() {
        var payload = bytes(stateJson("RUNNING"));
        when(stateService.ingest(any(), any(), any()))
                .thenThrow(NotFoundException.byLineCodeAndMachineCode("LINE1", "CNC1"));

        handler.handle(payload, STATE_TOPIC);

        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), contains("NotFoundException"));
    }

    @Test
    void sendsEventIdConflictToDeadLetter() {
        var payload = bytes(productionJson(1, 0));
        when(productionCountService.ingest(any(), any(), any()))
                .thenThrow(new ConflictException("eventId already exists with different data."));

        handler.handle(payload, PRODUCTION_TOPIC);

        verify(deadLetterPublisher).reject(eq(PRODUCTION_TOPIC), eq(payload), contains("ConflictException"));
    }

    @Test
    void sendsBusinessRuleViolationToDeadLetter() {
        var payload = bytes(stateJson("RUNNING"));
        when(stateService.ingest(any(), any(), any()))
                .thenThrow(new BusinessRuleException("occurredAt cannot be in the future"));

        handler.handle(payload, STATE_TOPIC);

        verify(deadLetterPublisher).reject(eq(STATE_TOPIC), eq(payload), contains("BusinessRuleException"));
    }

    @Test
    void rethrowsTransientFailuresWithoutDeadLettering() {
        var failure = new DataAccessResourceFailureException("database down");
        when(stateService.ingest(any(), any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> handler.handle(bytes(stateJson("RUNNING")), STATE_TOPIC))
                .isSameAs(failure);

        verifyNoInteractions(deadLetterPublisher);
    }

    @Test
    void rethrowsWhenDeadLetterPublishingFails() {
        var failure = new IllegalStateException("broker unavailable");
        doThrow(failure).when(deadLetterPublisher).reject(any(), any(), any());

        assertThatThrownBy(() -> handler.handle(bytes("not json"), STATE_TOPIC))
                .isSameAs(failure);
    }
}
