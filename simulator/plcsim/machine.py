import random
from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import IntEnum

from .config import MachineConfig
from .states import Mode


class Event(IntEnum):
    # The value is the priority when two events fall on the same instant.
    FAULT_END = 1
    CYCLE_END = 2
    FAULT_START = 3

@dataclass(frozen=True)
class MachineSnapshot:
    running: bool
    fault_code: int
    good: int
    scrap: int
    mode: Mode

@dataclass(frozen=True)
class MachineBehavior:
    scrap_rate: float = 0.0
    fault_mean_interval_s: float | None = None
    fault_duration_s: tuple[float, float] = (30.0, 120.0)
    fault_codes: tuple[int, ...] = (1,)

class Machine:
    def __init__(self, config: MachineConfig, behavior: MachineBehavior, rng: random.Random, start: datetime):
        self.config = config
        self.behavior = behavior
        self.rng = rng
        self._next_cycle_at = start + timedelta(milliseconds=config.cycle_time_ms)
        self._good = 0
        self._scrap = 0
        self._fault_code = 0
        self._mode = Mode.AUTO
        self._fault_ends_at: datetime | None = None
        self._next_fault_at: datetime | None = None
        self._schedule_next_fault(start)

    @property
    def _running(self) -> bool:
        return self._mode == Mode.AUTO and self._fault_code == 0

    def snapshot(self) -> MachineSnapshot:
        return MachineSnapshot(
            running=self._running,
            fault_code=self._fault_code,
            good=self._good,
            scrap=self._scrap,
            mode=self._mode,
        )

    def step(self, now: datetime) -> MachineSnapshot:
        while (event := self._next_event()) is not None:
            at, kind = event
            if at > now:
                break
            if kind == Event.CYCLE_END:
                self._on_cycle_end()
            elif kind == Event.FAULT_START:
                self._on_fault_start(at)
            else:
                self._on_fault_end(at)
        return self.snapshot()

    def _wrap(self, value: int) -> int:
        return value % (self.config.counter_max + 1)

    def _schedule_next_fault(self, after: datetime) -> None:
        if self.behavior.fault_mean_interval_s is not None:
            interval_s = self.rng.expovariate(1.0 / self.behavior.fault_mean_interval_s)
            self._next_fault_at = after + timedelta(seconds=interval_s)
        else:
            self._next_fault_at = None

    def _next_event(self) -> tuple[datetime, Event] | None:
        pending: list[tuple[datetime, Event]] = []
        if self._running:
            pending.append((self._next_cycle_at, Event.CYCLE_END))
        if self._fault_ends_at is not None:
            pending.append((self._fault_ends_at, Event.FAULT_END))
        elif self._next_fault_at is not None:
            pending.append((self._next_fault_at, Event.FAULT_START))
        return min(pending, default=None)

    def _on_cycle_end(self) -> None:
        self._next_cycle_at += timedelta(milliseconds=self.config.cycle_time_ms)
        if self.rng.random() < self.behavior.scrap_rate:
            self._scrap = self._wrap(self._scrap + 1)
        else:
            self._good = self._wrap(self._good + 1)

    def _on_fault_start(self, at: datetime) -> None:
        self._fault_code = self.rng.choice(self.behavior.fault_codes)
        fault_duration = self.rng.uniform(*self.behavior.fault_duration_s)
        self._fault_ends_at = at + timedelta(seconds=fault_duration)
        self._next_fault_at = None

    def _on_fault_end(self, at: datetime) -> None:
        self._fault_code = 0
        self._fault_ends_at = None
        self._schedule_next_fault(at)
        self._next_cycle_at = at + timedelta(milliseconds=self.config.cycle_time_ms)