from pathlib import Path

import pytest

from plcsim.behavior import InterruptionKind, MachineBehavior
from plcsim.config import ConfigError, load_config

PLANT_TOML = Path(__file__).resolve().parent.parent / "config" / "plant.toml"

HEADER = """
name = "Test plant"
[[lines]]
name = "Line"
code = "L1"
  [[lines.machines]]
  code = "M-01"
  cycle_time_ms = 1000
"""


def load(tmp_path, machine_extra: str = ""):
    path = tmp_path / "plant.toml"
    path.write_text(HEADER + machine_extra, encoding="utf-8")
    return load_config(str(path)).lines[0].machines[0]


def test_shipped_plant_config_loads_and_every_machine_has_valid_behavior():
    plant = load_config(str(PLANT_TOML))

    machines = {m.code: m for line in plant.lines for m in line.machines}
    assert set(machines) == {"ROBOT-01", "ROBOT-02", "FILLER-01"}
    assert set(machines["ROBOT-01"].behavior.interruptions) == set(InterruptionKind)
    assert set(machines["FILLER-01"].behavior.interruptions) == set(InterruptionKind)
    assert machines["ROBOT-02"].behavior == MachineBehavior()
    assert machines["FILLER-01"].counter_max == 100


def test_machine_without_behavior_gets_default_behavior(tmp_path):
    assert load(tmp_path).behavior == MachineBehavior()


def test_behavior_is_parsed(tmp_path):
    machine = load(
        tmp_path,
        """
  [lines.machines.behavior]
  scrap_rate = 0.25
  fault_codes = [11, 12]

  [lines.machines.behavior.fault]
  mean_interval_s = 90
  duration_s = [10, 25.5]

  [lines.machines.behavior.planned_stop]
  mean_interval_s = 600.5
  duration_s = [1, 2]
""",
    )

    behavior = machine.behavior
    assert behavior.scrap_rate == 0.25
    assert behavior.fault_codes == (11, 12)
    assert set(behavior.interruptions) == {InterruptionKind.FAULT, InterruptionKind.PLANNED_STOP}
    fault = behavior.interruptions[InterruptionKind.FAULT]
    assert fault.mean_interval_s == 90.0
    assert fault.duration_s == (10.0, 25.5)
    assert behavior.interruptions[InterruptionKind.PLANNED_STOP].mean_interval_s == 600.5


def test_behavior_without_interruptions_only_sets_scrap(tmp_path):
    machine = load(tmp_path, "\n  [lines.machines.behavior]\n  scrap_rate = 0.1\n")

    assert machine.behavior.scrap_rate == 0.1
    assert machine.behavior.interruptions == {}


FAULT_OK = "mean_interval_s = 60\n  duration_s = [5, 10]\n"


@pytest.mark.parametrize(
    ("extra", "message"),
    [
        ("\n  [lines.machines.behavior]\n  foo = 1\n", "Unknown key 'foo'"),
        ("\n  [lines.machines.behavior.fualt]\n  " + FAULT_OK, "Unknown key 'fualt'"),
        ("\n  [lines.machines.behavior.fault]\n  " + FAULT_OK + "  extra = 1\n", "Unknown key 'extra'"),
        ("\n  [lines.machines.behavior.fault]\n  duration_s = [5, 10]\n", "Missing key 'mean_interval_s'"),
        ("\n  [lines.machines.behavior.fault]\n  mean_interval_s = 60\n", "Missing key 'duration_s'"),
        ("\n  [lines.machines.behavior.fault]\n  mean_interval_s = 60\n  duration_s = [5]\n", "two numbers"),
        ("\n  [lines.machines.behavior.fault]\n  mean_interval_s = 60\n  duration_s = [5, \"x\"]\n", "two numbers"),
        ("\n  [lines.machines.behavior.fault]\n  mean_interval_s = 60\n  duration_s = [10, 5]\n", "duration_s"),
        ("\n  [lines.machines.behavior.fault]\n  mean_interval_s = 0\n  duration_s = [5, 10]\n", "mean_interval_s"),
        ("\n  [lines.machines.behavior.fault]\n  mean_interval_s = \"60\"\n  duration_s = [5, 10]\n", "mean_interval_s"),
        ("\n  [lines.machines.behavior]\n  scrap_rate = 1.5\n", "scrap_rate"),
        ("\n  [lines.machines.behavior]\n  scrap_rate = true\n", "scrap_rate"),
        ("\n  [lines.machines.behavior]\n  scrap_rate = \"a\"\n", "scrap_rate"),
        ("\n  [lines.machines.behavior]\n  fault_codes = [0]\n", "fault_codes"),
        ("\n  [lines.machines.behavior]\n  fault_codes = []\n", "fault_codes"),
        ("\n  [lines.machines.behavior]\n  fault_codes = [70000]\n", "fault_codes"),
        ("\n  [lines.machines.behavior]\n  fault_codes = 5\n", "fault_codes"),
        ("\n  behavior = 3\n", "Expected 'behavior' to be of type dict"),
    ],
)
def test_invalid_behavior_is_rejected_with_location(tmp_path, extra, message):
    with pytest.raises(ConfigError, match=message) as error:
        load(tmp_path, extra)

    assert "lines[0].machines[0]" in str(error.value)
