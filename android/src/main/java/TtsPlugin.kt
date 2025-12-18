package io.affex.tts

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.JSArray
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import app.tauri.plugin.Invoke
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

@InvokeArg
class SpeakArgs {
    var text: String = ""
    var language: String? = null
    var voiceId: String? = null
    var rate: Float = 1.0f
    var pitch: Float = 1.0f
    var volume: Float = 1.0f
    var queueMode: String = "flush" // "flush" or "add"
}

@InvokeArg
class GetVoicesArgs {
    var language: String? = null
}

@InvokeArg
class PreviewVoiceArgs {
    var voiceId: String = ""
    var text: String? = null
    
    /** Returns the sample text or a default preview message */
    fun sampleText(): String = text ?: "Hello! This is a sample of how this voice sounds."
}

data class PendingSpeak(val invoke: Invoke, val args: SpeakArgs)

@TauriPlugin
class TtsPlugin(private val activity: Activity) : Plugin(activity), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingRequests = ConcurrentLinkedQueue<PendingSpeak>()
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    companion object {
        private const val TAG = "TtsPlugin"
    }

    init {
        Log.d(TAG, "============================================")
        Log.d(TAG, "TtsPlugin INIT")
        Log.d(TAG, "  Package: ${activity.packageName}")
        Log.d(TAG, "  Android SDK: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "  Creating TextToSpeech engine...")
        tts = TextToSpeech(activity, this)
        audioManager = activity.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
        Log.d(TAG, "  AudioManager initialized: ${audioManager != null}")
        Log.d(TAG, "============================================")
    }

    override fun onInit(status: Int) {
        Log.d(TAG, "============================================")
        Log.d(TAG, "TTS onInit() CALLED")
        Log.d(TAG, "  Status: $status (SUCCESS=${TextToSpeech.SUCCESS}, ERROR=${TextToSpeech.ERROR})")
        
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.i(TAG, "  TTS initialized successfully")
            
            // Log available info
            tts?.let { engine ->
                val defaultVoice = engine.defaultVoice
                Log.d(TAG, "  Default voice: ${defaultVoice?.name ?: "null"}")
                Log.d(TAG, "  Default language: ${engine.defaultVoice?.locale?.toLanguageTag() ?: "unknown"}")
                Log.d(TAG, "  Available voices: ${engine.voices?.size ?: 0}")
            }
            
            // Setup utterance progress listener for speech events
            setupUtteranceProgressListener()
            
            // Process all pending requests
            val pendingCount = pendingRequests.size
            Log.d(TAG, "  Processing $pendingCount pending requests")
            while (pendingRequests.isNotEmpty()) {
                val pending = pendingRequests.poll()
                pending?.let { executeSpeakInternal(it.invoke, it.args) }
            }
        } else {
            Log.e(TAG, "  TTS initialization FAILED with status: $status")
        }
        Log.d(TAG, "============================================")
    }
    
    /**
     * Setup listener for speech events (start, finish, error)
     */
    private fun setupUtteranceProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Speech started: $utteranceId")
                val event = JSObject()
                event.put("id", utteranceId ?: "")
                trigger("speech:start", event)
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Speech finished: $utteranceId")
                val event = JSObject()
                event.put("id", utteranceId ?: "")
                trigger("speech:finish", event)
                
                // Release audio focus when done
                releaseAudioFocus()
            }
            
            @Deprecated("Deprecated in API level 21")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Speech error: $utteranceId")
                val event = JSObject()
                event.put("id", utteranceId ?: "")
                event.put("error", "Speech synthesis error")
                trigger("speech:error", event)
                
                releaseAudioFocus()
            }
            
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "Speech error: $utteranceId, code: $errorCode")
                val event = JSObject()
                event.put("id", utteranceId ?: "")
                event.put("error", getErrorMessage(errorCode))
                event.put("code", errorCode)
                trigger("speech:error", event)
                
                releaseAudioFocus()
            }
            
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                Log.d(TAG, "Speech stopped: $utteranceId, interrupted: $interrupted")
                val event = JSObject()
                event.put("id", utteranceId ?: "")
                event.put("interrupted", interrupted)
                trigger("speech:cancel", event)
                
                releaseAudioFocus()
            }
        })
    }
    
    /**
     * Request audio focus before speaking
     */
    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioFocusRequest = focusRequest
            audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
    
    /**
     * Release audio focus after speaking
     */
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
    
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            TextToSpeech.ERROR -> "Generic error"
            TextToSpeech.ERROR_INVALID_REQUEST -> "Invalid request"
            TextToSpeech.ERROR_NETWORK -> "Network error"
            TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> "TTS not installed"
            TextToSpeech.ERROR_OUTPUT -> "Output error"
            TextToSpeech.ERROR_SERVICE -> "Service error"
            TextToSpeech.ERROR_SYNTHESIS -> "Synthesis error"
            else -> "Unknown error ($errorCode)"
        }
    }

    @Command
    fun speak(invoke: Invoke) {
        Log.i(TAG, "============================================")
        Log.i(TAG, "speak() CALLED")
        val args = invoke.parseArgs(SpeakArgs::class.java)
        Log.d(TAG, "  Text: \"${args.text.take(50)}${if (args.text.length > 50) "..." else ""}\"")
        Log.d(TAG, "  Language: ${args.language ?: "default"}")
        Log.d(TAG, "  VoiceId: ${args.voiceId ?: "default"}")
        Log.d(TAG, "  Rate: ${args.rate}, Pitch: ${args.pitch}, Volume: ${args.volume}")
        Log.d(TAG, "  QueueMode: ${args.queueMode}")
        Log.d(TAG, "  TTS initialized: $isInitialized")
        
        if (!isInitialized) {
            Log.w(TAG, "  TTS not initialized, queuing request (queue size: ${pendingRequests.size})")
            pendingRequests.add(PendingSpeak(invoke, args))
            return
        }
        
        executeSpeakInternal(invoke, args)
        Log.i(TAG, "============================================")
    }
    
    private fun executeSpeakInternal(invoke: Invoke, args: SpeakArgs) {
        Log.d(TAG, "executeSpeakInternal() called")
        try {
            tts?.let { engine ->
                // Request audio focus before speaking
                val hasFocus = requestAudioFocus()
                Log.d(TAG, "  Audio focus requested: $hasFocus")
                
                var warning: String? = null
                
                args.voiceId?.let { voiceId ->
                    Log.d(TAG, "  Looking for voice: $voiceId")
                    val voices = engine.voices ?: emptySet()
                    val selectedVoice = voices.find { it.name == voiceId }
                    if (selectedVoice != null) {
                        engine.voice = selectedVoice
                        Log.d(TAG, "  Voice set: ${selectedVoice.name}")
                    } else {
                        Log.w(TAG, "  Voice not found: $voiceId, using default voice")
                        warning = "Voice '$voiceId' not found, using default voice"
                    }
                } ?: run {
                    args.language?.let { lang ->
                        Log.d(TAG, "  Setting language: $lang")
                        val locale = parseLocale(lang)
                        val result = engine.setLanguage(locale)
                        Log.d(TAG, "  setLanguage result: $result")
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "  Language not supported: $lang, using default")
                            warning = "Language '$lang' not supported, using default language"
                        }
                    }
                }

                val rate = args.rate.coerceIn(0.1f, 4.0f)
                val pitch = args.pitch.coerceIn(0.5f, 2.0f)
                engine.setSpeechRate(rate)
                engine.setPitch(pitch)
                Log.d(TAG, "  Rate: $rate, Pitch: $pitch")

                val params = Bundle()
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, args.volume)
                
                val utteranceId = "tts_${System.currentTimeMillis()}"
                Log.d(TAG, "  Utterance ID: $utteranceId")
                
                // Determine queue mode: QUEUE_FLUSH (default) or QUEUE_ADD
                val queueMode = if (args.queueMode.lowercase() == "add") {
                    Log.d(TAG, "  Queue mode: QUEUE_ADD")
                    TextToSpeech.QUEUE_ADD
                } else {
                    Log.d(TAG, "  Queue mode: QUEUE_FLUSH")
                    TextToSpeech.QUEUE_FLUSH
                }
                
                val speakResult = engine.speak(args.text, queueMode, params, utteranceId)
                Log.d(TAG, "  speak() result: $speakResult (SUCCESS=${TextToSpeech.SUCCESS}, ERROR=${TextToSpeech.ERROR})")

                val ret = JSObject()
                ret.put("success", true)
                ret.put("utteranceId", utteranceId)
                warning?.let { ret.put("warning", it) }
                invoke.resolve(ret)
            } ?: run {
                invoke.reject("TTS not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking: ${e.message}")
            invoke.reject("Failed to speak: ${e.message}")
        }
    }

    @Command
    fun stop(invoke: Invoke) {
        Log.i(TAG, "stop() CALLED")
        try {
            tts?.stop()
            Log.d(TAG, "  TTS stopped")
            val ret = JSObject()
            ret.put("success", true)
            invoke.resolve(ret)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop: ${e.message}", e)
            invoke.reject("Failed to stop: ${e.message}")
        }
    }

    @Command
    fun getVoices(invoke: Invoke) {
        Log.i(TAG, "getVoices() CALLED")
        val args = invoke.parseArgs(GetVoicesArgs::class.java)
        Log.d(TAG, "  Language filter: ${args.language ?: "none"}")
        
        if (!isInitialized) {
            Log.w(TAG, "  TTS not initialized")
            invoke.reject("TTS not initialized")
            return
        }

        try {
            val voices = tts?.voices ?: emptySet()
            Log.d(TAG, "  Total voices available: ${voices.size}")
            val voicesArray = JSArray()
            
            voices.forEach { voice ->
                val languageFilter = args.language?.lowercase()
                val voiceLanguage = voice.locale.toLanguageTag().lowercase()
                
                if (languageFilter == null || voiceLanguage.contains(languageFilter)) {
                    val voiceObj = JSObject()
                    voiceObj.put("id", voice.name)
                    // Use display name for better user experience
                    voiceObj.put("name", voice.locale.displayName)
                    voiceObj.put("language", voice.locale.toLanguageTag())
                    voicesArray.put(voiceObj)
                }
            }
            
            Log.d(TAG, "  Returning ${voicesArray.length()} voices (after filter)")
            val ret = JSObject()
            ret.put("voices", voicesArray)
            invoke.resolve(ret)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get voices: ${e.message}", e)
            invoke.reject("Failed to get voices: ${e.message}")
        }
    }

    @Command
    fun isSpeaking(invoke: Invoke) {
        Log.d(TAG, "isSpeaking() CALLED")
        try {
            val speaking = tts?.isSpeaking ?: false
            Log.d(TAG, "  Speaking: $speaking")
            val ret = JSObject()
            ret.put("speaking", speaking)
            invoke.resolve(ret)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check speaking status: ${e.message}", e)
            invoke.reject("Failed to check speaking status: ${e.message}")
        }
    }
    
    @Command
    fun pauseSpeaking(invoke: Invoke) {
        try {
            // Android doesn't have native pause support, we need to stop
            // and the app would need to track position for true pause/resume
            val ret = JSObject()
            ret.put("success", false)
            ret.put("reason", "Pause is not natively supported on Android")
            invoke.resolve(ret)
        } catch (e: Exception) {
            invoke.reject("Failed to pause: ${e.message}")
        }
    }
    
    @Command
    fun resumeSpeaking(invoke: Invoke) {
        try {
            val ret = JSObject()
            ret.put("success", false)
            ret.put("reason", "Resume is not natively supported on Android")
            invoke.resolve(ret)
        } catch (e: Exception) {
            invoke.reject("Failed to resume: ${e.message}")
        }
    }
    
    @Command
    fun previewVoice(invoke: Invoke) {
        Log.i(TAG, "previewVoice() CALLED")
        val args = invoke.parseArgs(PreviewVoiceArgs::class.java)
        Log.d(TAG, "  VoiceId: ${args.voiceId}")
        Log.d(TAG, "  Sample text: \"${args.sampleText().take(30)}...\"")
        
        if (!isInitialized) {
            Log.w(TAG, "  TTS not initialized")
            invoke.reject("TTS not initialized")
            return
        }
        
        try {
            tts?.let { engine ->
                // Request audio focus before speaking
                requestAudioFocus()
                
                // Stop any current speech
                engine.stop()
                Log.d(TAG, "  Stopped current speech")
                
                // Find and set the voice
                val voices = engine.voices ?: emptySet()
                val selectedVoice = voices.find { it.name == args.voiceId }
                
                if (selectedVoice != null) {
                    engine.voice = selectedVoice
                    Log.d(TAG, "  Voice set: ${selectedVoice.name}")
                } else {
                    Log.w(TAG, "  Voice not found: ${args.voiceId}")
                    val ret = JSObject()
                    ret.put("success", false)
                    ret.put("warning", "Voice '${args.voiceId}' not found")
                    invoke.resolve(ret)
                    return
                }
                
                // Use default settings for preview
                engine.setSpeechRate(1.0f)
                engine.setPitch(1.0f)
                
                val params = Bundle()
                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                
                val utteranceId = "preview_${System.currentTimeMillis()}"
                engine.speak(args.sampleText(), TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                Log.d(TAG, "  Preview started with utterance: $utteranceId")
                
                val ret = JSObject()
                ret.put("success", true)
                invoke.resolve(ret)
            } ?: run {
                Log.e(TAG, "  TTS engine is null")
                invoke.reject("TTS not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error previewing voice: ${e.message}", e)
            invoke.reject("Failed to preview voice: ${e.message}")
        }
    }

    private fun parseLocale(languageTag: String): Locale {
        Log.d(TAG, "parseLocale($languageTag)")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Locale.forLanguageTag(languageTag)
        } else {
            val parts = languageTag.split("-", "_")
            when (parts.size) {
                1 -> Locale(parts[0])
                2 -> Locale(parts[0], parts[1])
                else -> Locale(parts[0], parts[1], parts[2])
            }
        }
    }

    /**
     * Clean up TTS resources. Called when activity is destroyed.
     */
    fun cleanup() {
        Log.d(TAG, "cleanup() CALLED")
        releaseAudioFocus()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "  TTS resources released")
    }
    
    /**
     * Called when the plugin's activity is destroyed.
     * Ensures proper cleanup of TTS resources.
     */
    override fun onDestroy() {
        Log.d(TAG, "onDestroy() CALLED")
        super.onDestroy()
        cleanup()
    }
}
