using System.Threading.Channels;
using MesLite.Collector.Configuration;
using Microsoft.Extensions.Options;
using Opc.Ua;
using Opc.Ua.Client;
using Opc.Ua.Client.Subscriptions;

namespace MesLite.Collector.OpcUa;

public class TagSubscriber(
    ILogger<TagSubscriber> logger,
    ChannelWriter<TagChange> writer,
    IHostApplicationLifetime lifetime,
    NodeIdResolver resolver,
    IOptions<CollectorOptions> options
) : ISubscriptionNotificationHandler, IAsyncDisposable
{
    private readonly Dictionary<string, (MachineOptions Machine, LogicalTag Tag)> _itemMap = new();
    private ISubscription? _subscription;
    private int _generation = 0;
    private ushort _nsIndex;

    private ManagedSession? _session;
    private string? _namespaceUri;

    private readonly CollectorOptions _config = options.Value;
    public void Subscribe(
        ManagedSession session
    )
    {
        _itemMap.Clear();
        var machines = _config.Machines;
        foreach (var machine in machines)
        {
            foreach (var tag in Enum.GetValues<LogicalTag>())
            {
                _itemMap[$"{machine.Line}.{machine.Machine}.{tag}"] = (machine, tag);
            }
        }
        var uri = _config.Opc.NamespaceUri;
        var index = session.NamespaceUris.GetIndex(uri!);
        if (index < 0)
        {
            throw new InvalidOperationException($"NamespaceUri '{uri}' not found in server.");
        }
        var nsIndex = (ushort)index;
        _session = session;
        _namespaceUri = uri;
        _nsIndex = nsIndex;
        var subscription = session.AddSubscription(
            this,
            new Opc.Ua.Client.Subscriptions.SubscriptionOptions
            {
                PublishingInterval = _config.Opc.PublishingInterval,
                KeepAliveCount = _config.Opc.KeepAliveCount,
                LifetimeCount = _config.Opc.LifetimeCount,
                PublishingEnabled = true
            });
        _subscription = subscription;
        foreach (var (itemName, (machine, tag)) in _itemMap)
        {
            var nodeId = new NodeId(resolver.Resolve(machine, tag), nsIndex);
            var added = subscription.TryAddMonitoredItem(itemName, nodeId,
                itemOptions  => itemOptions with
                {
                    SamplingInterval = _config.Opc.SamplingInterval,
                    QueueSize = _config.Opc.QueueSize,
                    DiscardOldest = true
                },
                out var monitoredItem);
            if (!added || monitoredItem is null)
            {
                throw new InvalidOperationException($"Failed to add monitored item for {itemName} (NodeId: {nodeId})");
            }
        }
    }
    public ValueTask OnDataChangeNotificationAsync(ISubscription subscription, uint sequenceNumber, DateTime publishTime, ReadOnlyMemory<DataValueChange> notification, PublishState publishStateMask, IReadOnlyList<string> stringTable)
    {
        foreach (var change in notification.Span)
        {
            if (change.MonitoredItem is null
                || !_itemMap.TryGetValue(change.MonitoredItem.Name, out var info))
            {
                continue;
            }
            // Machine and Line are guaranteed to be non-null because they are checked in the _itemMap.
            var tagChange = new TagChange(
                Machine: info.Machine.Machine!,
                Line: info.Machine.Line!,
                Tag: info.Tag,
                Value: change.Value.WrappedValue.AsBoxedObject(),
                StatusCode: change.Value.StatusCode,
                SourceTimestamp: change.Value.SourceTimestamp.ToDateTime(),
                Generation: Volatile.Read(ref _generation)
            );
            if (!writer.TryWrite(tagChange))
            {
                logger.LogWarning(
                    "Failed to write tag change for {Machine}.{Tag}",
                    info.Machine.Machine,
                    info.Tag);
            }
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
        if (state == Opc.Ua.Client.Subscriptions.SubscriptionState.Created)
        {
            Interlocked.Increment(ref _generation);
            CheckNamespaceIndex();
        }
        logger.LogInformation(
            "Subscription state changed: {State}, PublishState: {PublishState}",
            state,
            publishStateMask);
        return ValueTask.CompletedTask;
    }

    private void CheckNamespaceIndex()
    {
        if (_session is null || _namespaceUri is null)
        {
            return;
        }
        var current = _session.NamespaceUris.GetIndex(_namespaceUri);
        if (current == _nsIndex) return;
        logger.LogCritical(
            "Namespace index for '{NamespaceUri}' changed from {OldIndex} to {NewIndex}. This may indicate a server restart or reconfiguration. The collector will stop.",
            _namespaceUri,
            _nsIndex,
            current);
        Environment.ExitCode = 1;
        lifetime.StopApplication();
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
