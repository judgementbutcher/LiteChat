# Changelog

All notable user-facing changes are documented here. LiteChat follows semantic versioning.

## 0.6.0 - 2026-07-19

### Added

- A modern deep-black visual system with translucent glass surfaces, mint highlights, softly animated ambient light and a subtle background grid.
- Clear glass cards across providers, templates, archived conversations, navigation and the chat composer, with richer selection and transition motion.
- A prominent labelled Stop button while a model is answering.
- A regression test that verifies a stalled network stream can be cancelled immediately.

### Changed

- New installations now open in the dark theme with dynamic color disabled so the intended black visual hierarchy is preserved; light and system themes remain available.
- Updated Room, DataStore, Kotlin serialization, coroutines and AppCompat dependencies.
- Moved streaming and document I/O work off the main thread.

### Fixed

- Cancelling generation now closes the active OkHttp call immediately, including while the provider is connected but not sending another SSE line. Partial output remains saved with a stopped status.

## 0.5.0 - 2026-07-18

### Added

- Regenerate any answer with a different model from the message menu. Each attempt is kept as its own response version, and the model that produced the shown version is labelled once a message has more than one.
- An animated typing indicator while a reply streams in, a send control that morphs between send and stop, and gentle enter/exit motion for the model and search chips, attachment row and empty state.
- A short haptic confirmation when a message is sent.

### Changed

- Light, dark and dynamic color changes now cross-fade instead of snapping.
- Streaming replies are re-typeset at a steady cadence instead of on every persisted delta, removing redundant Markdown and LaTeX work on long or math-heavy answers without changing the finished layout.
- Conversation search is debounced, so typing no longer runs a database query on every keystroke.
- Decorative motion (the typing indicator and color cross-fade) follows the system "remove animations" accessibility setting.

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
