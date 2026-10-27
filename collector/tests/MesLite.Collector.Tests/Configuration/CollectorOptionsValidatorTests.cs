using MesLite.Collector.Configuration;
using Microsoft.Extensions.Options;

namespace MesLite.Collector.Tests.Configuration;

public class CollectorOptionsValidatorTests
{
    private const string ValidUrl = "opc.tcp://localhost:4840/mes-lite/";
    private const string ValidNamespace = "urn:mes-lite:simulator";

    private static readonly CollectorOptionsValidator Validator = new();

    private static OpcOptions ValidOpc() => new() { EndpointUrl = ValidUrl, NamespaceUri = ValidNamespace };

    private static Dictionary<string, string> ValidTags() =>
        Enum.GetValues<LogicalTag>().ToDictionary(t => t.ToString(), t => $"{{line}}.{{machine}}.{t}");

    private static List<MachineOptions> ValidMachines() =>
    [
        new() { Line = "ASM", Machine = "ROBOT-01" },
        new() { Line = "PACK", Machine = "FILLER-01", CounterMax = 100 },
    ];

    private static CollectorOptions Options(
        OpcOptions? opc = null,
        Dictionary<string, string>? tags = null,
        List<MachineOptions>? machines = null,
        TimeSpan? productionInterval = null) =>
        new()
        {
            Opc = opc ?? ValidOpc(),
            Tags = tags ?? ValidTags(),
            Machines = machines ?? ValidMachines(),
            ProductionInterval = productionInterval ?? TimeSpan.FromSeconds(10),
        };

    private static ValidateOptionsResult Validate(CollectorOptions options) => Validator.Validate(null, options);

    private static void AssertFailsWith(ValidateOptionsResult result, string expectedFragment)
    {
        Assert.True(result.Failed, "Expected validation to fail.");
        Assert.Contains(result.Failures!, failure => failure.Contains(expectedFragment));
    }

    [Fact]
    public void Validate_AcceptsAValidConfiguration()
    {
        Assert.True(Validate(Options()).Succeeded);
    }

    [Fact]
    public void Validate_AcceptsTheDefaultTimingValues()
    {
        var result = Validate(Options(opc: new OpcOptions { EndpointUrl = ValidUrl, NamespaceUri = ValidNamespace }));

        Assert.True(result.Succeeded);
    }

    [Fact]
    public void Validate_ReportsEveryProblemInOneResult()
    {
        var options = Options(
            opc: new OpcOptions { EndpointUrl = null, NamespaceUri = null },
            tags: new Dictionary<string, string>(),
            machines: []);

        var result = Validate(options);

        Assert.True(result.Failed);
        Assert.Contains(result.Failures!, f => f.Contains("EndpointUrl"));
        Assert.Contains(result.Failures!, f => f.Contains("NamespaceUri"));
        Assert.Contains(result.Failures!, f => f.Contains("Tags"));
        Assert.Contains(result.Failures!, f => f.Contains("Machines"));
    }

    // Opc: endpoint and namespace

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public void Validate_RejectsAMissingEndpointUrl(string? url)
    {
        var result = Validate(Options(opc: new OpcOptions { EndpointUrl = url, NamespaceUri = ValidNamespace }));

        AssertFailsWith(result, "Opc.EndpointUrl is required");
    }

    [Theory]
    [InlineData("not a url")]
    [InlineData("http://localhost:4840/mes-lite/")]
    [InlineData("localhost:4840")]
    public void Validate_RejectsAnEndpointUrlThatIsNotOpcTcp(string url)
    {
        var result = Validate(Options(opc: new OpcOptions { EndpointUrl = url, NamespaceUri = ValidNamespace }));

        AssertFailsWith(result, "not a valid opc.tcp URL");
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    public void Validate_RejectsAMissingNamespaceUri(string? uri)
    {
        var result = Validate(Options(opc: new OpcOptions { EndpointUrl = ValidUrl, NamespaceUri = uri }));

        AssertFailsWith(result, "Opc.NamespaceUri is required");
    }

    // Opc: subscription

    [Fact]
    public void Validate_RejectsAZeroQueueSize()
    {
        var opc = new OpcOptions { EndpointUrl = ValidUrl, NamespaceUri = ValidNamespace, QueueSize = 0 };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.QueueSize");
    }

    [Fact]
    public void Validate_RejectsAZeroKeepAliveCount()
    {
        var opc = new OpcOptions { EndpointUrl = ValidUrl, NamespaceUri = ValidNamespace, KeepAliveCount = 0 };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.KeepAliveCount");
    }

    [Fact]
    public void Validate_RejectsAZeroLifetimeCount()
    {
        var opc = new OpcOptions { EndpointUrl = ValidUrl, NamespaceUri = ValidNamespace, LifetimeCount = 0 };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.LifetimeCount must be greater than 0");
    }

    [Fact]
    public void Validate_RejectsALifetimeCountBelowThreeTimesTheKeepAliveCount()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            KeepAliveCount = 10,
            LifetimeCount = 29,
        };

        AssertFailsWith(Validate(Options(opc: opc)), "at least 3 times");
    }

    [Fact]
    public void Validate_AcceptsALifetimeCountOfExactlyThreeTimesTheKeepAliveCount()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            KeepAliveCount = 10,
            LifetimeCount = 30,
        };

        Assert.True(Validate(Options(opc: opc)).Succeeded);
    }

    [Fact]
    public void Validate_RejectsANonPositiveSamplingInterval()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            SamplingInterval = TimeSpan.Zero,
        };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.SamplingInterval must be greater than 0");
    }

    [Fact]
    public void Validate_AcceptsThePositiveProductionInterval()
    {
        Assert.True(Validate(Options(productionInterval: TimeSpan.FromSeconds(10))).Succeeded);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(-5)]
    public void Validate_RejectsANonPositiveProductionInterval(int seconds)
    {
        var options = Options(productionInterval: TimeSpan.FromSeconds(seconds));

        AssertFailsWith(Validate(options), "ProductionInterval must be greater than 0");
    }

    [Fact]
    public void Validate_RejectsANonPositivePublishingInterval()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            PublishingInterval = TimeSpan.Zero,
        };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.PublishingInterval must be greater than 0");
    }

    [Fact]
    public void Validate_RejectsAPublishingIntervalAboveOneMinute()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            PublishingInterval = TimeSpan.FromSeconds(61),
        };

        AssertFailsWith(Validate(Options(opc: opc)), "must not exceed 1 minute");
    }

    [Fact]
    public void Validate_RejectsASamplingIntervalAbovePublishingInterval()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            SamplingInterval = TimeSpan.FromSeconds(2),
            PublishingInterval = TimeSpan.FromSeconds(1),
        };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.SamplingInterval must not exceed Opc.PublishingInterval");
    }

    [Fact]
    public void Validate_AcceptsASamplingIntervalEqualToThePublishingInterval()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            SamplingInterval = TimeSpan.FromSeconds(1),
            PublishingInterval = TimeSpan.FromSeconds(1),
        };

        Assert.True(Validate(Options(opc: opc)).Succeeded);
    }

    // Opc: reconnection

    [Fact]
    public void Validate_RejectsANonPositiveReconnectInitialDelay()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            ReconnectInitialDelay = TimeSpan.Zero,
        };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.ReconnectInitialDelay must be greater than 0");
    }

    [Fact]
    public void Validate_RejectsAReconnectMaxDelayBelowTheInitialDelay()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            ReconnectInitialDelay = TimeSpan.FromSeconds(10),
            ReconnectMaxDelay = TimeSpan.FromSeconds(5),
        };

        AssertFailsWith(Validate(Options(opc: opc)), "Opc.ReconnectMaxDelay must not be less than");
    }

    [Fact]
    public void Validate_AcceptsAReconnectMaxDelayEqualToTheInitialDelay()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            ReconnectInitialDelay = TimeSpan.FromSeconds(5),
            ReconnectMaxDelay = TimeSpan.FromSeconds(5),
        };

        Assert.True(Validate(Options(opc: opc)).Succeeded);
    }

    [Fact]
    public void Validate_RejectsAReconnectMaxDelayAboveFiveMinutes()
    {
        var opc = new OpcOptions
        {
            EndpointUrl = ValidUrl,
            NamespaceUri = ValidNamespace,
            ReconnectMaxDelay = TimeSpan.FromMinutes(6),
        };

        AssertFailsWith(Validate(Options(opc: opc)), "must not exceed 5 minutes");
    }

    // Tags

    [Theory]
    [InlineData(nameof(LogicalTag.Running))]
    [InlineData(nameof(LogicalTag.FaultCode))]
    [InlineData(nameof(LogicalTag.GoodCounter))]
    [InlineData(nameof(LogicalTag.ScrapCounter))]
    [InlineData(nameof(LogicalTag.Mode))]
    public void Validate_RejectsAMissingLogicalTag(string missing)
    {
        var tags = ValidTags();
        tags.Remove(missing);

        AssertFailsWith(Validate(Options(tags: tags)), $"'{missing}' is missing or empty");
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    public void Validate_RejectsAnEmptyTagTemplate(string template)
    {
        var tags = ValidTags();
        tags[nameof(LogicalTag.Mode)] = template;

        AssertFailsWith(Validate(Options(tags: tags)), "'Mode' is missing or empty");
    }

    [Fact]
    public void Validate_RejectsAnUnknownLogicalTag()
    {
        var tags = ValidTags();
        tags["Temperature"] = "{line}.{machine}.Temperature";

        AssertFailsWith(Validate(Options(tags: tags)), "unknown logical tag 'Temperature'");
    }

    // Machines

    [Fact]
    public void Validate_RejectsAnEmptyMachineList()
    {
        AssertFailsWith(Validate(Options(machines: [])), "at least one machine is required");
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("  ")]
    public void Validate_RejectsAMachineWithoutLine(string? line)
    {
        var machines = new List<MachineOptions> { new() { Line = line, Machine = "ROBOT-01" } };

        AssertFailsWith(Validate(Options(machines: machines)), "Machines[0]: Line is required");
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("  ")]
    public void Validate_RejectsAMachineWithoutCode(string? machine)
    {
        var machines = new List<MachineOptions> { new() { Line = "ASM", Machine = machine } };

        AssertFailsWith(Validate(Options(machines: machines)), "Machines[0]: Machine is required");
    }

    [Fact]
    public void Validate_RejectsADuplicateLineAndMachine()
    {
        var machines = new List<MachineOptions>
        {
            new() { Line = "ASM", Machine = "ROBOT-01" },
            new() { Line = "ASM", Machine = "ROBOT-01" },
        };

        AssertFailsWith(Validate(Options(machines: machines)), "Machines[1]: duplicate machine 'ASM.ROBOT-01'");
    }

    [Fact]
    public void Validate_AcceptsTheSameMachineCodeOnDifferentLines()
    {
        var machines = new List<MachineOptions>
        {
            new() { Line = "ASM", Machine = "ROBOT-01" },
            new() { Line = "PACK", Machine = "ROBOT-01" },
        };

        Assert.True(Validate(Options(machines: machines)).Succeeded);
    }

    [Fact]
    public void Validate_RejectsAZeroCounterMax()
    {
        var machines = new List<MachineOptions> { new() { Line = "ASM", Machine = "ROBOT-01", CounterMax = 0 } };

        AssertFailsWith(Validate(Options(machines: machines)), "Machines[0]: CounterMax must be greater than 0");
    }

    [Fact]
    public void Validate_AcceptsAMachineWithoutCounterMax()
    {
        var machines = new List<MachineOptions> { new() { Line = "ASM", Machine = "ROBOT-01", CounterMax = null } };

        Assert.True(Validate(Options(machines: machines)).Succeeded);
    }
}
