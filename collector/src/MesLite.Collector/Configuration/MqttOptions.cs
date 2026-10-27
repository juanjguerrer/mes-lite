namespace MesLite.Collector.Configuration;

public class MqttOptions
{
    public string? Host { get; init; } = "localhost";

    public int Port { get; init; } = 1883;

    public string? ClientId { get; init; } = "mes-lite-collector";

    public TimeSpan RetryDelay { get; init; } = TimeSpan.FromSeconds(1);
}
