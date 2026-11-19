// silence-check: allow-file (reading aloud: opt-in in Settings, started only by the user's tap, never by itself)
package mg.baiboly.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mg.baiboly.app.MainActivity
import mg.baiboly.app.R
import mg.baiboly.app.UiLanguage
import mg.baiboly.core.bible.Canon
import mg.baiboly.data.AssetDatabases
import mg.baiboly.data.BibleRepository
import mg.baiboly.data.VerseText
import java.util.Locale
import java.util.concurrent.Executors

/** What is being read aloud, observed by the reader to highlight and follow the verse. */
data class ReadAloudState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val translation: String = "",
    val book: Int = 0,
    val chapter: Int = 0,
    val verse: Int = 0,
    /** Set when the text's language has no voice on the phone and another voice is used. */
    val fallbackVoice: String? = null,
    val error: String? = null,
)

/** Entry points used by the UI. Nothing here starts on its own. */
object ReadAloud {
    internal val mutableState = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = mutableState

    const val ACTION_START = "mg.baiboly.READ_START"
    const val ACTION_PAUSE = "mg.baiboly.READ_PAUSE"
    const val ACTION_RESUME = "mg.baiboly.READ_RESUME"
    const val ACTION_STOP = "mg.baiboly.READ_STOP"
    const val ACTION_NEXT = "mg.baiboly.READ_NEXT"
    const val ACTION_PREVIOUS = "mg.baiboly.READ_PREVIOUS"

    fun start(context: Context, translation: String, book: Int, chapter: Int, verse: Int, rate: Float) {
        val i = Intent(context, ReadAloudService::class.java).setAction(ACTION_START)
            .putExtra("translation", translation).putExtra("book", book).putExtra("chapter", chapter)
            .putExtra("verse", verse).putExtra("rate", rate)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
    }

    fun send(context: Context, action: String) {
        if (!state.value.active) return
        context.startService(Intent(context, ReadAloudService::class.java).setAction(action))
    }
}

/**
 * Reads the Bible aloud with the phone's own text-to-speech engine, verse by
 * verse, continuing into the next chapter. Runs as a media foreground service
 * so it goes on with the screen off; controls are in a silent notification and
 * on the lock screen. Pauses when another app takes the audio.
 */
class ReadAloudService : Service() {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var bible: BibleRepository
    private lateinit var session: MediaSession
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    private var translation = BibleRepository.PRIMARY
    private var book = 43
    private var chapter = 1
    private var verses: List<VerseText> = emptyList()
    private var index = 0
    private var rate = 1f
    private var playing = false
    /** The user's intent: false after Pause, so a chapter that finishes loading does not restart reading. */
    private var wantPlaying = false
    private var pendingStart = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        bible = BibleRepository(AssetDatabases(this))
        session = MediaSession(this, "BaibolyReadAloud").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stopReading()
                override fun onSkipToNext() = skip(+1)
                override fun onSkipToPrevious() = skip(-1)
            })
            isActive = true
        }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ReadAloud.ACTION_START -> {
                startInForeground()
                translation = intent.getStringExtra("translation") ?: BibleRepository.PRIMARY
                book = intent.getIntExtra("book", 43)
                chapter = intent.getIntExtra("chapter", 1)
                rate = intent.getFloatExtra("rate", 1f)
                val verse = intent.getIntExtra("verse", 1)
                wantPlaying = true
                ensureTts { loadChapter(startVerse = verse) }
            }
            ReadAloud.ACTION_PAUSE -> pause()
            ReadAloud.ACTION_RESUME -> resume()
            ReadAloud.ACTION_NEXT -> skip(+1)
            ReadAloud.ACTION_PREVIOUS -> skip(-1)
            ReadAloud.ACTION_STOP -> stopReading()
            else -> if (!ReadAloud.state.value.active) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ text to speech

    private fun ensureTts(then: () -> Unit) {
        if (tts != null && ttsReady) return then()
        pendingStart = true
        tts = TextToSpeech(applicationContext) { status ->
            main.post {
                if (status != TextToSpeech.SUCCESS) {
                    publish(error = getStringLocalized(R.string.tts_unavailable))
                    stopReading()
                    return@post
                }
                ttsReady = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        main.post { if (playing && utteranceId == currentId()) advance() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { if (playing && utteranceId == currentId()) advance() }
                    }
                })
                if (pendingStart) {
                    pendingStart = false
                    then()
                }
            }
        }
    }

    /** Voice for the text's language; Malagasy falls back to French (close spelling) when no Malagasy voice exists. */
    private fun chooseVoice(): String? {
        val t = tts ?: return null
        val lang = bible.translation(translation).lang
        val wanted = when (lang) { "fr" -> Locale.FRENCH; "en" -> Locale.ENGLISH; else -> Locale("mg") }
        val ok = t.isLanguageAvailable(wanted) >= TextToSpeech.LANG_AVAILABLE
        if (ok) {
            t.language = wanted
            return null
        }
        val fallback = if (lang == "mg") Locale.FRENCH else Locale.getDefault()
        t.language = fallback
        return fallback.getDisplayLanguage(Locale(UiLanguage.current(this).ifEmpty { "en" }))
    }

    private fun currentId() = "v$book.$chapter.${verses.getOrNull(index)?.verse}"

    private fun loadChapter(startVerse: Int) {
        val (t, b, c) = Triple(translation, book, chapter)
        io.execute {
            val vs = runCatching { bible.chapter(t, b, c).verses }.getOrDefault(emptyList())
            main.post {
                verses = vs
                index = vs.indexOfFirst { it.verse >= startVerse }.coerceAtLeast(0)
                if (vs.isEmpty()) return@post stopReading()
                val fallback = chooseVoice()
                tts?.setSpeechRate(rate)
                publish(fallbackVoice = fallback)
                if (wantPlaying) startPlaying() else updateSession()
            }
        }
    }

    private fun startPlaying() {
        if (!requestFocus()) return pause()
        playing = true
        acquireWakeLock()
        speakCurrent()
    }

    private fun speakCurrent() {
        val v = verses.getOrNull(index) ?: return
        tts?.speak(v.text, TextToSpeech.QUEUE_FLUSH, Bundle(), currentId())
        publish()
        updateSession()
    }

    private fun advance() {
        index++
        if (index < verses.size) return speakCurrent()
        // next chapter, in the text's own chapter numbering
        val counts = runCatching { bible.chapterCounts(translation) }.getOrDefault(emptyMap())
        val next = Canon.nextChapter(book, chapter, counts.ifEmpty { null }) ?: return stopReading()
        book = next.first
        chapter = next.second
        loadChapter(startVerse = 1)
    }

    private fun skip(delta: Int) {
        if (verses.isEmpty()) return
        index = (index + delta).coerceIn(0, verses.lastIndex)
        if (playing) speakCurrent() else publish()
    }

    private fun pause() {
        wantPlaying = false
        playing = false
        tts?.stop()
        releaseWakeLock()
        publish()
        updateSession()
    }

    private fun resume() {
        if (verses.isEmpty()) return
        wantPlaying = true
        startPlaying()
    }

    private fun stopReading() {
        wantPlaying = false
        playing = false
        tts?.stop()
        releaseWakeLock()
        abandonFocus()
        ReadAloud.mutableState.value = ReadAloudState()
        session.isActive = false
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        releaseWakeLock()
        abandonFocus()
        session.release()
        io.shutdown()
        ReadAloud.mutableState.value = ReadAloudState()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ state, notification, session

    private fun publish(fallbackVoice: String? = ReadAloud.state.value.fallbackVoice, error: String? = null) {
        val v = verses.getOrNull(index)?.verse ?: 0
        ReadAloud.mutableState.value = ReadAloudState(true, playing, translation, book, chapter, v, fallbackVoice, error)
        if (ReadAloud.state.value.active) notificationManager().notify(NOTIFICATION_ID, buildNotification())
    }

    private fun title(): String {
        val lang = runCatching { bible.translation(translation).lang }.getOrDefault("mg")
        val v = verses.getOrNull(index)?.verse
        return Canon.book(book).name(lang) + " " + chapter + (v?.let { ": $it" } ?: "")
    }

    private fun updateSession() {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, runCatching { bible.translation(translation).name }.getOrDefault(""))
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, getStringLocalized(R.string.read_aloud), NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null) // the notification itself makes no sound
            ch.enableVibration(false)
            ch.setShowBadge(false)
            notificationManager().createNotificationChannel(ch)
        }
    }

    private fun action(icon: Int, label: Int, action: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, action.hashCode(), Intent(this, ReadAloudService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        return Notification.Action.Builder(icon, getStringLocalized(label), pi).build()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_stat_read)
            .setContentTitle(title())
            .setContentText(runCatching { bible.translation(translation).name }.getOrDefault(""))
            .setContentIntent(open)
            .setOngoing(playing)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(action(android.R.drawable.ic_media_previous, R.string.previous_verse, ReadAloud.ACTION_PREVIOUS))
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, R.string.pause, ReadAloud.ACTION_PAUSE)
                else action(android.R.drawable.ic_media_play, R.string.resume, ReadAloud.ACTION_RESUME)
            )
            .addAction(action(android.R.drawable.ic_media_next, R.string.next_verse, ReadAloud.ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, R.string.stop, ReadAloud.ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
        if (Build.VERSION.SDK_INT < 26) {
            @Suppress("DEPRECATION")
            b.setPriority(Notification.PRIORITY_LOW).setSound(null).setVibrate(null)
        }
        return b.build()
    }

    private fun startInForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun getStringLocalized(id: Int): String = UiLanguage.wrap(this).getString(id)

    // ------------------------------------------------------------------ audio focus and wake lock

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) main.post { pause() }
    }

    private fun requestFocus(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { am.abandonAudioFocusRequest(it) }
        else @Suppress("DEPRECATION") am.abandonAudioFocus(focusListener)
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Baiboly:ReadAloud").apply { acquire(3 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL = "read_aloud"
        private const val NOTIFICATION_ID = 7
    }
}
