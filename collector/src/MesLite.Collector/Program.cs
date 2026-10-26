using MesLite.Collector;
using MesLite.Collector.Configuration;
using MesLite.Collector.OpcUa;
using Microsoft.Extensions.Options;
using Opc.Ua;

var builder = Host.CreateApplicationBuilder(args);

builder.Services
    .AddSingleton<IValidateOptions<CollectorOptions>, CollectorOptionsValidator>()
    .AddOptions<CollectorOptions>()
    .BindConfiguration("Collector")
    .ValidateOnStart();

builder.Services.AddSingleton(sp =>
    new NodeIdResolver(
        sp.GetRequiredService<IOptions<CollectorOptions>>().Value.Tags
    )
);

builder.Services
    .AddOpcUa()
    .AddClient(opt =>
        {
            opt.ApplicationName = "MesLite.Collector";
            opt.ApplicationUri = "urn:mes-lite:collector";
            opt.ProductUri = "uri:mes-lite:collector";
        }
    );

builder.Services.AddHostedService<CollectorService>();
builder.Services.AddSingleton<OpcSessionProvider>();
var host = builder.Build();
host.Run();
