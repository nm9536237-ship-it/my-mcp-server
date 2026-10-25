import os
import uvicorn
from starlette.responses import JSONResponse
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("Data-Bridge-Server")

STORED_DATA = []

@mcp.tool()
def send_to_bridge(items: list[str]) -> str:
    """Send and store any text, prompts, scenes, or data to the server queue."""
    global STORED_DATA
    STORED_DATA.extend(items)
    return f"Successfully saved {len(items)} items. Total items in queue: {len(STORED_DATA)}"

@mcp.tool()
def get_bridge_data() -> str:
    """Retrieve all stored items from the server queue."""
    global STORED_DATA
    if not STORED_DATA:
        return "The server queue is currently empty."
    return "\n".join([f"[{i+1}] {item}" for i, item in enumerate(STORED_DATA)])

@mcp.tool()
def clear_bridge_data() -> str:
    """Clear all items from the server queue."""
    global STORED_DATA
    STORED_DATA.clear()
    return "All items have been cleared from the queue."

@mcp.custom_route("/data", methods=["GET"])
async def read_data(request):
    return JSONResponse({
        "status": "success",
        "total": len(STORED_DATA),
        "items": STORED_DATA
    })

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    # Direct uvicorn run so Render binds the port cleanly without errors
    try:
        app = mcp.streamable_http_app()
    except AttributeError:
        app = mcp.sse_app()
    uvicorn.run(app, host="0.0.0.0", port=port)
