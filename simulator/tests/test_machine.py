import random
from datetime import UTC, datetime, timedelta

from plcsim.config import MAX_COUNTER, MachineConfig
from plcsim.machine import Machine, MachineBehavior
from plcsim.states import Mode

START = datetime(2026, 1, 1, 8, 0, 0, tzinfo=UTC)
CYCLE_MS = 1000


def make_machine(scrap_rate: float = 0.0, seed: int = 1, counter_max: int = MAX_COUNTER) -> Machine:
    config = MachineConfig(code="TEST-01", cycle_time_ms=CYCLE_MS, counter_max=counter_max)
    behavior = MachineBehavior(scrap_rate=scrap_rate)
    return Machine(config, behavior, random.Random(seed), START)


def at(milliseconds: int) -> datetime:
    return START + timedelta(milliseconds=milliseconds)


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


def test_one_big_step_equals_many_small_steps():
    big = make_machine(scrap_rate=0.3, seed=7)
    small = make_machine(scrap_rate=0.3, seed=7)

    big_snap = big.step(at(60 * CYCLE_MS))
    for ms in range(0, 60 * CYCLE_MS + 1, 100):
        small_snap = small.step(at(ms))

    assert small_snap == big_snap


def test_scrap_rate_one_makes_every_piece_scrap():
    snap = make_machine(scrap_rate=1.0).step(at(10 * CYCLE_MS))

    assert snap.scrap == 10
    assert snap.good == 0


def test_scrap_rate_zero_makes_no_scrap():
    snap = make_machine(scrap_rate=0.0).step(at(10 * CYCLE_MS))

    assert snap.scrap == 0
    assert snap.good == 10


def test_same_seed_gives_same_result():
    a = make_machine(scrap_rate=0.5, seed=42).step(at(100 * CYCLE_MS))
    b = make_machine(scrap_rate=0.5, seed=42).step(at(100 * CYCLE_MS))

    assert a == b


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
