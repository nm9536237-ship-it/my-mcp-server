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
    
