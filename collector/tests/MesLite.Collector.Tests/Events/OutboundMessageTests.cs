using System.Text.Json;
using MesLite.Collector.Events;

namespace MesLite.Collector.Tests.Events;

public class OutboundMessageTests
{
    private static readonly Guid Id = Guid.Parse("0197a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static readonly DateTimeOffset At = new(2026, 10, 27, 10, 0, 0, TimeSpan.Zero);

    [Fact]
    public void StateMessage_UsesTheStateTopic()
    {
        var message = OutboundMessage.From("ASM", "ROBOT-01", new StateEvent(Id, EventState.Running, At));

        Assert.Equal("mes-lite/ASM/ROBOT-01/state", message.topic);
        Assert.Equal(Id, message.eventId);
    }

    [Fact]
    public void ProductionMessage_UsesTheProductionTopic()
    {
        var message = OutboundMessage.From("PACK", "FILLER-01", new ProductionEvent(Id, 3, 1, At.AddSeconds(-10), At));

        Assert.Equal("mes-lite/PACK/FILLER-01/production", message.topic);
        Assert.Equal(Id, message.eventId);
    }

    [Fact]
    public void StateMessage_PayloadMatchesTheMesContract()
    {
        var message = OutboundMessage.From("ASM", "ROBOT-01", new StateEvent(Id, EventState.Down, At, "FaultCode 101"));

        using var json = JsonDocument.Parse(message.payload);
        var root = json.RootElement;
        Assert.Equal(Id.ToString(), root.GetProperty("eventId").GetString());
        Assert.Equal("DOWN", root.GetProperty("state").GetString());
        Assert.Equal(At, root.GetProperty("occurredAt").GetDateTimeOffset());
        Assert.Equal("FaultCode 101", root.GetProperty("reason").GetString());
    }

    [Fact]
    public void StateMessage_WritesPlannedStopWithAnUnderscore()
    {
        var message = OutboundMessage.From("ASM", "ROBOT-01", new StateEvent(Id, EventState.PlannedStop, At));

        using var json = JsonDocument.Parse(message.payload);
        Assert.Equal("PLANNED_STOP", json.RootElement.GetProperty("state").GetString());
    }

    [Fact]
    public void StateMessage_PayloadHasExactlyTheFieldsTheMesExpects()
    {
        var message = OutboundMessage.From("ASM", "ROBOT-01", new StateEvent(Id, EventState.Idle, At));

        using var json = JsonDocument.Parse(message.payload);
        var names = json.RootElement.EnumerateObject().Select(p => p.Name).Order().ToArray();
        Assert.Equal(["eventId", "occurredAt", "reason", "sequence", "state"], names);
    }

    [Fact]
    public void ProductionMessage_PayloadMatchesTheMesContract()
    {
        var start = At.AddSeconds(-10);
        var message = OutboundMessage.From("PACK", "FILLER-01", new ProductionEvent(Id, 3, 1, start, At));

        using var json = JsonDocument.Parse(message.payload);
        var root = json.RootElement;
        Assert.Equal(Id.ToString(), root.GetProperty("eventId").GetString());
        Assert.Equal(3, root.GetProperty("goodCount").GetInt64());
        Assert.Equal(1, root.GetProperty("scrapCount").GetInt64());
        Assert.Equal(start, root.GetProperty("intervalStart").GetDateTimeOffset());
        Assert.Equal(At, root.GetProperty("intervalEnd").GetDateTimeOffset());
    }

    [Fact]
    public void ProductionMessage_PayloadHasExactlyTheFieldsTheMesExpects()
    {
        var message = OutboundMessage.From("PACK", "FILLER-01", new ProductionEvent(Id, 3, 1, At.AddSeconds(-10), At));

        using var json = JsonDocument.Parse(message.payload);
        var names = json.RootElement.EnumerateObject().Select(p => p.Name).Order().ToArray();
        Assert.Equal(["eventId", "goodCount", "intervalEnd", "intervalStart", "scrapCount", "sequence"], names);
    }

    [Fact]
    public void StateMessage_CarriesTheSequenceInThePayloadAndTheEnvelope()
    {
        var message = OutboundMessage.From("ASM", "ROBOT-01", new StateEvent(Id, EventState.Idle, At, Sequence: 7));

        using var json = JsonDocument.Parse(message.payload);
        Assert.Equal(7, json.RootElement.GetProperty("sequence").GetInt64());
        Assert.Equal(7, message.sequence);
        Assert.Equal("ASM", message.line);
        Assert.Equal("ROBOT-01", message.machine);
    }

    [Fact]
    public void ProductionMessage_CarriesTheSequenceInThePayloadAndTheEnvelope()
    {
        var message = OutboundMessage.From("PACK", "FILLER-01", new ProductionEvent(Id, 3, 1, At.AddSeconds(-10), At, Sequence: 12));

        using var json = JsonDocument.Parse(message.payload);
        Assert.Equal(12, json.RootElement.GetProperty("sequence").GetInt64());
        Assert.Equal(12, message.sequence);
    }

    [Fact]
    public void Payload_WritesInstantsInUtc()
    {
        var message = OutboundMessage.From("ASM", "ROBOT-01", new StateEvent(Id, EventState.Running, At));

        using var json = JsonDocument.Parse(message.payload);
        var text = json.RootElement.GetProperty("occurredAt").GetString()!;
        Assert.Contains(text[^6..], new[] { "+00:00" });
        Assert.StartsWith("2026-10-27T10:00:00", text);
    }
}
