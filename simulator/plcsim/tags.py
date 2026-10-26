from collections import namedtuple

from asyncua.ua import VariantType

from .machine import MachineSnapshot
from .states import Mode

Tag = namedtuple("Tag", ["name", "type", "initial"])

TAGS = (
    Tag("Running", VariantType.Boolean, False),
    Tag("FaultCode", VariantType.UInt16, 0),
    Tag("GoodCounter", VariantType.UInt16, 0),
    Tag("ScrapCounter", VariantType.UInt16, 0),
    Tag("Mode", VariantType.UInt16, Mode.AUTO)
)
TAGS_BY_NAME = {tag.name: tag for tag in TAGS}
TagValues = dict[str, int | bool]

def tag_values(snap: MachineSnapshot) -> TagValues:
    """Return a dictionary of tag values from a MachineSnapshot."""
    return {
        "Running": snap.running,
        "FaultCode": snap.fault_code,
        "GoodCounter": snap.good,
        "ScrapCounter": snap.scrap,
        "Mode": snap.mode.value
    }
