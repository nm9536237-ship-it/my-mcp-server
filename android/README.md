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
4. Enter a unique name for the AI client and tap **Authorize this AI client**. Confirm with the device screen lock. The app creates a separate random credential for that client and copies its config once to the clipboard. The name is only your label: the app cannot verify whether the client is Gemini, Claude, or another provider.
5. Paste that config only into the selected MCP client. The app never displays the credential again; revoke the client in the app to invalidate it immediately.
6. Add the config to an MCP client that supports Streamable HTTP and custom headers.

The endpoint is `http://127.0.0.1:8765/mcp`. The server binds to loopback only and accepts only separately authorized client credentials. Credentials are stored as SHA-256 hashes in the app's private storage. Set a device screen lock before authorizing clients. Anyone who obtains a client's config can use that client's access until you revoke it; revoke immediately if a config is shared or exposed. Stop the MCP server and screen capture in the app when finished. Android may revoke screen capture when the service or device stops; grant it again from the app.

## Tools

- `get_screen_state`: screenshot (JPEG image content) and up to 300 visible UI nodes with text, view IDs, bounds, and interaction flags.
- `tap_screen`: dispatch a tap at screen coordinates.
- `type_text`: set text in the currently focused editable field.
- `swipe_screen`: dispatch a swipe, with duration limited to 100-5000 ms.

## Limits and privacy

`127.0.0.1` refers to the device running the MCP client. A desktop client cannot use its own localhost address to reach an Android phone; this app does not expose a public or LAN endpoint. Android requires the user to enable accessibility and approve each screen-capture session.

The app itself does not send screen content to a cloud service. It returns screen content to an authorized MCP client, and that client may forward it to an AI provider. Authorizing an AI client grants it the ability to read the screen and perform the listed gestures; revoke clients you no longer trust. Hosted Gemini or Claude services are cloud services and may receive tool results if their client forwards them. To keep screen data on the phone, use an MCP client and model that both run locally on the phone. This project does not include an AI model or MCP client and cannot prevent a separately installed client from transmitting tool results. Gestures and text entry are direct tool calls, so connect only a client you trust.