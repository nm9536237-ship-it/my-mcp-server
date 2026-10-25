import os
from starlette.responses import JSONResponse
from mcp.server.fastmcp import FastMCP

# खुला डेटा ब्रिज MCP सर्वर
mcp = FastMCP("Data-Bridge-Server")

# किसी भी तरह का डेटा/प्रॉम्प्ट स्टोर करने की लिस्ट
STORED_DATA = []

@mcp.tool()
def send_to_bridge(items: list[str]) -> str:
    """Claude से कोई भी डेटा, प्रॉम्प्ट, सीन, टेक्स्ट या लिंक इस सर्वर पर सुरक्षित भेजता है।"""
    global STORED_DATA
    count = len(items)
    STORED_DATA.extend(items)
    return f"सफलतापूर्वक {count} आइटम सर्वर पर सेव कर दिए गए हैं। सर्वर में अब कुल {len(STORED_DATA)} आइटम हैं।"

@mcp.tool()
def get_bridge_data() -> str:
    """सर्वर में सुरक्षित रखा गया सारा डेटा क्रम से दिखाता है।"""
    global STORED_DATA
    if not STORED_DATA:
        return "सर्वर अभी पूरी तरह खाली है।"
    
    result = "📦 सर्वर पर सुरक्षित डेटा सूची:\n"
    for i, item in enumerate(STORED_DATA, 1):
        result += f"\n[{i}]: {item}\n"
    return result

@mcp.tool()
def clear_bridge_data() -> str:
    """सर्वर के पूरे डेटा को साफ़ (खाली) कर देता है।"""
    global STORED_DATA
    STORED_DATA.clear()
    return "सर्वर का सारा डेटा साफ़ कर दिया गया है।"

# किसी भी ब्राउज़र या स्क्रिप्ट से डेटा देखने का वेब लिंक (/data)
@mcp.custom_route("/data", methods=["GET"])
async def read_data(request):
    return JSONResponse({
        "status": "success",
        "total": len(STORED_DATA),
        "items": STORED_DATA
    })

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    mcp.run(transport="sse", host="0.0.0.0", port=port)
