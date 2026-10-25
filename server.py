import os
from fastmcp import FastMCP

mcp = FastMCP("MyRenderServer")

@mcp.tool()
def hello_world(message: str = "Hello") -> str:
    """Test tool for MCP server"""
    return f"Server is running! Message: {message}"

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 10000))
    mcp.run(transport="sse", host="0.0.0.0", port=port)
