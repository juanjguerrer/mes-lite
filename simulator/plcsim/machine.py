import random
from dataclasses import dataclass
from datetime import datetime, timedelta

from .config import MachineConfig
from .states import Mode


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
        while self._next_cycle_at <= now:
            self._next_cycle_at += timedelta(milliseconds=self.config.cycle_time_ms)
            if self._running:
                if self.rng.random() < self.behavior.scrap_rate:
                    self._scrap = self._wrap(self._scrap + 1)
                else:
                    self._good = self._wrap(self._good + 1)
        return self.snapshot()

    def _wrap(self, value: int) -> int:
        return value % (self.config.counter_max + 1)