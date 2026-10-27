using System.Threading.Channels;
using MesLite.Collector.Configuration;
using MesLite.Collector.Events;
using MesLite.Collector.OpcUa;
using MesLite.Collector.Storage;
using Microsoft.Extensions.Options;
using Opc.Ua;

namespace MesLite.Collector.Mapping;

public sealed class MappingService(
    ChannelReader<TagChange> reader,
    ILogger<MappingService> logger,
    IOptions<CollectorOptions> options,
    ChannelWriter<OutboundMessage> outbound,
    SequenceAllocator sequenceAllocator
) : BackgroundService
{

    private readonly Dictionary<(string Line, string Machine), MachineStateTracker> _trackers = new();
    private readonly Dictionary<(string Line, string Machine, LogicalTag Tag), ushort> _counters = new();
    private readonly Dictionary<(string Line, string Machine), ProductionAccumulator> _accumulators = new();
    private readonly object _gate = new();
    private readonly StateSettler _stateSettler = new();

    private readonly Dictionary<(string Line, string Machine), uint> _counterMax =
        options.Value.Machines.ToDictionary(
            m => (m.Line!, m.Machine!),
            m => m.CounterMax ?? ushort.MaxValue);

    protected override Task ExecuteAsync(CancellationToken stoppingToken) =>
        Task.WhenAll(ReadLoopAsync(stoppingToken), WindowLoopAsync(stoppingToken),
        SettleLoopAsync(stoppingToken));

    private void HandleCounter(TagChange change, ushort value)
    {
        var counterKey = (change.Line, change.Machine, change.Tag);
        if (!_counters.TryGetValue(counterKey, out var previous))
        {
            _counters[counterKey] = value;
            return;
        }
        var max = _counterMax[(change.Line, change.Machine)];
        var delta = CounterDelta.Compute(previous, value, max);
        _counters[counterKey] = value;
        if (delta is null)
        {
            logger.LogWarning(
                "Counter {Tag} of {Line}.{Machine} dropped from {Previous} to {Value} without reaching {Max}: PLC reset, no delta.",
                change.Tag, change.Line, change.Machine, previous, value, max);
            return;
        }
        if (delta > 0)
        {
            lock (_gate)
            {
                if (!_accumulators.TryGetValue((change.Line, change.Machine), out var accumulator))
                {
                    accumulator = new ProductionAccumulator();
                    _accumulators.Add((change.Line, change.Machine), accumulator);
                }
                accumulator.Add(change.Tag, delta.Value, new DateTimeOffset(change.SourceTimestamp));
            }
        }
    }

    private async Task ReadLoopAsync(CancellationToken ct)
    {
        await foreach (var change in reader.ReadAllAsync(ct))
        {
            if (string.IsNullOrWhiteSpace(change.Line) ||
                string.IsNullOrWhiteSpace(change.Machine))
            {
                logger.LogWarning("Tag change has no line or machine.");
                continue;
            }
            if (StatusCode.IsBad(change.StatusCode))
            {
                logger.LogWarning(
                    "Tag change for {Line}.{Machine} has bad status code {StatusCode}.",
                    change.Line,
                    change.Machine,
                    change.StatusCode
                );
            }
            if (change.Tag is LogicalTag.GoodCounter or LogicalTag.ScrapCounter)
            {
                if (!StatusCode.IsBad(change.StatusCode) && change.Value is ushort counter)
                {
                    HandleCounter(change, counter);
                }
                continue;
            }

            var key = (change.Line, change.Machine);
            if (!_trackers.TryGetValue(key, out var tracker))
            {
                tracker = new MachineStateTracker();
                _trackers.Add(key, tracker);
            }
            var stateEvent = tracker.Apply(change);
            if (stateEvent is not null)
            {
                lock (_gate)
                {
                    var settled = _stateSettler.Offer(change.Line, change.Machine, stateEvent, DateTimeOffset.UtcNow);
                    if (settled is not null)
                    {
                        var numbered = settled with { Sequence = sequenceAllocator.Next(change.Line, change.Machine) };
                        var message = OutboundMessage.From(change.Line, change.Machine, numbered);
                        if (!outbound.TryWrite(message))
                        {
                            logger.LogWarning("Failed to write outbound message for {Line}.{Machine}.", change.Line, change.Machine);
                        }
                    }
                }
            }
        }
    }

    private async Task WindowLoopAsync(CancellationToken ct)
    {
        var interval = options.Value.ProductionInterval;
        using var periodicTimer = new PeriodicTimer(interval);
        try
        {
            while (await periodicTimer.WaitForNextTickAsync(ct))
            {
                var now = DateTimeOffset.UtcNow;
                var closed = new List<((string Line, string Machine) Key, ProductionEvent Event)>();
                lock (_gate)
                {
                    foreach (var (key, accumulator) in _accumulators)
                    {
                        var production = accumulator.Close(now);
                        if (production is not null)
                        {
                            closed.Add((key, production));
                        }
                    }
                    foreach (var (key, production) in closed)
                    {
                        var productionWithSequence = production with { Sequence = sequenceAllocator.Next(key.Line, key.Machine) };
                        var message = OutboundMessage.From(key.Line, key.Machine, productionWithSequence);
                        if (!outbound.TryWrite(message))
                        {
                            logger.LogWarning("Failed to write outbound message for {Line}.{Machine}.", key.Line, key.Machine);
                        }
                    }
                }
            }
        }
        catch (OperationCanceledException)
        {
            logger.LogInformation("Mapping service stopping");
        }
    }
    private async Task SettleLoopAsync(CancellationToken ct)
    {
        var settleTime = options.Value.StateSettleTime;
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(250));
        try
        {
            while (await timer.WaitForNextTickAsync(ct))
            {
                IReadOnlyList<(string Line, string Machine, StateEvent Event)> released;
                lock (_gate)
                {
                    released = _stateSettler.Release(DateTimeOffset.UtcNow, settleTime);
                    foreach (var (line, machine, stateEvent) in released)
                    {
                        var numberedEvent = stateEvent with { Sequence = sequenceAllocator.Next(line, machine) };
                        var message = OutboundMessage.From(line, machine, numberedEvent);
                        if (!outbound.TryWrite(message))
                        {
                            logger.LogWarning("Failed to write outbound message for {Line}.{Machine}.", line, machine);
                        }
                    }
                }
            }
        }
        catch (OperationCanceledException)
        {
        }
    }
}
