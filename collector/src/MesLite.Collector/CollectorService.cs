using MesLite.Collector.Configuration;
using MesLite.Collector.OpcUa;
using Microsoft.Extensions.Options;
using Opc.Ua;
using Opc.Ua.Client;

namespace MesLite.Collector;

public sealed class CollectorService(
    ILogger<CollectorService> logger,
    IOptions<CollectorOptions> options,
    OpcSessionProvider connection,
    NodeIdResolver resolver
) : BackgroundService
{
    CollectorOptions config { get; } = options.Value;
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var session = await connection.ConnectAsync(stoppingToken);
        if (config.Opc.NamespaceUri == null)
        {
            throw new InvalidOperationException("Opc.NamespaceUri is not configured.");
        }
        var nsIndex = session.NamespaceUris.GetIndex(config.Opc.NamespaceUri);
        if (nsIndex < 0)
        {
            throw new InvalidOperationException($"NamespaceUri '{config.Opc.NamespaceUri}' not found in server.");
        }
        var machine = config.Machines[0];
        var nodeId = new NodeId(resolver.Resolve(machine, LogicalTag.Running), (ushort)nsIndex);
        var value = await session.ReadValueAsync(nodeId, stoppingToken);
        logger.LogInformation("Read value for {Line}, {Machine}, {Tag}: {Value}", machine.Line, machine.Machine, LogicalTag.Running, value);
        // foreach (var machine in config.Machines)
        // {
        //     var counter = machine.CounterMax ?? 0;
        //     logger.LogInformation("{endpointUrl}, {namespaceUri}, {Line} ,{Machine}, {CounterMax}", endpointUrl, namespaceUri, machine.Line, machine.Machine, counter);
        // }
        await Task.Delay(Timeout.Infinite, stoppingToken);
    }
}
