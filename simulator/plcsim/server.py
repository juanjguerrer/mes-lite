import argparse
import asyncio
import os
import secrets
import sys
from datetime import UTC, datetime
from pathlib import Path

from asyncua import ua
from asyncua.common.node import Node
from asyncua.server.server import Server

from .config import ConfigError, PlantConfig, load_config
from .simulation import changed, create_machines
from .tags import TAGS, TAGS_BY_NAME, tag_values

DEFAULT_CONFIG = Path(__file__).resolve().parent.parent / "config" / "plant.toml"
TICK_S = 0.25

async def main(plant_config: PlantConfig, seed: str):

    server = Server()
    await server.init()
    server.set_endpoint("opc.tcp://0.0.0.0:4840/mes-lite/")
    uri = "urn:mes-lite:simulator"
    idx = await server.register_namespace(uri)
    nodes = await build_address_space(server, idx, plant_config)
    start = datetime.now(UTC)
    machines = create_machines(plant_config, seed=seed, start=start)
    async with server:
        previous: dict[str, dict[str, int | bool ]] = {}
        while True:
            now = ua.DateTime.now(UTC)
            for machine in machines.values():
                current = tag_values(machine.step(now))
                changed_tags = changed(previous.get(machine.config.code, {}), current)
                previous[machine.config.code] = current
                for name,value in changed_tags.items():
                    node = nodes[(machine.config.code, name)]
                    tag = TAGS_BY_NAME[name]
                    data = ua.DataValue(ua.Variant(value, tag.type), SourceTimestamp=now)
                    await node.write_value(data)
            await asyncio.sleep(TICK_S)

async def build_address_space(server: Server, idx: int, plant_config: PlantConfig) -> dict[tuple[str, str], Node]:
    plant_node_id = build_node_id("Plant", idx)
    plant_node_name = build_node_name("Plant", idx)
    plant = await server.nodes.objects.add_object(plant_node_id, plant_node_name)
    nodes : dict[tuple[str, str], Node] = {}
    for line in plant_config.lines:
        line_node_id = build_node_id(line.code, idx)
        line_node_name = build_node_name(line.code, idx)
        line_folder = await plant.add_folder(line_node_id, line_node_name)
        for machine in line.machines:
            machine_node_id = build_node_id(f"{line.code}.{machine.code}", idx)
            machine_node_name = build_node_name(machine.code, idx)
            machine_obj = await line_folder.add_object(machine_node_id, machine_node_name)
            for tag in TAGS:
                tag_node_id = build_node_id(f"{line.code}.{machine.code}.{tag.name}", idx)
                tag_node_name = build_node_name(tag.name, idx)
                nodes[(machine.code, tag.name)] = await machine_obj.add_variable(tag_node_id, tag_node_name, tag.initial, varianttype=tag.type)
    return nodes

def build_node_id(id: str, idx: int) -> ua.NodeId:
    id = ua.String(id)
    idx = ua.Int16(idx)
    return ua.NodeId(id, idx)

def build_node_name(name: str, idx: int) -> ua.QualifiedName:
    return ua.QualifiedName(name, idx)

def resolve_config_path(cli_value: str | None) -> Path:
    if cli_value:
        return Path(cli_value)
    env_value = os.environ.get("PLCSIM_CONFIG")
    if env_value:
        return Path(env_value)
    return DEFAULT_CONFIG

def resolve_seed(cli_value: str | None) -> str:
    seed = os.environ.get("PLCSIM_SEED")
    if cli_value:
        seed = cli_value
    if seed is None:
        seed = secrets.randbits(32)
    print("Seed: " + str(seed))
    return str(seed)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="PLC simulator (OPC UA)")
    parser.add_argument("--config", help="path to plant.toml (env: PLCSIM_CONFIG)")
    parser.add_argument("--seed", help="random seed for machine behavior")
    args = parser.parse_args()
    try:
        plant_config = load_config(str(resolve_config_path(args.config)))
        seed = resolve_seed(args.seed)
    except ConfigError as e:
        print(f"Error loading config: {e}")
        sys.exit(1)

    try:
        asyncio.run(main(plant_config, seed=seed))

    except KeyboardInterrupt:
        print("Server stopped by user")
