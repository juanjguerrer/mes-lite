using MesLite.Collector.Configuration;
using MesLite.Collector.Events;
using MesLite.Collector.OpcUa;
using Opc.Ua;

namespace MesLite.Collector.Mapping;

public class MachineStateTracker(
)
{
    private readonly MachineSnapshot _snapshot = new();
    public StateEvent? Apply(TagChange change)
    {
        if (StatusCode.IsBad(change.StatusCode))
        {
            if (change.Tag is LogicalTag.Running or LogicalTag.FaultCode or LogicalTag.Mode)
            {
                _snapshot.Seen.Remove(change.Tag);
                if (_snapshot.LastState != MachineState.Unknown)
                {
                    _snapshot.LastState = MachineState.Unknown;
                }
            }
            return null;
        }
        if (change.Generation > _snapshot.Generation)
        {
            _snapshot.Generation = change.Generation;
            _snapshot.Seen.Clear();
            _snapshot.Recovering = _snapshot.LastState.HasValue;
        }
        switch (change.Tag)
        {
            case LogicalTag.Running:
                if (change.Value is bool running)
                    _snapshot.IsRunning = running;
                break;

            case LogicalTag.FaultCode:
                if (change.Value is ushort faultCode)
                    _snapshot.FaultCode = faultCode;
                break;

            case LogicalTag.Mode:
                if (change.Value is ushort mode &&
                    Enum.IsDefined(typeof(MachineMode), mode))
                {
                    _snapshot.Mode = (MachineMode)mode;
                }
                break;
        }
        _snapshot.Seen.Add(change.Tag);
        if (!_snapshot.Seen.Contains(LogicalTag.Running) ||
            !_snapshot.Seen.Contains(LogicalTag.FaultCode) ||
            !_snapshot.Seen.Contains(LogicalTag.Mode))
        {
            return null;
        }
        var state = StateMapper.Map(
            _snapshot.IsRunning!.Value,
            _snapshot.FaultCode!.Value,
            _snapshot.Mode!.Value
        );
        if (state == _snapshot.LastState)
        {
            _snapshot.Recovering = false;
            return null;
        }
        var recovered = _snapshot.Recovering;
        _snapshot.LastState = state;
        _snapshot.Recovering = false;

        var reason = recovered ? "Recovered after recconect"
        : state == MachineState.Down ? $"Fault code: {_snapshot.FaultCode}" : null;

        return new StateEvent(
            Guid.CreateVersion7(),
            ToEventState(state),
            OccurredAt: new DateTimeOffset(change.SourceTimestamp),
            Reason: reason
        );
    }

    private static EventState ToEventState(MachineState state) => state switch
    {
        MachineState.Running => EventState.Running,
        MachineState.Idle => EventState.Idle,
        MachineState.Down => EventState.Down,
        MachineState.Setup => EventState.Setup,
        MachineState.PlannedStop => EventState.PlannedStop,
        _ => throw new ArgumentOutOfRangeException(nameof(state), state, "State has no corresponding event state."),
    };
}
