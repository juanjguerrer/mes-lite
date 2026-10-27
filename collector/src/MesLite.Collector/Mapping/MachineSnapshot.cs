
using MesLite.Collector.Configuration;

namespace MesLite.Collector.Mapping;

public sealed class MachineSnapshot
{
    public bool? IsRunning { get; set; }
    public ushort? FaultCode { get; set; }
    public MachineMode? Mode { get; set; }
    public int Generation { get; set; }
    public HashSet<LogicalTag> Seen { get; } = new();
    public MachineState? LastState { get; set; }
    public bool Recovering { get; set; }
    public Dictionary<LogicalTag, ushort> Counters { get; } = new();
}
