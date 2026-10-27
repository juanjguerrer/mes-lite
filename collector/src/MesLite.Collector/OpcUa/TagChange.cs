using MesLite.Collector.Configuration;
using Opc.Ua;

namespace MesLite.Collector.OpcUa;

public record class TagChange(
    string Line,
    string Machine,
    LogicalTag Tag,
    object? Value,
    StatusCode StatusCode,
    DateTime SourceTimestamp,
    int Generation = 0
);
