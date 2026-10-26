using MesLite.Collector;
using MesLite.Collector.Configuration;
using MesLite.Collector.OpcUa;
using Microsoft.Extensions.Options;
using Opc.Ua;
using Opc.Ua.Client;

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
            opt.ApplicationName = "MyClient";
            opt.ApplicationUri = "urn:localhost:MyClient";
            opt.ProductUri = "uri:example.com:MyClient";
        }
    );

builder.Services.AddHostedService<CollectorService>();
builder.Services.AddSingleton<OpcSessionProvider>();
var host = builder.Build();
host.Run();
