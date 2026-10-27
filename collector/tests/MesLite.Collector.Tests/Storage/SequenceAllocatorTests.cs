using MesLite.Collector.Storage;

namespace MesLite.Collector.Tests.Storage;

public class SequenceAllocatorTests
{
    [Fact]
    public void Next_StartsAtOneForAMachineWithoutHistory()
    {
        var allocator = new SequenceAllocator();

        Assert.Equal(1, allocator.Next("ASM", "ROBOT-01"));
    }

    [Fact]
    public void Next_IncrementsByOneOnEachCall()
    {
        var allocator = new SequenceAllocator();

        Assert.Equal(1, allocator.Next("ASM", "ROBOT-01"));
        Assert.Equal(2, allocator.Next("ASM", "ROBOT-01"));
        Assert.Equal(3, allocator.Next("ASM", "ROBOT-01"));
    }

    [Fact]
    public void Next_ContinuesFromTheInitialValues()
    {
        var allocator = new SequenceAllocator(new Dictionary<(string Line, string Machine), long>
        {
            [("ASM", "ROBOT-01")] = 41,
        });

        Assert.Equal(42, allocator.Next("ASM", "ROBOT-01"));
    }

    [Fact]
    public void Next_KeepsAnIndependentCounterPerMachine()
    {
        var allocator = new SequenceAllocator(new Dictionary<(string Line, string Machine), long>
        {
            [("ASM", "ROBOT-01")] = 10,
        });

        Assert.Equal(1, allocator.Next("ASM", "ROBOT-02"));
        Assert.Equal(11, allocator.Next("ASM", "ROBOT-01"));
        Assert.Equal(2, allocator.Next("ASM", "ROBOT-02"));
    }

    [Fact]
    public void Next_TreatsTheSameMachineCodeOnAnotherLineAsADifferentMachine()
    {
        var allocator = new SequenceAllocator();

        Assert.Equal(1, allocator.Next("ASM", "M-01"));
        Assert.Equal(1, allocator.Next("PACK", "M-01"));
    }

    [Fact]
    public void Next_IsNotAffectedByLaterChangesToTheInitialDictionary()
    {
        var initial = new Dictionary<(string Line, string Machine), long>
        {
            [("ASM", "ROBOT-01")] = 5,
        };
        var allocator = new SequenceAllocator(initial);

        initial[("ASM", "ROBOT-01")] = 500;

        Assert.Equal(6, allocator.Next("ASM", "ROBOT-01"));
    }

    [Fact]
    public async Task Next_NeverRepeatsANumberUnderConcurrentCalls()
    {
        var allocator = new SequenceAllocator();
        const int threads = 8;
        const int callsPerThread = 1000;

        var batches = await Task.WhenAll(Enumerable.Range(0, threads).Select(_ =>
            Task.Run(() => Enumerable.Range(0, callsPerThread)
                .Select(_ => allocator.Next("ASM", "ROBOT-01"))
                .ToArray())));

        var all = batches.SelectMany(b => b).Order().ToArray();
        Assert.Equal(threads * callsPerThread, all.Length);
        Assert.Equal(Enumerable.Range(1, threads * callsPerThread).Select(i => (long)i), all);
    }
}
