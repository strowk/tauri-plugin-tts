# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2025-12

### Added

- Initial release
- Cross-platform TTS support (macOS, Windows, Linux, iOS, Android)
- `speak()` - Text-to-speech with customizable options
- `stop()` - Stop current speech
- `getVoices()` - List available voices with language info
- `isSpeaking()` - Check if speech is in progress
- Voice selection by ID (`voiceId` parameter)
- Rate normalization (1.0 = normal speed across all platforms)
- Pitch control (0.5 - 2.0)
- Volume control (0.0 - 1.0)
- Language selection (`language` parameter)
- TypeScript bindings with full type definitions
- Comprehensive documentation and examples

### Platform Support

| Platform | Engine                            |
| -------- | --------------------------------- |
| macOS    | AVFoundation (via tts crate)      |
| Windows  | SAPI (via tts crate)              |
| Linux    | speech-dispatcher (via tts crate) |
| iOS      | AVSpeechSynthesizer               |
| Android  | TextToSpeech API                  |

### Requirements

- Tauri: 2.9+
- Rust: 1.77+
- Android SDK: 24+ (Android 7.0+)
- iOS: 14.0+
