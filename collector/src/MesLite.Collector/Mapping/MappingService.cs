using System.Threading.Channels;
using MesLite.Collector.Configuration;
using MesLite.Collector.OpcUa;
using Microsoft.Extensions.Options;
using Opc.Ua;

namespace MesLite.Collector.Mapping;

public sealed class MappingService(
    ChannelReader<TagChange> reader,
    ILogger<MappingService> logger,
    IOptions<CollectorOptions> options
) : BackgroundService
{
    private readonly Dictionary<(string Line, string Machine), MachineSnapshot> _machines = new();

    private readonly Dictionary<(string Line, string Machine), uint> _counterMax =
        options.Value.Machines.ToDictionary(
            m => (m.Line!, m.Machine!),
            m => m.CounterMax ?? ushort.MaxValue);

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
                if (change.Tag is LogicalTag.Running or LogicalTag.FaultCode or LogicalTag.Mode)
                {
                    snapshot.Seen.Remove(change.Tag);
                    if (snapshot.LastState != MachineState.Unknown)
                    {
                        logger.LogInformation(
                            "Machine {Line}.{Machine} state: Unknown (tag {Tag} has bad status code).",
                            change.Line, change.Machine, change.Tag);
                        snapshot.LastState = MachineState.Unknown;
                    }
                }
                continue;
            }
            if (change.Generation > snapshot.Generation)
            {
                snapshot.Generation = change.Generation;
                snapshot.Seen.Clear();
                snapshot.Recovering = snapshot.LastState.HasValue;
            }
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
                case LogicalTag.GoodCounter:
                case LogicalTag.ScrapCounter:
                    if (change.Value is ushort counter)
                        HandleCounter(change, snapshot, counter);
                    break;
            }
            snapshot.Seen.Add(change.Tag);
            if (!snapshot.Seen.Contains(LogicalTag.Running) ||
                !snapshot.Seen.Contains(LogicalTag.FaultCode) ||
                !snapshot.Seen.Contains(LogicalTag.Mode))
            {
                continue;
            }
            var state = StateMapper.Map(
                snapshot.IsRunning!.Value,
                snapshot.FaultCode!.Value,
                snapshot.Mode!.Value
            );
            if (state == snapshot.LastState)
            {
                snapshot.Recovering = false;
                continue;
            }
            logger.LogInformation(
                "Machine {Line}.{Machine} state: {State}{Recovered}",
                change.Line, change.Machine, state,
                snapshot.Recovering ? " (recovered, time approximate)" : "");
            snapshot.LastState = state;
            snapshot.Recovering = false;
        }
    }

    private void HandleCounter(TagChange change, MachineSnapshot snapshot, ushort value)
    {
        if (!snapshot.Counters.TryGetValue(change.Tag, out var previous))
        {
            snapshot.Counters[change.Tag] = value;
            return;
        }
        var max = _counterMax[(change.Line, change.Machine)];
        var delta = CounterDelta.Compute(previous, value, max);
        snapshot.Counters[change.Tag] = value;
        if (delta is null)
        {
            logger.LogWarning(
                "Counter {Tag} of {Line}.{Machine} dropped from {Previous} to {Value} without reaching {Max}: PLC reset, no delta.",
                change.Tag, change.Line, change.Machine, previous, value, max);
            return;
        }
        if (delta > 0)
        {
            logger.LogInformation(
                "Counter {Tag} of {Line}.{Machine} increased by {Delta} (from {Previous} to {Value}, max {Max}).",
                change.Tag, change.Line, change.Machine, delta, previous, value, max);
        }
    }
}
