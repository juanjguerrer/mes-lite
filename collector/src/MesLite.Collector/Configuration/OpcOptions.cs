namespace MesLite.Collector.Configuration;

public class OpcOptions
{
    public string? EndpointUrl { get; init; }
    public string? NamespaceUri { get; init; }

    public uint QueueSize { get; init; } = 10;

    public uint KeepAliveCount { get; init; } = 10;

    public uint LifetimeCount { get; init; } = 30;

    public TimeSpan SamplingInterval { get; init; } = TimeSpan.FromMilliseconds(250);

    public TimeSpan PublishingInterval { get; init; } = TimeSpan.FromSeconds(1);

    public TimeSpan ReconnectInitialDelay { get; init; } = TimeSpan.FromSeconds(1);

    public TimeSpan ReconnectMaxDelay { get; init; } = TimeSpan.FromSeconds(30);

}
