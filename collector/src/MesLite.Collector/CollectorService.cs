using MesLite.Collector.Configuration;
using Microsoft.Extensions.Options;

namespace MesLite.Collector;

public sealed class CollectorService(
    ILogger<CollectorService> logger,
    IOptions<CollectorOptions> options
) : BackgroundService
{
    CollectorOptions config { get; } = options.Value;
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var opcOptions = config.Opc;
        var endpointUrl = opcOptions.EndpointUrl;
        var namespaceUri = opcOptions.NamespaceUri;
        foreach (var machine in config.Machines)
        {
            var counter = machine.CounterMax ?? 0;
            logger.LogInformation("{endpointUrl}, {namespaceUri}, {Line} ,{Machine}, {CounterMax}", endpointUrl, namespaceUri, machine.Line, machine.Machine, counter);
        }
        await Task.Delay(Timeout.Infinite, stoppingToken);
    }
}
