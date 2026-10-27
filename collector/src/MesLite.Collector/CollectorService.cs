using MesLite.Collector.OpcUa;

namespace MesLite.Collector;

public sealed class CollectorService(
    ILogger<CollectorService> logger,
    OpcSessionProvider connection,
    TagSubscriber subscriber,
    IHostApplicationLifetime lifetime
) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            await using var session = await connection.ConnectAsync(stoppingToken);
            subscriber.Subscribe(session);
            try
            {
                await Task.Delay(Timeout.Infinite, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                logger.LogInformation("Collector stopping");
            }
            finally
            {
                await subscriber.DisposeAsync();
                await session.CloseAsync();
            }
        } catch (Exception ex) when (!stoppingToken.IsCancellationRequested)
        {
            logger.LogCritical(ex, "Collector failed; shutting down");
            Environment.ExitCode = 1;
            lifetime.StopApplication();
        }
    }
}
