using MesLite.Collector.Mapping;

namespace MesLite.Collector.Tests.Mapping;

public class StateMapperTests
{
    [Theory]
    [InlineData(true, MachineState.Running)]
    [InlineData(false, MachineState.Idle)]
    public void Map_InAutoWithoutFault_FollowsTheRunningFlag(bool isRunning, MachineState expected)
    {
        Assert.Equal(expected, StateMapper.Map(isRunning, faultCode: 0, MachineMode.Auto));
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public void Map_InSetup_IsSetupWhetherOrNotItIsRunning(bool isRunning)
    {
        Assert.Equal(MachineState.Setup, StateMapper.Map(isRunning, faultCode: 0, MachineMode.Setup));
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public void Map_InPlannedStop_IsPlannedStopWhetherOrNotItIsRunning(bool isRunning)
    {
        Assert.Equal(MachineState.PlannedStop, StateMapper.Map(isRunning, faultCode: 0, MachineMode.PlannedStop));
    }

    [Theory]
    [InlineData(true, MachineMode.Auto)]
    [InlineData(false, MachineMode.Auto)]
    [InlineData(true, MachineMode.Setup)]
    [InlineData(false, MachineMode.Setup)]
    [InlineData(true, MachineMode.PlannedStop)]
    [InlineData(false, MachineMode.PlannedStop)]
    public void Map_WithAnyFaultCode_IsDownInEveryMode(bool isRunning, MachineMode mode)
    {
        Assert.Equal(MachineState.Down, StateMapper.Map(isRunning, faultCode: 101, mode));
    }

    [Theory]
    [InlineData((ushort)1)]
    [InlineData((ushort)310)]
    [InlineData(ushort.MaxValue)]
    public void Map_TreatsEveryNonZeroFaultCodeAsDown(ushort faultCode)
    {
        Assert.Equal(MachineState.Down, StateMapper.Map(isRunning: true, faultCode, MachineMode.Auto));
    }

    [Fact]
    public void Map_ThrowsWhenTheModeIsUnknown()
    {
        Assert.Throws<ArgumentOutOfRangeException>(
            () => StateMapper.Map(isRunning: true, faultCode: 0, (MachineMode)99));
    }
}
