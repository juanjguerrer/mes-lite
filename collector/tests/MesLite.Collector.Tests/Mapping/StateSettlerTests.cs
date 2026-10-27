using MesLite.Collector.Events;
using MesLite.Collector.Mapping;

namespace MesLite.Collector.Tests.Mapping;

public class StateSettlerTests
{
    private static readonly DateTimeOffset T0 = new(2026, 10, 27, 10, 0, 0, TimeSpan.Zero);
    private static readonly TimeSpan Settle = TimeSpan.FromSeconds(1);

    private static StateEvent Event(EventState state, DateTimeOffset occurredAt) =>
        new(Guid.CreateVersion7(), state, occurredAt);

    [Fact]
    public void Offer_HoldsTheFirstEventOfAMachine()
    {
        var settler = new StateSettler();

        var released = settler.Offer("ASM", "ROBOT-01", Event(EventState.Running, T0), T0);

        Assert.Null(released);
    }

    [Fact]
    public void Offer_ReplacesTheHeldEventWhenOccurredAtIsTheSame()
    {
        var settler = new StateSettler();
        var idle = Event(EventState.Idle, T0);
        var setup = Event(EventState.Setup, T0);
        settler.Offer("ASM", "ROBOT-01", idle, T0);

        var released = settler.Offer("ASM", "ROBOT-01", setup, T0.AddMilliseconds(10));

        Assert.Null(released);
        var final = Assert.Single(settler.Release(T0.AddSeconds(5), Settle));
        Assert.Equal(setup, final.Event);
    }

    [Fact]
    public void Offer_ReleasesThePreviousEventWhenOccurredAtDiffers()
    {
        var settler = new StateSettler();
        var running = Event(EventState.Running, T0);
        var down = Event(EventState.Down, T0.AddSeconds(5));
        settler.Offer("ASM", "ROBOT-01", running, T0);

        var released = settler.Offer("ASM", "ROBOT-01", down, T0.AddSeconds(5));

        Assert.Equal(running, released);
        var final = Assert.Single(settler.Release(T0.AddSeconds(10), Settle));
        Assert.Equal(down, final.Event);
    }

    [Fact]
    public void Offer_KeepsMachinesIndependent()
    {
        var settler = new StateSettler();
        settler.Offer("ASM", "ROBOT-01", Event(EventState.Running, T0), T0);

        var released = settler.Offer("ASM", "ROBOT-02", Event(EventState.Idle, T0.AddSeconds(5)), T0.AddSeconds(5));

        Assert.Null(released);
        Assert.Equal(2, settler.Release(T0.AddSeconds(10), Settle).Count);
    }

    [Fact]
    public void Release_ReturnsNothingBeforeTheSettleTime()
    {
        var settler = new StateSettler();
        settler.Offer("ASM", "ROBOT-01", Event(EventState.Running, T0), T0);

        var released = settler.Release(T0.AddMilliseconds(999), Settle);

        Assert.Empty(released);
    }

    [Fact]
    public void Release_ReturnsTheEventOnceTheSettleTimeHasPassed()
    {
        var settler = new StateSettler();
        var running = Event(EventState.Running, T0);
        settler.Offer("ASM", "ROBOT-01", running, T0);

        var released = Assert.Single(settler.Release(T0.Add(Settle), Settle));

        Assert.Equal("ASM", released.Line);
        Assert.Equal("ROBOT-01", released.Machine);
        Assert.Equal(running, released.Event);
    }

    [Fact]
    public void Release_DoesNotReturnTheSameEventTwice()
    {
        var settler = new StateSettler();
        settler.Offer("ASM", "ROBOT-01", Event(EventState.Running, T0), T0);
        settler.Release(T0.AddSeconds(2), Settle);

        var second = settler.Release(T0.AddSeconds(3), Settle);

        Assert.Empty(second);
    }

    [Fact]
    public void Release_CountsTheWaitFromTheLastOfferForThatMoment()
    {
        var settler = new StateSettler();
        settler.Offer("ASM", "ROBOT-01", Event(EventState.Idle, T0), T0);
        settler.Offer("ASM", "ROBOT-01", Event(EventState.Setup, T0), T0.AddMilliseconds(800));

        var released = settler.Release(T0.AddMilliseconds(1500), Settle);

        Assert.Empty(released);
    }

    [Fact]
    public void Release_OnlyReturnsTheMachinesThatAreDue()
    {
        var settler = new StateSettler();
        settler.Offer("ASM", "ROBOT-01", Event(EventState.Running, T0), T0);
        settler.Offer("ASM", "ROBOT-02", Event(EventState.Idle, T0.AddSeconds(2)), T0.AddSeconds(2));

        var released = Assert.Single(settler.Release(T0.AddSeconds(2), Settle));

        Assert.Equal("ROBOT-01", released.Machine);
    }
}
