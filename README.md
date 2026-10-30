import os
from fastmcp import FastMCP

mcp = FastMCP("MyRenderMCP")

@mcp.tool()
def hello(name: str) -> str:
    """टेस्टिंग टूल"""
    return f"नमस्ते {name}, आपका MCP सर्वर चालू है!"

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    mcp.run(transport="streamable-http", host="0.0.0.0", port=port)

## Android Local MCP Controller

The `android/` directory contains a native Android companion app with local screen and accessibility tools. See [android/README.md](android/README.md) for setup, permissions, and privacy limitations. The Android endpoint is loopback-only; this repository does not publish or host phone screen data.
    
