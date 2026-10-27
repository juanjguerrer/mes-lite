using Microsoft.Extensions.Options;

namespace MesLite.Collector.Configuration;

public class CollectorOptionsValidator : IValidateOptions<CollectorOptions>
{
    public ValidateOptionsResult Validate(string? name, CollectorOptions options)
    {
        var errors = new List<string>();
        ValidateCollectorOptions(options, errors);
        ValidateMqtt(options.Mqtt, errors);
        ValidateOpc(options.Opc, errors);
        ValidateTags(options.Tags, errors);
        ValidateMachines(options.Machines, errors);

        return errors.Count > 0
            ? ValidateOptionsResult.Fail(errors)
            : ValidateOptionsResult.Success;
    }

    private static void ValidateCollectorOptions(CollectorOptions options, List<string> errors)
    {
        if (options.ProductionInterval <= TimeSpan.Zero)
        {
            errors.Add("ProductionInterval must be greater than 0.");
        }

        if (string.IsNullOrWhiteSpace(options.OutboxPath))
        {
            errors.Add("OutboxPath is required.");
        }
    }

    private static void ValidateMqtt(MqttOptions mqtt, List<string> errors)
    {
        if (string.IsNullOrWhiteSpace(mqtt.Host))
        {
            errors.Add("Mqtt.Host is required.");
        }

        if (mqtt.Port is < 1 or > 65535)
        {
            errors.Add("Mqtt.Port must be between 1 and 65535.");
        }

        if (string.IsNullOrWhiteSpace(mqtt.ClientId))
        {
            errors.Add("Mqtt.ClientId is required.");
        }

        if (mqtt.RetryDelay <= TimeSpan.Zero)
        {
            errors.Add("Mqtt.RetryDelay must be greater than 0.");
        }
    }

    private static void ValidateOpc(OpcOptions opc, List<string> errors)
    {
        if (string.IsNullOrWhiteSpace(opc.EndpointUrl))
        {
            errors.Add("Opc.EndpointUrl is required.");
        }
        else if (!Uri.TryCreate(opc.EndpointUrl, UriKind.Absolute, out var uri)
                || uri.Scheme != "opc.tcp")
        {
            errors.Add($"Opc.EndpointUrl '{opc.EndpointUrl}' is not a valid opc.tcp URL.");
        }

        if (string.IsNullOrWhiteSpace(opc.NamespaceUri))
        {
            errors.Add("Opc.NamespaceUri is required.");
        }

        if (opc.QueueSize == 0)
        {
            errors.Add("Opc.QueueSize must be greater than 0.");
        }

        if (opc.KeepAliveCount == 0)
        {
            errors.Add("Opc.KeepAliveCount must be greater than 0.");
        }

        if (opc.LifetimeCount == 0)
        {
            errors.Add("Opc.LifetimeCount must be greater than 0.");
        }
        else if (opc.LifetimeCount < 3 * opc.KeepAliveCount)
        {
            errors.Add("Opc.LifetimeCount must be at least 3 times Opc.KeepAliveCount.");
        }

        // TimeSpan values are written as hh:mm:ss.fff; a bare "250" would be read as 250 days.
        if (opc.SamplingInterval <= TimeSpan.Zero)
        {
            errors.Add("Opc.SamplingInterval must be greater than 0.");
        }

        if (opc.PublishingInterval <= TimeSpan.Zero)
        {
            errors.Add("Opc.PublishingInterval must be greater than 0.");
        }
        else if (opc.PublishingInterval > TimeSpan.FromMinutes(1))
        {
            errors.Add("Opc.PublishingInterval must not exceed 1 minute.");
        }
        else if (opc.SamplingInterval > opc.PublishingInterval)
        {
            errors.Add("Opc.SamplingInterval must not exceed Opc.PublishingInterval.");
        }
        if (opc.ReconnectInitialDelay <= TimeSpan.Zero)
        {
            errors.Add("Opc.ReconnectInitialDelay must be greater than 0.");
        }

        if (opc.ReconnectMaxDelay < opc.ReconnectInitialDelay)
        {
            errors.Add("Opc.ReconnectMaxDelay must not be less than Opc.ReconnectInitialDelay.");
        }
        else if (opc.ReconnectMaxDelay > TimeSpan.FromMinutes(5))
        {
            errors.Add("Opc.ReconnectMaxDelay must not exceed 5 minutes.");
        }
    }

    private static void ValidateTags(Dictionary<string, string> tags, List<string> errors)
    {
        foreach (var key in tags.Keys)
        {
            if (!Enum.TryParse<LogicalTag>(key, out _))
            {
                errors.Add($"Tags: unknown logical tag '{key}'.");
            }
        }

        foreach (var tag in Enum.GetValues<LogicalTag>())
        {
            if (!tags.TryGetValue(tag.ToString(), out var template)
                || string.IsNullOrWhiteSpace(template))
            {
                errors.Add($"Tags: '{tag}' is missing or empty.");
            }
        }
    }

    private static void ValidateMachines(List<MachineOptions> machines, List<string> errors)
    {
        if (machines.Count == 0)
        {
            errors.Add("Machines: at least one machine is required.");
            return;
        }

        var seen = new HashSet<(string, string)>();

        for (var i = 0; i < machines.Count; i++)
        {
            var m = machines[i];
            var hasLine = !string.IsNullOrWhiteSpace(m.Line);
            var hasMachine = !string.IsNullOrWhiteSpace(m.Machine);

            if (!hasLine)
            {
                errors.Add($"Machines[{i}]: Line is required.");
            }

            if (!hasMachine)
            {
                errors.Add($"Machines[{i}]: Machine is required.");
            }

            if (hasLine && hasMachine && !seen.Add((m.Line!, m.Machine!)))
            {
                errors.Add($"Machines[{i}]: duplicate machine '{m.Line}.{m.Machine}'.");
            }

            if (m.CounterMax == 0)
            {
                errors.Add($"Machines[{i}]: CounterMax must be greater than 0.");
            }
        }
    }
}
