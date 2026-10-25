import os
import uvicorn
from starlette.responses import JSONResponse
from mcp.server.fastmcp import FastMCP

PORT = int(os.environ.get("PORT", 8000))

# 421 Host header error fix: DNS-rebinding protection band karna
# aur host 0.0.0.0 par rakhna, taaki onrender.com se requests aa sakein.
try:
    from mcp.server.transport_security import TransportSecuritySettings

    mcp = FastMCP(
        "Data-Bridge-Server",
        host="0.0.0.0",
        port=PORT,
        stateless_http=True,
        transport_security=TransportSecuritySettings(
            enable_dns_rebinding_protection=False
        ),
    )
except (ImportError, TypeError):
    # Purane mcp version ke liye fallback
    mcp = FastMCP("Data-Bridge-Server", host="0.0.0.0", port=PORT)

STORED_DATA = []


@mcp.tool()
def send_to_bridge(items: list[str]) -> str:
    """Send and store any text, prompts, scenes, or data to the server queue."""
    STORED_DATA.extend(items)
    return f"Successfully saved {len(items)} items. Total items in queue: {len(STORED_DATA)}"


@mcp.tool()
def get_bridge_data() -> str:
    """Retrieve all stored items from the server queue."""
    if not STORED_DATA:
        return "The server queue is currently empty."
    return "\n".join([f"[{i + 1}] {item}" for i, item in enumerate(STORED_DATA)])


@mcp.tool()
def clear_bridge_data() -> str:
    """Clear all items from the server queue."""
    STORED_DATA.clear()
    return "All items have been cleared from the queue."


@mcp.custom_route("/", methods=["GET"])
async def health(request):
    return JSONResponse({"status": "ok", "server": "Data-Bridge-Server"})


@mcp.custom_route("/data", methods=["GET"])
async def read_data(request):
    return JSONResponse(
        {
            "status": "success",
            "total": len(STORED_DATA),
            "items": STORED_DATA,
        }
    )


if __name__ == "__main__":
    try:
        app = mcp.streamable_http_app()  # endpoint: /mcp
    except AttributeError:
        app = mcp.sse_app()  # endpoint: /sse
    uvicorn.run(app, host="0.0.0.0", port=PORT)
