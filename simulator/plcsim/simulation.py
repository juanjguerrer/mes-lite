import random
from datetime import datetime

from .config import PlantConfig
from .machine import Machine
from .tags import TagValues


def create_machines(config: PlantConfig, seed: int | str, start: datetime) -> dict[str, Machine]:
    machines: dict[str, Machine] = {}
    for line in config.lines:
        for machine_config in line.machines:
            rng = random.Random(f"{seed}:{machine_config.code}")
            machine = Machine(machine_config, machine_config.behavior, rng, start)
            machines[machine_config.code] = machine
    return machines

def changed(previous: TagValues, current: TagValues) -> TagValues:
    """Return a dictionary of tag values that have changed between two dictionaries."""
    changes: TagValues = {}
    for tag in current:
        if tag not in previous or previous[tag] != current[tag]:
            changes[tag] = current[tag]
    return changes