import math
from dataclasses import dataclass, field
from enum import IntEnum

MAX_FAULT_CODE = 65535

class InterruptionKind(IntEnum):
    # The value is the priority when two interruptions start at the same instant.
    FAULT = 1
    IDLE = 2
    SETUP = 3
    PLANNED_STOP = 4

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
