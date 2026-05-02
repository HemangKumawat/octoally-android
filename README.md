# OctoAlly Android

Android client for [OctoAlly](https://github.com/), a self-hosted Claude Code companion. Connects to your OctoAlly server over Tailscale (or any reachable IP) and exposes your Claude Code sessions as a real terminal on your phone — paste images, switch projects, fire slash commands, and survive backgrounding without losing the WebSocket.

## What's in here

- **Hybrid render:** xterm.js inside an Android System WebView (wrapped in a `FrameLayout` with explicit `MATCH_PARENT` layout params — the canonical fix for the "blank WebView in Compose AndroidView" trap).
- **Dual WebSocket architecture:** one socket for agent events (status, prompts, hooks, route badges); a separate socket per session for raw PTY bytes feeding xterm.
- **Six personality themes:** Pro Operator, Neon Vibecoder, Retro CRT, Tasteful Studio, Swiss Minimal, Playful Creature. 17-token shape per variant, animated swap, picker in Settings.
- **WebViewPool:** session swaps reuse warm WebView + WebSocket pairs (LRU, capacity 4) so switching is near-instant instead of paying a full Chromium init each time.
- **Touch scroll + pinch zoom + haptics:** xterm.js doesn't ship a touch handler, so we bind our own `touchstart/move/end → term.scrollLines` with rAF momentum decay; pinch is a Compose `detectTransformGestures` overlay; every primary tap fires a `LongPress` haptic.
- **One-tap "+ Terminal":** new sessions inside a project default to a Terminal task and skip the form (long-press still opens the advanced launcher).

## Modules

```
app/                 MainActivity, NavHost, HiltApp, SessionSyncService
core/
  network/           WebSocket clients, AgentEvent, TokenProvider, reconnect logic
  data/              Room (ProjectEntity, SkillsDB), DataStore (SettingsRepository)
  ui/                Theme tokens (6 variants), Typography, Haptics
feature/
  projects/          Project list, ProjectsRail drawer, ActiveSessions, new-session
  session/           SessionViewModel, SessionScreen, TerminalWebView, CommandBar, palette
  settings/          Server connection + appearance + font size
  files/             FileExplorer + viewer
  git/               Git status / diff panel
```

## Setup

### 1. Clone

```
git clone <this-repo-url>
cd octoally-android
```

### 2. Configure your server IP

Two places need your OctoAlly server's reachable IP (Tailscale, LAN, or anything that resolves):

**a)** `app/src/main/res/xml/network_security_config.xml` — add a `<domain>` entry under `<domain-config>`:
```xml
<domain includeSubdomains="false">100.x.y.z</domain>
```
(Required because Android blocks cleartext HTTP by default; this whitelist allows it for your specific Tailscale CGNAT addresses.)

**b)** First launch of the app → **Settings** tab → enter Host / Main Port / Upload Port → Save & Test.

### 3. Build

The repo ships with a Gradle wrapper. From the project root:

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. `adb install -r app-debug.apk` to your phone or emulator.

### 4. Server-side requirement

You need an OctoAlly server reachable at the IP you configured. The Android client expects:

- HTTP API at `http://<host>:<mainPort>` (default 42010)
- WebSocket endpoints `/api/sessions` (agent events) and `/api/terminal/<sessionId>` (raw PTY bytes)
- Optional upload endpoint at `<host>:<uploadPort>` (default 7799) for clipboard image upload

## Build requirements

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1) or newer |
| compileSdk | 35 |
| minSdk | 29 (Android 10) |
| Kotlin | 2.1.x |
| JVM toolchain | 17 |
| Gradle | 8.9+ (wrapper provided) |

## License

MIT — see [LICENSE](LICENSE).

This client is independent of and not affiliated with Anthropic. "Claude Code" is a product of Anthropic.
