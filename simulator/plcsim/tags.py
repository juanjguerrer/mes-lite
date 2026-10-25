from collections import namedtuple
from asyncua.ua import VariantType
from .states import Mode

Tag = namedtuple("Tag", ["name", "type", "initial"])

TAGS = (
    Tag("Running", VariantType.Boolean, False),
    Tag("FaultCode", VariantType.UInt16, 0),
    Tag("GoodCounter", VariantType.UInt16, 0),
    Tag("ScrapCounter", VariantType.UInt16, 0),
    Tag("Mode", VariantType.UInt16, Mode.AUTO)
)