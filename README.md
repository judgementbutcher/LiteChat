# LiteChat

LiteChat is a lightweight, native Android AI chat client. It talks directly from the device to the provider selected by the user: there is no LiteChat account, proxy, VPS, domain, analytics, advertising, remote configuration, or crash-collection service.

- Kotlin, Jetpack Compose, Material 3, Room and DataStore
- Android 12+ (`minSdk 31`), `compileSdk` / `targetSdk 35`
- OpenAI Responses, Anthropic Messages, Gemini `generateContent`, and OpenAI-compatible Chat Completions
- Local conversation search, pinning, archive/restore, response versions (including regenerating an answer with a different model), prompt templates, attachments and exports
- Stable follow-output behavior, IME-aware composer, and live native Markdown/LaTeX rendering
- English and Simplified Chinese; a deep-black animated glass theme by default, plus light, system and Android dynamic color options
- MIT licensed

## Install the debug APK

The generated artifact is `artifacts/LiteChat-0.6.3-debug.apk`. Android may ask you to allow installation from the app used to open the file. Debug builds are signed with the standard local Android debug certificate and are intended for testing, not store distribution.

To build it yourself:

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Set `sdk.dir` in the ignored `local.properties` file if `ANDROID_HOME` is not configured. JDK 17 or newer and Android SDK 35 are required.

## First use

1. Open **Providers** and select a preset or add a custom provider.
2. Enter the API key issued by that provider, save, then use **Test connection**. A successful test imports the remote model list; models can always be added manually.
3. Create a conversation. Choose the model from the composer and enable search when needed. Conversation title and system prompt live in the top-bar menu.
4. Send a message. While the model answers, the composer exposes a labelled **Stop** button that immediately cancels the network stream; partial output is retained. **Retry** creates another response version without deleting earlier versions.

Preset endpoints:

| Provider | Protocol | Base URL |
|---|---|---|
| OpenAI | Responses | `https://api.openai.com/v1` |
| Anthropic | Messages | `https://api.anthropic.com/v1` |
| Gemini | `streamGenerateContent` | `https://generativelanguage.googleapis.com/v1beta` |
| DeepSeek | OpenAI-compatible | `https://api.deepseek.com` |
| OpenRouter | OpenAI-compatible | `https://openrouter.ai/api/v1` |

Custom endpoints must be valid HTTPS URLs. LiteChat deliberately does not support cleartext LAN endpoints, custom CAs, or an in-app HTTP/SOCKS proxy; it uses Android's normal network and VPN configuration.

Model capabilities are negotiated at request time. LiteChat does not reject an image or hide search because of incomplete model metadata: it maps the requested feature to the selected protocol and lets the provider return the authoritative capability result. OpenRouter uses its native web plugin; other OpenAI-compatible endpoints receive a `web_search` tool request when search is enabled.

## Local data and keys

Room is the single source of truth for conversations. Requests are rebuilt from selected local response versions and do not rely on provider-side conversation state.

API keys are encrypted with a non-exportable Android Keystore AES-GCM key. Ciphertext is stored under the app's no-backup private directory. Keys are not stored in Room, DataStore, logs, JSON backups, Markdown exports, or request bodies. Android backup and device transfer are disabled for all app data.

Deleting the app deletes its local database, attachments and keys. Use **Data → Export JSON backup** first if the conversation history is needed. Backup schema `2` includes provider configuration without keys, models, pinned/archive conversation state, response versions, attachment data and templates. Schema `1` backups remain importable, and import does not change keys already stored on the receiving device.

## Attachments and rendering

- At most 4 files per message and 10 MiB per file.
- Images are copied to private storage, resized to a maximum edge of 2048 pixels when necessary, shown as thumbnails in the conversation, and available in a full-screen pinch-to-zoom preview.
- UTF-8, UTF-8 BOM, UTF-16 LE/BE TXT and Markdown files are extracted locally.
- PDFs are parsed locally; OCR is not provided. A scanned PDF with no extractable text produces an explicit error.
- Text injection is capped at 100,000 characters per file and visibly marked as truncated.
- Responses render headings, lists, quotes, links, tables and fenced code blocks locally as text streams in. Remote images are not loaded.
- LaTeX is typeset on-device, including inline `$...$` / `\\(...\\)` and display `$$...$$` / `\\[...\\]` notation. Fractions, roots, scripts, matrices and common AMS symbols are supported without a WebView or network renderer.

## Failure behavior

Streaming output is persisted at a throttled interval. Cancellation, network interruption and malformed events preserve partial text and status. Authentication, quota/rate-limit, context-length and unsupported-capability errors include an actionable message. Paid generation requests are never retried automatically.

No API key is required by the automated tests. TLS MockWebServer fixtures cover streaming, malformed events, 401, 429, request mapping, multimodal payloads and search fields. Instrumentation tests cover Room migration/cascades, first-run behavior, live Markdown/LaTeX rendering, follow-output stability and full-screen image preview.

The same unit test, lint and APK build run on every GitHub pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for the local workflow and project conventions.

## Manual update check

This build checks releases from `judgementbutcher/LiteChat`. It only fetches release metadata when the user taps the check button; it never downloads or installs an update. Forks can change or clear the `GITHUB_OWNER` and `GITHUB_REPO` `BuildConfig` fields in `app/build.gradle.kts`.

## Project layout

```text
app/src/main/java/app/litechat/android/
├── attachment/       # image, text and PDF processing
├── data/             # repository, Room, backup and settings
├── network/          # four protocol adapters, SSE and error mapping
├── security/         # Android Keystore-backed secret storage
└── ui/               # Compose navigation and screens
```

## Scope

LiteChat intentionally excludes accounts, cloud sync, RAG/knowledge bases, MCP/plugin execution, arbitrary tool execution, OCR, audio/video, Office documents, a generic web scraper, silent updating and release signing.

## License

[MIT](LICENSE)
