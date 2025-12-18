use serde::{Deserialize, Serialize};

/// Queue mode for speech requests
#[derive(Debug, Clone, Copy, Default, Deserialize, Serialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum QueueMode {
    /// Flush any pending speech and start speaking immediately (default)
    #[default]
    Flush,
    /// Add to queue and speak after current speech finishes
    Add,
}

/// Request to speak text
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SpeakRequest {
    /// The text to speak
    pub text: String,
    /// The language/locale code (e.g., "en-US", "pt-BR", "ja-JP")
    #[serde(default)]
    pub language: Option<String>,
    /// Voice ID to use (from getVoices)
    #[serde(default)]
    pub voice_id: Option<String>,
    /// Speech rate (0.25 = quarter speed, 0.5 = half speed, 1.0 = normal, 2.0 = double speed)
    #[serde(default = "default_rate")]
    pub rate: f32,
    /// Pitch (0.5 = low, 1.0 = normal, 2.0 = high)
    #[serde(default = "default_pitch")]
    pub pitch: f32,
    /// Volume (0.0 = silent, 1.0 = full volume)
    #[serde(default = "default_volume")]
    pub volume: f32,
    /// Queue mode: "flush" (default) or "add"
    #[serde(default)]
    pub queue_mode: QueueMode,
}

fn default_rate() -> f32 {
    1.0
}
fn default_pitch() -> f32 {
    1.0
}
fn default_volume() -> f32 {
    1.0
}

/// Response from speak command
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SpeakResponse {
    /// Whether speech was successfully initiated
    pub success: bool,
    /// Optional warning message (e.g., voice not found, using fallback)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub warning: Option<String>,
}

/// Request to stop speaking
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StopRequest {}

/// Response from stop command
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StopResponse {
    pub success: bool,
}

/// A voice available on the system
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Voice {
    /// Unique identifier for the voice
    pub id: String,
    /// Display name of the voice
    pub name: String,
    /// Language code (e.g., "en-US")
    pub language: String,
}

/// Request to get available voices
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GetVoicesRequest {
    /// Optional language filter
    #[serde(default)]
    pub language: Option<String>,
}

/// Response with available voices
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GetVoicesResponse {
    pub voices: Vec<Voice>,
}

/// Request to check if TTS is currently speaking
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct IsSpeakingRequest {}

/// Response for is_speaking check
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct IsSpeakingResponse {
    pub speaking: bool,
}

/// Response for pause/resume commands
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PauseResumeResponse {
    pub success: bool,
    /// Reason for failure (if success is false)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

/// Request to preview a voice with sample text
#[derive(Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PreviewVoiceRequest {
    /// Voice ID to preview
    pub voice_id: String,
    /// Optional custom sample text (uses default if not provided)
    #[serde(default)]
    pub text: Option<String>,
}

impl PreviewVoiceRequest {
    /// Get the sample text, using a default if none provided
    pub fn sample_text(&self) -> &str {
        self.text
            .as_deref()
            .unwrap_or("Hello! This is a sample of how this voice sounds.")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_speak_request_defaults() {
        let json = r#"{"text": "Hello world"}"#;
        let request: SpeakRequest = serde_json::from_str(json).unwrap();

        assert_eq!(request.text, "Hello world");
        assert!(request.language.is_none());
        assert!(request.voice_id.is_none());
        assert_eq!(request.rate, 1.0);
        assert_eq!(request.pitch, 1.0);
        assert_eq!(request.volume, 1.0);
    }

    #[test]
    fn test_speak_request_full() {
        let json = r#"{
            "text": "Olá",
            "language": "pt-BR",
            "voiceId": "com.apple.voice.enhanced.pt-BR",
            "rate": 0.8,
            "pitch": 1.2,
            "volume": 0.9
        }"#;

        let request: SpeakRequest = serde_json::from_str(json).unwrap();
        assert_eq!(request.text, "Olá");
        assert_eq!(request.language, Some("pt-BR".to_string()));
        assert_eq!(
            request.voice_id,
            Some("com.apple.voice.enhanced.pt-BR".to_string())
        );
        assert_eq!(request.rate, 0.8);
        assert_eq!(request.pitch, 1.2);
        assert_eq!(request.volume, 0.9);
    }

    #[test]
    fn test_voice_serialization() {
        let voice = Voice {
            id: "test-voice".to_string(),
            name: "Test Voice".to_string(),
            language: "en-US".to_string(),
        };

        let json = serde_json::to_string(&voice).unwrap();
        assert!(json.contains("\"id\":\"test-voice\""));
        assert!(json.contains("\"name\":\"Test Voice\""));
        assert!(json.contains("\"language\":\"en-US\""));
    }

    #[test]
    fn test_get_voices_request_optional_language() {
        // Without language filter
        let json1 = r#"{}"#;
        let request1: GetVoicesRequest = serde_json::from_str(json1).unwrap();
        assert!(request1.language.is_none());

        // With language filter
        let json2 = r#"{"language": "en"}"#;
        let request2: GetVoicesRequest = serde_json::from_str(json2).unwrap();
        assert_eq!(request2.language, Some("en".to_string()));
    }
}
