# MES Lite Collector

.NET Worker Service that connects to a PLC's OPC UA server, subscribes to the
configured machine tags, turns their changes into MES events and publishes them
over MQTT.

It is the real piece of the v0.4 chain. In virtual commissioning the PLC is
simulated (`simulator/`); moving to a real plant means dropping the simulator and
changing this service's configuration, not its code.

```
PLC simulator (OPC UA) -> collector -> Mosquitto (MQTT) -> MES
```

## How it works

```
OPC UA subscription -> tag changes -> mapping -> outbox (SQLite) -> MQTT publisher
```

1. **Subscription.** One monitored item per machine and logical tag. Every
   notification is tagged with the machine, the logical tag and a `Generation`
   that grows each time the subscription is recreated.
2. **Mapping.** Per machine, the tags are turned into two kinds of events (see
   [Events](#events)).
3. **Outbox.** Every event is written to a local SQLite file before anything is
   published.
4. **Publisher.** Sends the pending events to the broker with QoS 1 and marks each
   one as sent only after the broker confirms it.

## Requirements

- .NET 10 SDK
- An MQTT broker (`docker compose up -d mosquitto` from the repository root).
- The simulator running (see `simulator/`), or any OPC UA server that exposes the
  configured tags.
- To see the events arrive in the MES: the MES running with `mes.mqtt.enabled=true`
  (`MES_MQTT_ENABLED=true`).

## Run

From the `collector/` directory:

```bash
dotnet run --project src/MesLite.Collector
```

Stop it with Ctrl+C. The outbox file (`outbox.db`) is created in the working
directory on the first run.

## Tests

From the `collector/` directory:

```bash
dotnet test
```

## Configuration

Settings live in the `Collector` section of
`src/MesLite.Collector/appsettings.json` and can be overridden with environment
variables using `__` as separator, for example `Collector__Opc__EndpointUrl`.
Time values are written as `hh:mm:ss` (`"00:00:10"` is ten seconds).

| Setting | Meaning | Default |
| --- | --- | --- |
| `Opc.EndpointUrl` | OPC UA endpoint (`opc.tcp://...`), no security, anonymous | required |
| `Opc.NamespaceUri` | Namespace URI of the tags; the index is resolved from it at connection time, never hardcoded | required |
| `Opc.SamplingInterval` | How often the server samples each tag | `00:00:00.250` |
| `Opc.PublishingInterval` | How often the server sends accumulated changes | `00:00:01` |
| `Opc.QueueSize` | Changes kept per tag between two publications | `10` |
| `Opc.KeepAliveCount`, `Opc.LifetimeCount` | Subscription keep-alive and lifetime, in publishing intervals (lifetime must be at least 3 times the keep-alive) | `10`, `60` |
| `Opc.ReconnectInitialDelay`, `Opc.ReconnectMaxDelay` | Backoff of the OPC UA reconnection | `00:00:01`, `00:00:30` |
| `Tags` | NodeId identifier template for each logical tag, with `{line}` and `{machine}` placeholders | required |
| `Machines` | `Line` and `Machine` codes (the MES identity) and an optional `CounterMax` | required |
| `ProductionInterval` | Length of the production window | `00:00:10` |
| `OutboxPath` | SQLite file of the outbox | `outbox.db` |
| `Mqtt.Host`, `Mqtt.Port` | Broker address | `localhost`, `1883` |
| `Mqtt.ClientId` | MQTT client id | `mes-lite-collector` |
| `Mqtt.RetryDelay` | First wait after a publishing error; it doubles up to 60 s | `00:00:01` |

The five logical tags are fixed: `Running`, `FaultCode`, `GoodCounter`,
`ScrapCounter` and `Mode`. Their NodeIds come from the `Tags` templates, so a plant
with another naming scheme only needs other templates. Per-machine overrides for
tags that do not follow a template are not implemented yet.

`CounterMax` is the value after which a counter wraps to 0 (inclusive). It defaults
to 65535, the maximum of a `UInt16`.

Configuration is validated at startup; the service fails fast with a clear message
if it is invalid.

## Events

Topics and payloads follow what the MES MQTT adapter accepts. Timestamps are UTC.

| Topic | Payload |
| --- | --- |
| `mes-lite/<line>/<machine>/state` | `eventId`, `state`, `occurredAt`, `reason` (optional) |
| `mes-lite/<line>/<machine>/production` | `eventId`, `goodCount`, `scrapCount`, `intervalStart`, `intervalEnd` |

### State

The state is derived from the tags and published only when it changes. The first
state seen for each machine is published as a baseline. `occurredAt` is the
`SourceTimestamp` of the change that caused it.

| `FaultCode` | `Mode` | `Running` | State |
| --- | --- | --- | --- |
| not 0 | any | any | `DOWN` (`reason` carries the fault code) |
| 0 | 1 (SETUP) | any | `SETUP` |
| 0 | 2 (PLANNED_STOP) | any | `PLANNED_STOP` |
| 0 | 0 (AUTO) | true | `RUNNING` |
| 0 | 0 (AUTO) | false | `IDLE` |

If a tag arrives with a bad status the state becomes unknown internally and nothing
is published until good values come back.

### Production

The delta of each counter is added to the machine's open window. Every
`ProductionInterval` the window is closed and, if it has any parts, one event is
published with the good and scrap counts of that interval. Empty windows publish
nothing.

- The first value seen of a counter is only a baseline.
- A counter that drops after reaching `CounterMax` is a rollover and counts the
  parts up to the new value.
- A counter that drops without reaching `CounterMax` is a PLC restart: it is logged
  and produces no delta.

## Delivery

Delivery to the MES is at least once. An event is stored in the outbox before it is
published and marked as sent after the broker confirms it. If the broker is down the
events wait in the outbox and are sent in order when it comes back; if the collector
dies between the broker confirming and the outbox being updated, that event is sent
again. Every event carries a unique `eventId` so the receiver can discard repeats.

Not covered yet:

- Events still in memory when the process dies are lost, including the open
  production window (at most `ProductionInterval` of production).
- The mapping state is not persisted. After a restart each machine publishes its
  state again as a baseline, and the counters start from a new baseline, so parts
  produced while the collector was down are not counted.

## Reconnection and restarts

If the OPC UA server goes down or restarts, the session reconnects on its own with
a growing delay (`Opc.ReconnectInitialDelay` up to `Opc.ReconnectMaxDelay`) and
without a total time limit. The subscription is recreated and the server sends the
current value of every tag again; those values carry a new `Generation` so they can
be told apart from real changes. A state that changed during the outage is
published with a reason saying it was recovered after the reconnection, since its
time is only approximate.

After the subscription is recreated the collector checks that the namespace URI
still maps to the same index. If it changed, the subscribed NodeIds would point to
the wrong nodes, so the collector logs a critical message, stops and exits with
code 1.

The MQTT publisher retries on its own with a growing delay while the broker is
unavailable.

The collector does not restart itself: run it under something that restarts it on
failure, such as `restart: on-failure` in Docker Compose, systemd, or the recovery
options of a Windows service.

## Deployment

The collector is not containerized yet: `compose.yaml` only runs the broker, and the
simulator and the MES are started by hand. If it is added to the compose file, the
outbox path needs a persistent volume, otherwise pending events are lost with the
container.

## Layout

```
collector/
  MesLite.Collector.slnx
  src/MesLite.Collector/
    Configuration/   options and their validation
    OpcUa/           session, NodeId resolution and the subscription
    Mapping/         state and production logic
    Events/          event models, JSON contract and outbox writer
    Storage/         SQLite outbox and MQTT publisher
  tests/MesLite.Collector.Tests/
```
