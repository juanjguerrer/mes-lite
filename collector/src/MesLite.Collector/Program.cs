using System.Threading.Channels;
using MesLite.Collector;
using MesLite.Collector.Configuration;
using MesLite.Collector.Events;
using MesLite.Collector.Mapping;
using MesLite.Collector.OpcUa;
using Microsoft.Extensions.Options;
using Opc.Ua;

var builder = Host.CreateApplicationBuilder(args);

builder.Services
    .AddSingleton(
        _ => Channel.CreateUnbounded<TagChange>(
            new UnboundedChannelOptions
            {
                SingleReader = true,
                SingleWriter = false
            }));

builder.Services.AddSingleton(sp => sp.GetRequiredService<Channel<TagChange>>().Writer);

builder.Services.AddSingleton(sp => sp.GetRequiredService<Channel<TagChange>>().Reader);

builder.Services
    .AddSingleton(
        _ => Channel.CreateUnbounded<OutboundMessage>(
            new UnboundedChannelOptions
            {
                SingleReader = true,
                SingleWriter = false
            }));

builder.Services.AddSingleton(sp => sp.GetRequiredService<Channel<OutboundMessage>>().Writer);

builder.Services.AddSingleton(sp => sp.GetRequiredService<Channel<OutboundMessage>>().Reader);

builder.Services.AddSingleton<TagSubscriber>();

builder.Services.AddHostedService<MappingService>();

builder.Services.AddHostedService<OutboundLogger>();

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
