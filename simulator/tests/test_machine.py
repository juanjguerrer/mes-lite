import random
from datetime import UTC, datetime, timedelta
from itertools import pairwise

from plcsim.config import MAX_COUNTER, MachineConfig
from plcsim.machine import Machine, MachineBehavior, MachineSnapshot
from plcsim.states import Mode

START = datetime(2026, 1, 1, 8, 0, 0, tzinfo=UTC)
CYCLE_MS = 1000


def make_machine(scrap_rate: float = 0.0, seed: int = 1, counter_max: int = MAX_COUNTER) -> Machine:
    config = MachineConfig(code="TEST-01", cycle_time_ms=CYCLE_MS, counter_max=counter_max)
    behavior = MachineBehavior(scrap_rate=scrap_rate)
    return Machine(config, behavior, random.Random(seed), START)


def at(milliseconds: int) -> datetime:
    return START + timedelta(milliseconds=milliseconds)


FAULT_CODES = (7, 9)


def make_faulty_machine(seed: int = 1, scrap_rate: float = 0.0) -> Machine:
    config = MachineConfig(code="TEST-01", cycle_time_ms=CYCLE_MS)
    behavior = MachineBehavior(
        scrap_rate=scrap_rate,
        fault_mean_interval_s=5.0,
        fault_duration_s=(10.0, 10.0),
        fault_codes=FAULT_CODES,
    )
    return Machine(config, behavior, random.Random(seed), START)


def walk(machine: Machine, step_ms: int, total_ms: int) -> list[MachineSnapshot]:
    return [machine.step(at(ms)) for ms in range(step_ms, total_ms + 1, step_ms)]


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


def test_faults_are_disabled_by_default():
    snaps = walk(make_machine(), step_ms=1000, total_ms=1000 * CYCLE_MS)

    assert all(snap.fault_code == 0 and snap.running for snap in snaps)


def test_fault_starts_and_then_ends():
    snaps = walk(make_faulty_machine(), step_ms=1000, total_ms=300 * 1000)

    first_fault = next(i for i, snap in enumerate(snaps) if snap.fault_code != 0)
    assert snaps[first_fault].running is False
    assert snaps[first_fault].fault_code in FAULT_CODES
    recovered = [snap for snap in snaps[first_fault:] if snap.fault_code == 0]
    assert recovered
    assert recovered[0].running is True


def test_fault_lasts_the_configured_duration():
    snaps = walk(make_faulty_machine(), step_ms=100, total_ms=120 * 1000)

    start = next(i for i, snap in enumerate(snaps) if snap.fault_code != 0)
    end = next(i for i in range(start, len(snaps)) if snaps[i].fault_code == 0)
    observed_s = (end - start) * 0.1
    assert 9.9 <= observed_s <= 10.1


def test_fault_code_is_nonzero_only_while_not_running():
    snaps = walk(make_faulty_machine(seed=5), step_ms=250, total_ms=600 * 1000)

    for snap in snaps:
        assert snap.fault_code in (0, *FAULT_CODES)
        if snap.fault_code != 0:
            assert snap.running is False
        else:
            assert snap.running is True


def test_no_pieces_are_counted_during_a_fault():
    snaps = walk(make_faulty_machine(seed=3, scrap_rate=0.3), step_ms=1000, total_ms=600 * 1000)

    checked = 0
    for before, after in pairwise(snaps):
        if before.fault_code != 0 and after.fault_code != 0:
            assert (after.good, after.scrap) == (before.good, before.scrap)
            checked += 1
    assert checked > 0


def test_production_resumes_after_a_fault():
    snaps = walk(make_faulty_machine(seed=2), step_ms=1000, total_ms=600 * 1000)

    first_fault = next(i for i, snap in enumerate(snaps) if snap.fault_code != 0)
    assert snaps[-1].good != snaps[first_fault].good


def test_one_big_step_equals_many_small_steps_with_faults():
    big = make_faulty_machine(seed=11, scrap_rate=0.3)
    small = make_faulty_machine(seed=11, scrap_rate=0.3)

    big_snap = big.step(at(900 * 1000))
    small_snap = walk(small, step_ms=100, total_ms=900 * 1000)[-1]

    assert small_snap == big_snap


def test_same_seed_gives_same_fault_sequence():
    a = walk(make_faulty_machine(seed=21), step_ms=500, total_ms=300 * 1000)
    b = walk(make_faulty_machine(seed=21), step_ms=500, total_ms=300 * 1000)
    c = walk(make_faulty_machine(seed=22), step_ms=500, total_ms=300 * 1000)

    assert a == b
    assert a != c
