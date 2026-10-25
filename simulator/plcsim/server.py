import asyncio
from datetime import timezone

from asyncua import ua
from asyncua.server.server import Server
from asyncua.common.node import Node
from .config import PlantConfig, load_config, ConfigError
from .tags import TAGS
import argparse
import os
import sys
from pathlib import Path

DEFAULT_CONFIG = Path(__file__).resolve().parent.parent / "config" / "plant.toml"

async def main(plant_config: PlantConfig):

    server = Server()
    await server.init()
    server.set_endpoint("opc.tcp://0.0.0.0:4840/mes-lite/")
    uri = "urn:mes-lite:simulator"
    idx = await server.register_namespace(uri)
    await build_address_space(server, idx, plant_config)

    async with server:
        while True:
            await asyncio.sleep(1)

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

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="PLC simulator (OPC UA)")
    parser.add_argument("--config", help="path to plant.toml (env: PLCSIM_CONFIG)")
    args = parser.parse_args()
    try:
        plant_config = load_config(str(resolve_config_path(args.config)))
    except ConfigError as e:
        print(f"Error loading config: {e}")
        sys.exit(1)

    try:
        asyncio.run(main(plant_config))

    except KeyboardInterrupt:
        print("Server stopped by user")