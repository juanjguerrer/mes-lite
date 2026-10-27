

namespace MesLite.Collector.Events;

public record class StateEvent(
    Guid EventId,
    EventState State,
    DateTimeOffset OccurredAt,
    string? Reason = null
)
{

}
