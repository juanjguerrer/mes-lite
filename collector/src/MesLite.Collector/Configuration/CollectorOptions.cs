namespace MesLite.Collector.Configuration;

public class CollectorOptions
{
    public OpcOptions Opc { get; init; } = new();

    public Dictionary<string, string> Tags { get; init; } = new();

    public List<MachineOptions> Machines { get; init; } = new();

    public TimeSpan ProductionInterval { get; init; } = TimeSpan.FromSeconds(10);
}
