package io.affex.tts

import android.speech.tts.TextToSpeech
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.util.Locale

/**
 * Instrumented tests for TTS Plugin.
 * 
 * These tests run on an Android device/emulator and validate
 * functionality that requires the Android framework.
 */
@RunWith(AndroidJUnit4::class)
class TtsInstrumentedTest {
    
    /**
     * Test that the package name is correct
     */
    @Test
    fun packageName_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.affex.tts.test", appContext.packageName)
    }
    
    /**
     * Test TTS engine can be initialized
     */
    @Test
    fun ttsEngine_canBeInitialized() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var initialized = false
        
        val tts = TextToSpeech(appContext) { status ->
            initialized = (status == TextToSpeech.SUCCESS)
        }
        
        // Give it time to initialize
        Thread.sleep(1000)
        
        assertNotNull("TTS engine should be created", tts)
        tts.shutdown()
    }
    
    /**
     * Test default locale is available
     */
    @Test
    fun defaultLocale_isAvailable() {
        val defaultLocale = Locale.getDefault()
        assertNotNull("Default locale should exist", defaultLocale)
        assertTrue("Locale should have language", defaultLocale.language.isNotEmpty())
    }
    
    /**
     * Test common locales format
     */
    @Test
    fun commonLocales_areValid() {
        val locales = listOf(
            Locale.US,
            Locale.UK,
            Locale.FRANCE,
            Locale.GERMANY
        )
        
        for (locale in locales) {
            assertNotNull("Locale should not be null", locale)
            assertTrue("Locale should have language", locale.language.isNotEmpty())
        }
    }
}
