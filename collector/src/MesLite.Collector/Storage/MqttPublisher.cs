using MesLite.Collector.Configuration;
using Microsoft.Extensions.Options;
using MQTTnet;
using MQTTnet.Protocol;

namespace MesLite.Collector.Storage;

public sealed class MqttPublisher(
    Outbox outbox,
    IOptions<CollectorOptions> options,
    ILogger<MqttPublisher> logger
) : BackgroundService
{
    private readonly IMqttClient _client = new MqttClientFactory().CreateMqttClient();

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var mqtt = options.Value.Mqtt;
        var clientOptions = new MqttClientOptionsBuilder()
            .WithTcpServer(mqtt.Host, mqtt.Port)
            .WithClientId(mqtt.ClientId)
            .Build();
        var delay = mqtt.RetryDelay;
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (!_client.IsConnected)
                {
                    await _client.ConnectAsync(clientOptions, stoppingToken);
                    logger.LogInformation("Connected to MQTT broker at {Host}:{Port}", mqtt.Host, mqtt.Port);
                }

                var pending = await outbox.GetPendingAsync(50, stoppingToken);
                if (pending.Count == 0)
                {
                    await Task.Delay(500, stoppingToken);
                    continue;
                }
                foreach (var message in pending)
                {
                    var mqttMessage = new MqttApplicationMessageBuilder()
                        .WithTopic(message.topic)
                        .WithPayload(message.payload)
                        .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
                        .Build();
                    var result = await _client.PublishAsync(mqttMessage, stoppingToken);
                    if (!result.IsSuccess)
                    {
                        throw new InvalidOperationException($"Broker rejected message {message.eventId} with reason {result.ReasonCode}");
                    }
                    await outbox.MarkSentAsync(message.eventId, stoppingToken);
                }
                delay = mqtt.RetryDelay;
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                logger.LogWarning(ex, "Error publishing events to MQTT broker");
                await Task.Delay(delay, stoppingToken);
                delay = TimeSpan.FromSeconds(Math.Min(delay.TotalSeconds * 2, 60));
            }
        }
    }

    public override async Task StopAsync(CancellationToken cancellationToken)
    {
        await base.StopAsync(cancellationToken);

        if (_client.IsConnected)
        {
            await _client.DisconnectAsync(new MqttClientDisconnectOptions(), cancellationToken);
        }
    }
}
