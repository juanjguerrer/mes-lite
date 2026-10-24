package io.github.juanjguerrer.meslite.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private final MqttInboundHandler handler = new MqttInboundHandler(
        productionCountService,
        stateService,
        Validation.buildDefaultValidatorFactory().getValidator(),
        JsonMapper.builder().build()
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
        verifyNoInteractions(productionCountService);
    }

    @Test
    void ingestsValidProductionCount() {
        handler.handle(bytes(productionJson(10, 2)), PRODUCTION_TOPIC);

        var captor = ArgumentCaptor.forClass(CreateProductionCountRequest.class);
        verify(productionCountService).ingest(eq("line1"), eq("cnc1"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CreateProductionCountRequest(
            EVENT_ID, 10L, 2L, Instant.parse("2026-10-23T10:00:00Z"), Instant.parse("2026-10-23T10:01:00Z")));
        verifyNoInteractions(stateService);
    }

    @Test
    void rejectsInvalidTopicWithoutTouchingServices() {
        assertThatThrownBy(() -> handler.handle(bytes(stateJson("RUNNING")), "mes-lite/line1/state"))
                .isInstanceOf(InvalidMqttMessageException.class);

        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void rejectsNullTopic() {
        assertThatThrownBy(() -> handler.handle(bytes(stateJson("RUNNING")), null))
                .isInstanceOf(InvalidMqttMessageException.class);

        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> handler.handle(new byte[0], STATE_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class)
                .hasMessageContaining(STATE_TOPIC);

        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> handler.handle(null, STATE_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class);

        verifyNoInteractions(stateService, productionCountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "  null  "})
    void rejectsJsonNullLiteral(String json) {
        assertThatThrownBy(() -> handler.handle(bytes(json), STATE_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class);

        verifyNoInteractions(stateService, productionCountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{", "[1,2,3]", "\"text\"", "{\"eventId\":\"not-a-uuid\"}"})
    void rejectsMalformedJson(String json) {
        assertThatThrownBy(() -> handler.handle(bytes(json), STATE_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class)
                .hasMessageContaining("Failed to parse");

        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void rejectsUnknownStateValue() {
        assertThatThrownBy(() -> handler.handle(bytes(stateJson("EXPLODING")), STATE_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class);

        verifyNoInteractions(stateService);
    }

    @Test
    void rejectsStateEventMissingRequiredFields() {
        assertThatThrownBy(() -> handler.handle(bytes("{}"), STATE_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class)
                .hasMessageContaining("eventId")
                .hasMessageContaining("state")
                .hasMessageContaining("occurredAt");

        verifyNoInteractions(stateService);
    }

    @Test
    void rejectsNegativeProductionCounts() {
        assertThatThrownBy(() -> handler.handle(bytes(productionJson(-1, -2)), PRODUCTION_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class)
                .hasMessageContaining("goodCount")
                .hasMessageContaining("scrapCount");

        verifyNoInteractions(productionCountService);
    }

    @Test
    void rejectsPayloadOfTheOtherMessageType() {
        assertThatThrownBy(() -> handler.handle(bytes(productionJson(1, 0)), STATE_TOPIC))
                .isInstanceOf(InvalidMqttMessageException.class);

        verifyNoInteractions(stateService, productionCountService);
    }

    @Test
    void domainExceptionsFromServicesPropagateUntouched() {
        var notFound = NotFoundException.byLineCodeAndMachineCode("LINE1", "CNC1");
        when(stateService.ingest(any(), any(), any())).thenThrow(notFound);

        assertThatThrownBy(() -> handler.handle(bytes(stateJson("RUNNING")), STATE_TOPIC))
                .isSameAs(notFound);
    }
}
