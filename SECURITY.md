# Security

## Reporting

For now, file a private issue or email the maintainer. Do not open public GitHub issues for vulnerabilities.

## Threat model

OctoAlly Android is a thick client to a self-hosted OctoAlly server. The user controls both. We assume:

- The server is reachable over a private network (typically Tailscale).
- The user enters their own server's IP in Settings; we ship no defaults.
- The phone is the user's own; we don't try to defend against a rooted-and-malicious owner.
- Cleartext HTTP is only allowed to IPs the user explicitly whitelists in `app/src/main/res/xml/network_security_config.xml`. Everything else requires HTTPS.

## Known limitations / TODO

The following were identified in pre-release security review and are tracked here. They are non-blocking for the v1 public release because the conditions under which they would be exploitable do not exist today, but they need to land before any of those conditions changes.

### 1. Terminal WebSocket has no `Authorization` header

**File:** `feature/session/src/main/java/com/octoally/feature/session/ui/TerminalWebView.kt` — the `connect()` method opens `ws://<host>/api/terminal/<sessionId>` with no bearer token. The rest of the app has a `TokenProvider` plumbed through `OctoAllyWebSocketClient` for the agent-events socket, but the terminal socket bypasses it.

**Why deferred:** The current `NoopTokenProvider` returns null, so even the agent-events socket sends no header. The moment a real `TokenProvider` is wired (e.g. DataStore-backed bearer), the terminal socket becomes the weakest link.

**Fix:** Inject `TokenProvider` into `TerminalHolder`; mirror `OctoAllyWebSocketClient.kt:104-107`'s pattern of adding `Authorization: Bearer <token>` to the `Request.Builder` when the token is non-null.

### 2. `baseUrl` nav-arg is unvalidated

**File:** `app/src/main/java/com/octoally/app/MainActivity.kt` route `terminal/{sessionId}?baseUrl={baseUrl}` — `baseUrl` flows directly into `TerminalWebView.connect()` and gets concatenated into the WebSocket URL with no scheme/host validation.

**Why deferred:** The only exported intent filter on `MainActivity` is `ACTION_SEND` for `image/*`, which doesn't carry a `baseUrl` extra. There is no exposed deep-link path that an attacker-controlled URL could enter today.

**Fix:** Validate `baseUrl` against the user-saved `NetworkConfig.mainHost` before opening the socket; reject anything that doesn't match.

### 3. Crash file is unrotated and unredacted

**File:** `app/src/main/java/com/octoally/app/OctoAllyApplication.kt` writes uncaught exception stack traces to `filesDir/last_crash.txt` with no size cap and no redaction of host/path strings.

**Why deferred:** `android:allowBackup="false"` (set in the manifest) prevents this file from being included in any auto-backup or `adb backup`. The file is only readable by the app itself.

**Fix:** Cap file size, redact `host=`/`baseUrl=` substrings, and delete after surfacing once.

### 4. Image upload has no size cap

**File:** `feature/session/src/main/java/com/octoally/feature/session/clipboard/ClipboardUploadViewModel.kt` reads the entire image content URI into memory and Base64-encodes it. A hostile share-intent pointing at a 500MB content URI (e.g. a `MediaStore` video misreported as `image/*`) could OOM the process.

**Why deferred:** Self-hosted; the only entity sending share intents is the user.

**Fix:** Pre-check `cursor.getLong(SIZE)` and reject inputs above ~25MB.

## What we did fix in the pre-release pass

- Sanitized all hardcoded Tailscale IPs from defaults; users now enter their own.
- Replaced manual JS string escape in `injectSys` with `JSONObject.quote()` (the prior table missed U+2028 / U+2029 JS line terminators, which an attacker-controlled server error message could use to break out of the JS string literal).
- Added `proguard-rules.pro` with `-assumenosideeffects` for `Log.{v,d,i}` to strip diagnostic logging from release builds.
- Hardened `MainActivity` against task-hijacking with `android:taskAffinity=""` + `android:launchMode="singleTask"`.
- Confirmed no `addJavascriptInterface`, no `setAllowFileAccess`, no TLS bypass anywhere, `WebViewAssetLoader` properly serving content over the synthetic `https://appassets.androidplatform.net` origin, `cleartextTrafficPermitted="false"` baseline.
