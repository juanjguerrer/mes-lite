using Microsoft.Extensions.Options;

namespace MesLite.Collector.Configuration;

public class CollectorOptionsValidator : IValidateOptions<CollectorOptions>
{
    public ValidateOptionsResult Validate(string? name, CollectorOptions options)
    {
        var errors = new List<string>();

        ValidateOpc(options.Opc, errors);
        ValidateTags(options.Tags, errors);
        ValidateMachines(options.Machines, errors);

        return errors.Count > 0
            ? ValidateOptionsResult.Fail(errors)
            : ValidateOptionsResult.Success;
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
