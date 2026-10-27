using MesLite.Collector.Configuration;
using MesLite.Collector.OpcUa;

namespace MesLite.Collector.Tests.OpcUa;

public class NodeIdResolverTests
{
    private static readonly MachineOptions Robot = new() { Line = "ASM", Machine = "ROBOT-01" };

    private static NodeIdResolver ResolverWith(params (string Tag, string Template)[] tags) =>
        new(tags.ToDictionary(t => t.Tag, t => t.Template));

    [Fact]
    public void Resolve_ReplacesLineAndMachinePlaceholders()
    {
        var resolver = ResolverWith(("Running", "{line}.{machine}.Running"));

        var identifier = resolver.Resolve(Robot, LogicalTag.Running);

        Assert.Equal("ASM.ROBOT-01.Running", identifier);
    }

    [Fact]
    public void Resolve_UsesTheTemplateOfTheRequestedTag()
    {
        var resolver = ResolverWith(
            ("Running", "{line}.{machine}.Running"),
            ("GoodCounter", "{line}.{machine}.GoodCounter"));

        Assert.Equal("ASM.ROBOT-01.GoodCounter", resolver.Resolve(Robot, LogicalTag.GoodCounter));
    }

    [Fact]
    public void Resolve_ReplacesEveryOccurrenceOfAPlaceholder()
    {
        var resolver = ResolverWith(("Mode", "{machine}.{machine}.Mode"));

        Assert.Equal("ROBOT-01.ROBOT-01.Mode", resolver.Resolve(Robot, LogicalTag.Mode));
    }

    [Fact]
    public void Resolve_SupportsQuotedIdentifiersLikeSiemensDataBlocks()
    {
        var resolver = ResolverWith(("Running", "\"DB_{machine}\".\"Running\""));

        Assert.Equal("\"DB_ROBOT-01\".\"Running\"", resolver.Resolve(Robot, LogicalTag.Running));
    }

    [Fact]
    public void Resolve_ReturnsTheTemplateUnchangedWhenItHasNoPlaceholders()
    {
        var resolver = ResolverWith(("Running", "Plant.Robot1.Run"));

        Assert.Equal("Plant.Robot1.Run", resolver.Resolve(Robot, LogicalTag.Running));
    }

    [Fact]
    public void Resolve_ThrowsWhenTheTagIsNotConfigured()
    {
        var resolver = ResolverWith(("Running", "{line}.{machine}.Running"));

        Assert.Throws<InvalidOperationException>(() => resolver.Resolve(Robot, LogicalTag.Mode));
    }

    [Fact]
    public void Resolve_ThrowsWhenTheTemplateIsEmpty()
    {
        var resolver = ResolverWith(("Running", ""));

        Assert.Throws<InvalidOperationException>(() => resolver.Resolve(Robot, LogicalTag.Running));
    }

    [Theory]
    [InlineData(null, "ROBOT-01")]
    [InlineData("", "ROBOT-01")]
    [InlineData("ASM", null)]
    [InlineData("ASM", "")]
    public void Resolve_ThrowsWhenTheMachineHasNoLineOrCode(string? line, string? machine)
    {
        var resolver = ResolverWith(("Running", "{line}.{machine}.Running"));
        var incomplete = new MachineOptions { Line = line, Machine = machine };

        Assert.Throws<InvalidOperationException>(() => resolver.Resolve(incomplete, LogicalTag.Running));
    }

    [Fact]
    public void Resolve_ThrowsWhenAPlaceholderIsUnknown()
    {
        var resolver = ResolverWith(("Running", "{plant}.{machine}.Running"));

        Assert.Throws<InvalidOperationException>(() => resolver.Resolve(Robot, LogicalTag.Running));
    }
}
