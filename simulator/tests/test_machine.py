import math
import random
from datetime import UTC, datetime, timedelta
from itertools import pairwise

import pytest

from plcsim.behavior import InterruptionKind, InterruptionSpec, MachineBehavior
from plcsim.config import MAX_COUNTER, MachineConfig
from plcsim.machine import Machine, MachineSnapshot
from plcsim.states import Mode

START = datetime(2026, 1, 1, 8, 0, 0, tzinfo=UTC)
CYCLE_MS = 1000
FAULT_CODE = 7

# What a snapshot must show while each kind of interruption is active.
EXPECTED_DURING = {
    InterruptionKind.FAULT: (FAULT_CODE, Mode.AUTO),
    InterruptionKind.IDLE: (0, Mode.AUTO),
    InterruptionKind.SETUP: (0, Mode.SETUP),
    InterruptionKind.PLANNED_STOP: (0, Mode.PLANNED_STOP),
}
ALL_KINDS = list(InterruptionKind)


class MeanRandom(random.Random):
    """Exponential draws always return their mean, so interruption starts are predictable."""

    def expovariate(self, lambd: float = 1.0) -> float:
        return 1.0 / lambd


def at(milliseconds: int) -> datetime:
    return START + timedelta(milliseconds=milliseconds)


def spec(interval_s: float | None, duration_s: float | tuple[float, float] = 5.0) -> InterruptionSpec:
    low, high = (duration_s, duration_s) if isinstance(duration_s, int | float) else duration_s
    return InterruptionSpec(mean_interval_s=interval_s, duration_s=(low, high))


def make_machine(
    *,
    scrap_rate: float = 0.0,
    seed: int = 1,
    counter_max: int = MAX_COUNTER,
    fault_codes: tuple[int, ...] = (FAULT_CODE,),
    interruptions: dict[InterruptionKind, InterruptionSpec] | None = None,
    rng: random.Random | None = None,
) -> Machine:
    config = MachineConfig(code="TEST-01", cycle_time_ms=CYCLE_MS, counter_max=counter_max)
    behavior = MachineBehavior(
        scrap_rate=scrap_rate,
        fault_codes=fault_codes,
        interruptions=interruptions or {},
    )
    return Machine(config, behavior, rng or random.Random(seed), START)


def periodic_machine(kind: InterruptionKind) -> Machine:
    """Cycle 1 s; `kind` starts at t=10 s and lasts 5 s, then repeats 10 s after each end."""
    return make_machine(interruptions={kind: spec(10.0, 5.0)}, rng=MeanRandom(1))


def busy_machine(seed: int, scrap_rate: float = 0.3) -> Machine:
    """Every kind of interruption active, with random intervals and durations."""
    return make_machine(
        seed=seed,
        scrap_rate=scrap_rate,
        fault_codes=(7, 9),
        interruptions={
            InterruptionKind.FAULT: spec(40.0, (3.0, 8.0)),
            InterruptionKind.IDLE: spec(30.0, (1.0, 4.0)),
            InterruptionKind.SETUP: spec(90.0, (5.0, 12.0)),
            InterruptionKind.PLANNED_STOP: spec(120.0, (5.0, 12.0)),
        },
    )


def walk(machine: Machine, step_ms: int, total_ms: int) -> list[MachineSnapshot]:
    return [machine.step(at(ms)) for ms in range(step_ms, total_ms + 1, step_ms)]


# --- production -------------------------------------------------------------


def test_initial_snapshot_is_running_with_zero_counters():
    snap = make_machine().snapshot()

    assert snap.running is True
    assert snap.fault_code == 0
    assert snap.mode == Mode.AUTO
    assert snap.good == 0
    assert snap.scrap == 0


def test_no_piece_before_first_cycle_completes():
    snap = make_machine().step(at(500))

    assert snap.good == 0
    assert snap.scrap == 0
    assert snap.running is True


def test_piece_is_counted_exactly_at_cycle_boundary():
    snap = make_machine().step(at(CYCLE_MS))

    assert snap.good == 1


def test_five_cycles_produce_five_good_pieces():
    snap = make_machine().step(at(5 * CYCLE_MS))

    assert snap.good == 5
    assert snap.scrap == 0
    assert snap.fault_code == 0
    assert snap.running is True


def test_partial_cycle_is_not_counted():
    snap = make_machine().step(at(5 * CYCLE_MS + 999))

    assert snap.good == 5


def test_stepping_to_an_earlier_time_changes_nothing():
    machine = make_machine()
    later = machine.step(at(5 * CYCLE_MS))

    assert machine.step(at(2 * CYCLE_MS)) == later


def test_scrap_rate_one_makes_every_piece_scrap():
    snap = make_machine(scrap_rate=1.0).step(at(10 * CYCLE_MS))

    assert snap.scrap == 10
    assert snap.good == 0


def test_scrap_rate_zero_makes_no_scrap():
    snap = make_machine(scrap_rate=0.0).step(at(10 * CYCLE_MS))

    assert snap.scrap == 0
    assert snap.good == 10


def test_total_pieces_equal_cycles_regardless_of_scrap():
    snap = make_machine(scrap_rate=0.4, seed=3).step(at(50 * CYCLE_MS))

    assert snap.good + snap.scrap == 50


def test_good_counter_wraps_to_zero_after_counter_max():
    snap = make_machine(counter_max=3).step(at(5 * CYCLE_MS))

    assert snap.good == 1


def test_good_counter_reaches_counter_max_before_wrapping():
    snap = make_machine(counter_max=3).step(at(3 * CYCLE_MS))

    assert snap.good == 3


def test_scrap_counter_wraps_to_zero_after_counter_max():
    snap = make_machine(scrap_rate=1.0, counter_max=3).step(at(5 * CYCLE_MS))

    assert snap.scrap == 1
    assert snap.good == 0


# --- determinism ------------------------------------------------------------


def test_one_big_step_equals_many_small_steps():
    big = make_machine(scrap_rate=0.3, seed=7)
    small = make_machine(scrap_rate=0.3, seed=7)

    big_snap = big.step(at(60 * CYCLE_MS))
    small_snap = walk(small, step_ms=100, total_ms=60 * CYCLE_MS)[-1]

    assert small_snap == big_snap


def test_one_big_step_equals_many_small_steps_with_every_interruption():
    big = busy_machine(seed=11)
    small = busy_machine(seed=11)

    big_snap = big.step(at(900 * 1000))
    small_snap = walk(small, step_ms=100, total_ms=900 * 1000)[-1]

    assert small_snap == big_snap


def test_same_seed_gives_same_result():
    a = make_machine(scrap_rate=0.5, seed=42).step(at(100 * CYCLE_MS))
    b = make_machine(scrap_rate=0.5, seed=42).step(at(100 * CYCLE_MS))

    assert a == b


def test_same_seed_gives_same_interruption_sequence():
    a = walk(busy_machine(seed=21), step_ms=500, total_ms=600 * 1000)
    b = walk(busy_machine(seed=21), step_ms=500, total_ms=600 * 1000)
    c = walk(busy_machine(seed=22), step_ms=500, total_ms=600 * 1000)

    assert a == b
    assert a != c


# --- interruptions ----------------------------------------------------------


def test_interruptions_are_disabled_by_default():
    snaps = walk(make_machine(), step_ms=1000, total_ms=1000 * CYCLE_MS)

    assert all(snap.fault_code == 0 and snap.mode == Mode.AUTO and snap.running for snap in snaps)


def test_spec_without_interval_never_interrupts():
    machine = make_machine(interruptions={InterruptionKind.FAULT: spec(None)})

    snaps = walk(machine, step_ms=1000, total_ms=1000 * CYCLE_MS)

    assert all(snap.running for snap in snaps)


@pytest.mark.parametrize("kind", ALL_KINDS, ids=lambda kind: kind.name)
def test_snapshot_during_each_kind_of_interruption(kind):
    snap = periodic_machine(kind).step(at(12_000))

    fault_code, mode = EXPECTED_DURING[kind]
    assert snap.running is False
    assert snap.fault_code == fault_code
    assert snap.mode == mode


@pytest.mark.parametrize("kind", ALL_KINDS, ids=lambda kind: kind.name)
def test_machine_is_running_again_exactly_when_the_interruption_ends(kind):
    machine = periodic_machine(kind)

    just_before = machine.step(at(14_999))
    at_end = machine.step(at(15_000))

    assert just_before.running is False
    assert at_end.running is True
    assert at_end.fault_code == 0
    assert at_end.mode == Mode.AUTO


@pytest.mark.parametrize("kind", ALL_KINDS, ids=lambda kind: kind.name)
def test_no_pieces_are_counted_during_an_interruption(kind):
    machine = periodic_machine(kind)

    started = machine.step(at(10_000))
    still_stopped = machine.step(at(14_999))

    assert started.good == 10
    assert still_stopped.good == 10


@pytest.mark.parametrize("kind", ALL_KINDS, ids=lambda kind: kind.name)
def test_first_piece_after_an_interruption_takes_one_full_cycle(kind):
    machine = periodic_machine(kind)

    before_cycle = machine.step(at(15_999))
    after_cycle = machine.step(at(16_000))

    assert before_cycle.good == 10
    assert after_cycle.good == 11


@pytest.mark.parametrize("kind", ALL_KINDS, ids=lambda kind: kind.name)
def test_interruptions_repeat_after_each_end(kind):
    machine = periodic_machine(kind)

    before_second = machine.step(at(24_999))
    during_second = machine.step(at(25_000))

    assert before_second.good == 19
    assert before_second.running is True
    assert during_second.good == 20
    assert during_second.running is False


def test_cycle_ending_at_the_instant_an_interruption_starts_is_counted():
    snap = periodic_machine(InterruptionKind.IDLE).step(at(10_000))

    assert snap.good == 10
    assert snap.running is False


def test_simultaneous_starts_are_resolved_by_lowest_kind_value():
    machine = make_machine(
        interruptions={InterruptionKind.SETUP: spec(10.0), InterruptionKind.FAULT: spec(10.0)},
        rng=MeanRandom(1),
    )

    snap = machine.step(at(12_000))

    assert snap.fault_code == FAULT_CODE
    assert snap.mode == Mode.AUTO


def test_every_configured_fault_code_is_eventually_used():
    machine = make_machine(
        fault_codes=(7, 9),
        interruptions={InterruptionKind.FAULT: spec(10.0, 5.0)},
        rng=MeanRandom(1),
    )

    snaps = walk(machine, step_ms=1000, total_ms=2000 * 1000)

    assert {snap.fault_code for snap in snaps} == {0, 7, 9}


def test_zero_duration_interruption_does_not_stall_the_machine():
    machine = make_machine(interruptions={InterruptionKind.IDLE: spec(10.0, 0.0)}, rng=MeanRandom(1))

    snap = machine.step(at(100_000))

    assert snap.good > 0


# --- invariants -------------------------------------------------------------


def test_snapshots_always_respect_state_invariants():
    snaps = walk(busy_machine(seed=5), step_ms=250, total_ms=3600 * 1000)

    for snap in snaps:
        assert snap.fault_code in (0, 7, 9)
        if snap.running:
            assert snap.fault_code == 0
            assert snap.mode == Mode.AUTO
        if snap.fault_code != 0:
            assert snap.running is False
            assert snap.mode == Mode.AUTO
        if snap.mode != Mode.AUTO:
            assert snap.running is False
            assert snap.fault_code == 0


def test_long_walk_exercises_every_kind_of_interruption():
    snaps = walk(busy_machine(seed=5), step_ms=1000, total_ms=3600 * 1000)

    assert any(snap.fault_code != 0 for snap in snaps)
    assert any(not snap.running and snap.fault_code == 0 and snap.mode == Mode.AUTO for snap in snaps)
    assert any(snap.mode == Mode.SETUP for snap in snaps)
    assert any(snap.mode == Mode.PLANNED_STOP for snap in snaps)


def test_counters_never_decrease_and_stay_within_counter_max():
    snaps = walk(busy_machine(seed=9), step_ms=1000, total_ms=3600 * 1000)

    for before, after in pairwise(snaps):
        assert after.good >= before.good
        assert after.scrap >= before.scrap
    assert all(0 <= snap.good <= MAX_COUNTER and 0 <= snap.scrap <= MAX_COUNTER for snap in snaps)


def test_counters_do_not_move_while_stopped():
    snaps = walk(busy_machine(seed=3), step_ms=1000, total_ms=3600 * 1000)

    checked = 0
    for before, after in pairwise(snaps):
        if not before.running and not after.running:
            assert (after.good, after.scrap) == (before.good, before.scrap)
            checked += 1
    assert checked > 0


# --- behavior validation ----------------------------------------------------


@pytest.mark.parametrize(
    ("kwargs", "field"),
    [
        ({"scrap_rate": -0.1}, "scrap_rate"),
        ({"scrap_rate": 1.1}, "scrap_rate"),
        ({"scrap_rate": math.nan}, "scrap_rate"),
        ({"fault_codes": ()}, "fault_codes"),
        ({"fault_codes": (0,)}, "fault_codes"),
        ({"fault_codes": (1, 65536)}, "fault_codes"),
        ({"fault_codes": (True,)}, "fault_codes"),
        ({"fault_codes": (1.5,)}, "fault_codes"),
    ],
)
def test_invalid_machine_behavior_is_rejected(kwargs, field):
    with pytest.raises(ValueError, match=field):
        MachineBehavior(**kwargs)


@pytest.mark.parametrize(
    "kwargs",
    [
        {"scrap_rate": 0.0},
        {"scrap_rate": 1.0},
        {"fault_codes": (1, 65535)},
    ],
)
def test_boundary_machine_behavior_is_accepted(kwargs):
    MachineBehavior(**kwargs)


@pytest.mark.parametrize(
    ("kwargs", "field"),
    [
        ({"mean_interval_s": 0.0}, "mean_interval_s"),
        ({"mean_interval_s": -1.0}, "mean_interval_s"),
        ({"mean_interval_s": math.nan}, "mean_interval_s"),
        ({"mean_interval_s": math.inf}, "mean_interval_s"),
        ({"duration_s": (math.nan, 5.0)}, "duration_s"),
        ({"duration_s": (0.0, math.inf)}, "duration_s"),
        ({"duration_s": (-1.0, 5.0)}, "duration_s"),
        ({"duration_s": (10.0, 5.0)}, "duration_s"),
    ],
)
def test_invalid_interruption_spec_is_rejected(kwargs, field):
    with pytest.raises(ValueError, match=field):
        InterruptionSpec(**kwargs)


@pytest.mark.parametrize(
    "kwargs",
    [
        {"mean_interval_s": None},
        {"mean_interval_s": 0.001, "duration_s": (0.0, 0.0)},
        {"duration_s": (5.0, 5.0)},
    ],
)
def test_boundary_interruption_spec_is_accepted(kwargs):
    InterruptionSpec(**kwargs)
