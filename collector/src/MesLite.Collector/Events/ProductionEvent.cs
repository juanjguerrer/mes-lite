namespace MesLite.Collector.Events;

public record class ProductionEvent(
    Guid EventId,
    long GoodCount,
    long ScrapCount,
    DateTimeOffset IntervalStart,
    DateTimeOffset IntervalEnd,
    long Sequence = 0
)
{

}
