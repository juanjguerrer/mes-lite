package io.github.juanjguerrer.meslite.mqtt;

public record MqttTopic(
    String lineCode,
    String machineCode,
    MqttMessageType messageType
) {

    public static MqttTopic parse(String topic) {
        if (topic == null || topic.isBlank()) {
            throw InvalidMqttMessageException.invalidTopicFormat(topic, "Topic cannot be null or blank");
        }
        // "mes-lite/<lineCode>/<machineCode>/<messageType>"
        String[] parts = topic.split("/", -1);
        if (parts.length != 4) {
            throw InvalidMqttMessageException.invalidTopicFormat(topic, "Expected format: mes-lite/<lineCode>/<machineCode>/<eventType>.");
        }
        if (!"mes-lite".equals(parts[0])) {
            throw InvalidMqttMessageException.invalidTopicFormat(topic, "Prefix must be 'mes-lite'.");
        }
        if (parts[1].isBlank()) {
            throw InvalidMqttMessageException.invalidTopicFormat(topic, "Line code cannot be blank.");
        }
        if (parts[2].isBlank()) {
            throw InvalidMqttMessageException.invalidTopicFormat(topic, "Machine code cannot be blank.");
        }
        var messageType = MqttMessageType.fromString(parts[3], topic);
        return new MqttTopic(parts[1], parts[2], messageType);
    }
}
