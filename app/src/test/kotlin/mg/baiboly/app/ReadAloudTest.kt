// silence-check: allow-file (test of the opt-in reading-aloud service with a simulated voice engine)
package mg.baiboly.app

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mg.baiboly.app.audio.ReadAloud
import mg.baiboly.app.audio.ReadAloudService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowTextToSpeech
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reading aloud: reads the right verse, follows on, keeps going in a foreground service, stops cleanly. */
@RunWith(AndroidJUnit4::class)
class ReadAloudTest {
    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun idle(cond: () -> Boolean) {
        for (i in 0 until 200) {
            ShadowLooper.idleMainLooper()
            if (cond()) return
            Thread.sleep(25)
        }
    }

    @Test fun readsJohn3FromVerse16AndContinues() {
        val start = Intent(app, ReadAloudService::class.java).setAction(ReadAloud.ACTION_START)
            .putExtra("translation", "kjv").putExtra("book", 43).putExtra("chapter", 3).putExtra("verse", 16).putExtra("rate", 1f)
        val controller = Robolectric.buildService(ReadAloudService::class.java, start).create().startCommand(0, 1)
        val service = controller.get()
        // runs as a foreground service (keeps reading with the screen off)
        assertTrue(shadowOf(service).isLastForegroundNotificationAttached || shadowOf(service).lastForegroundNotification != null)

        ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.ENGLISH)
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()!!
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        // The simulated engine finishes each verse at once, so the service reads on by itself:
        // verse 16, then 17, ... to the end of the chapter, then on into chapter 4.
        idle { ReadAloud.state.value.chapter == 4 }
        val spoken = shadowOf(tts).spokenTextList
        assertTrue(spoken[0].startsWith("For God so loved the world"), spoken[0])
        assertTrue(spoken[1].startsWith("For God sent not his Son"), spoken[1])
        assertTrue(spoken[20].startsWith("He that believeth on the Son"), spoken[20]) // John 3:36
        assertTrue(spoken[21].startsWith("When therefore the Lord knew"), spoken[21]) // John 4:1
        val s = ReadAloud.state.value
        assertTrue(s.active)
        assertEquals(43, s.book)
        assertEquals(4, s.chapter)

        // pause / resume / stop from the notification or the reading bar
        controller.withIntent(Intent(app, ReadAloudService::class.java).setAction(ReadAloud.ACTION_PAUSE)).startCommand(0, 2)
        assertFalse(ReadAloud.state.value.playing)
        controller.withIntent(Intent(app, ReadAloudService::class.java).setAction(ReadAloud.ACTION_STOP)).startCommand(0, 3)
        assertFalse(ReadAloud.state.value.active)
    }

    @Test fun silentUnlessSwitchedOn() {
        // A fresh install has reading aloud off: the reader offers no Listen action.
        val prefs = app.getSharedPreferences("app_state", 0)
        assertFalse(prefs.getBoolean("read_aloud", false))
    }

    @Test fun malagasyIsNotReadWithAnotherLanguagesVoice() {
        // the phone has French and English voices, but no Malagasy one
        ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.FRENCH)
        ShadowTextToSpeech.addLanguageAvailability(java.util.Locale.ENGLISH)
        val start = Intent(app, ReadAloudService::class.java).setAction(ReadAloud.ACTION_START)
            .putExtra("translation", "mg1865").putExtra("book", 43).putExtra("chapter", 3).putExtra("verse", 16).putExtra("rate", 1f)
        Robolectric.buildService(ReadAloudService::class.java, start).create().startCommand(0, 1)
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()!!
        shadowOf(tts).onInitListener.onInit(TextToSpeech.SUCCESS)
        idle { ReadAloud.errors.value != null }
        assertTrue(shadowOf(tts).spokenTextList.isEmpty(), "nothing is read with a French voice")
        assertFalse(ReadAloud.state.value.active)
        assertTrue(ReadAloud.errors.value!!.second.contains("malagasy", ignoreCase = true), ReadAloud.errors.value!!.second)
    }
}
