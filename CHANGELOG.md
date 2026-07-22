# Changelog

All notable user-facing changes are documented here. LiteChat follows semantic versioning.

## 0.7.6 - 2026-07-22

### Fixed

- Removed the duplicate microphone in the composer. The empty message state now shows a single voice button, matching the ChatGPT mobile layout instead of two side-by-side microphones that triggered the same dictation action.

## 0.7.5 - 2026-07-22

### Fixed

- Update checks now identify themselves to GitHub and fall back to the public latest-release page when the anonymous API is rate-limited with HTTP 403/429.
- APK and checksum downloads include the same client identity for more reliable GitHub asset delivery.

## 0.7.4 - 2026-07-22

### Fixed

- Voice input is now always visible in the composer, including on devices where Android does not report a dedicated speech recognition service.
- Added a system voice-recognition fallback and kept the microphone affordance beside the primary action to match the ChatGPT mobile composer.

### Improved

- Refined the conversation screen spacing, neutral background, message widths, user bubble shape, and empty state for a closer ChatGPT-style layout.

## 0.7.3 - 2026-07-22

### Added

- Added a ChatGPT-style voice input action to the composer. When the message is empty, the primary action is a blue microphone button; tapping it requests audio permission and inserts live speech transcription into the draft.
- Recording state now uses a clear stop control and voice waveform treatment, while typed drafts keep the send action.

## 0.7.2 - 2026-07-22

### Fixed

- Streaming replies no longer flicker. While a response is generating it is now painted one Markdown block at a time: completed blocks stay fixed and only the newest block repaints as tokens arrive, instead of redrawing the whole answer on every update. Formatting and LaTeX stay live throughout, and the finished reply is unchanged and fully selectable.

## 0.7.1 - 2026-07-22

### Fixed

- Restored live Markdown and LaTeX formatting while replies stream. Incoming snapshots are conflated, parsed off the main thread, and atomically applied to the existing message view so rich formatting stays responsive without blank frames or stale renders.

## 0.7.0 - 2026-07-22

### Added

- Added on-device speech input with partial transcription and an animated recording control.

### Improved

- Streaming replies now update through a lightweight text path and parse Markdown only after completion, reducing frame work and visible flicker.
- Chat rows use stable content types and smoother insertion/removal transitions for more consistent scrolling.

## 0.6.6 - 2026-07-22

### Fixed

- Streaming replies no longer look like two overlapping answers. The reply text now paints over its previous frame on every update, so partially rendered tokens are cleared instead of ghosting on top of each other while the model is answering.

## 0.6.5 - 2026-07-21

### Changed

- Redesigned the interface around a calm, flat, neutral visual language inspired by the ChatGPT app: the animated ambient backdrop and translucent "liquid glass" panels are replaced by solid backgrounds and flat surfaces, the palette moves to neutral greys with a single high-contrast accent, and user messages now sit in a simple borderless grey bubble with their actions below.

### Fixed

- Streaming replies no longer flicker. Assistant text is kept non-selectable while it is still arriving, so the whole reply no longer blanks and repaints on every token; text selection is enabled once the response finishes.

## 0.6.4 - 2026-07-21

### Fixed

- Streaming replies no longer force the chat viewport to follow every response update, preventing repeated scroll repositioning and visible flicker while the model is answering.
- Added a one-shot scroll-to-bottom action so users can read earlier content during generation and return to the latest response when they choose.

## 0.6.3 - 2026-07-21

### Changed

- Refined the default dark interface into a calmer, more concise glass system with clearer depth, brighter text contrast and a more cohesive mint-and-lilac accent palette.
- Reworked the background into low-contrast flowing light ribbons and added restrained refraction highlights across the navigation, chat composer, message bubbles and management screens.
- Settings and data-management controls are now grouped into clearer translucent panels; glass motion shares one lightweight animation timeline and respects the system remove-animations setting.

## 0.6.2 - 2026-07-21

### Added

- In-app updates from GitHub Releases: LiteChat now compares versions, downloads the matching APK, verifies its SHA-256 checksum, and opens the Android system installer.

### Fixed

- Update checks now reject releases that do not include the matching APK and checksum assets instead of directing users to an incomplete release.

## 0.6.1 - 2026-07-21

### Fixed

- Streaming replies no longer make the chat viewport flicker. Output following now uses one scroll path, pauses reliably while the user reads earlier messages, and resumes when the user returns to the bottom or sends a new message.

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
