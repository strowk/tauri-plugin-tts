import AVFoundation
import SwiftRs
import Tauri
import UIKit
import WebKit

class SpeakArgs: Decodable {
    let text: String
    let language: String?
    let voiceId: String?
    let rate: Float?
    let pitch: Float?
    let volume: Float?
    let queueMode: String? // "flush" (default) or "add"
}

class GetVoicesArgs: Decodable {
    let language: String?
}

class PreviewVoiceArgs: Decodable {
    let voiceId: String
    let text: String?
    
    /// Returns the sample text or a default preview message
    var sampleText: String {
        return text ?? "Hello! This is a sample of how this voice sounds."
    }
}

class TtsPlugin: Plugin, AVSpeechSynthesizerDelegate {
    private let synthesizer = AVSpeechSynthesizer()
    private var currentUtteranceId: String?
    private var wasInterrupted: Bool = false
    
    override init() {
        super.init()
        NSLog("[TtsPlugin] ============================================")
        NSLog("[TtsPlugin] PLUGIN INIT")
        NSLog("[TtsPlugin]   iOS Version: \(UIDevice.current.systemVersion)")
        NSLog("[TtsPlugin]   Device: \(UIDevice.current.model)")
        synthesizer.delegate = self
        NSLog("[TtsPlugin]   Synthesizer delegate set")
        setupAudioSession()
        setupInterruptionHandling()
        NSLog("[TtsPlugin] ============================================")
    }
    
    /// Configure audio session for speech synthesis
    private func setupAudioSession() {
        NSLog("[TtsPlugin] setupAudioSession() CALLED")
        do {
            let session = AVAudioSession.sharedInstance()
            // Use playback category with spoken audio mode for TTS
            try session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
            try session.setActive(true)
            NSLog("[TtsPlugin]   Audio session configured: category=playback, mode=spokenAudio")
        } catch {
            NSLog("[TtsPlugin]   ERROR: Failed to configure audio session: \(error.localizedDescription)")
        }
    }
    
    /// Setup observers for audio session interruptions (phone calls, Siri, etc.)
    private func setupInterruptionHandling() {
        NSLog("[TtsPlugin] setupInterruptionHandling() CALLED")
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioSessionInterruption),
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance()
        )
        
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioRouteChange),
            name: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance()
        )
        NSLog("[TtsPlugin]   Observers registered")
    }
    
    /// Handles audio route changes (headphones plugged/unplugged, Bluetooth, etc.)
    @objc private func handleAudioRouteChange(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let reasonValue = userInfo[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: reasonValue) else {
            return
        }
        
        switch reason {
        case .oldDeviceUnavailable:
            // Headphones disconnected - pause speech if playing
            if synthesizer.isSpeaking {
                synthesizer.pauseSpeaking(at: .word)
                NSLog("[TtsPlugin] Speech paused due to audio route change (device unavailable)")
                trigger("speech:pause", data: JSObject())
            }
        case .newDeviceAvailable:
            NSLog("[TtsPlugin] New audio device available")
        default:
            break
        }
    }
    
    /// Handle audio interruptions such as phone calls
    @objc private func handleAudioSessionInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else {
            return
        }
        
        switch type {
        case .began:
            // Interruption began - TTS will be paused automatically
            if synthesizer.isSpeaking {
                wasInterrupted = true
                synthesizer.pauseSpeaking(at: .word)
                NSLog("[TtsPlugin] Speech paused due to interruption")
                
                trigger("speech:interrupted", data: JSObject())
            }
            
        case .ended:
            // Interruption ended - check if we should resume
            guard let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt else { return }
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            
            if options.contains(.shouldResume) && wasInterrupted {
                // Reactivate audio session and resume speech
                do {
                    try AVAudioSession.sharedInstance().setActive(true)
                    synthesizer.continueSpeaking()
                    NSLog("[TtsPlugin] Speech resumed after interruption")
                } catch {
                    NSLog("[TtsPlugin] Failed to resume after interruption: \(error.localizedDescription)")
                }
            }
            wasInterrupted = false
            
        @unknown default:
            break
        }
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    
    // MARK: - AVSpeechSynthesizerDelegate
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        var event = JSObject()
        if let id = currentUtteranceId {
            event["id"] = id
        }
        trigger("speech:start", data: event)
        NSLog("[TtsPlugin] Speech started")
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        var event = JSObject()
        if let id = currentUtteranceId {
            event["id"] = id
        }
        trigger("speech:finish", data: event)
        currentUtteranceId = nil
        NSLog("[TtsPlugin] Speech finished")
        
        // Optionally deactivate audio session to allow other audio
        // try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        var event = JSObject()
        if let id = currentUtteranceId {
            event["id"] = id
        }
        trigger("speech:cancel", data: event)
        currentUtteranceId = nil
        NSLog("[TtsPlugin] Speech cancelled")
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didPause utterance: AVSpeechUtterance) {
        trigger("speech:pause", data: JSObject())
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didContinue utterance: AVSpeechUtterance) {
        trigger("speech:resume", data: JSObject())
    }
    
    // MARK: - Commands
    
    @objc public func speak(_ invoke: Invoke) throws {
        NSLog("[TtsPlugin] ============================================")
        NSLog("[TtsPlugin] speak() CALLED")
        
        let args = try invoke.parseArgs(SpeakArgs.self)
        NSLog("[TtsPlugin]   Text length: \(args.text.count) chars")
        NSLog("[TtsPlugin]   Language: \(args.language ?? "default")")
        NSLog("[TtsPlugin]   VoiceId: \(args.voiceId ?? "default")")
        NSLog("[TtsPlugin]   Rate: \(args.rate ?? 1.0)")
        NSLog("[TtsPlugin]   Pitch: \(args.pitch ?? 1.0)")
        NSLog("[TtsPlugin]   Volume: \(args.volume ?? 1.0)")
        NSLog("[TtsPlugin]   QueueMode: \(args.queueMode ?? "flush")")
        
        // Ensure audio session is active
        setupAudioSession()
        
        // Handle queue mode: "flush" (default) stops current speech, "add" queues it
        let shouldFlush = (args.queueMode ?? "flush").lowercased() != "add"
        if shouldFlush && synthesizer.isSpeaking {
            NSLog("[TtsPlugin]   Flushing current speech")
            synthesizer.stopSpeaking(at: .immediate)
        }
        
        let utterance = AVSpeechUtterance(string: args.text)
        
        // Generate utterance ID for tracking
        currentUtteranceId = UUID().uuidString
        
        var warning: String? = nil
        
        if let voiceId = args.voiceId {
            if let voice = AVSpeechSynthesisVoice.speechVoices().first(where: { $0.identifier == voiceId }) {
                utterance.voice = voice
            } else {
                NSLog("[TtsPlugin] Voice not found: \(voiceId), using default")
                warning = "Voice '\(voiceId)' not found, using default voice"
            }
        } else if let language = args.language {
            if let voice = AVSpeechSynthesisVoice(language: language) {
                utterance.voice = voice
            } else {
                NSLog("[TtsPlugin] Language not supported: \(language), using default")
                warning = "Language '\(language)' not supported, using default language"
            }
        }
        
        if let rate = args.rate {
            let normalizedRate = rate * 0.5
            utterance.rate = min(max(normalizedRate, AVSpeechUtteranceMinimumSpeechRate), AVSpeechUtteranceMaximumSpeechRate)
        }
        
        if let pitch = args.pitch {
            utterance.pitchMultiplier = min(max(pitch, 0.5), 2.0)
        }
        
        if let volume = args.volume {
            utterance.volume = min(max(volume, 0.0), 1.0)
        }
        
        synthesizer.speak(utterance)
        
        var response: [String: Any] = [
            "success": true,
            "utteranceId": currentUtteranceId as Any
        ]
        if let w = warning {
            response["warning"] = w
        }
        invoke.resolve(response)
    }
    
    @objc public func stop(_ invoke: Invoke) throws {
        NSLog("[TtsPlugin] ============================================")
        NSLog("[TtsPlugin] stop() CALLED")
        NSLog("[TtsPlugin]   isSpeaking: \(synthesizer.isSpeaking)")
        synthesizer.stopSpeaking(at: .immediate)
        NSLog("[TtsPlugin]   Speech stopped")
        invoke.resolve(["success": true])
    }
    
    @objc public func pauseSpeaking(_ invoke: Invoke) throws {
        NSLog("[TtsPlugin] pauseSpeaking() CALLED")
        NSLog("[TtsPlugin]   isSpeaking: \(synthesizer.isSpeaking), isPaused: \(synthesizer.isPaused)")
        
        if synthesizer.isSpeaking && !synthesizer.isPaused {
            synthesizer.pauseSpeaking(at: .word)
            NSLog("[TtsPlugin]   Speech paused")
            invoke.resolve(["success": true])
        } else {
            let reason = synthesizer.isPaused ? "Already paused" : "Not speaking"
            NSLog("[TtsPlugin]   Cannot pause: \(reason)")
            invoke.resolve(["success": false, "reason": reason])
        }
    }
    
    @objc public func resumeSpeaking(_ invoke: Invoke) throws {
        NSLog("[TtsPlugin] resumeSpeaking() CALLED")
        NSLog("[TtsPlugin]   isPaused: \(synthesizer.isPaused)")
        
        if synthesizer.isPaused {
            synthesizer.continueSpeaking()
            NSLog("[TtsPlugin]   Speech resumed")
            invoke.resolve(["success": true])
        } else {
            NSLog("[TtsPlugin]   Cannot resume: Not paused")
            invoke.resolve(["success": false, "reason": "Not paused"])
        }
    }
    
    @objc public func getVoices(_ invoke: Invoke) throws {
        NSLog("[TtsPlugin] getVoices() CALLED")
        
        let args = try invoke.parseArgs(GetVoicesArgs.self)
        let allVoices = AVSpeechSynthesisVoice.speechVoices()
        NSLog("[TtsPlugin]   Total available voices: \(allVoices.count)")
        NSLog("[TtsPlugin]   Language filter: \(args.language ?? "none")")
        
        var voices: [[String: String]] = []
        
        for voice in allVoices {
            let languageFilter = args.language?.lowercased()
            let voiceLanguage = voice.language.lowercased()
            
            if languageFilter == nil || voiceLanguage.contains(languageFilter!) {
                voices.append([
                    "id": voice.identifier,
                    "name": voice.name,
                    "language": voice.language
                ])
            }
        }
        
        NSLog("[TtsPlugin]   Filtered voices: \(voices.count)")
        invoke.resolve(["voices": voices])
    }
    
    @objc public func isSpeaking(_ invoke: Invoke) throws {
        NSLog("[TtsPlugin] isSpeaking() CALLED")
        NSLog("[TtsPlugin]   Speaking: \(synthesizer.isSpeaking), Paused: \(synthesizer.isPaused)")
        invoke.resolve([
            "speaking": synthesizer.isSpeaking,
            "paused": synthesizer.isPaused
        ])
    }
    
    @objc public func previewVoice(_ invoke: Invoke) throws {
        NSLog("[TtsPlugin] ============================================")
        NSLog("[TtsPlugin] previewVoice() CALLED")
        
        let args = try invoke.parseArgs(PreviewVoiceArgs.self)
        NSLog("[TtsPlugin]   VoiceId: \(args.voiceId)")
        NSLog("[TtsPlugin]   Sample text: \(args.sampleText)")
        
        // Ensure audio session is active
        setupAudioSession()
        
        // Stop any current speech
        if synthesizer.isSpeaking {
            NSLog("[TtsPlugin]   Stopping current speech")
            synthesizer.stopSpeaking(at: .immediate)
        }
        
        let utterance = AVSpeechUtterance(string: args.sampleText)
        currentUtteranceId = UUID().uuidString
        
        // Set the voice to preview
        if let voice = AVSpeechSynthesisVoice.speechVoices().first(where: { $0.identifier == args.voiceId }) {
            utterance.voice = voice
            NSLog("[TtsPlugin]   Voice found: \(voice.name)")
        } else {
            NSLog("[TtsPlugin]   ERROR: Voice not found: \(args.voiceId)")
            invoke.resolve(["success": false, "warning": "Voice '\(args.voiceId)' not found"])
            return
        }
        
        // Use default settings for preview
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        utterance.pitchMultiplier = 1.0
        utterance.volume = 1.0
        
        synthesizer.speak(utterance)
        NSLog("[TtsPlugin]   Preview started")
        invoke.resolve(["success": true])
    }
}

@_cdecl("init_plugin_tts")
func initPlugin() -> Plugin {
    return TtsPlugin()
}

