using MesLite.Collector.Configuration;
using Opc.Ua;

namespace MesLite.Collector.OpcUa;

public record class TagChange(
    MachineOptions Machine,
    LogicalTag Tag,
    object? Value,
    StatusCode StatusCode,
    DateTime SourceTimestamp
);
