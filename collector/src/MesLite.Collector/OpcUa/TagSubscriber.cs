namespace MesLite.Collector.OpcUa;

using MesLite.Collector.Configuration;
using Microsoft.Extensions.Options;
using Opc.Ua;
using Opc.Ua.Client;
using Opc.Ua.Client.Subscriptions;

public class TagSubscriber(
    ILogger<TagSubscriber> logger
) : ISubscriptionNotificationHandler, IAsyncDisposable
{
    private readonly Dictionary<string, (MachineOptions Machine, LogicalTag Tag)> _itemMap = new();
    private ISubscription? _subscription;
    public ISubscription Subscribe(
        ManagedSession session,
        IReadOnlyList<MachineOptions> machines,
        NodeIdResolver resolver,
        IOptions<CollectorOptions> config,
        ushort nsIndex
    )
    {
        foreach (var machine in machines)
        {
            foreach (var tag in Enum.GetValues<LogicalTag>())
            {
                _itemMap[$"{machine.Line}.{machine.Machine}.{tag}"] = (machine, tag);
            }
        }
        var subscription = session.AddSubscription(
            this,
            new Opc.Ua.Client.Subscriptions.SubscriptionOptions
            {
                PublishingInterval = config.Value.Opc.PublishingInterval,
                KeepAliveCount = config.Value.Opc.KeepAliveCount,
                LifetimeCount = config.Value.Opc.LifetimeCount,
                PublishingEnabled = true
            });
        _subscription = subscription;
        foreach (var (itemName, (machine, tag)) in _itemMap)
        {
            var nodeId = new NodeId(resolver.Resolve(machine, tag), nsIndex);
            var added = subscription.TryAddMonitoredItem(itemName, nodeId,
                options => options with
                {
                    SamplingInterval = config.Value.Opc.SamplingInterval,
                    QueueSize = config.Value.Opc.QueueSize,
                    DiscardOldest = true
                },
                out var monitoredItem);
            if (!added || monitoredItem is null)
            {
                logger.LogError("Failed to add monitored item for {ItemName} with NodeId {NodeId}", itemName, nodeId);
                continue;
            }
        }
        return subscription;
    }
    public ValueTask OnDataChangeNotificationAsync(ISubscription subscription, uint sequenceNumber, DateTime publishTime, ReadOnlyMemory<DataValueChange> notification, PublishState publishStateMask, IReadOnlyList<string> stringTable)
    {
        logger.LogInformation(
            "Data change notification received. SequenceNumber: {SequenceNumber}, PublishTime: {PublishTime}, PublishState: {PublishState}, ItemCount: {ItemCount}",
            sequenceNumber,
            publishTime,
            publishStateMask,
            notification.Length
        );
        foreach (var change in notification.Span)
        {
            if (change.MonitoredItem is null
                || !_itemMap.TryGetValue(change.MonitoredItem.Name, out var info))
            {
                logger.LogWarning("Received data change for unknown monitored item.");
                continue;
            }
            logger.LogInformation(
                "Data change received. Item: {Item}, Value: {Value}, Status: {Status}, SourceTimestamp: {SourceTimestamp}",
                change.MonitoredItem?.ToString() ?? "<null>",
                change.Value.WrappedValue,
                change.Value.StatusCode,
                change.Value.SourceTimestamp
            );
        }
        return ValueTask.CompletedTask;
    }

    public ValueTask OnEventDataNotificationAsync(ISubscription subscription, uint sequenceNumber, DateTime publishTime, ReadOnlyMemory<EventNotification> notification, PublishState publishStateMask, IReadOnlyList<string> stringTable)
    {
        return ValueTask.CompletedTask;
    }

    public ValueTask OnKeepAliveNotificationAsync(ISubscription subscription, uint sequenceNumber, DateTime publishTime, PublishState publishStateMask)
    {
        return ValueTask.CompletedTask;
    }

    public ValueTask OnSubscriptionStateChangedAsync(ISubscription subscription, Opc.Ua.Client.Subscriptions.SubscriptionState state, PublishState publishStateMask, CancellationToken ct = default)
    {
        logger.LogInformation(
            "Subscription state changed: {State}, PublishState: {PublishState}",
            state,
            publishStateMask);
        return ValueTask.CompletedTask;
    }

    public async ValueTask DisposeAsync()
    {
        if (_subscription is not null)
        {
            await _subscription.DisposeAsync();
            _subscription = null;
        }
    }
}
