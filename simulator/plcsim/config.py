import re
import sys
import tomllib
from dataclasses import dataclass
from typing import Any

_MISSING = object()
MAX_COUNTER = 65535
CODE_RULE = re.compile(r"(?![-_])[A-Z0-9_-]+(?<![-_])")

class ConfigError(Exception):
    pass

@dataclass(frozen=True)
class MachineConfig:
    code: str
    cycle_time_ms: int
    counter_max: int = MAX_COUNTER

@dataclass(frozen=True)
class LineConfig:
    name: str
    code: str
    machines: tuple[MachineConfig, ...]

@dataclass(frozen=True)
class PlantConfig:
    name: str
    lines: tuple[LineConfig, ...]

def load_config(path: str) -> PlantConfig:
    data: dict
    try:
        with open(path, "rb") as f:
            data = tomllib.load(f)
    except FileNotFoundError as e:
        raise ConfigError(f"Config file not found: {path}") from e
    except tomllib.TOMLDecodeError as e:
        raise ConfigError(f"Error parsing config file {path}: {e}") from e
    lines = []
    seen_lines = {}
    _check_unknown(data, {"name", "lines"}, "plant")
    lines_data = _get(data, "lines", list, "plant")
    seen_machines = {}
    for i, line_data in enumerate(lines_data):
        machines = []
        line_where = f"lines[{i}]"
        _require_table(line_data, line_where)
        _check_unknown(line_data, {"name", "code", "machines"}, line_where)
        machines_data = _get(line_data, "machines", list, line_where)
        for j, machine_data in enumerate(machines_data):
            machine_where = f"{line_where}.machines[{j}]"
            _require_table(machine_data, machine_where)
            _check_unknown(machine_data, {"code", "cycle_time_ms", "counter_max"}, machine_where)
            cycle_time_ms: int = _get(machine_data, "cycle_time_ms", int, machine_where)
            if cycle_time_ms <= 0:
                raise ConfigError(f"Expected 'cycle_time_ms' to be positive in {machine_where}, got {cycle_time_ms}")
            counter_max = _get(machine_data, "counter_max", int, machine_where, default=MAX_COUNTER)
            if not (1 <= counter_max <= MAX_COUNTER):
                raise ConfigError(f"Expected 'counter_max' to be in range 1-{MAX_COUNTER} in {machine_where}, got {counter_max}")

            code = _get(machine_data, "code", str, machine_where)
            _check_code_format(code, machine_where)
            if code in seen_machines:
                raise ConfigError(f"Duplicate machine code '{code}' in {seen_machines[code]} and {machine_where}")
            machine = MachineConfig(
                code=code,
                cycle_time_ms=cycle_time_ms,
                counter_max=counter_max
            )
            seen_machines[machine.code] = machine_where
            machines.append(machine)

        code = _get(line_data, "code", str, line_where)
        _check_code_format(code, line_where)
        if code in seen_lines:
            raise ConfigError(f"Duplicate line code '{code}' in {seen_lines[code]} and {line_where}")
        line = LineConfig(
            name=_get(line_data, "name", str, line_where),
            code=code,
            machines=tuple(machines)
        )
        seen_lines[code] = line_where
        lines.append(line)
    return PlantConfig(
        name=_get(data, "name", str, "plant"),
        lines=tuple(lines)
    )

def _get(table, key : str, expected_type, where, default=_MISSING) -> Any:
    if key not in table:
        if default is not _MISSING:
            return default
        raise ConfigError(f"Missing key '{key}' in {where}")
    value = table[key]
    bool_invalid = isinstance(value, bool) and expected_type is not bool
    if not isinstance(value, expected_type) or bool_invalid:
        raise ConfigError(f"Expected '{key}' to be of type {expected_type.__name__} in {where}, got {type(value).__name__}")
    return value

def _check_unknown(table, allowed, where):
    for key in table:
        if key not in allowed:
            raise ConfigError(f"Unknown key '{key}' in {where}")

def _require_table(value, where):
    if not isinstance(value, dict):
        raise ConfigError(f"Expected a table in {where}, got {type(value).__name__}")

def _check_code_format(code: str, where: str) -> None:
    if not CODE_RULE.fullmatch(code):
        raise ConfigError(f"Invalid code format '{code}' in {where}. Codes must be uppercase letters, digits, hyphens, or underscores, and cannot start or end with a hyphen or underscore.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <config_file>", file=sys.stderr)
        sys.exit(1)
    config_file = sys.argv[1]
    try:
        config = load_config(config_file)
        print(config)
    except ConfigError as e:
        print(f"Error loading config: {e}", file=sys.stderr)
        sys.exit(1)

