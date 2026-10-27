using System.Text.Json;

namespace MesLite.Collector.Events;

public record class OutboundMessage(
    string topic,
    string payload,
    Guid eventId
)
{
    public static OutboundMessage From(string line, string machine, StateEvent e) => new OutboundMessage(
            topic: $"mes-lite/{line}/{machine}/state",
            payload: JsonSerializer.Serialize(e, EventJson.Options),
            eventId: e.EventId);

    public static OutboundMessage From(string line, string machine, ProductionEvent e) => new OutboundMessage(
            topic: $"mes-lite/{line}/{machine}/production",
            payload: JsonSerializer.Serialize(e, EventJson.Options),
            eventId: e.EventId);
}
