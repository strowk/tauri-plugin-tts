use serde::{ser::Serializer, Serialize};

use crate::models::ValidationError;

pub type Result<T> = std::result::Result<T, Error>;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error(transparent)]
    Io(#[from] std::io::Error),

    #[cfg(mobile)]
    #[error(transparent)]
    PluginInvoke(#[from] tauri::plugin::mobile::PluginInvokeError),

    #[cfg(desktop)]
    #[error("TTS error: {0}")]
    Tts(#[from] tts::Error),

    #[error("Failed to acquire lock on TTS engine")]
    LockError,

    #[error("TTS engine mutex was poisoned - internal state may be corrupted")]
    MutexPoisoned,

    #[error("TTS not initialized")]
    NotInitialized,

    #[error("Validation error: {0}")]
    Validation(#[from] ValidationError),

    #[error("TTS operation failed: {0}")]
    OperationFailed(String),
}

impl Error {
    pub fn code(&self) -> &'static str {
        match self {
            Error::Io(_) => "IO_ERROR",
            #[cfg(mobile)]
            Error::PluginInvoke(_) => "PLUGIN_INVOKE_ERROR",
            #[cfg(desktop)]
            Error::Tts(_) => "TTS_ENGINE_ERROR",
            Error::LockError => "LOCK_ERROR",
            Error::MutexPoisoned => "MUTEX_POISONED",
            Error::NotInitialized => "NOT_INITIALIZED",
            Error::Validation(_) => "VALIDATION_ERROR",
            Error::OperationFailed(_) => "OPERATION_FAILED",
        }
    }
}

impl Serialize for Error {
    fn serialize<S>(&self, serializer: S) -> std::result::Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        use serde::ser::SerializeStruct;

        let mut state = serializer.serialize_struct("Error", 2)?;
        state.serialize_field("code", self.code())?;
        state.serialize_field("message", &self.to_string())?;
        state.end()
    }
}
