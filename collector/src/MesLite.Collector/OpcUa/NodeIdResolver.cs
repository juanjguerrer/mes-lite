using MesLite.Collector.Configuration;

namespace MesLite.Collector.OpcUa;

public sealed class NodeIdResolver
{
    private Dictionary<string, string> Tags { get; init; }
    public NodeIdResolver(Dictionary<string, string> tags)
    {
        Tags = tags;
    }
    public string Resolve(MachineOptions machine, LogicalTag tag)
    {
        if (!Tags.TryGetValue(tag.ToString(), out var template) || string.IsNullOrEmpty(template))
        {
            throw new InvalidOperationException($"Tag {tag} not found in configuration");
        }
        if (string.IsNullOrEmpty(machine.Line) || string.IsNullOrEmpty(machine.Machine))
        {
            throw new InvalidOperationException($"Machine {machine.Machine}, {machine.Line} has no line or machine configured");
        }
        var identifier = template.Replace("{line}", machine.Line)
            .Replace("{machine}", machine.Machine);

        if (identifier.Contains("{") || identifier.Contains("}"))
        {
            throw new InvalidOperationException($"Tag {tag} for machine {machine.Machine}, {machine.Line} has unresolved placeholders in identifier: {identifier}");
        }
        return identifier;
    }
}
