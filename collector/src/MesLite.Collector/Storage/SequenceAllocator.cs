
namespace MesLite.Collector.Storage;

public sealed class SequenceAllocator
{
    private readonly Dictionary<(string Line, string Machine), long> _sequences = new();
    private readonly object _lock = new();

    public SequenceAllocator(IReadOnlyDictionary<(string Line, string Machine), long> initial)
    {
        foreach (var (key, value) in initial)
        {
            _sequences[key] = value;
        }
    }
    public SequenceAllocator()
    : this(new Dictionary<(string Line, string Machine), long>())
    {
    }
    public long Next(string line, string machine)
    {
        lock (_lock)
        {
            var key = (line, machine);
            if (_sequences.TryGetValue(key, out var existing))
            {
                _sequences[key] = existing + 1;
                return existing + 1;
            }

            _sequences[key] = 1;
            return 1;
        }
    }
}
