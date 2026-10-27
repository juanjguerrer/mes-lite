
namespace MesLite.Collector.Mapping;

public static class CounterDelta
{
    public static uint? Compute(uint previous, uint current, uint max)
    {
        if (current >= previous)
            return current - previous;
        if (previous == max)
            return current + 1;
        return null;
    }
}
