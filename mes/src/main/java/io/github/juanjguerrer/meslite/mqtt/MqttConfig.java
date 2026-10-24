package io.github.juanjguerrer.meslite.mqtt;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
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
        return options;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public Mqttv5PahoMessageDrivenChannelAdapter mqttInboundAdapter(
        MqttConnectionOptions options,
        MessageChannel mqttInputChannel
    ) {
        Mqttv5PahoMessageDrivenChannelAdapter adapter = new Mqttv5PahoMessageDrivenChannelAdapter(
            options,
            properties.clientId(),
            properties.subscriptionTopics().toArray(new String[0])
        );
        adapter.setOutputChannel(mqttInputChannel);
        adapter.setQos(properties.qos());
        return adapter;
    }
}
