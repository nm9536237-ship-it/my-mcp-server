# Android Local MCP Controller

This is a native Android companion app. It is separate from the Python/Render service in the repository root. It does not call an AI provider or run an autonomous decision loop.

## Build

Open this `android/` directory in Android Studio and let Gradle sync. Install the debug app on an Android 8.0+ device. A local Android SDK is required; build from a configured environment with:

```sh
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Start

1. Open the app and enable its accessibility service in Android Settings.
2. Tap **Grant screen capture** and approve Android's screen-sharing prompt.
3. Tap **Start local MCP server**. Android shows an ongoing notification while it is active.
4. Copy the client config from the app, or use `mcp_config.json` and replace `PASTE_APP_TOKEN_HERE` with the app's displayed access token.
5. Add that config to an MCP client that supports Streamable HTTP and custom headers.

The endpoint is `http://127.0.0.1:8765/mcp`. The server binds to loopback only, and requires the per-install bearer token. Stop the server in the app when finished. Android may revoke screen capture when the service or device stops; grant it again from the app.

## Tools

- `get_screen_state`: screenshot (JPEG image content) and up to 300 visible UI nodes with text, view IDs, bounds, and interaction flags.
- `tap_screen`: dispatch a tap at screen coordinates.
- `type_text`: set text in the currently focused editable field.
- `swipe_screen`: dispatch a swipe, with duration limited to 100-5000 ms.

## Limits and privacy

`127.0.0.1` refers to the device running the MCP client. A desktop client cannot use its own localhost address to reach an Android phone; this app does not expose a public or LAN endpoint. Android requires the user to enable accessibility and approve each screen-capture session.

The app itself does not send screen content to a cloud service. It returns screen content to the configured MCP client, and that client may forward it to an AI provider. To keep screen data on the phone, use an MCP client and model that both run locally on the phone. This project does not include an AI model or MCP client and cannot prevent a separately installed client from transmitting tool results. Gestures and text entry are direct tool calls, so connect only a client you trust.