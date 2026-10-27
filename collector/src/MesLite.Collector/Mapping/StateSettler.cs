using MesLite.Collector.Events;

namespace MesLite.Collector.Mapping;

public class StateSettler
{
    private readonly Dictionary<(string Line, string Machine), (StateEvent Event, DateTimeOffset ReceivedAt)> _held = new();

    public StateEvent? Offer(string line, string machine, StateEvent stateEvent, DateTimeOffset now)
    {
        var key = (line, machine);
        if (_held.TryGetValue(key, out var existing))
        {
            _held[key] = (stateEvent, now);
            return existing.Event.OccurredAt == stateEvent.OccurredAt
                ? null
                : existing.Event;
        }

        _held[key] = (stateEvent, now);
        return null;
    }

    public IReadOnlyList<(string Line, string Machine, StateEvent Event)> Release(DateTimeOffset now, TimeSpan settleTime)
    {
        var released = new List<(string Line, string Machine, StateEvent Event)>();

        foreach (var (key, value) in _held.ToList())
        {
            if (now - value.ReceivedAt >= settleTime)
            {
                released.Add((key.Line, key.Machine, value.Event));
            }
        }
        foreach (var (line, machine, _) in released)
        {
            _held.Remove((line, machine));
        }

        return released;
    }
}
