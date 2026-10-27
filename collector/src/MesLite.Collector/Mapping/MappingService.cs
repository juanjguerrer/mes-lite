using System.Threading.Channels;
using MesLite.Collector.Configuration;
using MesLite.Collector.OpcUa;
using Opc.Ua;

namespace MesLite.Collector.Mapping;

public sealed class MappingService(
    ChannelReader<TagChange> reader,
    ILogger<MappingService> logger
) : BackgroundService
{
    private readonly Dictionary<(string Line, string Machine), MachineSnapshot> _machines = new();

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        await foreach (var change in reader.ReadAllAsync(stoppingToken))
        {
            if (string.IsNullOrWhiteSpace(change.Line) ||
                string.IsNullOrWhiteSpace(change.Machine))
            {
                logger.LogWarning("Tag change has no line or machine.");
                continue;
            }
            var key = (change.Line, change.Machine);
            if (!_machines.TryGetValue(key, out var snapshot))
            {
                snapshot = new MachineSnapshot();
                _machines.Add(key, snapshot);
            }
            if (StatusCode.IsBad(change.StatusCode))
            {
                logger.LogWarning(
                    "Tag change for {Line}.{Machine} has bad status code {StatusCode}.",
                    change.Line,
                    change.Machine,
                    change.StatusCode
                );
                continue;
            }
            var isResync = change.Generation > snapshot.Generation;
            snapshot.Generation = change.Generation;
            switch (change.Tag)
            {
                case LogicalTag.Running:
                    if (change.Value is bool running)
                        snapshot.IsRunning = running;
                    break;

                case LogicalTag.FaultCode:
                    if (change.Value is ushort faultCode)
                        snapshot.FaultCode = faultCode;
                    break;

                case LogicalTag.Mode:
                    if (change.Value is ushort mode &&
                        Enum.IsDefined(typeof(MachineMode), mode))
                    {
                        snapshot.Mode = (MachineMode)mode;
                    }
                    break;
            }
            if (snapshot.IsRunning.HasValue &&
                snapshot.FaultCode.HasValue &&
                snapshot.Mode.HasValue)
            {
                var state = StateMapper.Map(
                    snapshot.IsRunning.Value,
                    snapshot.FaultCode.Value,
                    snapshot.Mode.Value
                );

                if (!isResync)
                {
                    logger.LogInformation(
                        "Machine {Line}.{Machine} state: {State}",
                        change.Line, change.Machine, state);
                }
            }
        }
    }
}
