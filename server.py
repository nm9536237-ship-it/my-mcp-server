import os
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("Naresh-Simple-Server")

@mcp.tool()
def get_story_prompt(topic: str) -> str:
    """कहानी और वीडियो के लिए बेहतरीन सिनेमैटिक प्रॉम्प्ट देता है।"""
    return f"Cinematic 3D animation style shot of {topic}, vibrant colors, smooth camera movement, highly detailed."

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    mcp.run(transport="sse", host="0.0.0.0", port=port)

