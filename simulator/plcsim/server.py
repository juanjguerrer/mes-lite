import asyncio

from asyncua.server.server import Server
from asyncua import ua

async def main():
    server = Server()
    await server.init()
    server.set_endpoint("opc.tcp://0.0.0.0:4840/mes-lite/")
    uri = "urn:mes-lite:simulator"
    idx = await server.register_namespace(uri)

    plant = await server.nodes.objects.add_object(idx, "Plant")
    var = await plant.add_variable(idx, "Counter", 0, varianttype=ua.VariantType.UInt16)
    async with server:
        counter = 65530
        while True:
            data = ua.DataValue(ua.Variant(counter, ua.VariantType.UInt16), SourceTimestamp = ua.DateTime.now())
            await var.write_value(data)
            counter = (counter + 1) % 65536
            await asyncio.sleep(1)

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Server stopped by user")