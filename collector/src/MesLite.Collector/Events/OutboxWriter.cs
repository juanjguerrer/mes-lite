using System.Threading.Channels;
using MesLite.Collector.Storage;

namespace MesLite.Collector.Events;

public sealed class OutboxWriter(
    ChannelReader<OutboundMessage> reader,
    ILogger<OutboxWriter> logger,
    Outbox outbox
) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await foreach (var message in reader.ReadAllAsync(stoppingToken))
            {
                try
                {
                    if (!await outbox.AddAsync(message, stoppingToken))
                    {
                        logger.LogWarning("Message {EventId} for topic {Topic} was not stored: duplicate event id or sequence.", message.eventId, message.topic);
                    }
                    else
                    {
                        logger.LogDebug("{Topic} {Payload}", message.topic, message.payload);
                    }
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    logger.LogError(ex, "Failed to store message {EventId} for topic {Topic}; it is lost.", message.eventId, message.topic);
                }
            }
        }
        catch (OperationCanceledException)
        {
            logger.LogInformation("Outbox writer stopping");
        }
    }
}
