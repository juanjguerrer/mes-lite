using System.Threading.Channels;

namespace MesLite.Collector.Events;

// Temporary consumer: logs every outbound message. The outbox and the MQTT publisher replace it.
public sealed class OutboundLogger(
    ChannelReader<OutboundMessage> reader,
    ILogger<OutboundLogger> logger
) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await foreach (var message in reader.ReadAllAsync(stoppingToken))
            {
                logger.LogInformation("{Topic} {Payload}", message.topic, message.payload);
            }
        }
        catch (OperationCanceledException)
        {
            logger.LogInformation("Outbound logger stopping");
        }
    }
}
