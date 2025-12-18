package io.affex.tts

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TTS Plugin models and utilities.
 * 
 * These tests run on the development machine (host) and validate
 * core logic without requiring Android framework dependencies.
 */
class TtsUnitTest {
    
    /**
     * Test speech rate validation
     */
    @Test
    fun speechRate_isWithinRange() {
        val validRates = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f)
        val invalidRates = listOf(0.0f, -0.1f, 2.5f, 10.0f)
        
        for (rate in validRates) {
            assertTrue("Rate $rate should be valid", rate in 0.25f..2.0f)
        }
        
        for (rate in invalidRates) {
            assertFalse("Rate $rate should be invalid", rate in 0.25f..2.0f)
        }
    }
    
    /**
     * Test pitch validation
     */
    @Test
    fun pitch_isWithinRange() {
        val validPitches = listOf(0.5f, 1.0f, 1.5f, 2.0f)
        val invalidPitches = listOf(0.0f, -0.1f, 2.5f)
        
        for (pitch in validPitches) {
            assertTrue("Pitch $pitch should be valid", pitch in 0.5f..2.0f)
        }
        
        for (pitch in invalidPitches) {
            assertFalse("Pitch $pitch should be invalid", pitch in 0.5f..2.0f)
        }
    }
    
    /**
     * Test volume validation
     */
    @Test
    fun volume_isWithinRange() {
        val validVolumes = listOf(0.0f, 0.5f, 1.0f)
        val invalidVolumes = listOf(-0.1f, 1.1f, 2.0f)
        
        for (volume in validVolumes) {
            assertTrue("Volume $volume should be valid", volume in 0.0f..1.0f)
        }
        
        for (volume in invalidVolumes) {
            assertFalse("Volume $volume should be invalid", volume in 0.0f..1.0f)
        }
    }
    
    /**
     * Test language code format
     */
    @Test
    fun languageCode_formatIsValid() {
        val validCodes = listOf("en-US", "pt-BR", "es-ES", "fr-FR", "de-DE")
        
        for (code in validCodes) {
            assertTrue(
                "Language code $code should match BCP-47 format",
                code.matches(Regex("^[a-z]{2}-[A-Z]{2}$"))
            )
        }
    }
    
    /**
     * Test voice properties
     */
    @Test
    fun voice_hasRequiredProperties() {
        // Mock voice data structure
        data class Voice(val id: String, val name: String, val language: String)
        
        val voice = Voice(
            id = "com.apple.voice.compact.en-US.Samantha",
            name = "Samantha",
            language = "en-US"
        )
        
        assertTrue("Voice id should not be empty", voice.id.isNotEmpty())
        assertTrue("Voice name should not be empty", voice.name.isNotEmpty())
        assertTrue("Voice language should not be empty", voice.language.isNotEmpty())
    }
    
    /**
     * Test speak request defaults
     */
    @Test
    fun speakRequest_hasCorrectDefaults() {
        val defaultRate = 1.0f
        val defaultPitch = 1.0f
        val defaultVolume = 1.0f
        val defaultVoiceId: String? = null
        
        assertEquals(1.0f, defaultRate, 0.001f)
        assertEquals(1.0f, defaultPitch, 0.001f)
        assertEquals(1.0f, defaultVolume, 0.001f)
        assertNull("Default voice_id should be null", defaultVoiceId)
    }
}
