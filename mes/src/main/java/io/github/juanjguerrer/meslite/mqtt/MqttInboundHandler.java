package io.github.juanjguerrer.meslite.mqtt;

import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import io.github.juanjguerrer.meslite.event.MachineStateEventService;
import io.github.juanjguerrer.meslite.event.dto.CreateStateEventRequest;
import io.github.juanjguerrer.meslite.production.ProductionCountService;
import io.github.juanjguerrer.meslite.production.dto.CreateProductionCountRequest;

import java.util.Set;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class MqttInboundHandler {

    private final ProductionCountService productionCountService;
    private final MachineStateEventService machineStateEventService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public MqttInboundHandler(
        ProductionCountService productionCountService,
        MachineStateEventService machineStateEventService,
        Validator validator,
        ObjectMapper objectMapper
    ) {
        this.productionCountService = productionCountService;
        this.machineStateEventService = machineStateEventService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(
        byte[] payload,
        @Header(MqttHeaders.RECEIVED_TOPIC) String topic
    ) {
        MqttTopic topicParsed = MqttTopic.parse(topic);
        switch (topicParsed.messageType()) {
            case MqttMessageType.STATE:
                handleStateEvent(
                    topicParsed.lineCode(),
                    topicParsed.machineCode(),
                    payload,
                    topic
                );
                break;
            case MqttMessageType.PRODUCTION:
                handleProductionCount(
                    topicParsed.lineCode(),
                    topicParsed.machineCode(),
                    payload,
                    topic
                );
                break;
            default:
                throw InvalidMqttMessageException.unknownMessageType(topicParsed.messageType().toString(), topic);
        }
    }

    private void handleStateEvent(String lineCode, String machineCode, byte[] payload, String topic) {
        CreateStateEventRequest request =
            validatePayload(payload, topic, CreateStateEventRequest.class);
        machineStateEventService.ingest(
            lineCode,
            machineCode,
            request
        );
    }

    private void handleProductionCount(String lineCode, String machineCode, byte[] payload, String topic) {
        CreateProductionCountRequest request =
            validatePayload(payload, topic, CreateProductionCountRequest.class);
        productionCountService.ingest(
            lineCode,
            machineCode,
            request
        );
    }

    private <T> T validatePayload(byte[] payload, String topic, Class<T> payloadClass) {
        if (payload == null || payload.length == 0) {
            throw InvalidMqttMessageException.emptyPayload(topic);
        }
        T payloadObject = null;
        try {
            payloadObject = objectMapper.readValue(payload, payloadClass);
        } catch (JacksonException e) {
            throw InvalidMqttMessageException.failedParsing(e);
        }
        if (payloadObject == null) {
            throw InvalidMqttMessageException.emptyPayload(topic);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(payloadObject);
        if (!violations.isEmpty()) {
            throw InvalidMqttMessageException.failedValidation(violations);
        }
        return payloadObject;
    }
}
