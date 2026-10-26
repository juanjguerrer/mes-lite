import random
from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import IntEnum

from .behavior import InterruptionKind, MachineBehavior
from .config import MachineConfig
from .states import Mode

_MODE_BY_KIND = {
    InterruptionKind.SETUP: Mode.SETUP,
    InterruptionKind.PLANNED_STOP: Mode.PLANNED_STOP,
}

class Event(IntEnum):
    # The value is the priority when two events fall on the same instant.
    INTERRUPTION_END = 1
    CYCLE_END = 2
    INTERRUPTION_START = 3

@dataclass(frozen=True)
class MachineSnapshot:
    running: bool
    fault_code: int
    good: int
    scrap: int
    mode: Mode

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
        self._active: InterruptionKind | None = None
        self._active_ends_at: datetime | None = None
        self._next_start: dict[InterruptionKind, datetime] = {}
        self._schedule_next_starts(start)

    @property
    def _running(self) -> bool:
        return self._active is None and self._mode == Mode.AUTO

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
            at, kind, interruption = event
            if at > now:
                break
            if kind is Event.CYCLE_END:
                self._on_cycle_end()
            elif kind is Event.INTERRUPTION_START:
                assert interruption is not None
                self._on_interruption_start(at, interruption)
            else:
                self._on_interruption_end(at)
        return self.snapshot()

    def _wrap(self, value: int) -> int:
        return value % (self.config.counter_max + 1)

    def _schedule_next_starts(self, after: datetime) -> None:
        self._next_start = {}
        for kind in InterruptionKind:
            spec = self.behavior.interruptions.get(kind)
            if spec is None or spec.mean_interval_s is None:
                continue
            interval_s = self.rng.expovariate(1.0 / spec.mean_interval_s)
            self._next_start[kind] = after + timedelta(seconds=interval_s)

    def _next_event(self) -> tuple[datetime, Event, InterruptionKind | None] | None:
        pending: list[tuple[datetime, Event, InterruptionKind | None]] = []
        if self._active is not None:
            assert self._active_ends_at is not None
            pending.append((self._active_ends_at, Event.INTERRUPTION_END, None))
        else:
            pending.append((self._next_cycle_at, Event.CYCLE_END, None))
            for kind, at in self._next_start.items():
                pending.append((at, Event.INTERRUPTION_START, kind))
        return min(pending, default=None)

    def _on_cycle_end(self) -> None:
        self._next_cycle_at += timedelta(milliseconds=self.config.cycle_time_ms)
        if self.rng.random() < self.behavior.scrap_rate:
            self._scrap = self._wrap(self._scrap + 1)
        else:
            self._good = self._wrap(self._good + 1)

    def _on_interruption_start(self, at: datetime, kind: InterruptionKind) -> None:
        spec = self.behavior.interruptions[kind]
        if kind is InterruptionKind.FAULT:
            self._fault_code = self.rng.choice(self.behavior.fault_codes)
        duration_s = self.rng.uniform(*spec.duration_s)
        self._active = kind
        self._mode = _MODE_BY_KIND.get(kind, Mode.AUTO)
        self._active_ends_at = at + timedelta(seconds=duration_s)
        self._next_start = {}

    def _on_interruption_end(self, at: datetime) -> None:
        self._active = None
        self._active_ends_at = None
        self._fault_code = 0
        self._mode = Mode.AUTO
        self._schedule_next_starts(at)
        self._next_cycle_at = at + timedelta(milliseconds=self.config.cycle_time_ms)
