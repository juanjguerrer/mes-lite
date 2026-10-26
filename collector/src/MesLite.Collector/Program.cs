using MesLite.Collector;
using MesLite.Collector.Configuration;
using Microsoft.Extensions.Options;

var builder = Host.CreateApplicationBuilder(args);
builder.Services.AddHostedService<CollectorService>()
    .AddSingleton<IValidateOptions<CollectorOptions>, CollectorOptionsValidator>()
    .AddOptions<CollectorOptions>()
    .BindConfiguration("Collector")
    .ValidateOnStart();
var host = builder.Build();
host.Run();
