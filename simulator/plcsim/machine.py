import math
import random
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import IntEnum

from .config import MachineConfig
from .states import Mode

MAX_FAULT_CODE = 65535

class InterruptionKind(IntEnum):
    FAULT = 1
    IDLE = 2
    SETUP = 3
    PLANNED_STOP = 4

_MODE_BY_KIND = {
    InterruptionKind.SETUP: Mode.SETUP,
    InterruptionKind.PLANNED_STOP: Mode.PLANNED_STOP,
}

@dataclass(frozen=True)
class InterruptionSpec:
    mean_interval_s: float | None = None
    duration_s: tuple[float, float] = (30.0, 120.0)

    def __post_init__(self) -> None:
        if not all(math.isfinite(d) for d in self.duration_s):
            raise ValueError(f"duration_s must be finite, got {self.duration_s}")
        if self.mean_interval_s is not None and not math.isfinite(self.mean_interval_s):
            raise ValueError(f"mean_interval_s must be finite, got {self.mean_interval_s}")
        if self.mean_interval_s is not None and self.mean_interval_s <= 0.0:
            raise ValueError(f"mean_interval_s must be positive, got {self.mean_interval_s}")
        if self.duration_s[0] < 0.0:
            raise ValueError(f"duration_s must be non-negative, got {self.duration_s}")
        if self.duration_s[0] > self.duration_s[1]:
            raise ValueError(f"duration_s min must be <= max, got {self.duration_s}")

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

@dataclass(frozen=True)
class MachineBehavior:
    scrap_rate: float = 0.0
    fault_codes: tuple[int, ...] = (1,)
    interruptions: dict[InterruptionKind, InterruptionSpec] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not math.isfinite(self.scrap_rate) or not (0.0 <= self.scrap_rate <= 1.0):
            raise ValueError(f"scrap_rate must be in [0, 1], got {self.scrap_rate}")
        if not self.fault_codes:
            raise ValueError("fault_codes must not be empty")
        for code in self.fault_codes:
            if isinstance(code, bool) or not isinstance(code, int) or not (1 <= code <= MAX_FAULT_CODE):
                raise ValueError(f"fault_codes must be in [1, {MAX_FAULT_CODE}], got {code!r}")

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