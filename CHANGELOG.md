# Changelog

All notable user-facing changes are documented here. LiteChat follows semantic versioning.

## 0.4.0 - 2026-07-17

### Added

- Full-size, pinch-to-zoom image previews from sent messages, with real thumbnails in both the composer and conversation.
- Native web-search request mapping for OpenAI-compatible endpoints and OpenRouter's web plugin.
- Provider error details when a multimodal or search request is rejected upstream.

### Changed

- Image input and web search are now negotiated with the provider instead of being blocked by stale local model-capability labels.
- Markdown and LaTeX render continuously while a response is streaming.
- Attached documents use compact file surfaces in the sent message instead of exposing local paths.

### Fixed

- Replaced token-by-token maximum-distance scrolling with a stable bottom anchor.
- User scrolling now pauses output following immediately and returning to the bottom resumes it.
- Switching models no longer silently turns off an active search preference.

## 0.3.0 - 2026-07-17

### Added

- Native, offline LaTeX typesetting for inline and display math, including fractions, scripts and matrices.
- A focused Android instrumentation regression for streaming-to-Markdown rendering.
- Public contribution, security and continuous-integration documentation.

### Changed

- Rebuilt the chat composer with a tighter model/search/action hierarchy and responsive keyboard handling.
- Refined navigation, typography, spacing, surfaces, empty states and light/dark color systems.
- Kept streaming responses in a stable text layout, then applied rich rendering once complete.
- Reduced the debug APK by roughly 460 KiB compared with 0.2.0 despite the expanded math support.

### Fixed

- Removed repeated maximum-offset list jumps that could make the screen flash or temporarily appear empty during streaming.
- Prevented IME padding from inflating the Scaffold bottom bar and pushing the composer to the top of the screen.
- Preserved the app bar, conversation viewport and scroll-follow state while the keyboard is visible.

## 0.2.0 - 2026-07-17

- Added attachments, response versions, archive/pin/search, prompt templates, exports and update checks.
- Expanded protocol and failure handling across OpenAI, Anthropic, Gemini and compatible providers.

## 0.1.0 - 2026-07-16

- Initial public Android release.
