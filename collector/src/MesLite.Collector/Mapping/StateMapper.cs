namespace MesLite.Collector.Mapping;

public static class StateMapper
{
    public static MachineState Map(bool isRunning, ushort faultCode, MachineMode mode) =>
        faultCode != 0 ? MachineState.Down :
        mode switch
        {
            MachineMode.Setup => MachineState.Setup,
            MachineMode.PlannedStop => MachineState.PlannedStop,
            MachineMode.Auto => isRunning ? MachineState.Running : MachineState.Idle,
            _ => throw new ArgumentOutOfRangeException(nameof(mode), mode, "Unknown machine mode.")
        };
}
