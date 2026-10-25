import os
import uuid
import datetime
from pathlib import Path

import httpx
import uvicorn
from starlette.responses import JSONResponse, FileResponse
from mcp.server.fastmcp import FastMCP

PORT = int(os.environ.get("PORT", 8000))

# --- Veo settings (sab Render ke Environment Variables se badal sakte hain) ---
BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
VEO_MODEL = os.environ.get("VEO_MODEL", "veo-3.1-generate-preview")
MAX_PER_DAY = int(os.environ.get("MAX_VIDEOS_PER_DAY", "5"))  # kharch par roksham
PUBLIC_URL = os.environ.get("RENDER_EXTERNAL_URL", "").rstrip("/")

VIDEO_DIR = Path("/tmp/videos")
VIDEO_DIR.mkdir(parents=True, exist_ok=True)

# 421 Host header fix
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
    mcp = FastMCP("Data-Bridge-Server", host="0.0.0.0", port=PORT)

STORED_DATA = []
JOBS = {}  # job_id -> {"operation": str, "prompt": str, "file": str | None}
USAGE = {"date": "", "count": 0}


def _api_key():
    return os.environ.get("GEMINI_API_KEY", "").strip()


def _link(filename):
    return f"{PUBLIC_URL}/videos/{filename}"


# ------------------------- Bridge (queue) tools -------------------------
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


# ------------------------- Video tools (Veo) -------------------------
@mcp.tool()
async def generate_video(prompt: str, aspect_ratio: str = "16:9") -> str:
    """Start generating a short video with Google Veo from a text prompt.
    Returns a job_id immediately. Video takes a few minutes; then call
    get_video_status(job_id). aspect_ratio: "16:9" (landscape) or "9:16" (portrait).
    This uses the paid Gemini API."""
    key = _api_key()
    if not key:
        return "GEMINI_API_KEY Render ke Environment Variables mein nahi mili. Pehle use add karein."

    today = datetime.date.today().isoformat()
    if USAGE["date"] != today:
        USAGE["date"], USAGE["count"] = today, 0
    if USAGE["count"] >= MAX_PER_DAY:
        return f"Aaj ki limit ({MAX_PER_DAY} videos) poori ho gayi."

    payload = {"instances": [{"prompt": prompt}]}
    if aspect_ratio != "16:9":
        payload["parameters"] = {"aspectRatio": aspect_ratio}

    async with httpx.AsyncClient(timeout=60) as client:
        r = await client.post(
            f"{BASE_URL}/models/{VEO_MODEL}:predictLongRunning",
            headers={"x-goog-api-key": key, "Content-Type": "application/json"},
            json=payload,
        )
    if r.status_code != 200:
        return f"Gemini API error {r.status_code}: {r.text[:400]}"

    operation = r.json().get("name")
    if not operation:
        return f"Unexpected response: {r.text[:400]}"

    USAGE["count"] += 1
    job_id = uuid.uuid4().hex[:8]
    JOBS[job_id] = {"operation": operation, "prompt": prompt, "file": None}
    return (
        f"Video generation shuru ho gayi. job_id = {job_id}. "
        "2-5 minute baad get_video_status(job_id) se check karein."
    )


@mcp.tool()
async def get_video_status(job_id: str) -> str:
    """Check a video job started with generate_video. When ready, returns the video link."""
    job = JOBS.get(job_id)
    if not job:
        return "Ye job_id nahi mili (server restart hone par purani jobs mit jaati hain)."
    if job["file"]:
        return f"Video taiyar hai: {_link(job['file'])}"

    key = _api_key()
    if not key:
        return "GEMINI_API_KEY nahi mili."

    async with httpx.AsyncClient(timeout=120, follow_redirects=True) as client:
        r = await client.get(
            f"{BASE_URL}/{job['operation']}", headers={"x-goog-api-key": key}
        )
        if r.status_code != 200:
            return f"Status check error {r.status_code}: {r.text[:400]}"
        data = r.json()

        if not data.get("done"):
            return "Video abhi ban rahi hai. Thodi der baad dobara check karein."
        if "error" in data:
            return f"Video generation fail hui: {str(data['error'])[:400]}"

        try:
            uri = data["response"]["generateVideoResponse"]["generatedSamples"][0][
                "video"
            ]["uri"]
        except (KeyError, IndexError, TypeError):
            return f"Kaam poora hua par video link nahi mila: {str(data)[:500]}"

        v = await client.get(uri, headers={"x-goog-api-key": key})
        if v.status_code != 200:
            return f"Video download error {v.status_code}"

    filename = f"{uuid.uuid4().hex}.mp4"
    (VIDEO_DIR / filename).write_bytes(v.content)
    job["file"] = filename
    link = _link(filename)
    STORED_DATA.append(f"VIDEO [{job['prompt'][:60]}]: {link}")
    return f"Video taiyar hai: {link}"


# ------------------------- Web routes -------------------------
@mcp.custom_route("/", methods=["GET"])
async def health(request):
    return JSONResponse({"status": "ok", "server": "Data-Bridge-Server"})


@mcp.custom_route("/data", methods=["GET"])
async def read_data(request):
    return JSONResponse(
        {"status": "success", "total": len(STORED_DATA), "items": STORED_DATA}
    )


@mcp.custom_route("/videos/{name}", methods=["GET"])
async def serve_video(request):
    name = request.path_params["name"]
    path = VIDEO_DIR / name
    if Path(name).name != name or not path.is_file():
        return JSONResponse({"error": "not found"}, status_code=404)
    return FileResponse(path, media_type="video/mp4")


if __name__ == "__main__":
    try:
        app = mcp.streamable_http_app()  # endpoint: /mcp
    except AttributeError:
        app = mcp.sse_app()  # endpoint: /sse
    uvicorn.run(app, host="0.0.0.0", port=PORT)
