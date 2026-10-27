using MesLite.Collector.Configuration;
using MesLite.Collector.OpcUa;
using Microsoft.Extensions.Options;
using Opc.Ua;

namespace MesLite.Collector;

public sealed class CollectorService(
    ILogger<CollectorService> logger,
    IOptions<CollectorOptions> options,
    OpcSessionProvider connection,
    NodeIdResolver resolver,
    ILoggerFactory loggerFactory
) : BackgroundService
{
    private readonly CollectorOptions _config = options.Value;
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await using var session = await connection.ConnectAsync(stoppingToken);
        if (_config.Opc.NamespaceUri == null)
        {
            throw new InvalidOperationException("Opc.NamespaceUri is not configured.");
        }
        var nsIndex = session.NamespaceUris.GetIndex(_config.Opc.NamespaceUri);
        if (nsIndex < 0)
        {
            throw new InvalidOperationException($"NamespaceUri '{_config.Opc.NamespaceUri}' not found in server.");
        }
        logger.LogInformation("Connected to OPC UA server. NamespaceUri '{NamespaceUri}' has index {NamespaceIndex}.", _config.Opc.NamespaceUri, nsIndex);
        await using var subscriber = new TagSubscriber(logger: loggerFactory.CreateLogger<TagSubscriber>());
        var subscription = subscriber.Subscribe(session, _config.Machines, resolver, (ushort)nsIndex);
        try
        {
            await Task.Delay(Timeout.Infinite, stoppingToken);
        }
        catch (OperationCanceledException)
        {
            logger.LogInformation("Collector stopping");
        }
    }
}
