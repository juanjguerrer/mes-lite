
namespace MesLite.Collector.Mapping;

public sealed class MachineSnapshot
{
    public bool? IsRunning { get; set; }
    public ushort? FaultCode { get; set; }
    public MachineMode? Mode { get; set; }
    public int Generation { get; set; }
}
