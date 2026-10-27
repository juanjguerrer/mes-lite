using MesLite.Collector.Configuration;
using MesLite.Collector.Events;

namespace MesLite.Collector.Mapping;

public class ProductionAccumulator
{
    private long _totalGood = 0;
    private long _totalScrap = 0;

    private DateTimeOffset? _intervalStart = null;

    public void Add(LogicalTag tag, uint delta, DateTimeOffset at)
    {
        if (_intervalStart is null)
        {
            _intervalStart = at;
        }

        switch (tag)
        {
            case LogicalTag.GoodCounter:
                _totalGood += delta;
                break;
            case LogicalTag.ScrapCounter:
                _totalScrap += delta;
                break;
            default:
                throw new ArgumentOutOfRangeException(nameof(tag), tag, "Invalid tag for production accumulator");
        }
    }

    public ProductionEvent? Close(DateTimeOffset now)
    {
        if (now <= _intervalStart || _intervalStart is null)
        {
            return null;
        }
        var total = _totalGood + _totalScrap;
        if (total == 0)
        {
            return null;
        }
        var productionEvent = new ProductionEvent(
            EventId: Guid.CreateVersion7(),
            IntervalStart: _intervalStart.Value,
            IntervalEnd: now,
            GoodCount: _totalGood,
            ScrapCount: _totalScrap
        );
        _intervalStart = now;
        _totalGood = 0;
        _totalScrap = 0;
        return productionEvent;
    }

}
