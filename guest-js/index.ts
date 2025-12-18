import { invoke } from "@tauri-apps/api/core";

/**
 * Queue mode for speech requests
 */
export type QueueMode = "flush" | "add";

/**
 * Options for speaking text
 */
export interface SpeakOptions {
  /** The text to speak */
  text: string;
  /** The language/locale code (e.g., "en-US", "pt-BR", "ja-JP") */
  language?: string;
  /** Specific voice ID to use (from getVoices). Takes priority over language */
  voiceId?: string;
  /** Speech rate (0.25 = quarter speed, 0.5 = half speed, 1.0 = normal, 2.0 = double speed) */
  rate?: number;
  /** Pitch (0.5 = low, 1.0 = normal, 2.0 = high) */
  pitch?: number;
  /** Volume (0.0 = silent, 1.0 = full volume) */
  volume?: number;
  /** Queue mode: "flush" (default) stops current speech, "add" queues after current */
  queueMode?: QueueMode;
}

/**
 * A voice available on the system
 */
export interface Voice {
  /** Unique identifier for the voice */
  id: string;
  /** Display name of the voice */
  name: string;
  /** Language code (e.g., "en-US") */
  language: string;
}

/**
 * Speak the given text using text-to-speech
 *
 * @param options - The speak options including text and optional parameters
 * @returns Promise that resolves when speech has started
 *
 * @example
 * ```typescript
 * import { speak } from "tauri-plugin-tts-api";
 *
 * // Simple usage
 * await speak({ text: "Hello, world!" });
 *
 * // With options
 * await speak({
 *   text: "Olá, mundo!",
 *   language: "pt-BR",
 *   rate: 0.8,
 *   pitch: 1.2,
 *   volume: 1.0
 * });
 *
 * // Queue mode - add to queue instead of interrupting
 * await speak({ text: "First sentence" });
 * await speak({ text: "Second sentence", queueMode: "add" }); // Speaks after first finishes
 * ```
 */
export async function speak(options: SpeakOptions): Promise<void> {
  await invoke("plugin:tts|speak", {
    payload: {
      text: options.text,
      language: options.language ?? null,
      voiceId: options.voiceId ?? null,
      rate: options.rate ?? 1.0,
      pitch: options.pitch ?? 1.0,
      volume: options.volume ?? 1.0,
      queueMode: options.queueMode ?? "flush",
    },
  });
}

/**
 * Stop any ongoing speech
 *
 * @example
 * ```typescript
 * import { stop } from "tauri-plugin-tts-api";
 *
 * await stop();
 * ```
 */
export async function stop(): Promise<void> {
  await invoke("plugin:tts|stop");
}

/**
 * Get available voices, optionally filtered by language
 *
 * @param language - Optional language code to filter voices
 * @returns Array of available voices
 *
 * @example
 * ```typescript
 * import { getVoices } from "tauri-plugin-tts-api";
 *
 * // Get all voices
 * const allVoices = await getVoices();
 *
 * // Get only English voices
 * const englishVoices = await getVoices("en");
 *
 * // Get only Brazilian Portuguese voices
 * const ptBrVoices = await getVoices("pt-BR");
 * ```
 */
export async function getVoices(language?: string): Promise<Voice[]> {
  const response = await invoke<{ voices: Voice[] }>("plugin:tts|get_voices", {
    payload: { language: language ?? null },
  });
  return response.voices;
}

/**
 * Check if TTS is currently speaking
 *
 * @returns True if speech is in progress
 *
 * @example
 * ```typescript
 * import { isSpeaking, stop } from "tauri-plugin-tts-api";
 *
 * if (await isSpeaking()) {
 *   await stop();
 * }
 * ```
 */
export async function isSpeaking(): Promise<boolean> {
  const response = await invoke<{ speaking: boolean }>(
    "plugin:tts|is_speaking"
  );
  return response.speaking;
}

/**
 * Response for pause/resume operations
 */
export interface PauseResumeResponse {
  success: boolean;
  reason?: string;
}

/**
 * Pause the current speech (iOS only - Android/Desktop not supported)
 *
 * @returns Promise with success status and optional reason
 *
 * @example
 * ```typescript
 * import { pauseSpeaking, resumeSpeaking } from "tauri-plugin-tts-api";
 *
 * const result = await pauseSpeaking();
 * if (result.success) {
 *   // Speech is paused
 *   await resumeSpeaking();
 * } else {
 *   console.log("Cannot pause:", result.reason);
 * }
 * ```
 */
export async function pauseSpeaking(): Promise<PauseResumeResponse> {
  return await invoke<PauseResumeResponse>("plugin:tts|pause_speaking");
}

/**
 * Resume paused speech (iOS only - Android/Desktop not supported)
 *
 * @returns Promise with success status and optional reason
 */
export async function resumeSpeaking(): Promise<PauseResumeResponse> {
  return await invoke<PauseResumeResponse>("plugin:tts|resume_speaking");
}

/**
 * Options for previewing a voice
 */
export interface PreviewVoiceOptions {
  /** The voice ID to preview (from getVoices) */
  voiceId: string;
  /** Optional custom text for preview (defaults to a sample sentence) */
  text?: string;
}

/**
 * Preview a voice with sample text
 *
 * Useful for letting users hear what a voice sounds like before selecting it.
 * Uses default rate, pitch, and volume settings.
 *
 * @param options - The preview options including voiceId and optional text
 * @returns Promise that resolves when preview has started
 *
 * @example
 * ```typescript
 * import { getVoices, previewVoice } from "tauri-plugin-tts-api";
 *
 * // Get available voices
 * const voices = await getVoices();
 *
 * // Preview a specific voice
 * await previewVoice({ voiceId: voices[0].id });
 *
 * // Preview with custom text
 * await previewVoice({
 *   voiceId: voices[0].id,
 *   text: "Testing this voice!"
 * });
 * ```
 */
export async function previewVoice(
  options: PreviewVoiceOptions
): Promise<void> {
  await invoke("plugin:tts|preview_voice", {
    payload: {
      voiceId: options.voiceId,
      text: options.text ?? null,
    },
  });
}
