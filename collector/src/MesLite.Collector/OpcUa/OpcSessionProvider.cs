using MesLite.Collector.Configuration;
using Microsoft.Extensions.Options;
using Opc.Ua;
using Opc.Ua.Client;

namespace MesLite.Collector.OpcUa;

public sealed class OpcSessionProvider(
    IOptions<CollectorOptions> options,
    IManagedSessionFactory factory
)
{
    public async Task<ManagedSession> ConnectAsync(CancellationToken ct)
    {
        var url = options.Value.Opc.EndpointUrl;
        if (string.IsNullOrEmpty(url))
        {
            throw new InvalidOperationException("Opc.EndpointUrl is not configured.");
        }
        var description = new EndpointDescription(url)
        {
            SecurityMode = MessageSecurityMode.None,
            SecurityPolicyUri = SecurityPolicies.None
        };
        var endpoint = new ConfiguredEndpoint(new ConfiguredEndpointCollection(), description);
        var opc = options.Value.Opc;
        return await factory.ConnectAsync(
            endpoint,
            builder => builder.WithReconnectPolicy(policy =>
                policy with
                {
                    InitialDelay = opc.ReconnectInitialDelay,
                    MaxDelay = opc.ReconnectMaxDelay,
                    MaxRetries = 0,
                    JitterFactor = 0.1,
                    MaxTotalReconnectTime = Timeout.InfiniteTimeSpan
                }
            ),
            ct
        );
    }

}
