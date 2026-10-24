package io.github.juanjguerrer.meslite.mqtt;

public enum MqttMessageType {
    STATE("state"),
    PRODUCTION("production");

    private final String type;

    MqttMessageType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static MqttMessageType fromString(String type, String topic) {
        for (MqttMessageType messageType : MqttMessageType.values()) {
            if (messageType.type.equalsIgnoreCase(type)) {
                return messageType;
            }
        }
        throw InvalidMqttMessageException.unknownMessageType(type, topic);
    }
}
