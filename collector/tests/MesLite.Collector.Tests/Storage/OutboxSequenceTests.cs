using MesLite.Collector.Configuration;
using MesLite.Collector.Events;
using MesLite.Collector.Storage;
using Microsoft.Data.Sqlite;
using Microsoft.Extensions.Options;

namespace MesLite.Collector.Tests.Storage;

public sealed class OutboxSequenceTests : IDisposable
{
    private static readonly DateTimeOffset At = new(2026, 10, 27, 10, 0, 0, TimeSpan.Zero);
    private readonly string _directory = Path.Combine(Path.GetTempPath(), "meslite-outbox-" + Guid.NewGuid().ToString("N"));
    private readonly Outbox _outbox;

    public OutboxSequenceTests()
    {
        Directory.CreateDirectory(_directory);
        _outbox = new Outbox(Options.Create(new CollectorOptions
        {
            OutboxPath = Path.Combine(_directory, "outbox.db"),
        }));
    }

    public void Dispose()
    {
        SqliteConnection.ClearAllPools();
        Directory.Delete(_directory, recursive: true);
    }

    private static OutboundMessage State(string line, string machine, long sequence) =>
        OutboundMessage.From(line, machine, new StateEvent(Guid.NewGuid(), EventState.Running, At, Sequence: sequence));

    [Fact]
    public void GetLastSequences_IsEmptyForAnEmptyOutbox()
    {
        Assert.Empty(_outbox.GetLastSequences());
    }

    [Fact]
    public async Task GetLastSequences_ReturnsTheMaximumPerLineAndMachine()
    {
        await _outbox.AddAsync(State("ASM", "ROBOT-01", 1), CancellationToken.None);
        await _outbox.AddAsync(State("ASM", "ROBOT-01", 2), CancellationToken.None);
        await _outbox.AddAsync(State("ASM", "ROBOT-02", 1), CancellationToken.None);
        await _outbox.AddAsync(State("PACK", "FILLER-01", 5), CancellationToken.None);

        var last = _outbox.GetLastSequences();

        Assert.Equal(3, last.Count);
        Assert.Equal(2, last[("ASM", "ROBOT-01")]);
        Assert.Equal(1, last[("ASM", "ROBOT-02")]);
        Assert.Equal(5, last[("PACK", "FILLER-01")]);
    }

    [Fact]
    public async Task GetLastSequences_UsesTheSameKeyOrderAsTheAllocator()
    {
        await _outbox.AddAsync(State("ASM", "ROBOT-01", 7), CancellationToken.None);

        var allocator = new SequenceAllocator(_outbox.GetLastSequences());

        Assert.Equal(8, allocator.Next("ASM", "ROBOT-01"));
    }

    [Fact]
    public async Task GetPendingAsync_ReturnsLineMachineAndSequenceUnswapped()
    {
        var message = State("ASM", "ROBOT-01", 3);
        await _outbox.AddAsync(message, CancellationToken.None);

        var pending = Assert.Single(await _outbox.GetPendingAsync(10, CancellationToken.None));

        Assert.Equal("ASM", pending.line);
        Assert.Equal("ROBOT-01", pending.machine);
        Assert.Equal(3, pending.sequence);
        Assert.Equal(message.eventId, pending.eventId);
    }

    [Fact]
    public async Task AddAsync_IgnoresTheSameEventStoredTwice()
    {
        var message = State("ASM", "ROBOT-01", 1);

        await _outbox.AddAsync(message, CancellationToken.None);
        await _outbox.AddAsync(message, CancellationToken.None);

        Assert.Single(await _outbox.GetPendingAsync(10, CancellationToken.None));
    }
}
