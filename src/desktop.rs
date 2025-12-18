use serde::de::DeserializeOwned;
use std::sync::Mutex;
use tauri::{plugin::PluginApi, AppHandle, Runtime};
use tts::Tts as TtsEngine;

use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<Tts<R>> {
    let engine = TtsEngine::default()?;
    Ok(Tts {
        app: app.clone(),
        engine: Mutex::new(engine),
    })
}

/// Access to the TTS APIs.
pub struct Tts<R: Runtime> {
    #[allow(dead_code)]
    app: AppHandle<R>,
    engine: Mutex<TtsEngine>,
}

impl<R: Runtime> Tts<R> {
    /// Speak the given text
    pub fn speak(&self, payload: SpeakRequest) -> crate::Result<SpeakResponse> {
        let mut engine = self.engine.lock().map_err(|_| crate::Error::LockError)?;

        // Set voice if specified
        if let Some(ref voice_id) = payload.voice_id {
            if let Ok(voices) = engine.voices() {
                if let Some(voice) = voices.into_iter().find(|v| v.id() == *voice_id) {
                    let _ = engine.set_voice(&voice);
                }
            }
        }

        // Convert rate from user scale (0.25-2.0 where 1.0 = normal) to TTS library scale
        // Platform differences:
        // - macOS (AVFoundation): 0.0-1.0 where 0.5 is normal
        // - Windows (SAPI): varies by voice, generally 0-10 where 0 is normal
        // - Linux (speech-dispatcher): -100 to 100 where 0 is normal
        // The tts library abstracts this, but on macOS it passes through directly
        #[cfg(target_os = "macos")]
        let rate_to_set = {
            // macOS: multiply by 0.5 to map 1.0 -> 0.5
            let normalized = payload.rate * 0.5;
            normalized.clamp(0.1, 1.0)
        };
        #[cfg(target_os = "windows")]
        let rate_to_set = {
            // Windows SAPI: rate is typically -10 to 10, tts lib normalizes to 0.0-1.0
            // 1.0 user = 0.5 lib (normal)
            let normalized = payload.rate * 0.5;
            normalized.clamp(0.1, 1.0)
        };
        #[cfg(target_os = "linux")]
        let rate_to_set = {
            // Linux speech-dispatcher: tts lib normalizes, similar mapping
            let normalized = payload.rate * 0.5;
            normalized.clamp(0.1, 1.0)
        };
        let _ = engine.set_rate(rate_to_set);

        // Pitch: tts library uses 0.5-2.0, same as our API
        let _ = engine.set_pitch(payload.pitch);

        // Volume: both use 0.0-1.0
        let _ = engine.set_volume(payload.volume);

        // Determine if we should interrupt current speech
        // flush (default) = interrupt, add = queue
        let interrupt = payload.queue_mode != QueueMode::Add;

        // Speak the text
        engine.speak(&payload.text, interrupt)?;

        Ok(SpeakResponse {
            success: true,
            warning: None,
        })
    }

    /// Stop any ongoing speech
    pub fn stop(&self) -> crate::Result<StopResponse> {
        let mut engine = self.engine.lock().map_err(|_| crate::Error::LockError)?;
        engine.stop()?;
        Ok(StopResponse { success: true })
    }

    /// Get available voices
    pub fn get_voices(&self, payload: GetVoicesRequest) -> crate::Result<GetVoicesResponse> {
        let engine = self.engine.lock().map_err(|_| crate::Error::LockError)?;
        let voices = engine.voices()?;

        let filtered_voices: Vec<Voice> = voices
            .into_iter()
            .filter(|v| {
                if let Some(ref lang_filter) = payload.language {
                    v.language()
                        .to_string()
                        .to_lowercase()
                        .contains(&lang_filter.to_lowercase())
                } else {
                    true
                }
            })
            .map(|v| Voice {
                id: v.id().to_string(),
                name: v.name().to_string(),
                language: v.language().to_string(),
            })
            .collect();

        Ok(GetVoicesResponse {
            voices: filtered_voices,
        })
    }

    /// Check if TTS is currently speaking
    pub fn is_speaking(&self) -> crate::Result<IsSpeakingResponse> {
        let engine = self.engine.lock().map_err(|_| crate::Error::LockError)?;
        let speaking = engine.is_speaking()?;
        Ok(IsSpeakingResponse { speaking })
    }

    /// Pause the current speech (not supported on desktop)
    pub fn pause_speaking(&self) -> crate::Result<PauseResumeResponse> {
        // Desktop TTS library (tts-rs) doesn't support pause/resume
        // Return a descriptive error
        Ok(PauseResumeResponse {
            success: false,
            reason: Some("Pause is not supported on desktop platform".to_string()),
        })
    }

    /// Resume paused speech (not supported on desktop)
    pub fn resume_speaking(&self) -> crate::Result<PauseResumeResponse> {
        // Desktop TTS library (tts-rs) doesn't support pause/resume
        Ok(PauseResumeResponse {
            success: false,
            reason: Some("Resume is not supported on desktop platform".to_string()),
        })
    }

    /// Preview a voice with sample text
    pub fn preview_voice(&self, payload: PreviewVoiceRequest) -> crate::Result<SpeakResponse> {
        // Create a speak request with the sample text and specified voice
        let speak_request = SpeakRequest {
            text: payload.sample_text().to_string(),
            language: None,
            voice_id: Some(payload.voice_id),
            rate: 1.0,
            pitch: 1.0,
            volume: 1.0,
            queue_mode: QueueMode::Flush,
        };
        self.speak(speak_request)
    }
}
