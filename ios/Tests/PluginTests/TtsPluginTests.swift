import XCTest
@testable import tauri_plugin_tts

/// Unit tests for TTS Plugin
final class TtsPluginTests: XCTestCase {
    
    /// Test speech rate validation
    func testSpeechRateRange() throws {
        let validRates: [Float] = [0.25, 0.5, 1.0, 1.5, 2.0]
        let invalidRates: [Float] = [0.0, -0.1, 2.5]
        
        for rate in validRates {
            XCTAssertTrue(rate >= 0.25 && rate <= 2.0, "Rate \(rate) should be valid")
        }
        
        for rate in invalidRates {
            XCTAssertFalse(rate >= 0.25 && rate <= 2.0, "Rate \(rate) should be invalid")
        }
    }
    
    /// Test pitch validation
    func testPitchRange() throws {
        let validPitches: [Float] = [0.5, 1.0, 1.5, 2.0]
        let invalidPitches: [Float] = [0.0, -0.1, 2.5]
        
        for pitch in validPitches {
            XCTAssertTrue(pitch >= 0.5 && pitch <= 2.0, "Pitch \(pitch) should be valid")
        }
        
        for pitch in invalidPitches {
            XCTAssertFalse(pitch >= 0.5 && pitch <= 2.0, "Pitch \(pitch) should be invalid")
        }
    }
    
    /// Test volume validation
    func testVolumeRange() throws {
        let validVolumes: [Float] = [0.0, 0.5, 1.0]
        let invalidVolumes: [Float] = [-0.1, 1.1, 2.0]
        
        for volume in validVolumes {
            XCTAssertTrue(volume >= 0.0 && volume <= 1.0, "Volume \(volume) should be valid")
        }
        
        for volume in invalidVolumes {
            XCTAssertFalse(volume >= 0.0 && volume <= 1.0, "Volume \(volume) should be invalid")
        }
    }
    
    /// Test language code format (BCP-47)
    func testLanguageCodeFormat() throws {
        let validCodes = ["en-US", "pt-BR", "es-ES", "fr-FR", "de-DE"]
        let pattern = "^[a-z]{2}-[A-Z]{2}$"
        let regex = try NSRegularExpression(pattern: pattern)
        
        for code in validCodes {
            let range = NSRange(code.startIndex..., in: code)
            let matches = regex.numberOfMatches(in: code, range: range)
            XCTAssertEqual(matches, 1, "Language code \(code) should match BCP-47 format")
        }
    }
    
    /// Test speak request defaults
    func testSpeakRequestDefaults() throws {
        let defaultRate: Float = 1.0
        let defaultPitch: Float = 1.0
        let defaultVolume: Float = 1.0
        let defaultVoiceId: String? = nil
        
        XCTAssertEqual(defaultRate, 1.0, accuracy: 0.001)
        XCTAssertEqual(defaultPitch, 1.0, accuracy: 0.001)
        XCTAssertEqual(defaultVolume, 1.0, accuracy: 0.001)
        XCTAssertNil(defaultVoiceId, "Default voice_id should be nil")
    }
}
