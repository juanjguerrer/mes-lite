using MesLite.Collector.Mapping;

namespace MesLite.Collector.Tests.Mapping;

public class CounterDeltaTests
{
    [Theory]
    [InlineData(0u, 1u, 1u)]
    [InlineData(10u, 15u, 5u)]
    [InlineData(99u, 100u, 1u)]
    public void Compute_WhenTheCounterGrows_ReturnsTheDifference(uint previous, uint current, uint expected)
    {
        Assert.Equal(expected, CounterDelta.Compute(previous, current, max: 100));
    }

    [Fact]
    public void Compute_WhenTheValueDoesNotChange_ReturnsZero()
    {
        Assert.Equal(0u, CounterDelta.Compute(previous: 42, current: 42, max: 100));
    }

    [Fact]
    public void Compute_WhenItStaysAtTheMaximum_ReturnsZero()
    {
        Assert.Equal(0u, CounterDelta.Compute(previous: 100, current: 100, max: 100));
    }

    [Theory]
    [InlineData(0u, 1u)]
    [InlineData(1u, 2u)]
    [InlineData(4u, 5u)]
    public void Compute_WhenThePreviousValueWasTheMaximum_CountsTheRolloverAsOneStep(uint current, uint expected)
    {
        Assert.Equal(expected, CounterDelta.Compute(previous: 100, current, max: 100));
    }

    [Fact]
    public void Compute_RolloverUsesTheMaximumOfTheMachine()
    {
        Assert.Equal(1u, CounterDelta.Compute(previous: ushort.MaxValue, current: 0, max: ushort.MaxValue));
    }

    [Theory]
    [InlineData(50u, 0u)]
    [InlineData(99u, 3u)]
    [InlineData(1u, 0u)]
    public void Compute_WhenTheValueDropsBeforeTheMaximum_ReturnsNullBecauseThePlcWasReset(uint previous, uint current)
    {
        Assert.Null(CounterDelta.Compute(previous, current, max: 100));
    }
}
