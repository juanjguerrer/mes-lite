package io.github.juanjguerrer.meslite.mqtt;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.messaging.MessageChannel;

@ConditionalOnProperty(prefix = "mes.mqtt", name = "enabled", havingValue = "true")
@Configuration
public class MqttConfig {

    private final MqttProperties properties;

    public MqttConfig(MqttProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MqttConnectionOptions mqttConnectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setAutomaticReconnect(true);
        options.setServerURIs(new String[]{properties.url()});
        options.setCleanStart(false);
        options.setSessionExpiryInterval(properties.sessionExpiryInterval());
        // Should be less than Paho's default of 10
        options.setReceiveMaximum(5);
        options.setMaxReconnectDelay(5000);
        return options;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public Mqttv5PahoMessageDrivenChannelAdapter mqttInboundAdapter(
        MqttConnectionOptions mqttConnectionOptions,
        MessageChannel mqttInputChannel
    ) {
        Mqttv5PahoMessageDrivenChannelAdapter adapter = new Mqttv5PahoMessageDrivenChannelAdapter(
            mqttConnectionOptions,
            properties.clientId(),
            properties.subscriptionTopics().toArray(new String[0])
        );
        adapter.setOutputChannel(mqttInputChannel);
        adapter.setQos(properties.qos());
        return adapter;
    }

    @Bean
    public MessageChannel mqttDeadLetterChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttConnectionOptions mqttDeadLetterConnectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setAutomaticReconnect(true);
        options.setServerURIs(new String[]{properties.url()});
        options.setCleanStart(true);
        return options;
    }

    @ServiceActivator(inputChannel = "mqttDeadLetterChannel")
    @Bean
    public Mqttv5PahoMessageHandler mqttDeadLetterHandler(
        MqttConnectionOptions mqttDeadLetterConnectionOptions
    ) {
        Mqttv5PahoMessageHandler handler = new Mqttv5PahoMessageHandler(
            mqttDeadLetterConnectionOptions,
            properties.clientId() + "-dead-letter"
        );
        handler.setDefaultTopic(properties.deadLetterTopic());
        handler.setDefaultQos(properties.deadLetterQos());
        return handler;
    }
}
