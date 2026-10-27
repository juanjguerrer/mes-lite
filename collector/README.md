# MES Lite Collector

.NET Worker Service that connects to a PLC's OPC UA server, subscribes to the
configured machine tags and, in later steps, turns their changes into MES events.

It is the real piece of the v0.4 chain. In virtual commissioning the PLC is
simulated (`simulator/`); moving to a real plant means dropping the simulator and
changing this service's configuration, not its code.

```
PLC simulator (OPC UA) -> collector -> Mosquitto (MQTT) -> MES
```

## Current scope

Read-only: the collector connects to the OPC UA server, subscribes to the
configured tags and logs every change with its value and `SourceTimestamp`. It
reconnects if the server goes down or restarts.

Not implemented yet:

- Mapping tag changes to MES events (machine state, counters).
- Publishing to MQTT.
- SQLite store-and-forward.

## Requirements

- .NET 10 SDK
- The simulator running (see `simulator/`), or any OPC UA server that exposes the
  configured tags.

## Run

From the `collector/` directory:

```
dotnet run --project src/MesLite.Collector
```

Stop it with Ctrl+C.

## Configuration

Settings live in the `Collector` section of
`src/MesLite.Collector/appsettings.json` and can be overridden with environment
variables using `__` as separator, for example `Collector__Opc__EndpointUrl`.

- **OPC UA endpoint and namespace URI.** The namespace index is resolved from the
  URI at connection time, never hardcoded.
- **Tags.** A fixed set of logical tags (`Running`, `FaultCode`, `GoodCounter`,
  `ScrapCounter`, `Mode`), each with a NodeId identifier template using the
  `{line}` and `{machine}` placeholders.
- **Machines.** Line and machine codes (the MES identity), with optional
  per-machine overrides for tags whose NodeIds do not follow the template, and an
  optional `CounterMax` for counter rollover detection.

Configuration is validated at startup; the service fails fast with a clear message
if it is invalid.

## Reconnection and restarts

If the OPC UA server goes down or restarts, the session reconnects on its own with
a growing delay (`ReconnectInitialDelay` up to `ReconnectMaxDelay`) and without a
total time limit. The subscription is recreated and the server sends the current
value of every tag again; those values carry a new `Generation` so they can be told
apart from real changes.

After the subscription is recreated the collector checks that the namespace URI
still maps to the same index. If it changed, the subscribed NodeIds would point to
the wrong nodes, so the collector logs a critical message, stops and exits with
code 1.

The collector does not restart itself: run it under something that restarts it on
failure, such as `restart: on-failure` in Docker Compose, systemd, or the recovery
options of a Windows service.

## Layout

```
collector/
  MesLite.Collector.slnx
  src/MesLite.Collector/    Worker Service
  tests/                    unit tests (added with the event mapping)
```
