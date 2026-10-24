package io.github.juanjguerrer.meslite.mqtt;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "mes.mqtt")
public record MqttProperties(
    boolean enabled,
    String url,
    String clientId,
    List<String> subscriptionTopics,
    String deadLetterTopic,
    @Min(0) @Max(2) int qos,
    @Min(0) long sessionExpiryInterval
) {
    @AssertTrue(message = "url, clientId, subscriptionTopics and deadLetterTopic must be set when MQTT is enabled (mes.mqtt.enabled=true)")
    public boolean isConfigurationValid() {
        return !enabled || hasText(url) && hasText(clientId) && subscriptionTopics != null && !subscriptionTopics.isEmpty() && hasText(deadLetterTopic);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
